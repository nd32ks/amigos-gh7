/* Kinbridge frontend — view routing, companion chat, and daily journal. */

const HISTORY_LIMIT = 20;
const GREETING = 'Hello! I\'m Kin, your companion. How are you today?';

const VIEWS = ['home', 'chat', 'journal', 'wellness', 'play'];

const DEFAULT_GAME_PLAN = Object.freeze({
  key: 'welcome',
  difficulty: 'easy',
  gamesPerWeek: 2,
  optionalDifficulty: 'medium',
  title: 'Start with a smile',
  description: 'There is no score to guide today’s rhythm yet. Begin with two gentle games this week, or choose any level that feels fun.',
});

let activeCrosswordDifficulty = 'easy';
let crosswordSessions = {};
let recreationPlan = DEFAULT_GAME_PLAN;
let recreationPlanError = false;
let hasChosenCrossword = false;

/* ---------------- State (replaced immutably, never mutated) ---------------- */

let chatMessages = loadChatHistory();
let isSending = false;

function todayKey() {
  const now = new Date();
  const pad = (value) => String(value).padStart(2, '0');
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

function storageKey() {
  return `kinbridge:chat:${todayKey()}`;
}

function loadChatHistory() {
  try {
    const raw = localStorage.getItem(storageKey());
    const parsed = raw ? JSON.parse(raw) : [];
    if (Array.isArray(parsed) && parsed.length > 0) {
      return parsed;
    }
  } catch {
    // Corrupt storage — fall through to a fresh conversation.
  }
  return [{ role: 'model', text: GREETING }];
}

function saveChatHistory(messages) {
  try {
    localStorage.setItem(storageKey(), JSON.stringify(messages));
  } catch {
    // Storage may be full or blocked; the chat still works in memory.
  }
}

/* ---------------- Routing ---------------- */

function currentRoute() {
  const name = window.location.hash.replace(/^#\//, '');
  return VIEWS.includes(name) ? name : 'home';
}

function showView(name) {
  for (const view of VIEWS) {
    const section = document.getElementById(`view-${view}`);
    section.hidden = view !== name;
  }
  for (const link of document.querySelectorAll('[data-nav]')) {
    link.classList.toggle('active', link.dataset.nav === name);
  }
  if (name === 'chat') {
    renderChat();
    document.getElementById('chat-input').focus();
  }
  if (name === 'journal') {
    loadJournalDays();
  }
  if (name === 'wellness') {
    loadWellness();
  }
  if (name === 'play') {
    loadRecreation();
  }
}

window.addEventListener('hashchange', () => showView(currentRoute()));

/* ---------------- Chat ---------------- */

function bubbleElement(message) {
  const div = document.createElement('div');
  div.className = `bubble ${message.role === 'user' ? 'bubble-user' : 'bubble-model'}`;
  div.textContent = message.text;
  return div;
}

function typingElement() {
  const div = document.createElement('div');
  div.className = 'bubble-typing';
  div.textContent = 'Kin is thinking';
  const dot = document.createElement('span');
  dot.className = 'pulse';
  div.appendChild(dot);
  return div;
}

function renderChat() {
  const container = document.getElementById('chat-messages');
  container.replaceChildren(...chatMessages.map(bubbleElement));
  if (isSending) {
    container.appendChild(typingElement());
  }
  container.lastElementChild?.scrollIntoView({ block: 'end' });
}

function setStatus(text) {
  const status = document.getElementById('chat-status');
  status.textContent = text ?? '';
  status.hidden = !text;
}

function apiPayload(messages) {
  // The greeting is client-side only; Gemini expects history to start with
  // the user, so send from the first user message onward.
  const firstUserIndex = messages.findIndex((message) => message.role === 'user');
  if (firstUserIndex === -1) {
    return [];
  }
  return messages.slice(firstUserIndex).slice(-HISTORY_LIMIT);
}

async function sendMessage(text) {
  chatMessages = [...chatMessages, { role: 'user', text }];
  saveChatHistory(chatMessages);
  isSending = true;
  setStatus(null);
  setSendEnabled(false);
  renderChat();

  try {
    const response = await fetch('/api/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ messages: apiPayload(chatMessages) }),
    });
    const result = await response.json();
    if (!response.ok || !result.success) {
      throw new Error(result.error || `Request failed (${response.status})`);
    }
    chatMessages = [...chatMessages, { role: 'model', text: result.data.reply }];
    saveChatHistory(chatMessages);
  } catch (error) {
    console.error('Chat request failed:', error);
    setStatus('Kin couldn\'t answer just now. Please try sending your message again.');
  } finally {
    isSending = false;
    setSendEnabled(true);
    renderChat();
  }
}

function setSendEnabled(enabled) {
  document.getElementById('chat-send').disabled = !enabled;
}

function setupChatForm() {
  const form = document.getElementById('chat-form');
  const input = document.getElementById('chat-input');

  const submit = () => {
    const text = input.value.trim();
    if (!text || isSending) {
      return;
    }
    input.value = '';
    input.style.height = 'auto';
    sendMessage(text);
  };

  form.addEventListener('submit', (event) => {
    event.preventDefault();
    submit();
  });

  input.addEventListener('keydown', (event) => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      submit();
    }
  });

  input.addEventListener('input', () => {
    input.style.height = 'auto';
    input.style.height = `${Math.min(input.scrollHeight, 140)}px`;
  });
}

