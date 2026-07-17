package com.amigos.kinbridge;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Social Worker third profile (V2.2 §B) — read-only caseload preview.
 * Permission matrix: social workers see wellness trend + adherence, never
 * the diary or the condition list. Detail view reuses the guardian dashboard
 * for the one real elder (elder_0001).
 */
public class CarePanelActivity extends AppCompatActivity {

    private final UserRepository userRepository = new UserRepository();

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(FontScale.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_care_panel);

        SideMenu.bind(this);

        findViewById(R.id.careLogout).setOnClickListener(v -> {
            userRepository.signOut();
            FontScale.reset(this);
            Intent intent = new Intent(this, OnboardingActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });

        loadCaseload();
    }

    private void loadCaseload() {
        try {
            JSONObject root = new JSONObject(readAsset("v2/care_worker.json"));
            JSONObject worker = root.getJSONObject("worker");
            ((TextView) findViewById(R.id.careWorkerName))
                    .setText(worker.getString("name") + " · " + worker.optString("area"));

            LinearLayout list = findViewById(R.id.caseloadList);
            JSONArray caseload = root.getJSONArray("caseload");
            for (int i = 0; i < caseload.length(); i++) {
                JSONObject elder = caseload.getJSONObject(i);
                list.addView(buildCard(elder, i));
            }
        } catch (Exception e) {
            // Mock data is bundled; failure here means a broken asset — log and move on.
            android.util.Log.w("CarePanel", "caseload load failed", e);
        }
    }

    private View buildCard(JSONObject elder, int index) {
        float density = getResources().getDisplayMetrics().density;

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        int pad = (int) (20 * density);
        card.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.topMargin = (int) (16 * density);
        card.setLayoutParams(cardLp);

        // Name row + alert badge
        LinearLayout nameRow = new LinearLayout(this);
        nameRow.setOrientation(LinearLayout.HORIZONTAL);
        nameRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView name = new TextView(this);
        name.setTextSize(20);
        name.setTextColor(getColor(R.color.ink_black));
        name.setTypeface(name.getTypeface(), Typeface.BOLD);
        name.setText(elder.optString("preferred_address", elder.optString("name")));
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        name.setLayoutParams(nameLp);
        nameRow.addView(name);
        if (elder.optBoolean("alert")) {
            TextView badge = new TextView(this);
            badge.setTextSize(12);
            badge.setTextColor(getColor(R.color.pure_white));
            badge.setBackgroundColor(getColor(R.color.signal_violet));
            int bPadH = (int) (10 * density);
            int bPadV = (int) (3 * density);
            badge.setPadding(bPadH, bPadV, bPadH, bPadV);
            badge.setText(R.string.alert_badge);
            nameRow.addView(badge);
        }
        card.addView(nameRow);

        TextView area = new TextView(this);
        area.setTextSize(13);
        area.setTextColor(getColor(R.color.slate_gray));
        area.setText(elder.optString("area"));
        card.addView(area);

        // Wellness trend sparkline (mock data per caseload entry)
        TrendChartView sparkline = new TrendChartView(this, null);
        LinearLayout.LayoutParams chartLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) (48 * density));
        chartLp.topMargin = (int) (12 * density);
        sparkline.setLayoutParams(chartLp);
        sparkline.setData(mockCri(index), mockEwma(index));
        card.addView(sparkline);

        TextView adherence = new TextView(this);
        adherence.setTextSize(13);
        adherence.setTextColor(getColor(R.color.ash_gray));
        adherence.setText(getString(R.string.adherence_pct, elder.optInt("adherence_pct")));
        LinearLayout.LayoutParams adhLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        adhLp.topMargin = (int) (8 * density);
        adherence.setLayoutParams(adhLp);
        card.addView(adherence);

        // Detail view for the one real elder; others are seeded mock rows.
        if ("elder_0001".equals(elder.optString("profile_id"))) {
            card.setOnClickListener(v ->
                    startActivity(new Intent(this, DashboardActivity.class)));
        }
        return card;
    }

    private List<Double> mockCri(int seed) {
        List<Double> out = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            out.add(80 - seed * 6 - i * (1.5 + seed) + 2 * Math.sin(i + seed));
        }
        return out;
    }

    private List<Double> mockEwma(int seed) {
        List<Double> out = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            out.add(82 - seed * 6 - i * (1.2 + seed * 0.8) + Math.sin(i * 0.7 + seed));
        }
        return out;
    }

    private String readAsset(String name) throws Exception {
        InputStream in = getAssets().open(name);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        in.close();
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
