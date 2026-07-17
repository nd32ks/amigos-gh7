package com.amigos.kinbridge;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.amigos.kinbridge.onboard.DumpParser;
import com.amigos.kinbridge.onboard.DumpParser.ExtractedFact;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * Guardian context-dump wizard (V2 §5): free-text dump → deterministic
 * extraction → facts cascade into three tiers → confirm writes them into the
 * elder's profile (prefs-merged, per §7 the live V1 asset file stays intact).
 */
public class OnboardWizardActivity extends AppCompatActivity {

    private List<ExtractedFact> extracted;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(FontScale.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboard_wizard);

        findViewById(R.id.wizardBack).setOnClickListener(v -> finish());
        findViewById(R.id.processButton).setOnClickListener(v -> processDump());
        findViewById(R.id.confirmButton).setOnClickListener(v -> confirm());
    }

    private void processDump() {
        String text = ((EditText) findViewById(R.id.dumpInput)).getText().toString().trim();
        if (text.isEmpty()) {
            return;
        }
        // Gemini structured extraction (V2 §5); DumpParser is the offline
        // fallback, matching the doc's cached-parse escape hatch (§7).
        GeminiClient.extractFacts(text, new GeminiClient.ExtractCallback() {
            @Override
            public void onResult(java.util.List<ExtractedFact> facts) {
                extracted = facts.isEmpty() ? DumpParser.parse(text) : facts;
                populateTiers();
                crossfade(findViewById(R.id.stepDump), findViewById(R.id.stepReview));
            }

            @Override
            public void onError() {
                extracted = DumpParser.parse(text);
                populateTiers();
                crossfade(findViewById(R.id.stepDump), findViewById(R.id.stepReview));
            }
        });
    }

    private void populateTiers() {
        LinearLayout tier1 = findViewById(R.id.tier1List);
        LinearLayout tier2 = findViewById(R.id.tier2List);
        LinearLayout tier3 = findViewById(R.id.tier3List);
        tier1.removeAllViews();
        tier2.removeAllViews();
        tier3.removeAllViews();

        int delay = 0;
        for (ExtractedFact fact : extracted) {
            LinearLayout target = fact.tier == 1 ? tier1 : fact.tier == 2 ? tier2 : tier3;
            TextView row = factRow(fact);
            target.addView(row);
            // Facts materialize one by one (V2 §5.2 animated review)
            row.setAlpha(0f);
            row.animate().alpha(1f).setStartDelay(delay).setDuration(200).start();
            delay += 120;
        }
        addEmptyIfNeeded(tier1);
        addEmptyIfNeeded(tier2);
        addEmptyIfNeeded(tier3);
    }

    private TextView factRow(ExtractedFact fact) {
        float density = getResources().getDisplayMetrics().density;
        TextView row = new TextView(this);
        row.setText(fact.canonical);
        row.setTextSize(15);
        row.setTextColor(getColor(R.color.ink_black));
        row.setBackgroundResource(R.drawable.bg_card);
        int padH = (int) (14 * density);
        int padV = (int) (10 * density);
        row.setPadding(padH, padV, padH, padV);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (8 * density);
        row.setLayoutParams(lp);
        return row;
    }

    private void addEmptyIfNeeded(LinearLayout list) {
        if (list.getChildCount() == 0) {
            TextView empty = new TextView(this);
            empty.setText(R.string.review_empty_tier);
            empty.setTextSize(13);
            empty.setTextColor(getColor(R.color.slate_gray));
            list.addView(empty);
        }
    }

    private void confirm() {
        if (extracted == null) {
            finish();
            return;
        }
        try {
            SharedPreferences state = getSharedPreferences("elder_state", MODE_PRIVATE);
            JSONArray arr = new JSONArray(state.getString("custom_facts_json", "[]"));
            int next = arr.length();
            for (ExtractedFact fact : extracted) {
                JSONObject o = new JSONObject();
                o.put("factId", "CUSTOM_" + (++next));
                o.put("tier", fact.tier);
                o.put("category", fact.category);
                o.put("canonical", fact.canonical);
                o.put("probe", getString(R.string.custom_probe, fact.canonical));
                arr.put(o);
            }
            state.edit().putString("custom_facts_json", arr.toString()).apply();
        } catch (Exception e) {
            android.util.Log.w("OnboardWizard", "persist failed", e);
        }
        finish();
    }

    private void crossfade(View from, View to) {
        from.animate().alpha(0f).setDuration(150).withEndAction(() -> {
            from.setVisibility(View.GONE);
            from.setAlpha(1f);
            to.setAlpha(0f);
            to.setVisibility(View.VISIBLE);
            to.animate().alpha(1f).setDuration(150).start();
        }).start();
    }
}