/* ---------------- Journal ---------------- */

async function fetchJson(url) {
  const response = await fetch(url);
  const result = await response.json();
  if (!response.ok || !result.success) {
    throw new Error(result.error || `Request failed (${response.status})`);
  }
  return result.data;
}

function formatDayLabel(stamp) {
  const [year, month, day] = stamp.split('-').map(Number);
  const date = new Date(year, month - 1, day);
  return date.toLocaleDateString(undefined, {
    weekday: 'short', year: 'numeric', month: 'long', day: 'numeric',
  });
}

async function loadJournalDays() {
  const daysContainer = document.getElementById('journal-days');
  const emptyNote = document.getElementById('journal-empty');
  const page = document.getElementById('journal-page');

  try {
    const { days } = await fetchJson('/api/logs');
    emptyNote.hidden = days.length > 0;
    if (days.length === 0) {
      daysContainer.replaceChildren();
      page.hidden = true;
      return;
    }
    daysContainer.replaceChildren(...days.map((day) => dayPill(day)));
    openJournalPage(days[0]);
  } catch (error) {
    console.error('Failed to load journal days:', error);
    emptyNote.hidden = false;
    emptyNote.textContent = 'The journal could not be loaded. Please try again.';
  }
}

function dayPill(day) {
  const button = document.createElement('button');
  button.type = 'button';
  button.className = 'day-pill';
  button.dataset.day = day;
  button.textContent = formatDayLabel(day);
  button.addEventListener('click', () => openJournalPage(day));
  return button;
}

async function openJournalPage(day) {
  for (const pill of document.querySelectorAll('.day-pill')) {
    pill.classList.toggle('selected', pill.dataset.day === day);
  }
  const page = document.getElementById('journal-page');
  try {
    const { content } = await fetchJson(`/api/logs/${day}`);
    document.getElementById('journal-page-date').textContent = formatDayLabel(day);
    document.getElementById('journal-page-content').textContent = content;
    page.hidden = false;
  } catch (error) {
    console.error('Failed to load journal page:', error);
    document.getElementById('journal-page-date').textContent = formatDayLabel(day);
    document.getElementById('journal-page-content').textContent =
      'This page could not be loaded. Please try again.';
    page.hidden = false;
  }
}

/* ---------------- Wellness dashboard ---------------- */

const VERDICT_CHIPS = {
  exact: { text: '✓ Remembered', className: 'chip-good' },
  partial: { text: '◐ Partly', className: 'chip-warn' },
  miss: { text: '✕ Missed', className: 'chip-bad' },
  no_answer: { text: '– No answer', className: 'chip-neutral' },
};

const TIER_NAMES = { 1: 'Core', 2: 'Recent', 3: 'Preference' };

function chipElement(text, className) {
  const span = document.createElement('span');
  span.className = `chip ${className}`;
  span.textContent = text;
  return span;
}

function probeListItem(probe) {
  const li = document.createElement('li');
  const left = document.createElement('span');
  left.className = 'probe-fact';
  left.textContent = probe.label;
  const time = document.createElement('span');
  time.className = 'probe-time';
  time.textContent = probe.time;
  left.appendChild(time);
  const chip = VERDICT_CHIPS[probe.verdict] ?? VERDICT_CHIPS.no_answer;
  li.append(left, chipElement(chip.text, chip.className));
  return li;
}

function factListItem(fact) {
  const li = document.createElement('li');
  const label = document.createElement('span');
  label.className = 'probe-fact';
  label.textContent = fact.label;
  li.append(label, chipElement(TIER_NAMES[fact.tier] ?? 'Other', 'chip-neutral'));
  return li;
}

async function loadWellness() {
  try {
    const data = await fetchJson('/api/screening');
    renderWellnessStats(data);
    renderWellnessAlert(data.today.tier1Misses);
    renderTrendChart(data.trend);
    renderProbeList(data.today.probes);
    renderFactsList(data.facts);
  } catch (error) {
    console.error('Failed to load wellness data:', error);
    const alert = document.getElementById('wellness-alert');
    alert.textContent = 'Wellness data could not be loaded. Please try again.';
    alert.hidden = false;
  }
}

function renderWellnessStats(data) {
  const latest = data.trend.length > 0 ? data.trend[data.trend.length - 1] : null;
  document.getElementById('stat-index').textContent =
    data.today.index === null ? '–' : String(data.today.index);
  document.getElementById('stat-checks').textContent = String(data.today.probes.length);
  document.getElementById('stat-trend').textContent =
    latest === null ? '–' : String(latest.ewma);
}

function renderWellnessAlert(tier1Misses) {
  const alert = document.getElementById('wellness-alert');
  if (tier1Misses > 0) {
    alert.textContent = '⚠ A core memory check-in was missed today. A gentle call might be reassuring.';
    alert.hidden = false;
  } else {
    alert.hidden = true;
  }
}

