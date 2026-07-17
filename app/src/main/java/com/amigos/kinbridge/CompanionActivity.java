package com.amigos.kinbridge;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.amigos.kinbridge.scoring.ScoringEngine;
import com.amigos.kinbridge.scoring.ScoringEngine.Escalation;
import com.amigos.kinbridge.scoring.ScoringEngine.Event;
import com.amigos.kinbridge.scoring.ScoringEngine.Verdict;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Elder companion screen — typed chat first (hour-6 gate). Runs the real
 * Workflow Evaluation Agent loop: probe scheduler → judge → CRI/EWMA →
 * escalation, plus the V1 social-match engine (keyword → mock group).
 *
 * Demo Mode (demo_script.md): triple-tap Kenang's message to cycle the three
 * scripted scenarios — T1 miss → T2 warning → match found — each forced
 * through the real pipeline.
 */
public class CompanionActivity extends AppCompatActivity {

    private final ElderRepository repository = new ElderRepository();
    private final DailyLogStore dailyLog = new DailyLogStore("Ibu");
    private final List<GeminiClient.ChatTurn> chatHistory = new ArrayList<>();
    private final List<Event> sessionEvents = new ArrayList<>();
    private final Set<String> shownMatches = new HashSet<>();
    private final List<String> elderTurns = new ArrayList<>();
    private final Set<Integer> probeAnswerIndices = new HashSet<>();

    private ElderProfile profile;
    private ElderFact armedProbe;
    private ReminderEngine reminderEngine;

    // Find My Friends (V2 §2) + guided choice (V2.2 §C/D)
    private com.amigos.kinbridge.friends.FriendMatcher.Match topFriendMatch;
    private boolean friendAsked;
    private boolean awaitingFriendConsent;
    private View guidedRow;
    private View inputRow;
    private Verdict forcedVerdict;
    private double forcedConfidence;
    private int turnsSinceProbe;
    private boolean probesDisabled;
    private boolean sessionClosed;

    private View root;
    private TextView companionText;
    private LinearLayout transcript;
    private ScrollView transcriptScroll;
    private EditText chatInput;

    // Voice layer: built-in STT + TTS (Web Speech equivalent per failure ladder)
    private TextToSpeech tts;
    private SpeechRecognizer recognizer;
    private ImageView micButton;
    private ImageView diaryButton;
    /** True while the next STT result is a diary dictation, not a chat turn. */
    private boolean diaryMode;
    private boolean ttsReady;
    private int utteranceCounter;

    // Demo Mode state
    private int demoTaps;
    private long firstDemoTapAt;
    private int demoScenario;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(FontScale.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_companion);

        root = findViewById(android.R.id.content);
        companionText = findViewById(R.id.companionText);
        transcript = findViewById(R.id.transcript);
        transcriptScroll = findViewById(R.id.transcriptScroll);
        chatInput = findViewById(R.id.chatInput);

