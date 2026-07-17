package com.amigos.kinbridge;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Firestore access for the elder's ground-truth profile, event ledger,
 * trend points and family alerts (system_architecture.md §3, Firestore flavour).
 */
public class ElderRepository {

    public static final String ELDER_ID = "ibu_sri";
    private static final String ELDERS = "elders";

    public interface ProfileCallback {
        void onLoaded(ElderProfile profile);

        void onError(String message);
    }

    public interface SimpleCallback {
        void onDone();
    }

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    /** Seeds Ibu Sri's profile + 30-day pre-seeded trend (82→60) if absent. */
    public void ensureSeeded(SimpleCallback onDone) {
        db.collection(ELDERS).document(ELDER_ID).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                onDone.onDone();
                return;
            }
            Map<String, Object> elder = new HashMap<>();
            elder.put("name", "Ibu Sri Rahayu Wijaya");
            elder.put("location", "Gading Serpong");
            elder.put("prevEwma", 60.0);
            elder.put("facts", seedFactMaps());
            db.collection(ELDERS).document(ELDER_ID).set(elder)
                    .addOnSuccessListener(unused -> seedTrend(onDone))
                    .addOnFailureListener(e -> onDone.onDone());
        }).addOnFailureListener(e -> onDone.onDone());
    }

    /** 30 days of gentle decline 82→60 with a deterministic wobble (demo chart). */
    private void seedTrend(SimpleCallback onDone) {
        long dayMs = 24L * 3600_000L;
        long now = System.currentTimeMillis();
        for (int i = 0; i < 30; i++) {
            double cri = 82 - (22.0 * i / 29) + 2.5 * Math.sin(i * 1.7);
            double ewma = 82 - (22.0 * i / 29) + 1.2 * Math.sin(i * 0.9);
            Map<String, Object> point = new HashMap<>();
            point.put("ts", now - (29L - i) * dayMs);
            point.put("cri", cri);
            point.put("ewma", ewma);
            point.put("probes", 4 + (i % 3));
            db.collection(ELDERS).document(ELDER_ID).collection("trend").add(point);
        }
        onDone.onDone();
    }

    public void loadProfile(ProfileCallback callback) {
        db.collection(ELDERS).document(ELDER_ID).get().addOnSuccessListener(doc -> {
            if (!doc.exists()) {
                callback.onError("elder profile missing");
                return;
            }
            callback.onLoaded(profileFrom(doc));
        }).addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    @SuppressWarnings("unchecked")
    private ElderProfile profileFrom(DocumentSnapshot doc) {
        List<ElderFact> facts = new ArrayList<>();
        Object raw = doc.get("facts");
        if (raw instanceof List) {
            for (Object item : (List<Object>) raw) {
                if (item instanceof Map) {
                    facts.add(ElderFact.fromMap((Map<String, Object>) item));
                }
            }
        }
        Double prev = doc.getDouble("prevEwma");
        return new ElderProfile(doc.getString("name"), doc.getString("location"),
                prev != null ? prev : 60.0, facts);
    }

    /** Writes back the facts array (updates last_probed_at after a probe). */
    public void saveFacts(List<ElderFact> facts) {
        List<Map<String, Object>> maps = new ArrayList<>();
        for (ElderFact f : facts) {
            maps.add(f.toMap());
        }
        db.collection(ELDERS).document(ELDER_ID).update("facts", maps);
    }

    public void saveEvent(int tier, String verdict, int rawPoints, double criCredit, String label) {
        Map<String, Object> event = new HashMap<>();
        event.put("ts", System.currentTimeMillis());
        event.put("tier", tier);
        event.put("verdict", verdict);
        event.put("rawPoints", rawPoints);
        event.put("criCredit", criCredit);
        event.put("label", label);
        db.collection(ELDERS).document(ELDER_ID).collection("events").add(event);
    }

    public void saveTrendPoint(double cri, double ewma, int probes) {
        Map<String, Object> point = new HashMap<>();
        point.put("ts", System.currentTimeMillis());
        point.put("cri", cri);
        point.put("ewma", ewma);
        point.put("probes", probes);
        db.collection(ELDERS).document(ELDER_ID).collection("trend").add(point);
        db.collection(ELDERS).document(ELDER_ID).update("prevEwma", ewma);
    }

    public void saveAlert(String kind, String title, String body) {
        Map<String, Object> alert = new HashMap<>();
        alert.put("ts", System.currentTimeMillis());
        alert.put("kind", kind);
        alert.put("title", title);
        alert.put("body", body);
        db.collection(ELDERS).document(ELDER_ID).collection("alerts").add(alert);
    }

    public ListenerRegistration listenTrend(TrendListener listener) {
        return db.collection(ELDERS).document(ELDER_ID).collection("trend")
                .orderBy("ts", Query.Direction.ASCENDING).limitToLast(60)
                .addSnapshotListener((snap, e) -> {
                    if (snap != null) {
                        listener.onTrend(snap.getDocuments());
                    }
                });
    }

    public ListenerRegistration listenEvents(EventsListener listener) {
        return db.collection(ELDERS).document(ELDER_ID).collection("events")
                .orderBy("ts", Query.Direction.DESCENDING).limit(20)
                .addSnapshotListener((snap, e) -> {
                    if (snap != null) {
                        listener.onEvents(snap.getDocuments());
                    }
                });
    }

    public ListenerRegistration listenAlerts(AlertsListener listener) {
        return db.collection(ELDERS).document(ELDER_ID).collection("alerts")
                .orderBy("ts", Query.Direction.DESCENDING).limit(1)
                .addSnapshotListener((snap, e) -> {
                    if (snap != null && !snap.isEmpty()) {
                        listener.onAlert(snap.getDocuments().get(0));
                    }
                });
    }

    public interface TrendListener {
        void onTrend(List<DocumentSnapshot> points);
    }

    public interface EventsListener {
        void onEvents(List<DocumentSnapshot> events);
    }

    public interface AlertsListener {
        void onAlert(DocumentSnapshot alert);
    }

    /** Ground-truth facts reconstructed from agents/prompts.md + demo_script.md. */
    private List<Map<String, Object>> seedFactMaps() {
        List<ElderFact> facts = new ArrayList<>();

        facts.add(fact("T1_SPOUSE_NAME", 1,
                "Bagaimana kabar suami Ibu hari ini? Siapa nama beliau?",
                "How is your husband today? What is his name?",
                "Budi Wijaya", Arrays.asList("pak budi", "budi"),
                "nama suaminya", "her husband's name", 48));

        facts.add(fact("T2_FAMILY_VISIT", 2,
                "Ada yang datang berkunjung minggu ini? Siapa yang datang?",
                "Has anyone visited this week? Who came?",
                "Dewi", Arrays.asList("dewi"),
                "kunjungan keluarga minggu ini", "this week's family visit", 24));

        facts.add(fact("T3_ORCHIDS", 3,
                "Bagaimana kabar tanaman kesayangan Ibu? Sudah berbunga?",
                "How are your beloved plants? Have they bloomed?",
                "anggrek bulan", Arrays.asList("anggrek"),
                "anggreknya", "her orchids", 12));

        facts.add(fact("T3_BREAKFAST", 3,
                "Apa yang Ibu makan untuk sarapan pagi ini?",
                "What did you eat for breakfast this morning?",
                "bubur ayam", Arrays.asList("bubur"),
                "sarapan hari ini", "today's breakfast", 12));

        facts.add(fact("T3_RESTAURANT", 3,
                "Restoran yang Ibu kasih bintang lima itu, apa namanya?",
                "That restaurant you rated five stars — what was its name?",
                "ENA Dining", Arrays.asList("ena", "omakase"),
                "restoran favoritnya", "her favorite restaurant", 12));

        List<Map<String, Object>> maps = new ArrayList<>();
        for (ElderFact f : facts) {
            maps.add(f.toMap());
        }
        return maps;
    }

    private ElderFact fact(String id, int tier, String qId, String qEn, String canonical,
                           List<String> aliases, String topicId, String topicEn, long cooldownHours) {
        ElderFact f = new ElderFact();
        f.factId = id;
        f.tier = tier;
        f.questionId = qId;
        f.questionEn = qEn;
        f.canonical = canonical;
        f.aliases = aliases;
        f.topicId = topicId;
        f.topicEn = topicEn;
        f.cooldownHours = cooldownHours;
        f.lastProbedAt = 0;
        return f;
    }
}
