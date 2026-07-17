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
 * Gemini API client (judge + context-dump extraction).
 * Key is user-supplied for the hackathon demo — production must proxy this
 * through a backend; shipping keys in an APK is inherently extractable.
 */
public final class GeminiClient {

    private static final String API_KEY = "AIzaSyBdw4vQOmkd_XXcTGh7f8pHC37w6sP9qRM";
    private static final String MODEL = "gemini-2.5-flash";
    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL
                    + ":generateContent?key=" + API_KEY;
    private static final int TIMEOUT_MS = 10_000;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

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
                MAIN.post(callback::onError);
            }
        }).start();
    }

    // ---- Transport ----

    private static String post(String systemInstruction, String userText, double temperature)
            throws Exception {
        JSONObject body = new JSONObject();
        body.put("system_instruction", new JSONObject().put("parts",
                new JSONArray().put(new JSONObject().put("text", systemInstruction))));
        body.put("contents", new JSONArray().put(new JSONObject().put("parts",
                new JSONArray().put(new JSONObject().put("text", userText)))));
        body.put("generationConfig", new JSONObject()
                .put("temperature", temperature)
                .put("responseMimeType", "application/json"));

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
