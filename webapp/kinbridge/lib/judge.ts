import OpenAI from "openai";
import type { Fact, JudgeResult, Verdict } from "./types";

const openai = process.env.OPENAI_API_KEY ? new OpenAI() : null;

const MISS_PHRASES = [
  "tidak ingat",
  "tidak tahu",
  "lupa",
  "siapa ya",
  "apa ya",
  "tidak ada yang datang",
  "tidak ada",
  "tidak makan",
  "[tidak terdengar]",
  "[unintelligible]",
  "???",
];

function normalize(s: string): string {
  return s.toLowerCase().replace(/[^a-z0-9\s]/g, " ").trim();
}

function containsAny(haystack: string, needles: string[]): boolean {
  const h = normalize(haystack);
  return needles.some((n) => h.includes(normalize(n)));
}

function fallbackJudge(reply: string, fact: Fact): JudgeResult {
  const normalizedReply = normalize(reply);

  // Special demo triggers
  if (fact.fact_id === "T1_SPOUSE_NAME" && normalizedReply.includes("tidak ingat")) {
    return {
      verdict: "miss",
      confidence: 0.94,
      recalled_value: reply,
      reasoning_short: "Elder explicitly cannot recall spouse's name.",
    };
  }
  if (fact.fact_id === "T2_LAST_FAMILY_VISIT" && normalizedReply.includes("tidak ada yang datang")) {
    return {
      verdict: "miss",
      confidence: 0.9,
      recalled_value: reply,
      reasoning_short: "Elder states no family visit occurred.",
    };
  }

  const values = [fact.canonical_value]
    .flat()
    .concat(fact.accepted_aliases || []);

  if (containsAny(reply, values)) {
    return {
      verdict: "exact",
      confidence: 0.95,
      recalled_value: reply,
      reasoning_short: "Reply contains canonical value or alias.",
    };
  }

  if (containsAny(reply, MISS_PHRASES)) {
    return {
      verdict: "miss",
      confidence: 0.9,
      recalled_value: reply,
      reasoning_short: "Elder indicates inability to recall.",
    };
  }

  if (normalizedReply.length < 6 || normalizedReply.includes("ya dia") || normalizedReply.includes("eh")) {
    return {
      verdict: "partial",
      confidence: 0.8,
      recalled_value: reply,
      reasoning_short: "Hesitant or vague reply.",
    };
  }

  return {
    verdict: "no_answer",
    confidence: 0.85,
    recalled_value: reply,
    reasoning_short: "Reply does not address the fact.",
  };
}

export async function judge(
  reply: string,
  fact: Fact,
  probeText: string,
  forced?: Partial<JudgeResult>
): Promise<JudgeResult> {
  if (forced) {
    return {
      verdict: (forced.verdict as Verdict) || "miss",
      confidence: forced.confidence ?? 0.94,
      recalled_value: forced.recalled_value ?? reply,
      reasoning_short: forced.reasoning_short ?? "Forced verdict for demo.",
    };
  }

  if (!openai) {
    return fallbackJudge(reply, fact);
  }

  const canonical = Array.isArray(fact.canonical_value)
    ? fact.canonical_value.join(" / ")
    : fact.canonical_value;

  const system = `You evaluate whether an elderly Indonesian speaker correctly recalled a personal fact. Reply ONLY with the JSON format requested.`;

  const user = `EXPECTED FACT: ${canonical}
ACCEPTED ALIASES: ${(fact.accepted_aliases || []).join(", ")}
PROBE ASKED: ${probeText}
ELDER'S REPLY: ${reply}

Rules:
- "exact": reply contains the canonical value or any accepted alias.
- "partial": hesitant, vague, or directionally correct.
- "miss": states a wrong value, or explicitly cannot remember.
- "no_answer": reply is unrelated to the question.
- If she corrects herself, judge only her final statement.

Output JSON only:
{ "verdict": "exact|partial|miss|no_answer", "confidence": 0.0-1.0, "recalled_value": "<what she claimed>", "reasoning_short": "<max 20 words>" }`;

  try {
    const response = await openai.chat.completions.create({
      model: "gpt-4o-mini-2024-07-18",
      temperature: 0,
      response_format: { type: "json_object" },
      messages: [
        { role: "system", content: system },
        { role: "user", content: user },
      ],
    });

    const raw = response.choices[0].message.content || "";
    const parsed = JSON.parse(raw) as Partial<JudgeResult>;

    return {
      verdict: (parsed.verdict as Verdict) || "no_answer",
      confidence: typeof parsed.confidence === "number" ? parsed.confidence : 0.5,
      recalled_value: parsed.recalled_value || reply,
      reasoning_short: parsed.reasoning_short || "",
    };
  } catch (err) {
    console.error("Judge LLM error, falling back:", err);
    return fallbackJudge(reply, fact);
  }
}
