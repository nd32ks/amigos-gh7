# Kinbridge (Web)

A mobile-friendly web app: a gentle AI companion for the elderly. Everything the
elder shares in conversation is written to a **daily journal — one text file per
day** in `logs/` (e.g. `logs/2026-07-17.txt`), appended across every
conversation that day.

Works on Android and iPhone through the browser, and can be added to the home
screen as an app (PWA manifest included).

## Features

1. **AI Companion Chat** — Gemini-powered companion ("Kin") that chats in the
   elder's own language and logs the personal information they share, plus the
   **Daily Journal** to browse the one-file-per-day conversation logs.
2. **Cognitive Wellness Screening** — Kin gently weaves memory check-ins into
   conversation (about one every three turns), a temperature-0 judge scores each
   recall against ground-truth facts (`data/elder-facts.json`), and results roll
   into a tier-weighted Recall Index shown on the Wellness dashboard with a
   day-by-day trend. An early signal — never a diagnosis.
3. **Mind Garden** — a friendly crossword nook with Gentle, Steady, and
   Challenge levels. Its suggested weekly rhythm uses the Wellness recall
   signal as a play guide, never as a diagnosis; every level remains available
   and each crossword offers letter hints.

## Setup

```bash
cp .env.example .env   # then put your real GEMINI_API_KEY in .env
npm install
npm start
```

Open `http://localhost:3000` on this machine, or the `Network:` URL printed at
startup from a phone on the same Wi-Fi.

## Tests

```bash
npm test
```

## Security notes

- The Gemini key lives only in `.env` (gitignored) and is only used
  server-side; the browser never sees it.
- Daily logs contain personal information and are gitignored.
- `logs/:date` reads are validated against a strict `YYYY-MM-DD` pattern to
  prevent path traversal.

## Structure

```
server/           Express app
  index.js        bootstrap + static hosting
  config.js       env loading/validation
  gemini.js       Gemini API client (companion reply + fact extraction)
  dailyLog.js     one-text-file-per-day log store
  validate.js     chat request validation
  routes/         /api/chat, /api/logs
public/           frontend (no build step)
tests/            node:test suites
logs/             daily journals (created at runtime, gitignored)
```
