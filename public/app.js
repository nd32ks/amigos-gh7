/* Kinbridge frontend — view routing, companion chat, and daily journal. */

const HISTORY_LIMIT = 20;
const GREETING = 'Hello! I\'m Kin, your companion. How are you today?';

const VIEWS = ['home', 'chat', 'journal', 'wellness', 'games', 'call'];

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
  if (name === 'games') {
    loadGames();
  }
  if (name === 'call') {
    startCall();
  } else {
    routeBeforeCall = name;
    resetCallUi();
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

/* ---------------- Video call (demo) ---------------- */

const RING_DURATION_MS = 2500;

let routeBeforeCall = 'home';
let ringTimeoutId = null;
let callTimerId = null;

function callElements() {
  return {
    video: document.getElementById('call-video'),
    ringing: document.getElementById('call-ringing'),
    topbar: document.getElementById('call-topbar'),
    selfview: document.getElementById('call-selfview'),
    timer: document.getElementById('call-timer-value'),
    mute: document.getElementById('call-mute'),
    micOn: document.getElementById('call-mic-on'),
    micOff: document.getElementById('call-mic-off'),
  };
}

function formatCallTime(seconds) {
  const total = Math.floor(seconds);
  const hours = Math.floor(total / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  const rest = total % 60;
  const mm = String(minutes).padStart(2, '0');
  const ss = String(rest).padStart(2, '0');
  return hours > 0 ? `${hours}:${mm}:${ss}` : `${mm}:${ss}`;
}

function stopCallTimer() {
  if (callTimerId !== null) {
    clearInterval(callTimerId);
    callTimerId = null;
  }
}

/** Resets every piece of call UI and stops playback — safe to call anytime. */
function resetCallUi() {
  if (ringTimeoutId !== null) {
    clearTimeout(ringTimeoutId);
    ringTimeoutId = null;
  }
  stopCallTimer();
  const { video, ringing, topbar, selfview, timer } = callElements();
  video.pause();
  video.currentTime = 0;
  video.hidden = true;
  ringing.hidden = false;
  topbar.hidden = true;
  selfview.hidden = true;
  timer.textContent = '00:00';
}

function startCall() {
  resetCallUi();
  setMuted(false);
  ringTimeoutId = setTimeout(connectCall, RING_DURATION_MS);
}

async function connectCall() {
  ringTimeoutId = null;
  const { video, ringing, topbar, selfview, timer } = callElements();
  ringing.hidden = true;
  video.hidden = false;
  topbar.hidden = false;
  selfview.hidden = false;
  try {
    await video.play();
  } catch {
    // Autoplay with sound was blocked — fall back to muted playback.
    setMuted(true);
    video.play().catch((error) => console.error('Call video failed to play:', error));
  }
  stopCallTimer();
  callTimerId = setInterval(() => {
    timer.textContent = formatCallTime(video.currentTime);
  }, 250);
}

function endCall() {
  resetCallUi();
  window.location.hash = `#/${routeBeforeCall}`;
}

function setMuted(muted) {
  const { video, mute, micOn, micOff } = callElements();
  video.muted = muted;
  mute.setAttribute('aria-pressed', String(muted));
  mute.setAttribute('aria-label', muted ? 'Unmute microphone' : 'Mute microphone');
  micOn.hidden = muted;
  micOff.hidden = !muted;
}

function setupCallControls() {
  document.getElementById('call-end').addEventListener('click', endCall);
  document.getElementById('call-mute').addEventListener('click', () => {
    setMuted(!callElements().video.muted);
  });
  // The video reaching its end is the other side hanging up.
  callElements().video.addEventListener('ended', endCall);
}

/* ---------------- Boot ---------------- */

setupChatForm();
setupCallControls();
showView(currentRoute());
