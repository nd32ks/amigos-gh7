package com.amigos.kinbridge;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Access layer. Ground-truth profile/facts/groups come from the bundled
 * assets/elder_context.json (LAW shape — cannot fail on rules). Live state
 * (events, trend, alerts) lives in Firestore with listeners. Probe cooldowns
 * persist in SharedPreferences.
 */
public class ElderRepository {

    public static final String ELDER_ID = "ibu_sri";
    private static final String ELDERS = "elders";
    private static final String STATE_PREFS = "elder_state";
    private static final double SEEDED_EWMA = 60.0;

    public interface ProfileCallback {
        void onLoaded(ElderProfile profile);

        void onError(String message);
    }

    public interface SimpleCallback {
        void onDone();
    }

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // ---- Profile (bundled LAW data) ----

    public void loadProfile(Context context, ProfileCallback callback) {
        try {
            String json = readAsset(context, "elder_context.json");
            JSONObject root = new JSONObject(json);

            JSONObject elder = root.getJSONObject("elder");
            List<ElderFact> facts = new ArrayList<>();
            JSONArray factsArr = root.getJSONArray("facts");
            SharedPreferences state = context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE);
            for (int i = 0; i < factsArr.length(); i++) {
                ElderFact f = ElderFact.fromJson(factsArr.getJSONObject(i));
                f.lastProbedAt = state.getLong("probed_" + f.factId, 0);
                facts.add(f);
            }

            List<ElderProfile.CommunityGroup> groups = new ArrayList<>();
            JSONArray groupsArr = root.optJSONArray("community_groups_mock");
            if (groupsArr != null) {
                for (int i = 0; i < groupsArr.length(); i++) {
                    JSONObject g = groupsArr.getJSONObject(i);
                    ElderProfile.CommunityGroup group = new ElderProfile.CommunityGroup();
                    group.groupId = g.getString("group_id");
                    group.name = g.getString("name");
                    group.meets = g.optString("meets");
                    group.distanceKm = g.optDouble("distance_km");
                    group.keywords = ElderFact.toStringList(g.optJSONArray("interest_keywords"));
                    groups.add(group);
                }
            }

            String phone = "";
            JSONArray contacts = root.optJSONArray("escalation_contacts");
            if (contacts != null && contacts.length() > 0) {
                phone = contacts.getJSONObject(0).optString("phone");
            }

            callback.onLoaded(new ElderProfile(
                    elder.getString("name"), elder.optString("preferred_address", elder.getString("name")),
                    elder.optString("city"), SEEDED_EWMA, phone, facts, groups));
        } catch (Exception e) {
            callback.onError(e.getMessage());
        }
    }

    public void markProbed(Context context, ElderFact fact) {
        fact.lastProbedAt = System.currentTimeMillis();
        context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
                .edit().putLong("probed_" + fact.factId, fact.lastProbedAt).apply();
    }

    private static String readAsset(Context context, String name) throws Exception {
        InputStream in = context.getAssets().open(name);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        in.close();
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    // ---- Trend pre-seed (30-day decline 82→60, demo chart) ----

    /** Seeds trend points into Firestore only if the collection is empty. */
    public void ensureSeeded(SimpleCallback onDone) {
        db.collection(ELDERS).document(ELDER_ID).collection("trend").limit(1).get()
                .addOnSuccessListener(snap -> {
                    if (!snap.isEmpty()) {
                        onDone.onDone();
                        return;
                    }
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
                })
                .addOnFailureListener(e -> onDone.onDone());
    }

    /** Local copy of the seeded trend, for offline/rules-denied fallback. */
    public static List<double[]> localSeedTrend() {
        List<double[]> points = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            double cri = 82 - (22.0 * i / 29) + 2.5 * Math.sin(i * 1.7);
            double ewma = 82 - (22.0 * i / 29) + 1.2 * Math.sin(i * 0.9);
            points.add(new double[]{cri, ewma});
        }
        return points;
    }

    // ---- Live state (events, trend, alerts) ----

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

    public void saveMatchEvent(String label) {
        Map<String, Object> event = new HashMap<>();
        event.put("ts", System.currentTimeMillis());
        event.put("tier", 0);
        event.put("verdict", "match");
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
}