        Button sendButton = findViewById(R.id.sendButton);
        sendButton.setOnClickListener(v -> onSend());
        chatInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                onSend();
                return true;
            }
            return false;
        });
        companionText.setOnClickListener(v -> onDemoTap());

        guidedRow = findViewById(R.id.guidedRow);
        inputRow = findViewById(R.id.inputRow);
        findViewById(R.id.guidedYes).setOnClickListener(v -> answerGuided(false));
        findViewById(R.id.guidedNo).setOnClickListener(v -> answerGuided(false));
        findViewById(R.id.guidedUnknown).setOnClickListener(v -> answerGuided(false));
        findViewById(R.id.guidedDelegate).setOnClickListener(v -> answerGuided(true));

        reminderEngine = new ReminderEngine(this, this::companionSay);
        reminderEngine.start();
        loadFriends();
        setupVoice();

        repository.loadProfile(this, new ElderRepository.ProfileCallback() {
            @Override
            public void onLoaded(ElderProfile loaded) {
                profile = loaded;
                dailyLog.setElderName(loaded.preferredAddress);
                companionSay(getString(R.string.companion_greeting));
            }

            @Override
            public void onError(String message) {
                companionSay(getString(R.string.companion_greeting));
            }
        });
    }

    // ---- Conversation loop (workflow spec §2 state machine) ----

    private void onSend() {
        String text = chatInput.getText().toString().trim();
        if (text.isEmpty() || profile == null) {
            return;
        }
        chatInput.setText("");
        onElderTurn(text);
    }

    private void onElderTurn(String text) {
        addTranscript(true, text);
        elderTurns.add(text);

        // Elder voice command (V2.1 §1.3): Kenang reads yesterday's diary aloud.
        if (text.toLowerCase().contains("buku harian")) {
            repository.getLatestDiarySummary((summaryId, summaryEn) -> {
                String summary = indonesian() ? summaryId : summaryEn;
                companionSay(summary != null ? summary : getString(R.string.diary_empty));
            });
            return;
        }

        scanForMatch(text);

        if (armedProbe != null) {
            evaluate(text, armedProbe);
            return;
        }
        // A reply can ack a pending reminder instead of answering a probe.
        if (reminderEngine != null && reminderEngine.onElderReply(text)) {
            chatWithKenang();
            return;
        }
        // Friend-intent consent flow (V2 §2.4)
        if (awaitingFriendConsent) {
            awaitingFriendConsent = false;
            if (isYes(text) && topFriendMatch != null) {
                showFriendDialog(topFriendMatch);
            } else {
                chatWithKenang();
            }
            return;
        }
        if (!friendAsked && topFriendMatch != null && hasFriendIntent(text)) {
            friendAsked = true;
            awaitingFriendConsent = true;
            companionSay(getString(R.string.friend_ask));
            return;
        }
        turnsSinceProbe++;
        if (maybeProbe()) {
            return; // the scripted probe question is Kenang's reply this turn
        }
        chatWithKenang();
    }

    /** Free-form conversational reply from Gemini; canned ack when offline. */
    private void chatWithKenang() {
        companionText.setText(R.string.companion_thinking);
        GeminiClient.chat(new ArrayList<>(chatHistory), profile.preferredAddress,
                profile.city, indonesian(), new GeminiClient.ChatCallback() {
                    @Override
                    public void onReply(String reply) {
                        companionSay(reply);
                    }

                    @Override
                    public void onError() {
                        companionText.setText(R.string.kenang_offline);
                    }
                });
    }

    private boolean hasFriendIntent(String text) {
        String lower = text.toLowerCase();
        String[] intents = {"teman sma", "teman lama", "teman sekolah", "teman kerja",
                "teman kantor", "dulu di bri", "old friend", "school friend"};
        for (String intent : intents) {
            if (lower.contains(intent)) {
                return true;
            }
        }
        return false;
    }

    private boolean isYes(String text) {
        String lower = text.toLowerCase();
        String[] yes = {"ya", "iya", "mau", "boleh", "yes", "sure", "ok"};
        for (String word : yes) {
            if (lower.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private void loadFriends() {
        try {
            String deltaJson = readAsset("v2/profile_delta.json");
            String directoryJson = readAsset("v2/people_directory.json");
            List<com.amigos.kinbridge.friends.FriendMatcher.HistoryEntry> elderHistory =
                    new java.util.ArrayList<>();
            org.json.JSONObject delta = new org.json.JSONObject(deltaJson);
            addHistory(elderHistory, delta.optJSONArray("education_history"), "sma");
            addHistory(elderHistory, delta.optJSONArray("work_history"), "work");

            List<com.amigos.kinbridge.friends.FriendMatcher.Person> people =
                    new java.util.ArrayList<>();
            org.json.JSONArray arr = new org.json.JSONArray(directoryJson);
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.getJSONObject(i);
                com.amigos.kinbridge.friends.FriendMatcher.Person p =
                        new com.amigos.kinbridge.friends.FriendMatcher.Person();
                p.personId = o.getString("person_id");
                p.name = o.getString("name");
                p.city = o.optString("city");
                p.distanceKm = o.optDouble("distance_km");
                p.onPlatform = o.optBoolean("on_platform");
                addHistory(p.history, o.optJSONArray("education_history"), "sma");
                addHistory(p.history, o.optJSONArray("work_history"), "work");
                people.add(p);
            }
            List<com.amigos.kinbridge.friends.FriendMatcher.Match> matches =
                    com.amigos.kinbridge.friends.FriendMatcher.match(elderHistory, people);
            if (!matches.isEmpty()) {
                topFriendMatch = matches.get(0);
            }
        } catch (Exception e) {
            android.util.Log.w("Companion", "friends load failed", e);
        }
    }

    private void addHistory(List<com.amigos.kinbridge.friends.FriendMatcher.HistoryEntry> out,
                            org.json.JSONArray arr, String type) throws org.json.JSONException {
        if (arr == null) {
            return;
        }
        for (int i = 0; i < arr.length(); i++) {
            org.json.JSONObject o = arr.getJSONObject(i);
            org.json.JSONArray years = o.getJSONArray("years");
            String institution = o.has("institution") ? o.getString("institution") : o.getString("employer");
            out.add(new com.amigos.kinbridge.friends.FriendMatcher.HistoryEntry(
                    institution, type, years.getInt(0), years.getInt(1)));
        }
    }

    private void showFriendDialog(com.amigos.kinbridge.friends.FriendMatcher.Match match) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_friend);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        String[] parts = match.person.name.split(" ");
        StringBuilder initials = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty() && initials.length() < 2) {
                initials.append(Character.toUpperCase(part.charAt(0)));
            }
        }
        ((TextView) dialog.findViewById(R.id.friendInitials)).setText(initials.toString());
        ((TextView) dialog.findViewById(R.id.friendName)).setText(match.person.name);
        int gradYear = 0;
        for (com.amigos.kinbridge.friends.FriendMatcher.HistoryEntry h : match.person.history) {
            if ("sma".equals(h.type) && h.institution.equals(match.sharedInstitution)) {
                gradYear = h.toYear % 100;
            }
        }
        ((TextView) dialog.findViewById(R.id.friendLine)).setText(getString(
                R.string.friend_card_line, match.sharedInstitution,
                String.format(java.util.Locale.US, "%02d", gradYear), match.person.distanceKm));
        dialog.findViewById(R.id.friendConnect).setOnClickListener(v -> {
            repository.saveDelegation("friend_intro",
                    match.person.name,
                    match.sharedInstitution + " · " + match.person.city);
            Toast.makeText(this, R.string.friend_notified, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        dialog.show();
    }

    // ---- Guided choice (V2.2 §C/D) ----

    private void runGuidedQuestionScenario() {
        companionSay(getString(R.string.guided_komorbid_q));
        inputRow.setVisibility(View.GONE);
        guidedRow.setVisibility(View.VISIBLE);
    }

    private void answerGuided(boolean delegated) {
        guidedRow.setVisibility(View.GONE);
        inputRow.setVisibility(View.VISIBLE);
        if (delegated) {
            repository.saveDelegation("question",
                    getString(R.string.guided_komorbid_q), "Tanya Dewi saja ya.");
            companionSay(getString(R.string.guided_delegated));
        } else {
            companionSay(getString(R.string.guided_noted));
        }
    }

    private String readAsset(String name) throws Exception {
        java.io.InputStream in = getAssets().open(name);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        in.close();
        return new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
    }

    private void evaluate(String reply, ElderFact fact) {
        if (forcedVerdict != null) {
            // Demo Mode forced verdict — still runs the real scoring pipeline.
            Verdict verdict = forcedVerdict;
            double confidence = forcedConfidence;
            forcedVerdict = null;
            applyVerdict(reply, fact, verdict, confidence);
            return;
        }
        // Gemini judge (prompts.md §3, temp 0) with the local judge as the
        // offline fallback per the failure ladder.
        GeminiClient.judge(reply, fact, new GeminiClient.JudgeCallback() {
            @Override
            public void onResult(Verdict verdict, double confidence) {
                applyVerdict(reply, fact, verdict, confidence);
            }

            @Override
            public void onError() {
                Verdict fallback = judge(reply, fact);
                applyVerdict(reply, fact, fallback, confidenceOf(fallback));
            }
        });
    }

    private void applyVerdict(String reply, ElderFact fact, Verdict verdict, double confidence) {

        // V2.3 + MASTER_CHECKLIST §5: a cocok_kata (2-choice) exact earns a
        // discounted 0.75 credit — guessing right is weak signal.
        boolean cocokKata = "playful".equals(deliveryStyle) && "cocok_kata".equals(gameStyle);
        sessionEvents.add(cocokKata && verdict == Verdict.EXACT
                ? new Event(fact.tier, verdict, 0.75)
                : new Event(fact.tier, verdict));
        probeAnswerIndices.add(elderTurns.size() - 1); // probes are scoring data, not stories
        repository.saveEvent(fact.tier, verdict.name().toLowerCase(),
                ScoringEngine.rawPoints(verdict, fact.tier),
                ScoringEngine.credit(verdict), eventLabel(verdict, fact), deliveryStyle);
        repository.markProbed(this, fact);
        armedProbe = null;
        turnsSinceProbe = 0;

        // MASTER_CHECKLIST §5: a cocok_kata T1 miss never auto-fires acute —
        // a mis-tap is not "couldn't recall her husband". Warm ack, no pivot.
        if (cocokKata && fact.tier == 1 && verdict == Verdict.MISS) {
            chatWithKenang();
            return;
        }

        int t2Misses = 0;
        for (Event e : sessionEvents) {
            if (e.tier == 2 && e.verdict == Verdict.MISS) {
                t2Misses++;
            }
        }
        double sessionCri = sessionEvents.size() >= ScoringEngine.MIN_PROBES
                ? ScoringEngine.cri(sessionEvents) : 0;
        Escalation escalation = ScoringEngine.escalation(
                verdict, confidence, fact.tier, t2Misses, 0, sessionCri);

        switch (escalation) {
            case ACUTE:
                // Rule 1: companion pivot + family alert (spec §4, prompts.md §1)
                probesDisabled = true;
                repository.saveAlert("acute_t1",
                        getString(R.string.alert_acute_title),
                        getString(R.string.alert_acute_body));
                warmBackground();
                companionSay(getString(R.string.pivot_t1));
                return;
            case WARNING:
                repository.saveAlert("warning", getString(R.string.alert_warning_text), "");
                chatWithKenang();
                return;
            default:
                // Never correct her — warm AI reply and move on (prompts.md §1).
                chatWithKenang();
        }
    }

    private boolean maybeProbe() {
        if (probesDisabled || turnsSinceProbe < 3) {
            return false;
        }
        // Scheduler (prompts.md §2): highest-priority tier first, cooldown-gated.
        long now = System.currentTimeMillis();
        ElderFact next = null;
        for (ElderFact f : profile.facts) {
            if (f.isEligible(now) && (next == null || f.tier < next.tier)) {
                next = f;
            }
        }
        if (next != null) {
            armedProbe = next;
            // V2.3: roughly one probe in three MAY use a game phrasing, never
            // two in a row. Same fact, same judge, same scoring downstream.
            gameCounter++;
            if (!next.gameVariants.isEmpty() && !lastProbeWasGame && gameCounter >= 3) {
                ElderFact.GameVariant variant = next.gameVariants.get(0);
                deliveryStyle = "playful";
                gameStyle = variant.style;
                lastProbeWasGame = true;
                gameCounter = 0;
                companionSay(variant.promptId);
            } else {
                deliveryStyle = "conversational";
                gameStyle = null;
                lastProbeWasGame = false;
                companionSay(next.probe());
            }
            return true;
        }
        return false;
    }

    private int gameCounter;
    private boolean lastProbeWasGame;
    private String deliveryStyle = "conversational";
    private String gameStyle;

    // ---- Social match engine (V1: keyword → mock group) ----

    private void scanForMatch(String elderText) {
        String text = elderText.toLowerCase();
        for (ElderProfile.CommunityGroup group : profile.groups) {
            if (shownMatches.contains(group.groupId)) {
                continue;
            }
            for (String keyword : group.keywords) {
                if (text.contains(keyword.toLowerCase())) {
                    shownMatches.add(group.groupId);
                    showMatchModal(group);
                    repository.saveMatchEvent(group.name + " — " + group.distanceKm + " km");
                    return;
                }
            }
        }
    }

    private void showMatchModal(ElderProfile.CommunityGroup group) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_match);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        ((TextView) dialog.findViewById(R.id.matchTitle)).setText(R.string.match_title);
        ((TextView) dialog.findViewById(R.id.matchGroupName)).setText(group.name);
        ((TextView) dialog.findViewById(R.id.matchDetails)).setText(
                getString(R.string.match_details, group.distanceKm, group.meets));
        dialog.findViewById(R.id.matchNotify).setOnClickListener(v -> {
            Toast.makeText(this, R.string.match_notified, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        dialog.show();
        // Auto-dismiss after 12s (ui_spec.md §2 match-found behavior)
        handler.postDelayed(dialog::dismiss, 12_000);
    }

    // ---- Local judge placeholder (TODO: LLM judge via backend-held key) ----

    private Verdict judge(String reply, ElderFact fact) {
        String text = reply.toLowerCase();
        for (String value : fact.canonicalValues) {
            if (text.contains(value.toLowerCase())) {
                return Verdict.EXACT;
            }
        }
        for (String alias : fact.aliases) {
            if (!alias.isEmpty() && text.contains(alias.toLowerCase())) {
                return Verdict.EXACT;
            }
        }
        String[] missMarkers = {"tidak ingat", "lupa", "siapa ya", "entah",
                "tidak ada", "don't know", "not sure", "forgot", "can't remember"};
        for (String marker : missMarkers) {
            if (text.contains(marker)) {
                return Verdict.MISS;
            }
        }
        if (text.length() < 15 || text.contains("ya dia") || text.contains("hmm")
                || text.contains("maybe") || text.contains("mungkin")) {
            return Verdict.PARTIAL;
        }
        return Verdict.NO_ANSWER;
    }

    private double confidenceOf(Verdict verdict) {
        switch (verdict) {
            case EXACT:
                return 0.9;
            case MISS:
                return 0.9;
            case PARTIAL:
                return 0.7;
            default:
                return 0.6;
        }
    }

    // ---- Demo Mode: triple-tap cycles t1_miss → t2_warning → match_found ----

    private void onDemoTap() {
        long now = SystemClock.elapsedRealtime();
        if (now - firstDemoTapAt > 1500) {
            demoTaps = 0;
            firstDemoTapAt = now;
        }
        demoTaps++;
        if (demoTaps >= 3 && profile != null) {
            demoTaps = 0;
            int scenario = demoScenario % 4;
            demoScenario++;
            switch (scenario) {
                case 0:
                    runT1MissScenario();
                    break;
                case 1:
                    runT2WarningScenario();
                    break;
                case 2:
                    runMatchScenario();
                    break;
                default:
                    runGuidedQuestionScenario();
            }
        }
    }

    private void runT1MissScenario() {
        ElderFact fact = findFact("T1_SPOUSE_NAME");
        if (fact == null) {
            return;
        }
        armedProbe = fact;
        forcedVerdict = Verdict.MISS;
        forcedConfidence = 0.94;
        deliveryStyle = "conversational";
        gameStyle = null;
        companionSay(fact.probe());
        handler.postDelayed(() ->
                onElderTurn("Suami saya... saya tidak ingat, siapa ya namanya?"), 900);
    }

    private void runT2WarningScenario() {
        // Two T2 misses seed the warning rule (demo_script.md §4)
        String[] t2Ids = {"T2_BREAKFAST_TODAY", "T2_LAST_FAMILY_VISIT"};
        runForcedMissChain(t2Ids, 0);
    }

    private void runForcedMissChain(String[] factIds, int index) {
        if (index >= factIds.length) {
            return;
        }
        ElderFact fact = findFact(factIds[index]);
        if (fact == null) {
            return;
        }
        armedProbe = fact;
        forcedVerdict = Verdict.MISS;
        forcedConfidence = 0.9;
        companionSay(fact.probe());
        handler.postDelayed(() -> {
            onElderTurn("Tidak ingat, Bu.");
            handler.postDelayed(() -> runForcedMissChain(factIds, index + 1), 900);
        }, 900);
    }

    private void runMatchScenario() {
        onElderTurn("Saya senang merawat anggrek di teras.");
    }

    private ElderFact findFact(String factId) {
        for (ElderFact f : profile.facts) {
            if (f.factId.equals(factId)) {
                return f;
            }
        }
        return null;
    }

    // ---- Tier-1 pivot visual: background warms (ui_spec.md §2) ----

    private void warmBackground() {
        int from = getColor(R.color.pure_white);
        int to = getColor(R.color.tint_wash);
        ValueAnimator animator = ValueAnimator.ofObject(new ArgbEvaluator(), from, to);
        animator.setDuration(2000);
        animator.addUpdateListener(a -> root.setBackgroundColor((int) a.getAnimatedValue()));
        animator.start();
    }

    // ---- Session close: CRI + EWMA trend point (spec §2 on_session_end) ----

    @Override
    protected void onStop() {
        super.onStop();
        if (reminderEngine != null) {
            reminderEngine.stop();
        }
        if (!sessionClosed && profile != null
                && sessionEvents.size() >= ScoringEngine.MIN_PROBES) {
            sessionClosed = true;
            double cri = ScoringEngine.cri(sessionEvents);
            double ewma = ScoringEngine.ewma(cri, profile.prevEwma);
            repository.saveTrendPoint(cri, ewma, sessionEvents.size());
        }
        if (!elderTurns.isEmpty()) {
            generateDiary();
            appendDailySummary();
        }
    }

    /**
     * Gemini-written diary narrative appended to today's activity log file.
     * The raw transcript is already on disk; on failure it stays as-is.
     */
    private void appendDailySummary() {
        String transcript = dailyLog.readToday(this);
        if (transcript.isEmpty()) {
            return;
        }
        GeminiClient.summarizeDay(transcript, indonesian(), new GeminiClient.SummaryCallback() {
            @Override
            public void onSummary(String summary) {
                dailyLog.appendSummary(CompanionActivity.this, summary);
            }

            @Override
            public void onError() {
                // Raw transcript remains — never blocks session close
            }
        });
    }

    /**
     * Session-end diary entry (V2.1 §1.2 — one merged generation step):
     * bilingual summary + verbatim stories, probe answers excluded. Mood is
     * derived, never asked. Love-only surface: no scores, no verdicts.
     */
    private void generateDiary() {
        int exactCount = 0;
        boolean t1Miss = false;
        for (Event e : sessionEvents) {
            if (e.verdict == Verdict.EXACT) {
                exactCount++;
            }
            if (e.tier == 1 && e.verdict == Verdict.MISS) {
                t1Miss = true;
            }
        }

        String medKey = "rem_rem_med_01_" + new java.text.SimpleDateFormat("yyyyMMdd",
                java.util.Locale.US).format(new java.util.Date());
        boolean medAcked = "ACKED".equals(getSharedPreferences("elder_state", MODE_PRIVATE)
                .getString(medKey, ""));

        StringBuilder id = new StringBuilder("Hari ini Ibu mengobrol dengan Kenang");
        StringBuilder en = new StringBuilder("Today Ibu chatted with Kenang");
        if (exactCount > 0) {
            id.append(" dan berbagi banyak kenangan");
            en.append(" and shared plenty of memories");
        }
        if (medAcked) {
            id.append(". Ibu minum obat pagi tepat waktu.");
            en.append(". She took her morning medicine on time.");
        } else {
            id.append(".");
            en.append(".");
        }

        String mood = t1Miss ? "lelah" : exactCount > 0 ? "ceria" : "tenang";

        List<Map<String, Object>> stories = new ArrayList<>();
        for (int i = 0; i < elderTurns.size(); i++) {
            if (probeAnswerIndices.contains(i)) {
                continue; // scoring data stays out of the diary
            }
            String turn = elderTurns.get(i);
            String lower = turn.toLowerCase();
            if (turn.length() >= 40 && (lower.contains("waktu") || lower.contains("dulu")
                    || lower.contains("tahun") || lower.contains("when i") || lower.contains("used to"))) {
                Map<String, Object> story = new HashMap<>();
                String[] words = turn.split("\\s+");
                StringBuilder title = new StringBuilder();
                for (int w = 0; w < Math.min(6, words.length); w++) {
                    if (w > 0) {
                        title.append(' ');
                    }
                    title.append(words[w]);
                }
                title.append("…");
                story.put("title", title.toString());
                story.put("quote", turn); // verbatim, never translated (V2.1 §1.1)
                stories.add(story);
            }
        }

        Map<String, Object> entry = new HashMap<>();
        entry.put("date", new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .format(new java.util.Date()));
        entry.put("summaryId", id.toString());
        entry.put("summaryEn", en.toString());
        entry.put("moodTag", mood);
        entry.put("stories", stories);
        entry.put("sessions", 1);
        repository.saveDiaryEntry(entry);
    }

    // ---- UI helpers ----

    // ---- Voice layer: Kenang speaks (TTS), the elder talks (STT) ----

    private void setupVoice() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                java.util.Locale locale = indonesian()
                        ? new java.util.Locale("in", "ID") : java.util.Locale.US;
                tts.setLanguage(locale);
                tts.setSpeechRate(0.9f); // unhurried, elder-friendly
                ttsReady = true;
            }
        });

        micButton = findViewById(R.id.micButton);
        diaryButton = findViewById(R.id.diaryButton);
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            micButton.setVisibility(View.GONE);
            diaryButton.setVisibility(View.GONE);
            return;
        }
        micButton.setOnClickListener(v -> {
            diaryMode = false;
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) {
                startListening();
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.RECORD_AUDIO}, 77);
            }
        });
        diaryButton.setOnClickListener(v -> {
            diaryMode = true;
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) {
                companionText.setText(R.string.diary_listening);
                startListening();
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.RECORD_AUDIO}, 77);
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 77 && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startListening();
        } else if (requestCode == 77) {
            Toast.makeText(this, R.string.mic_permission, Toast.LENGTH_SHORT).show();
        }
    }

    private void startListening() {
        if (recognizer != null) {
            recognizer.stopListening();
            recognizer.destroy();
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onResults(Bundle results) {
                resetMicState();
                java.util.ArrayList<String> matches =
                        results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty() && !matches.get(0).trim().isEmpty()) {
                    onElderTurn(matches.get(0));
                }
            }

            @Override
            public void onError(int error) {
                resetMicState();
            }

            @Override public void onReadyForSpeech(Bundle params) { }
            @Override public void onBeginningOfSpeech() { }
            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() { }
            @Override public void onPartialResults(Bundle partialResults) { }
            @Override public void onEvent(int eventType, Bundle params) { }
        });

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, indonesian() ? "id-ID" : "en-US");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        recognizer.startListening(intent);
        micButton.setBackgroundResource(R.drawable.bg_card_selected);
    }

    private void resetMicState() {
        micButton.setBackgroundResource(R.drawable.bg_input);
        if (recognizer != null) {
            recognizer.destroy();
            recognizer = null;
        }
    }

    private void speakAloud(String text) {
        if (ttsReady && tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "kenang_" + (utteranceCounter++));
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (recognizer != null) {
            recognizer.destroy();
        }
        super.onDestroy();
    }

    private void companionSay(String text) {
        companionText.setText(text);
        addTranscript(false, text);
        speakAloud(text);
    }

    private void addTranscript(boolean elder, String text) {
        chatHistory.add(new GeminiClient.ChatTurn(elder, text));
        while (chatHistory.size() > 12) {
            chatHistory.remove(0); // bounded context window for the chat API
        }
        dailyLog.appendTurn(this, elder, text);
        TextView row = new TextView(this);
        row.setTextSize(15);
        row.setText(elder ? "Ibu: " + text : "Kenang: " + text);
        row.setTextColor(getColor(elder ? R.color.slate_gray : R.color.ink_black));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (8 * getResources().getDisplayMetrics().density);
        row.setLayoutParams(lp);
        transcript.addView(row);
        transcriptScroll.post(() -> transcriptScroll.fullScroll(View.FOCUS_DOWN));
    }

    /** Humanized feed label from the fact's category (schema has no topic field). */
    private String eventLabel(Verdict verdict, ElderFact fact) {
        String topic = categoryLabel(fact.category);
        switch (verdict) {
            case EXACT:
                return (indonesian() ? "Mengingat " : "Remembered ") + topic;
            case PARTIAL:
                return (indonesian() ? "Ragu tentang " : "Hesitated on ") + topic;
            case MISS:
                return (indonesian() ? "Tidak dapat mengingat " : "Couldn't recall ") + topic;
            default:
                return getString(R.string.event_no_answer);
        }
    }

    private boolean indonesian() {
        String tags = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
                .toLanguageTags();
        return tags.startsWith("id") || tags.startsWith("in") || tags.isEmpty();
    }

    private String categoryLabel(String category) {
        boolean id = indonesian();
        Map<String, String[]> map = new HashMap<>();
        map.put("core_identity.family", new String[]{"memori keluarga inti", "a core family memory"});
        map.put("core_identity.location", new String[]{"alamat rumahnya", "her home address"});
        map.put("core_identity.self", new String[]{"tahun lahirnya", "her birth year"});
        map.put("recent.meals", new String[]{"santapan hari ini", "today's meal"});
        map.put("recent.family_events", new String[]{"acara keluarga terbaru", "a recent family event"});
        map.put("recent.health_routine", new String[]{"rutinitas obatnya", "her medication routine"});
        map.put("preferences.dining", new String[]{"restoran favoritnya", "her favorite restaurant"});
        map.put("preferences.music", new String[]{"lagu kesukaannya", "her favorite song"});
        map.put("preferences.hobbies", new String[]{"anggrek kesayangannya", "her beloved orchids"});
        map.put("preferences.entertainment", new String[]{"tontonan favoritnya", "her favorite show"});
        String[] labels = map.get(category);
        if (labels == null) {
            return id ? "sesuatu" : "something";
        }
        return id ? labels[0] : labels[1];
    }
}
