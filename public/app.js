/* Kinbridge frontend — view routing, companion chat, and daily journal. */

const HISTORY_LIMIT = 20;
const GREETING = 'Hello! I\'m Kin, your companion. How are you today?';

const VIEWS = ['home', 'chat', 'journal'];

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

/* ---------------- Boot ---------------- */

setupChatForm();
showView(currentRoute());
