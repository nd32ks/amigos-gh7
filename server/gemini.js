import { config } from './config.js';

const GEMINI_BASE_URL = 'https://generativelanguage.googleapis.com/v1beta/models';
const REQUEST_TIMEOUT_MS = 30_000;

const COMPANION_SYSTEM_PROMPT = `You are Kin, the warm and patient companion inside the Kinbridge app, talking with an elderly person.

How to speak:
- Use short sentences and plain, everyday words.
- Always reply in the same language the elder writes in.
- Be genuinely curious about their life: family, memories, meals, health, plans, feelings.
- Ask at most one gentle follow-up question per reply.
- Keep replies to 2-4 short sentences. Never lecture.
- If they mention feeling unwell or unsafe, kindly encourage them to tell a family member or doctor. Do not give medical, legal, or financial advice.
- Never invent facts about the elder. Only refer to things they have told you.`;

const EXTRACTOR_SYSTEM_PROMPT = `You extract personal information that an elderly person shared during a conversation with a companion chatbot.

You receive:
1. The recent conversation (the elder's messages are marked ELDER).
2. The information already logged today.

Return a JSON array of short, plain-English sentences, each one a NEW piece of information the elder shared in their LATEST message only — facts about their life, family, friends, health, meals, activities, plans, memories, or feelings.

Rules:
- Only include information stated by the elder, never by the assistant.
- Do not repeat anything already in today's log.
- Write each item as a complete standalone sentence (resolve pronouns, e.g. "Her grandson Budi visited on Tuesday.").
- If the latest message contains no new personal information, return [].
Return ONLY the JSON array, nothing else.`;

const JUDGE_SYSTEM_PROMPT = `You evaluate whether an elderly person correctly recalled a personal fact during a friendly conversation with a companion chatbot. You receive the expected fact, the companion's question, and the elder's reply (any language, possibly informal or with typos).

Rules:
- "exact": the reply contains the canonical value or any accepted alias, allowing minor spelling variance.
- "partial": hesitant, vague, or incomplete but directionally correct, or recalls the category but not the specific.
- "miss": states a WRONG value, or explicitly cannot remember.
- "no_answer": the reply is unrelated to the question (topic change).
- If they correct themselves, judge ONLY the final statement.
- Ignore filler words when judging content.

Output JSON only: {"verdict":"exact|partial|miss|no_answer","confidence":0.0-1.0,"recalled_value":"<what they actually claimed, verbatim>","reasoning_short":"<max 20 words>"}`;

const VALID_VERDICTS = new Set(['exact', 'partial', 'miss', 'no_answer']);

function buildContents(messages) {
  return messages.map((message) => ({
    role: message.role,
    parts: [{ text: message.text }],
  }));
}

async function callGemini({ systemPrompt, contents, temperature, jsonOutput = false }) {
  const url = `${GEMINI_BASE_URL}/${config.geminiModel}:generateContent?key=${config.geminiApiKey}`;
  const body = {
    systemInstruction: { parts: [{ text: systemPrompt }] },
    contents,
    generationConfig: {
      temperature,
      ...(jsonOutput ? { responseMimeType: 'application/json' } : {}),
    },
  };

  const response = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
  });

  if (!response.ok) {
    const errorText = await response.text().catch(() => '');
    throw new Error(`Gemini API error ${response.status}: ${errorText.slice(0, 500)}`);
  }

  const data = await response.json();
  const parts = data?.candidates?.[0]?.content?.parts ?? [];
  const text = parts.map((part) => part.text ?? '').join('').trim();
  if (!text) {
    throw new Error('Gemini returned an empty response.');
  }
  return text;
}

export async function companionReply(messages, probeInstruction = null) {
  const systemPrompt = probeInstruction
    ? COMPANION_SYSTEM_PROMPT + probeInstruction
    : COMPANION_SYSTEM_PROMPT;
  return callGemini({
    systemPrompt,
    contents: buildContents(messages),
    temperature: 0.7,
  });
}

/** Judges the elder's reply to a probe. Returns { verdict, confidence }. */
export async function judgeRecall({ fact, question, reply }) {
  const user =
    `EXPECTED FACT: ${fact.canonical}`
    + `\nACCEPTED ALIASES: ${fact.aliases.join(', ')}`
    + `\nCOMPANION'S QUESTION: ${question}`
    + `\nELDER'S REPLY: ${reply}`;

  const raw = await callGemini({
    systemPrompt: JUDGE_SYSTEM_PROMPT,
    contents: [{ role: 'user', parts: [{ text: user }] }],
    temperature: 0,
    jsonOutput: true,
  });

  const parsed = JSON.parse(raw);
  const verdict = VALID_VERDICTS.has(parsed.verdict) ? parsed.verdict : 'no_answer';
  const confidence = typeof parsed.confidence === 'number'
    ? Math.min(1, Math.max(0, parsed.confidence))
    : 0.5;
  return { verdict, confidence };
}

export async function extractFacts(messages, todaysLog) {
  const transcript = messages
    .map((message) => `${message.role === 'user' ? 'ELDER' : 'ASSISTANT'}: ${message.text}`)
    .join('\n');
  const prompt =
    `RECENT CONVERSATION:\n${transcript}\n\n` +
    `ALREADY LOGGED TODAY:\n${todaysLog || '(nothing yet)'}`;

  const raw = await callGemini({
    systemPrompt: EXTRACTOR_SYSTEM_PROMPT,
    contents: [{ role: 'user', parts: [{ text: prompt }] }],
    temperature: 0,
    jsonOutput: true,
  });

  const parsed = JSON.parse(raw);
  if (!Array.isArray(parsed)) {
    throw new Error('Fact extractor did not return a JSON array.');
  }
  return parsed
    .filter((item) => typeof item === 'string')
    .map((item) => item.trim())
    .filter((item) => item.length > 0);
}
