package com.amigos.kinbridge;

import android.os.Handler;
import android.os.Looper;

import com.amigos.kinbridge.onboard.DumpParser;
import com.amigos.kinbridge.scoring.ScoringEngine.Verdict;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Gemini API client (judge + context-dump extraction + companion chat).
 * The key comes from git-ignored local.properties (GEMINI_API_KEY) via
 * BuildConfig — never commit it to source. Production must proxy this through
 * a backend; a key packaged in an APK is inherently extractable.
 */
public final class GeminiClient {

    private static final String API_KEY = BuildConfig.GEMINI_API_KEY;
    private static final String MODEL = "gemini-2.5-flash";
    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL
                    + ":generateContent?key=" + API_KEY;
    private static final int TIMEOUT_MS = 10_000;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final String TAG = "GeminiClient";

    private GeminiClient() {
    }

    // ---- Judge (prompts.md §3 — verbatim rules, temp 0, JSON out) ----

    public interface JudgeCallback {
        void onResult(Verdict verdict, double confidence);

        void onError();
    }

    public static void judge(String reply, ElderFact fact, JudgeCallback callback) {
        StringBuilder canonical = new StringBuilder();
        for (String v : fact.canonicalValues) {
            if (canonical.length() > 0) {
                canonical.append(" or ");
            }
            canonical.append(v);
        }
        StringBuilder aliases = new StringBuilder();
        for (String a : fact.aliases) {
            if (aliases.length() > 0) {
                aliases.append(", ");
            }
            aliases.append(a);
        }

        String system =
                "You evaluate whether an elderly Indonesian speaker correctly recalled a "
                + "personal fact. You receive the expected fact and her spoken reply "
                + "(transcribed, Bahasa Indonesia, may contain STT noise).\n\n"
                + "Rules:\n"
                + "- \"exact\": reply contains the canonical value or any accepted alias, "
                + "allowing minor STT spelling variance.\n"
                + "- \"partial\": hesitant, vague, or incomplete but directionally correct, "
                + "or recalls category but not the specific.\n"
                + "- \"miss\": states a WRONG value, or explicitly cannot remember.\n"
                + "- \"no_answer\": reply is unrelated to the question (topic change).\n"
                + "- If she corrects herself, judge ONLY her final statement.\n"
                + "- Ignore filler words (eh, anu, itu lho) when judging content.\n\n"
                + "Output JSON only: {\"verdict\":\"exact|partial|miss|no_answer\","
                + "\"confidence\":0.0-1.0,"
                + "\"recalled_value\":\"<what she actually claimed, verbatim>\","
                + "\"reasoning_short\":\"<max 20 words>\"}";

        String user = "EXPECTED FACT: " + canonical
                + "\nACCEPTED ALIASES: " + aliases
                + "\nELDER'S REPLY: " + reply;

        new Thread(() -> {
            try {
                JSONObject result = new JSONObject(post(system, user, 0.0));
                String v = result.optString("verdict", "").toLowerCase();
                double confidence = result.optDouble("confidence", 0.6);
                Verdict verdict;
                switch (v) {
                    case "exact":
                        verdict = Verdict.EXACT;
                        break;
                    case "partial":
                        verdict = Verdict.PARTIAL;
                        break;
                    case "miss":
                        verdict = Verdict.MISS;
                        break;
                    default:
                        verdict = Verdict.NO_ANSWER;
                }
                MAIN.post(() -> callback.onResult(verdict, confidence));
            } catch (Exception e) {
                android.util.Log.w(TAG, "Gemini call failed", e);
                MAIN.post(callback::onError);
            }
        }).start();
    }

    // ---- Companion replies (prompts.md §1 persona — Kenang speaks) ----

    public interface ReplyCallback {
        void onResult(String reply);

        void onError();
    }

    /**
     * Generates Kenang's conversational reply to an elder turn. Persona is the
     * prompts.md §1 companion: warm, honorific "Ibu", short sentences, never
     * clinical, never corrects her. Temperature 0.7 for natural warmth (the
     * judge stays temp 0 — scoring determinism is unaffected).
     */
    public static void companionReply(String elderText, boolean indonesian, ReplyCallback callback) {
        String language = indonesian ? "Bahasa Indonesia" : "English";
        String clinicalBan = indonesian
                ? "Never use clinical words: no \"tes\", \"pemeriksaan\", \"demensia\", \"penilaian\"."
                : "Never use clinical words: no \"test\", \"screening\", \"dementia\", \"score\".";
        String system =
                "You are \"Kenang\", a warm voice companion for an elderly woman.\n"
                + "LANGUAGE\n"
                + "- Speak ONLY " + language + ". Warm, respectful register: address her as \"Ibu\".\n"
                + "- Short sentences (max ~15 words). One question at most. Never rush.\n"
                + "- " + clinicalBan + "\n"
                + "PERSONA\n"
                + "- You are a friendly companion, like a thoughtful neighbor. You enjoy "
                + "hearing her stories and respond with genuine warmth and curiosity.\n"
                + "- If she says something factually wrong, DO NOT correct her. "
                + "Respond warmly and move on.\n"
                + "- Reply in 1-3 short sentences, conversational, never listy.\n\n"
                + "Output JSON only: {\"reply\":\"<her companion's answer>\"}";

        new Thread(() -> {
            try {
                JSONObject result = new JSONObject(post(system, elderText, 0.7));
                String reply = result.optString("reply");
                if (reply.isEmpty()) {
                    throw new IllegalStateException("empty reply");
                }
                String finalReply = reply;
                MAIN.post(() -> callback.onResult(finalReply));
            } catch (Exception e) {
                android.util.Log.w(TAG, "Gemini call failed", e);
                MAIN.post(callback::onError);
            }
        }).start();
    }