function renderProbeList(probes) {
  const list = document.getElementById('probe-list');
  list.replaceChildren(...probes.map(probeListItem));
  document.getElementById('probe-empty').hidden = probes.length > 0;
}

function renderFactsList(facts) {
  const list = document.getElementById('facts-list');
  list.replaceChildren(...facts.map(factListItem));
}

/* Trend chart: single-series line, index 0-100 per day. */

const CHART = {
  width: 640,
  height: 240,
  padLeft: 40,
  padRight: 20,
  padTop: 16,
  padBottom: 30,
  line: '#0f766e',
  grid: '#ececec',
  text: '#707070',
};

const SVG_NS = 'http://www.w3.org/2000/svg';

function svgElement(tag, attributes) {
  const element = document.createElementNS(SVG_NS, tag);
  for (const [key, value] of Object.entries(attributes)) {
    element.setAttribute(key, String(value));
  }
  return element;
}

function chartScales(count) {
  const plotWidth = CHART.width - CHART.padLeft - CHART.padRight;
  const plotHeight = CHART.height - CHART.padTop - CHART.padBottom;
  const xAt = (position) => (count === 1
    ? CHART.padLeft + plotWidth / 2
    : CHART.padLeft + (plotWidth * position) / (count - 1));
  const yAt = (value) => CHART.padTop + plotHeight * (1 - value / 100);
  return { xAt, yAt };
}

function shortDate(stamp) {
  const [year, month, day] = stamp.split('-').map(Number);
  return new Date(year, month - 1, day)
    .toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
}

function renderTrendChart(trend) {
  const container = document.getElementById('trend-chart');
  const emptyNote = document.getElementById('trend-empty');
  container.replaceChildren();
  emptyNote.hidden = trend.length > 0;
  if (trend.length === 0) {
    return;
  }

  const { xAt, yAt } = chartScales(trend.length);
  const svg = svgElement('svg', {
    viewBox: `0 0 ${CHART.width} ${CHART.height}`,
    role: 'img',
    'aria-label': `Recall index by day: ${trend.map((p) => `${shortDate(p.date)} ${p.index}`).join(', ')}`,
  });

  for (const gridValue of [0, 25, 50, 75, 100]) {
    const y = yAt(gridValue);
    svg.appendChild(svgElement('line', {
      x1: CHART.padLeft, y1: y, x2: CHART.width - CHART.padRight, y2: y,
      stroke: CHART.grid, 'stroke-width': 1,
    }));
    const tick = svgElement('text', {
      x: CHART.padLeft - 8, y: y + 4, 'text-anchor': 'end',
      fill: CHART.text, 'font-size': 12,
    });
    tick.textContent = String(gridValue);
    svg.appendChild(tick);
  }

  if (trend.length > 1) {
    const path = trend
      .map((point, position) => `${position === 0 ? 'M' : 'L'}${xAt(position)},${yAt(point.index)}`)
      .join(' ');
    svg.appendChild(svgElement('path', {
      d: path, fill: 'none', stroke: CHART.line,
      'stroke-width': 2, 'stroke-linecap': 'round', 'stroke-linejoin': 'round',
    }));
  }

  trend.forEach((point, position) => {
    const x = xAt(position);
    svg.appendChild(svgElement('circle', {
      cx: x, cy: yAt(point.index), r: 4,
      fill: CHART.line, stroke: '#ffffff', 'stroke-width': 2,
    }));
    const label = svgElement('text', {
      x, y: CHART.height - 8, 'text-anchor': 'middle',
      fill: CHART.text, 'font-size': 12,
    });
    label.textContent = shortDate(point.date);
    svg.appendChild(label);
  });

  const last = trend[trend.length - 1];
  const lastY = yAt(last.index);
  const lastLabelY = lastY < CHART.padTop + 18 ? lastY + 22 : lastY - 12;
  const lastLabel = svgElement('text', {
    x: xAt(trend.length - 1), y: lastLabelY, 'text-anchor': 'middle',
    fill: CHART.line, 'font-size': 13, 'font-weight': 600,
  });
  lastLabel.textContent = String(last.index);
  svg.appendChild(lastLabel);

  container.appendChild(svg);
  attachChartTooltip(container, svg, trend, xAt, yAt);
}

function attachChartTooltip(container, svg, trend, xAt, yAt) {
  const tooltip = document.createElement('div');
  tooltip.className = 'chart-tooltip';
  tooltip.hidden = true;
  container.appendChild(tooltip);

  svg.addEventListener('mousemove', (event) => {
    const rect = svg.getBoundingClientRect();
    const scale = CHART.width / rect.width;
    const mouseX = (event.clientX - rect.left) * scale;
    const nearest = trend.reduce((best, point, position) => {
      const distance = Math.abs(xAt(position) - mouseX);
      return distance < best.distance ? { position, distance } : best;
    }, { position: 0, distance: Infinity });

    const point = trend[nearest.position];
    tooltip.textContent = `${shortDate(point.date)} — index ${point.index}`;
    tooltip.style.left = `${xAt(nearest.position) / scale}px`;
    tooltip.style.top = `${yAt(point.index) / scale}px`;
    tooltip.hidden = false;
  });

  svg.addEventListener('mouseleave', () => {
    tooltip.hidden = true;
  });
}

