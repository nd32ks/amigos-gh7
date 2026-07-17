package com.amigos.kinbridge;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Komorbid tailoring (V2 §4): conditions compile into BEHAVIORAL effects —
 * never displayed, never injected verbatim. Deterministic rules, no ML.
 */
public final class TailoringRules {

    private final List<String> conditions = new ArrayList<>();

    public TailoringRules(String healthProfileJson) {
        try {
            JSONArray arr = new JSONObject(healthProfileJson).optJSONArray("conditions");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    conditions.add(arr.getString(i));
                }
            }
        } catch (Exception ignored) {
        }
    }

    public boolean has(String condition) {
        return conditions.contains(condition);
    }

    /** osteoartritis_lutut → seated movement variant (V2.2 §A.2). */
    public String movementPrompt(String defaultPrompt) {
        if (has("osteoartritis_lutut")) {
            return "Sekarang gerak-gerak sambil duduk yuk Bu, angkat tangan pelan-pelan lima kali saja. Sambil lihat anggreknya.";
        }
        return defaultPrompt;
    }

    /** diabetes_tipe_2 → culinary group tagline swaps to low-sugar (V2 §4 demo). */
    public String groupTagline(String groupId, String defaultMeets) {
        if (has("diabetes_tipe_2") && "grp_03".equals(groupId)) {
            return "menu sehat rendah gula · " + defaultMeets;
        }
        return defaultMeets;
    }

    /** Derived dashboard signal — the only surface the health profile gets. */
    public boolean hasDerivedSignal() {
        return !conditions.isEmpty();
    }
}
