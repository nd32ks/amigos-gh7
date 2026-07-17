package com.amigos.kinbridge;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * One ground-truth fact — shape per agents/elder_context.json (LAW).
 * canonical_value may be a string OR an array (e.g. children names), so it is
 * stored as a list everywhere.
 */
public class ElderFact {
    public String factId;
    public int tier;
    public String category;
    public List<String> canonicalValues = new ArrayList<>();
    public List<String> aliases = new ArrayList<>();
    public List<String> probeTemplatesId = new ArrayList<>();
    public long cooldownHours;
    public long validUntilMs; // 0 = no expiry
    public long lastProbedAt;

    public boolean isEligible(long nowMs) {
        if (validUntilMs > 0 && nowMs > validUntilMs) {
            return false;
        }
        return lastProbedAt == 0 || nowMs >= lastProbedAt + cooldownHours * 3600_000L;
    }

    /** First probe template (companions vary templates in the full build). */
    public String probe() {
        return probeTemplatesId.isEmpty() ? "" : probeTemplatesId.get(0);
    }

    static ElderFact fromJson(JSONObject o) throws JSONException {
        ElderFact f = new ElderFact();
        f.factId = o.getString("fact_id");
        f.tier = o.getInt("tier");
        f.category = o.optString("category");
        Object canonical = o.get("canonical_value");
        if (canonical instanceof JSONArray) {
            f.canonicalValues = toStringList((JSONArray) canonical);
        } else {
            f.canonicalValues.add(String.valueOf(canonical));
        }
        f.aliases = toStringList(o.optJSONArray("accepted_aliases"));
        f.probeTemplatesId = toStringList(o.optJSONArray("probe_templates_id"));
        f.cooldownHours = o.optLong("probe_cooldown_hours", 24);
        f.validUntilMs = parseIso(o.optString("valid_until", null));
        f.lastProbedAt = 0;
        return f;
    }

    static List<String> toStringList(JSONArray arr) {
        List<String> out = new ArrayList<>();
        if (arr == null) {
            return out;
        }
        for (int i = 0; i < arr.length(); i++) {
            out.add(arr.optString(i));
        }
        return out;
    }

    private static long parseIso(String iso) {
        if (iso == null || iso.isEmpty() || "null".equals(iso)) {
            return 0;
        }
        try {
            java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US);
            java.util.Date date = fmt.parse(iso);
            return date != null ? date.getTime() : 0;
        } catch (java.text.ParseException e) {
            return 0;
        }
    }
}