/* ---------------- Mind Garden — adaptive crosswords ---------------- */

const CROSSWORD_PUZZLES = Object.freeze({
  easy: {
    id: 'easy',
    level: 'Gentle',
    title: 'A sunny warm-up',
    description: 'A few familiar, feel-good words to ease into play.',
    size: 7,
    entries: [
      { id: 'sun', direction: 'across', row: 0, col: 1, answer: 'SUN', clue: 'A bright daytime star' },
      { id: 'nest', direction: 'down', row: 0, col: 3, answer: 'NEST', clue: 'A bird’s cozy home' },
      { id: 'tea', direction: 'across', row: 3, col: 3, answer: 'TEA', clue: 'A warm cup to sip' },
      { id: 'eat', direction: 'down', row: 3, col: 4, answer: 'EAT', clue: 'Have a meal' },
      { id: 'tan', direction: 'across', row: 5, col: 4, answer: 'TAN', clue: 'A golden-brown color' },
    ],
  },
  medium: {
    id: 'medium',
    level: 'Steady',
    title: 'The everyday trail',
    description: 'A satisfying mix of familiar places and small delights.',
    size: 9,
    entries: [
      { id: 'garden', direction: 'across', row: 0, col: 1, answer: 'GARDEN', clue: 'A place where flowers grow' },
      { id: 'gum', direction: 'down', row: 0, col: 1, answer: 'GUM', clue: 'Something you can chew' },
      { id: 'dance', direction: 'down', row: 0, col: 4, answer: 'DANCE', clue: 'Move to music' },
      { id: 'cloud', direction: 'across', row: 3, col: 4, answer: 'CLOUD', clue: 'A soft shape in the sky' },
      { id: 'lamp', direction: 'down', row: 3, col: 5, answer: 'LAMP', clue: 'It helps a room shine' },
      { id: 'map', direction: 'across', row: 5, col: 5, answer: 'MAP', clue: 'It helps you find the way' },
    ],
  },
  hard: {
    id: 'hard',
    level: 'Challenge',
    title: 'The curious corner',
    description: 'Longer words and more crossings for a lively stretch.',
    size: 11,
    entries: [
      { id: 'memory', direction: 'across', row: 0, col: 1, answer: 'MEMORY', clue: 'A moment you can look back on' },
      { id: 'music', direction: 'down', row: 0, col: 1, answer: 'MUSIC', clue: 'Songs and melodies' },
      { id: 'orange', direction: 'down', row: 0, col: 4, answer: 'ORANGE', clue: 'A citrus fruit' },
      { id: 'nature', direction: 'across', row: 3, col: 4, answer: 'NATURE', clue: 'The world of trees and sky' },
      { id: 'tea', direction: 'down', row: 3, col: 6, answer: 'TEA', clue: 'A leaf-steeped drink' },
      { id: 'easel', direction: 'across', row: 4, col: 6, answer: 'EASEL', clue: 'A stand for an artist’s canvas' },
      { id: 'lamp', direction: 'down', row: 4, col: 10, answer: 'LAMP', clue: 'A light by your chair' },
      { id: 'teacup', direction: 'across', row: 7, col: 5, answer: 'TEACUP', clue: 'A small cup with a handle' },
    ],
  },
});

const WEEKLY_PLAY_PROGRESS_PREFIX = 'kinbridge:mind-garden:';
let recreationRequestId = 0;

function crosswordCellKey(row, col) {
  return `${row}:${col}`;
}

function buildCrosswordModel(puzzle) {
  const cells = {};
  const entries = puzzle.entries.map((entry) => {
    const answer = entry.answer.toUpperCase();
    const cellKeys = [...answer].map((letter, position) => {
      const row = entry.row + (entry.direction === 'down' ? position : 0);
      const col = entry.col + (entry.direction === 'across' ? position : 0);
      if (row >= puzzle.size || col >= puzzle.size) {
        throw new Error(`Crossword entry ${entry.id} is outside its board.`);
      }
      const key = crosswordCellKey(row, col);
      const existing = cells[key];
      if (existing && existing.answer !== letter) {
        throw new Error(`Crossword entry ${entry.id} has a conflicting letter.`);
      }
      cells[key] = {
        key,
        row,
        col,
        answer: letter,
        entryIds: [...(existing?.entryIds ?? []), entry.id],
        number: existing?.number ?? null,
      };
      return key;
    });
    return { ...entry, answer, cellKeys, startKey: crosswordCellKey(entry.row, entry.col) };
  });

  const startCells = [...new Set(entries.map((entry) => entry.startKey))]
    .map((key) => cells[key])
    .sort((first, second) => first.row - second.row || first.col - second.col);
  const numberByStart = Object.fromEntries(startCells.map((cell, position) => [cell.key, position + 1]));
  for (const cell of startCells) {
    cells[cell.key] = { ...cell, number: numberByStart[cell.key] };
  }

  const numberedEntries = entries.map((entry) => ({
    ...entry,
    number: numberByStart[entry.startKey],
  }));
  return Object.freeze({
    puzzle,
    cells,
    entries: numberedEntries,
    entriesById: Object.fromEntries(numberedEntries.map((entry) => [entry.id, entry])),
  });
}

