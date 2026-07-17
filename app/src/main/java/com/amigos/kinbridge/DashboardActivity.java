package com.amigos.kinbridge;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Family dashboard (ui_spec.md §3): wellness trend, today's activity, recent
 * moments feed, acute alert modal (§4). The word is always "wellness".
 */
public class DashboardActivity extends AppCompatActivity {

    private final ElderRepository repository = new ElderRepository();
    private final UserRepository userRepository = new UserRepository();

    private TrendChartView trendChart;
    private LinearLayout eventsFeed;
    private TextView todayCount;
    private LinearLayout warningBanner;
    private TextView warningText;

    private ListenerRegistration trendRegistration;
    private ListenerRegistration eventsRegistration;
    private ListenerRegistration alertsRegistration;

    private long openedAt;
    private long lastShownAlertTs;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(FontScale.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        openedAt = System.currentTimeMillis();
        trendChart = findViewById(R.id.trendChart);
        eventsFeed = findViewById(R.id.eventsFeed);
        todayCount = findViewById(R.id.todayCount);
        warningBanner = findViewById(R.id.warningBanner);
        warningText = findViewById(R.id.warningText);

        findViewById(R.id.dashboardLogout).setOnClickListener(v -> {
            userRepository.signOut();
            FontScale.reset(this);
            Intent intent = new Intent(this, OnboardingActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });

        repository.ensureSeeded(() -> repository.loadProfile(this, new ElderRepository.ProfileCallback() {
            @Override
            public void onLoaded(ElderProfile profile) {
                TextView subtitle = findViewById(R.id.elderSubtitle);
                subtitle.setText(getString(R.string.elder_subtitle,
                        profile.preferredAddress, profile.city));
            }

            @Override
            public void onError(String message) {
            }
        }));
    }

    @Override
    protected void onStart() {
        super.onStart();
        trendRegistration = repository.listenTrend(this::onTrend);
        eventsRegistration = repository.listenEvents(this::onEvents);
        alertsRegistration = repository.listenAlerts(this::onAlert);
        scheduleTrendFallback();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (trendRegistration != null) {
            trendRegistration.remove();
        }
        if (eventsRegistration != null) {
            eventsRegistration.remove();
        }
        if (alertsRegistration != null) {
            alertsRegistration.remove();
        }
    }

    // ---- Live data ----

    private void onTrend(List<DocumentSnapshot> docs) {
        if (!docs.isEmpty()) {
            trendLoaded = true;
        }
        List<Double> cri = new ArrayList<>();
        List<Double> ewma = new ArrayList<>();
        for (DocumentSnapshot doc : docs) {
            Double c = doc.getDouble("cri");
            Double e = doc.getDouble("ewma");
            if (c != null && e != null) {
                cri.add(c);
                ewma.add(e);
            }
        }
        trendChart.setData(cri, ewma);
    }

    private boolean trendLoaded;

    /** Falls back to the bundled 30-day seed if Firestore yields nothing. */
    private void scheduleTrendFallback() {
        trendChart.postDelayed(() -> {
            if (!trendLoaded) {
                List<Double> cri = new ArrayList<>();
                List<Double> ewma = new ArrayList<>();
                for (double[] pair : ElderRepository.localSeedTrend()) {
                    cri.add(pair[0]);
                    ewma.add(pair[1]);
                }
                trendChart.setData(cri, ewma);
            }
        }, 4000);
    }

    private void onEvents(List<DocumentSnapshot> docs) {
        eventsFeed.removeAllViews();
        long startOfDay = startOfToday();
        int today = 0;
        for (DocumentSnapshot doc : docs) {
            Long ts = doc.getLong("ts");
            String verdict = doc.getString("verdict");
            if (ts != null && ts >= startOfDay && !"match".equals(verdict)) {
                today++;
            }
            addEventRow(verdict, doc.getString("label"),
                    doc.getLong("tier") != null ? doc.getLong("tier").intValue() : 3);
        }
        todayCount.setText(getString(R.string.moments_scored, today));
    }

    private void addEventRow(String verdict, String label, int tier) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        row.setPadding(0, pad, 0, pad);

        TextView icon = new TextView(this);
        icon.setTextSize(16);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                (int) (28 * getResources().getDisplayMetrics().density),
                LinearLayout.LayoutParams.WRAP_CONTENT);
        icon.setLayoutParams(iconLp);

        if ("exact".equals(verdict)) {
            icon.setText("✓");
            icon.setTextColor(getColor(R.color.mint_pulse));
        } else if ("match".equals(verdict)) {
            icon.setText("+");
            icon.setTextColor(getColor(R.color.signal_violet));
        } else if ("miss".equals(verdict)) {
            icon.setText("✗");
            icon.setTextColor(getColor(R.color.ink_black));
        } else {
            icon.setText("~");
            icon.setTextColor(getColor(R.color.slate_gray));
        }

        TextView labelView = new TextView(this);
        labelView.setTextSize(15);
        labelView.setTextColor(getColor(R.color.ink_black));
        labelView.setText(label != null ? label : "");
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        labelView.setLayoutParams(labelLp);

        TextView tierView = new TextView(this);
        tierView.setTextSize(12);
        tierView.setTextColor(getColor(R.color.slate_gray));
        tierView.setText(getString(R.string.tier_tag, tier));

        row.addView(icon);
        row.addView(labelView);
        row.addView(tierView);
        eventsFeed.addView(row);
    }

    private void onAlert(DocumentSnapshot alert) {
        Long ts = alert.getLong("ts");
        String kind = alert.getString("kind");
        if (ts == null || kind == null) {
            return;
        }
        if ("warning".equals(kind) && ts >= startOfToday()) {
            warningText.setText(alert.getString("title"));
            warningBanner.setVisibility(View.VISIBLE);
            return;
        }
        // Acute alerts show the modal once, only if they arrived while/after
        // the dashboard was opened (ui_spec.md §4).
        if ("acute_t1".equals(kind) && ts >= openedAt && ts > lastShownAlertTs) {
            lastShownAlertTs = ts;
            showAcuteAlert(alert.getString("title"), alert.getString("body"));
        }
    }

    private void showAcuteAlert(String title, String body) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_alert);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        ((TextView) dialog.findViewById(R.id.alertTitle)).setText(title);
        ((TextView) dialog.findViewById(R.id.alertBody)).setText(body);
        dialog.findViewById(R.id.alertCall).setOnClickListener(v ->
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:"))));
        dialog.findViewById(R.id.alertDismiss).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private long startOfToday() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }
}