    // ---- Context-dump extraction (V2 §5 — temp 0, tiered facts JSON out) ----
    public interface ExtractCallback {
        void onResult(List<DumpParser.ExtractedFact> facts);

        void onError();
    }

    public static void extractFacts(String dump, ExtractCallback callback) {
        String system =
                "You parse a family's free-text description of an elderly Indonesian "
                + "person into structured memory facts. Input may be Indonesian or English. "
                + "Output canonical values in the elder's language.\n"
                + "Tier rules: family/identity/address -> 1; medication/routines/recent events -> 2; "
                + "tastes/hobbies/preferences -> 3.\n"
                + "Output JSON only: {\"facts\":[{\"tier\":1|2|3,"
                + "\"category\":\"core_identity.family|core_identity.location|core_identity.self|"
                + "recent.health_routine|recent.meals|recent.family_events|recent.routine|"
                + "preferences.dining|preferences.music|preferences.hobbies|preferences.entertainment\","
                + "\"canonical\":\"short canonical value\"}]}";

        new Thread(() -> {
            try {
                JSONObject result = new JSONObject(post(system, dump, 0.0));
                JSONArray arr = result.getJSONArray("facts");
                List<DumpParser.ExtractedFact> facts = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    facts.add(new DumpParser.ExtractedFact(
                            o.optInt("tier", 3), o.optString("category", "preferences.hobbies"),
                            o.optString("canonical")));
                }
                MAIN.post(() -> callback.onResult(facts));
            } catch (Exception e) {
                android.util.Log.w(TAG, "Gemini call failed", e);
                MAIN.post(callback::onError);
            }
        }).start();
    }

    // ---- Companion chat (multi-turn, plain text out) ----

    /** One turn in the companion conversation. */
    public static final class ChatTurn {
        public final boolean elder;
        public final String text;

        public ChatTurn(boolean elder, String text) {
            this.elder = elder;
            this.text = text;
        }
    }

    public interface ChatCallback {
        void onReply(String reply);

        void onError();
    }

    public static void chat(List<ChatTurn> history, String elderName, String elderCity,
                            boolean indonesian, ChatCallback callback) {
        String language = indonesian ? "Bahasa Indonesia" : "English";
        String system =
                "You are Kenang, a warm and patient conversational companion for " + elderName
                + ", an elderly Indonesian woman living with Alzheimer's in " + elderCity + ".\n\n"
                + "Rules:\n"
                + "- Speak in " + language + " only.\n"
                + "- Use short, simple sentences — at most 2-3 per reply.\n"
                + "- Be warm, gentle, and encouraging, like a caring friend.\n"
                + "- NEVER correct, contradict, or quiz her, even if she says something "
                + "confused or repeated.\n"
                + "- Gently ask about her day, her activities, family, food, and fond "
                + "memories — she loves to reminisce.\n"
                + "- If she seems sad or confused, comfort her softly.\n"
                + "- Never give medical advice; if she mentions pain or feeling unwell, "
                + "kindly suggest telling her family.\n"
                + "- Never mention that you are evaluating her, or anything about scores, "
                + "tests, or memory checks.\n"
                + "- Plain text only — no lists, markdown, or emojis.";

        new Thread(() -> {
            try {
                // Gemini requires alternating user/model turns starting with user —
                // drop leading model turns and merge consecutive same-role turns.
                JSONArray contents = new JSONArray();
                for (ChatTurn turn : history) {
                    String role = turn.elder ? "user" : "model";
                    JSONObject last = contents.length() > 0
                            ? contents.getJSONObject(contents.length() - 1) : null;
                    if (last != null && role.equals(last.optString("role"))) {
                        last.getJSONArray("parts").getJSONObject(0)
                                .put("text", last.getJSONArray("parts").getJSONObject(0)
                                        .getString("text") + "\n" + turn.text);
                        continue;
                    }
                    if (last == null && !turn.elder) {
                        continue; // leading model turn — API must start with user
                    }
                    contents.put(new JSONObject().put("role", role).put("parts",
                            new JSONArray().put(new JSONObject().put("text", turn.text))));
                }
                String reply = post(system, contents, 0.7, false).trim();
                MAIN.post(() -> callback.onReply(reply));
            } catch (Exception e) {
                android.util.Log.w(TAG, "Gemini call failed", e);
                MAIN.post(callback::onError);
            }
        }).start();
    }

    // ---- Daily activity summary (diary-style narrative, plain text out) ----

    public interface SummaryCallback {
        void onSummary(String summary);

        void onError();
    }

    public static void summarizeDay(String transcript, boolean indonesian,
                                    SummaryCallback callback) {
        String language = indonesian ? "Bahasa Indonesia" : "English";
        String system =
                "You write a short diary entry about an elderly Indonesian woman's day, "
                + "based on her chat transcript with her companion Kenang. Write in "
                + language + ", third person about 'Ibu'. 3-6 sentences covering: what she "
                + "did today, what she ate, who she mentioned, her mood, and any fond "
                + "memories or life stories she shared. Warm, diary-like tone. Plain text "
                + "only — no headings, no lists, no clinical or assessment language.";
        String user = "TRANSCRIPT OF TODAY'S CONVERSATION:\n" + transcript;

        new Thread(() -> {
            try {
                JSONArray contents = new JSONArray().put(new JSONObject().put("role", "user")
                        .put("parts", new JSONArray().put(new JSONObject().put("text", user))));
                String summary = post(system, contents, 0.3, false).trim();
                MAIN.post(() -> callback.onSummary(summary));
            } catch (Exception e) {
                android.util.Log.w(TAG, "Gemini call failed", e);
                MAIN.post(callback::onError);
            }
        }).start();
    }

    // ---- Diary dictation (key information only, plain text out) ----

    /**
     * Elder dictates freely; Gemini keeps only the key information — activities,
     * media titles/characters, people, meals, feelings, memories — preserving
     * specific names and details verbatim, never the whole conversation.
     */
    public static void summarizeDiaryEntry(String transcript, boolean indonesian,
                                           SummaryCallback callback) {
        String language = indonesian ? "Bahasa Indonesia" : "English";
        String system =
                "You extract the key information from an elderly Indonesian woman's "
                + "dictated diary entry (transcribed speech, may contain STT noise). "
                + "Write in " + language + ", third person about 'Ibu'. 2-5 short "
                + "sentences capturing only the key points: what she did, what she "
                + "watched, read, or listened to (keep titles, characters, and details "
                + "exactly as she said them), who she met or spoke about, what she ate, "
                + "how she felt, and any memories from her past she shared. Plain text "
                + "only — no headings, no lists, no clinical language.";
        String user = "DICTATED DIARY ENTRY:\n" + transcript;

        new Thread(() -> {
            try {
                JSONArray contents = new JSONArray().put(new JSONObject().put("role", "user")
                        .put("parts", new JSONArray().put(new JSONObject().put("text", user))));
                String summary = post(system, contents, 0.3, false).trim();
                MAIN.post(() -> callback.onSummary(summary));
            } catch (Exception e) {
                android.util.Log.w(TAG, "Gemini call failed", e);
                MAIN.post(callback::onError);
            }
        }).start();
    }

    // ---- Transport ----

    private static String post(String systemInstruction, String userText, double temperature)
            throws Exception {
        JSONArray contents = new JSONArray().put(new JSONObject().put("parts",
                new JSONArray().put(new JSONObject().put("text", userText))));
        return post(systemInstruction, contents, temperature, true);
    }

    private static String post(String systemInstruction, JSONArray contents, double temperature,
                               boolean jsonOut) throws Exception {
        JSONObject body = new JSONObject();
        body.put("system_instruction", new JSONObject().put("parts",
                new JSONArray().put(new JSONObject().put("text", systemInstruction))));
        body.put("contents", contents);
        JSONObject generationConfig = new JSONObject().put("temperature", temperature);
        if (jsonOut) {
            generationConfig.put("responseMimeType", "application/json");
        }
        body.put("generationConfig", generationConfig);

        HttpURLConnection conn = (HttpURLConnection) new URL(ENDPOINT).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setDoOutput(true);
        byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
        OutputStream out = conn.getOutputStream();
        out.write(payload);
        out.close();

        int status = conn.getResponseCode();
        InputStream in = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int n;
        while ((n = in.read(chunk)) != -1) {
            buffer.write(chunk, 0, n);
        }
        in.close();
        conn.disconnect();
        if (status >= 400) {
            throw new IllegalStateException("Gemini HTTP " + status + ": " + buffer);
        }

        JSONObject response = new JSONObject(new String(buffer.toByteArray(), StandardCharsets.UTF_8));
        return response.getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts")
                .getJSONObject(0).getString("text");
    }
}