const CROSSWORD_MODELS = Object.freeze(
  Object.fromEntries(Object.entries(CROSSWORD_PUZZLES)
    .map(([difficulty, puzzle]) => [difficulty, buildCrosswordModel(puzzle)])),
);

function activeCrosswordModel() {
  return CROSSWORD_MODELS[activeCrosswordDifficulty];
}

function createCrosswordSession(model) {
  return {
    values: Object.fromEntries(Object.keys(model.cells).map((key) => [key, ''])),
    selectedEntryId: model.entries[0].id,
    hintedCells: [],
    hintsUsed: 0,
    completed: false,
    completionRecorded: false,
    status: 'Choose a clue to start.',
  };
}

function activeCrosswordSession() {
  const existing = crosswordSessions[activeCrosswordDifficulty];
  if (existing) {
    return existing;
  }
  const session = createCrosswordSession(activeCrosswordModel());
  crosswordSessions = { ...crosswordSessions, [activeCrosswordDifficulty]: session };
  return session;
}

function saveActiveCrosswordSession(session) {
  crosswordSessions = { ...crosswordSessions, [activeCrosswordDifficulty]: session };
}

function directionName(direction) {
  return direction === 'down' ? 'Down' : 'Across';
}

function completeEntry(session, model, entry) {
  return entry.cellKeys.every((key) => session.values[key] === model.cells[key].answer);
}

function completeCrossword(session, model) {
  return model.entries.every((entry) => completeEntry(session, model, entry));
}

function allCellsFilled(session, model) {
  return Object.keys(model.cells).every((key) => Boolean(session.values[key]));
}

function weekStamp(date = new Date()) {
  const weekStart = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  weekStart.setDate(weekStart.getDate() - ((weekStart.getDay() + 6) % 7));
  const pad = (value) => String(value).padStart(2, '0');
  return `${weekStart.getFullYear()}-${pad(weekStart.getMonth() + 1)}-${pad(weekStart.getDate())}`;
}

function weeklyPlayProgress() {
  try {
    const stored = JSON.parse(localStorage.getItem(`${WEEKLY_PLAY_PROGRESS_PREFIX}${weekStamp()}`) ?? '{}');
    return { completed: Number.isInteger(stored.completed) && stored.completed > 0 ? stored.completed : 0 };
  } catch {
    return { completed: 0 };
  }
}

function recordCompletedCrossword() {
  const next = { completed: weeklyPlayProgress().completed + 1 };
  try {
    localStorage.setItem(`${WEEKLY_PLAY_PROGRESS_PREFIX}${weekStamp()}`, JSON.stringify(next));
  } catch {
    // Storage is an optional convenience; the completed game still counts for this view.
  }
  return next.completed;
}

function fallbackPlanFromIndex(index) {
  if (!Number.isFinite(index)) {
    return DEFAULT_GAME_PLAN;
  }
  if (index < 40) {
    return {
      key: 'extra-play', difficulty: 'medium', gamesPerWeek: 4, optionalDifficulty: 'hard',
      title: 'A little extra play',
      description: 'Try four short, steady puzzles this week. If you feel like stretching, the Challenge crossword is waiting too.',
    };
  }
  if (index < 70) {
    return {
      key: 'steady-play', difficulty: 'medium', gamesPerWeek: 3, optionalDifficulty: 'easy',
      title: 'Keep a gentle rhythm',
      description: 'Three steady puzzles this week make a lovely little routine. Take them one at a time, whenever you feel fresh.',
    };
  }
  return {
    key: 'light-play', difficulty: 'easy', gamesPerWeek: 2, optionalDifficulty: 'medium',
    title: 'Keep it light and lively',
    description: 'Two gentle puzzles this week are plenty for a bright, playful check-in. Choose a steadier one whenever you want a little more.',
  };
}

function planFromWellnessData(data) {
  const trend = Array.isArray(data?.trend) ? data.trend : [];
  const latest = trend[trend.length - 1];
  const fallback = fallbackPlanFromIndex(latest?.ewma ?? data?.today?.index);
  const plan = data?.gamePlan;
  if (!plan || !CROSSWORD_PUZZLES[plan.difficulty]) {
    return fallback;
  }
  return {
    ...fallback,
    ...plan,
    gamesPerWeek: Number.isInteger(plan.gamesPerWeek) && plan.gamesPerWeek > 0
      ? plan.gamesPerWeek
      : fallback.gamesPerWeek,
  };
}

async function loadRecreation() {
  const requestId = ++recreationRequestId;
  renderRecreation();
  try {
    const data = await fetchJson('/api/screening');
    if (requestId !== recreationRequestId) {
      return;
    }
    recreationPlan = planFromWellnessData(data);
    recreationPlanError = false;
    if (!hasChosenCrossword) {
      activeCrosswordDifficulty = recreationPlan.difficulty;
    }
  } catch (error) {
    if (requestId !== recreationRequestId) {
      return;
    }
    console.error('Failed to load recreation plan:', error);
    recreationPlan = DEFAULT_GAME_PLAN;
    recreationPlanError = true;
  }
  renderRecreation();
}

function gameCountLabel(count) {
  return `${count} short ${count === 1 ? 'game' : 'games'} this week`;
}

function renderRecreation() {
  if (!document.getElementById('view-play')) {
    return;
  }
  if (!CROSSWORD_MODELS[activeCrosswordDifficulty]) {
    activeCrosswordDifficulty = recreationPlan.difficulty;
  }
  activeCrosswordSession();
  renderPlayPlan();
  renderPuzzlePicker();
  renderCrossword();
}

function renderPlayPlan() {
  const plan = recreationPlan;
  const progress = weeklyPlayProgress();
  const remaining = Math.max(plan.gamesPerWeek - progress.completed, 0);
  const title = document.getElementById('play-plan-title');
  const copy = document.getElementById('play-plan-copy');
  const count = document.getElementById('play-plan-count');
  const progressNote = document.getElementById('play-plan-progress');
  const action = document.getElementById('play-plan-action');
  const suggestedPuzzle = CROSSWORD_PUZZLES[plan.difficulty];

  title.textContent = recreationPlanError ? 'Choose what feels good today' : plan.title;
  copy.textContent = recreationPlanError
    ? 'Wellness data is taking a moment to load, so feel free to pick any crossword and play at your own pace.'
    : plan.description;
  count.textContent = gameCountLabel(plan.gamesPerWeek);
  progressNote.textContent = progress.completed > 0
    ? `${progress.completed} of ${plan.gamesPerWeek} suggested games finished this week${remaining > 0 ? ` · ${remaining} to go` : ' · lovely work'}`
    : 'No rush — one small puzzle at a time.';
  action.replaceChildren(document.createTextNode(`Play ${suggestedPuzzle.level}`));
  const arrow = document.createElement('span');
  arrow.setAttribute('aria-hidden', 'true');
  arrow.textContent = '→';
  action.appendChild(arrow);
}

function renderPuzzlePicker() {
  const picker = document.getElementById('puzzle-picker');
  const recommended = recreationPlan.difficulty;
  picker.replaceChildren(...Object.values(CROSSWORD_PUZZLES).map((puzzle) => {
    const model = CROSSWORD_MODELS[puzzle.id];
    const session = crosswordSessions[puzzle.id];
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'puzzle-choice';
    button.classList.toggle('selected', puzzle.id === activeCrosswordDifficulty);
    button.classList.toggle('recommended', puzzle.id === recommended);
    button.setAttribute('aria-pressed', String(puzzle.id === activeCrosswordDifficulty));

    const topLine = document.createElement('span');
    topLine.className = 'puzzle-choice-topline';
    const level = document.createElement('span');
    level.className = 'puzzle-choice-level';
    level.textContent = puzzle.level;
    topLine.appendChild(level);
    if (puzzle.id === recommended) {
      const badge = document.createElement('span');
      badge.className = 'puzzle-choice-badge';
      badge.textContent = 'Suggested';
      topLine.appendChild(badge);
    }

    const name = document.createElement('span');
    name.className = 'puzzle-choice-title';
    name.textContent = puzzle.title;
    const description = document.createElement('span');
    description.className = 'puzzle-choice-copy';
    description.textContent = puzzle.description;
    const meta = document.createElement('span');
    meta.className = 'puzzle-choice-meta';
    meta.textContent = session?.completed ? '✓ Completed' : `${model.entries.length} clues`;
    button.append(topLine, name, description, meta);
    button.addEventListener('click', () => selectCrossword(puzzle.id));
    return button;
  }));
}

function renderCrossword() {
  const model = activeCrosswordModel();
  const session = activeCrosswordSession();
  const { puzzle } = model;
  document.getElementById('crossword-level').textContent = `${puzzle.level} crossword`;
  document.getElementById('crossword-title').textContent = puzzle.title;
  document.getElementById('crossword-word-count').textContent = `${model.entries.length} clues`;

  const board = document.getElementById('crossword-board');
  board.style.setProperty('--crossword-grid-size', String(puzzle.size));
  board.setAttribute('aria-label', `${puzzle.level} crossword with ${model.entries.length} clues`);
  const squares = [];
  for (let row = 0; row < puzzle.size; row += 1) {
    for (let col = 0; col < puzzle.size; col += 1) {
      squares.push(crosswordSquare(model, session, row, col));
    }
  }
  board.replaceChildren(...squares);
  renderCrosswordClues();
  updateCrosswordUI();
}

function crosswordSquare(model, session, row, col) {
  const key = crosswordCellKey(row, col);
  const cell = model.cells[key];
  const square = document.createElement('div');
  if (!cell) {
    square.className = 'crossword-block';
    square.setAttribute('aria-hidden', 'true');
    return square;
  }

  square.className = 'crossword-cell';
  square.dataset.cellKey = key;
  if (cell.number !== null) {
    const number = document.createElement('span');
    number.className = 'crossword-number';
    number.textContent = String(cell.number);
    square.appendChild(number);
  }
  const input = document.createElement('input');
  input.type = 'text';
  input.maxLength = 1;
  input.inputMode = 'text';
  input.autocomplete = 'off';
  input.autocapitalize = 'characters';
  input.spellcheck = false;
  input.value = session.values[key];
  input.disabled = session.completed;
  input.dataset.cellKey = key;
  input.setAttribute('aria-label', `Row ${row + 1}, column ${col + 1}${cell.number !== null ? `, clue ${cell.number}` : ''}`);
  input.addEventListener('focus', () => selectEntryForCell(key));
  input.addEventListener('click', () => toggleEntryForCell(key));
  input.addEventListener('input', (event) => handleCrosswordInput(event, key));
  input.addEventListener('keydown', (event) => handleCrosswordKeydown(event, key));
  square.appendChild(input);
  return square;
}

function renderCrosswordClues() {
  const model = activeCrosswordModel();
  const session = activeCrosswordSession();
  const container = document.getElementById('crossword-clues');
  const groups = ['across', 'down'].map((direction) => {
    const group = document.createElement('section');
    group.className = 'clue-group';
    const heading = document.createElement('h3');
    heading.className = 'clue-group-title';
    heading.textContent = directionName(direction);
    const list = document.createElement('ol');
    list.className = 'clue-list';
    const entries = model.entries
      .filter((entry) => entry.direction === direction)
      .sort((first, second) => first.number - second.number);
    for (const entry of entries) {
      const item = document.createElement('li');
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'clue-button';
      button.dataset.clueId = entry.id;
      button.classList.toggle('selected', session.selectedEntryId === entry.id);
      button.classList.toggle('complete', completeEntry(session, model, entry));
      const number = document.createElement('span');
      number.className = 'clue-number';
      number.textContent = String(entry.number);
      const text = document.createElement('span');
      text.className = 'clue-text';
      text.textContent = entry.clue;
      button.append(number, text);
      if (completeEntry(session, model, entry)) {
        const check = document.createElement('span');
        check.className = 'clue-check';
        check.textContent = '✓';
        check.setAttribute('aria-label', 'Completed');
        button.appendChild(check);
      }
      button.addEventListener('click', () => chooseCrosswordEntry(entry.id, { focus: true, announce: true }));
      item.appendChild(button);
      list.appendChild(item);
    }
    group.append(heading, list);
    return group;
  });
  container.replaceChildren(...groups);
}

function updateCrosswordUI() {
  const model = activeCrosswordModel();
  const session = activeCrosswordSession();
  for (const square of document.querySelectorAll('.crossword-cell')) {
    const cell = model.cells[square.dataset.cellKey];
    square.classList.toggle('selected', cell.entryIds.includes(session.selectedEntryId));
    square.classList.toggle('hinted', session.hintedCells.includes(cell.key));
    square.classList.toggle('filled', Boolean(session.values[cell.key]));
  }
  document.getElementById('hint-count').textContent = `Hints used: ${session.hintsUsed}`;
  document.getElementById('crossword-status').textContent = session.status;
}

function selectCrossword(difficulty, focus = false) {
  if (!CROSSWORD_MODELS[difficulty]) {
    return;
  }
  activeCrosswordDifficulty = difficulty;
  hasChosenCrossword = true;
  activeCrosswordSession();
  renderRecreation();
  if (focus) {
    requestAnimationFrame(() => focusSelectedEntry());
  }
}

function selectedCrosswordEntry() {
  const model = activeCrosswordModel();
  const session = activeCrosswordSession();
  return model.entriesById[session.selectedEntryId] ?? model.entries[0];
}

function chooseCrosswordEntry(entryId, { focus = false, announce = false } = {}) {
  const model = activeCrosswordModel();
  const entry = model.entriesById[entryId];
  if (!entry) {
    return;
  }
  const session = activeCrosswordSession();
  const status = announce
    ? `${entry.number} ${directionName(entry.direction)} selected: ${entry.clue}`
    : session.status;
  saveActiveCrosswordSession({ ...session, selectedEntryId: entry.id, status });
  updateCrosswordUI();
  for (const clue of document.querySelectorAll('.clue-button')) {
    clue.classList.toggle('selected', clue.dataset.clueId === entry.id);
  }
  if (focus) {
    requestAnimationFrame(() => focusSelectedEntry());
  }
}

function selectEntryForCell(cellKey) {
  const model = activeCrosswordModel();
  const session = activeCrosswordSession();
  const current = model.entriesById[session.selectedEntryId];
  if (!current?.cellKeys.includes(cellKey)) {
    chooseCrosswordEntry(model.cells[cellKey].entryIds[0]);
  }
}

function toggleEntryForCell(cellKey) {
  const model = activeCrosswordModel();
  const cell = model.cells[cellKey];
  const session = activeCrosswordSession();
  const position = cell.entryIds.indexOf(session.selectedEntryId);
  const nextEntry = cell.entryIds[position >= 0 ? (position + 1) % cell.entryIds.length : 0];
  chooseCrosswordEntry(nextEntry);
}

function focusCell(cellKey) {
  document.querySelector(`.crossword-cell input[data-cell-key="${cellKey}"]`)?.focus();
}

function focusSelectedEntry() {
  const entry = selectedCrosswordEntry();
  const session = activeCrosswordSession();
  focusCell(entry.cellKeys.find((key) => !session.values[key]) ?? entry.cellKeys[0]);
}

function handleCrosswordInput(event, cellKey) {
  const value = event.currentTarget.value.replace(/[^a-z]/gi, '').slice(-1).toUpperCase();
  event.currentTarget.value = value;
  const model = activeCrosswordModel();
  const session = activeCrosswordSession();
  let nextSession = {
    ...session,
    values: { ...session.values, [cellKey]: value },
    completed: false,
  };

  if (completeCrossword(nextSession, model)) {
    finishCrossword(nextSession, model);
  } else {
    if (allCellsFilled(nextSession, model)) {
      nextSession = { ...nextSession, status: 'So close — one or two letters may need another look.' };
    }
    saveActiveCrosswordSession(nextSession);
    renderCrosswordClues();
    updateCrosswordUI();
  }

  if (value) {
    const entry = selectedCrosswordEntry();
    const position = entry.cellKeys.indexOf(cellKey);
    const nextKey = entry.cellKeys[position + 1];
    if (nextKey) {
      requestAnimationFrame(() => focusCell(nextKey));
    }
  }
}

function finishCrossword(session, model) {
  if (session.completed) {
    return;
  }
  const completedCount = session.completionRecorded ? weeklyPlayProgress().completed : recordCompletedCrossword();
  const remaining = Math.max(recreationPlan.gamesPerWeek - completedCount, 0);
  const status = remaining > 0
    ? `Lovely work — crossword complete! ${remaining} more ${remaining === 1 ? 'short game' : 'short games'} in this week’s playful plan.`
    : 'Lovely work — crossword complete! You have reached this week’s playful plan.';
  saveActiveCrosswordSession({
    ...session,
    completed: true,
    completionRecorded: true,
    status,
  });
  for (const input of document.querySelectorAll('.crossword-cell input')) {
    input.value = session.values[input.dataset.cellKey];
    input.disabled = true;
  }
  renderCrosswordClues();
  updateCrosswordUI();
  renderPlayPlan();
  renderPuzzlePicker();
}

function handleCrosswordKeydown(event, cellKey) {
  const keyDirections = {
    ArrowUp: [-1, 0], ArrowDown: [1, 0], ArrowLeft: [0, -1], ArrowRight: [0, 1],
  };
  if (keyDirections[event.key]) {
    event.preventDefault();
    const cell = activeCrosswordModel().cells[cellKey];
    const [rowStep, colStep] = keyDirections[event.key];
    let row = cell.row + rowStep;
    let col = cell.col + colStep;
    const size = activeCrosswordModel().puzzle.size;
    while (row >= 0 && row < size && col >= 0 && col < size) {
      const target = activeCrosswordModel().cells[crosswordCellKey(row, col)];
      if (target) {
        focusCell(target.key);
        return;
      }
      row += rowStep;
      col += colStep;
    }
    return;
  }
  if (event.key === 'Backspace' && !event.currentTarget.value) {
    const entry = selectedCrosswordEntry();
    const position = entry.cellKeys.indexOf(cellKey);
    if (position > 0) {
      event.preventDefault();
      focusCell(entry.cellKeys[position - 1]);
    }
  }
}

function revealCrosswordLetter() {
  const model = activeCrosswordModel();
  let session = activeCrosswordSession();
  let entry = selectedCrosswordEntry();
  let available = entry.cellKeys.filter((key) => session.values[key] !== model.cells[key].answer);
  if (available.length === 0) {
    entry = model.entries.find((candidate) => !completeEntry(session, model, candidate));
    if (entry) {
      available = entry.cellKeys.filter((key) => session.values[key] !== model.cells[key].answer);
    }
  }
  if (!entry || available.length === 0) {
    session = { ...session, status: 'Every clue is complete — wonderful work!' };
    saveActiveCrosswordSession(session);
    updateCrosswordUI();
    return;
  }

  const key = available[0];
  session = {
    ...session,
    selectedEntryId: entry.id,
    values: { ...session.values, [key]: model.cells[key].answer },
    hintedCells: [...new Set([...session.hintedCells, key])],
    hintsUsed: session.hintsUsed + 1,
    completed: false,
    status: `A letter has appeared in ${entry.number} ${directionName(entry.direction)}.`,
  };
  if (completeCrossword(session, model)) {
    finishCrossword(session, model);
    return;
  }
  saveActiveCrosswordSession(session);
  const input = document.querySelector(`.crossword-cell input[data-cell-key="${key}"]`);
  if (input) {
    input.value = model.cells[key].answer;
  }
  renderCrosswordClues();
  updateCrosswordUI();
}

function restartCrossword() {
  const model = activeCrosswordModel();
  const fresh = {
    ...createCrosswordSession(model),
    status: 'A fresh board is ready. Choose a clue when you are ready.',
  };
  saveActiveCrosswordSession(fresh);
  renderRecreation();
}

function setupRecreationControls() {
  document.getElementById('play-plan-action').addEventListener('click', () => {
    selectCrossword(recreationPlan.difficulty, true);
  });
  document.getElementById('game-hint').addEventListener('click', revealCrosswordLetter);
  document.getElementById('game-restart').addEventListener('click', restartCrossword);
}

/* ---------------- Boot ---------------- */

setupChatForm();
setupRecreationControls();
showView(currentRoute());
