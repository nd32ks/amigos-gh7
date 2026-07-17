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
import androidx.appcompat.app.AppCompatDelegate;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

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
    private ListenerRegistration delegationsRegistration;
    private ListenerRegistration diaryRegistration;

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

        LanguageToggle.bind(this);

        openedAt = System.currentTimeMillis();
        trendChart = findViewById(R.id.trendChart);
        eventsFeed = findViewById(R.id.eventsFeed);
        todayCount = findViewById(R.id.todayCount);
        warningBanner = findViewById(R.id.warningBanner);
        warningText = findViewById(R.id.warningText);

        findViewById(R.id.dashboardLogout).setOnClickListener(v -> {            userRepository.signOut();
            FontScale.reset(this);
            Intent intent = new Intent(this, OnboardingActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });

        findViewById(R.id.onboardLink).setOnClickListener(v ->
                startActivity(new Intent(this, OnboardWizardActivity.class)));

        buildAdherencePanel();

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
        delegationsRegistration = repository.listenDelegations(this::onDelegations);
        diaryRegistration = repository.listenDiary(this::onDiary);
        scheduleTrendFallback();

        findViewById(R.id.tabTrend).setOnClickListener(v -> selectTab(true));
        findViewById(R.id.tabDiary).setOnClickListener(v -> selectTab(false));
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
        if (delegationsRegistration != null) {
            delegationsRegistration.remove();
        }
        if (diaryRegistration != null) {
            diaryRegistration.remove();
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
        boolean playfulThisWeek = false;
        for (DocumentSnapshot doc : docs) {
            Long ts = doc.getLong("ts");
            String verdict = doc.getString("verdict");
            if (ts != null && ts >= startOfDay && !"match".equals(verdict)) {
                today++;
            }
            if (ts != null && ts >= System.currentTimeMillis() - 7L * 24 * 3600_000L
                    && "playful".equals(doc.getString("deliveryStyle"))) {
                playfulThisWeek = true;
            }
            addEventRow(verdict, doc.getString("label"),
                    doc.getLong("tier") != null ? doc.getLong("tier").intValue() : 3);
        }
        todayCount.setText(getString(R.string.moments_scored, today));
        findViewById(R.id.flavorLine).setVisibility(playfulThisWeek ? View.VISIBLE : View.GONE);
    }

    // ---- Tabs: Trend (clinical) | Diary (love, V2.1 §1.3) ----

    private boolean trendTab = true;

    private void selectTab(boolean trend) {
        trendTab = trend;
        int[] trendViews = {R.id.trendCard, R.id.adherenceCard, R.id.delegationsTitle,
                R.id.delegationsList, R.id.momentsTitle, R.id.eventsFeed};
        for (int id : trendViews) {
            findViewById(id).setVisibility(trend ? View.VISIBLE : View.GONE);
        }
        findViewById(R.id.diaryContent).setVisibility(trend ? View.GONE : View.VISIBLE);
        findViewById(R.id.tabTrend).setBackgroundResource(trend ? R.drawable.bg_lang_selected : 0);
        findViewById(R.id.tabDiary).setBackgroundResource(trend ? 0 : R.drawable.bg_lang_selected);
    }

    @SuppressWarnings("unchecked")
    private void onDiary(List<DocumentSnapshot> docs) {
        LinearLayout list = findViewById(R.id.diaryList);
        list.removeAllViews();
        findViewById(R.id.diaryEmpty).setVisibility(docs.isEmpty() ? View.VISIBLE : View.GONE);
        float density = getResources().getDisplayMetrics().density;
        boolean indonesian = AppCompatDelegate.getApplicationLocales()
                .toLanguageTags().startsWith("id");
        for (DocumentSnapshot doc : docs) {
            // Date header
            Long ts = doc.getLong("ts");
            TextView date = new TextView(this);
            date.setTextSize(20);
            date.setTextColor(getColor(R.color.ink_black));
            date.setTypeface(android.graphics.Typeface.SERIF);
            date.setText(doc.getString("date") != null ? doc.getString("date")
                    : new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                            .format(new java.util.Date(ts != null ? ts : 0)));
            LinearLayout.LayoutParams dateLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            dateLp.topMargin = (int) (20 * density);
            date.setLayoutParams(dateLp);
            list.addView(date);

            // Summary (bilingual at write time — never translated on demand, V2.1 §2.2)
            TextView summary = new TextView(this);
            summary.setTextSize(15);
            summary.setTextColor(getColor(R.color.ash_gray));
            summary.setText(indonesian ? doc.getString("summaryId") : doc.getString("summaryEn"));
            LinearLayout.LayoutParams sumLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            sumLp.topMargin = (int) (8 * density);
            summary.setLayoutParams(sumLp);
            list.addView(summary);

            // Story cards — verbatim quotes, never translated (V2.1 §1.1)
            List<Map<String, Object>> stories = (List<Map<String, Object>>) doc.get("stories");
            if (stories == null) {
                continue;
            }
            for (Map<String, Object> story : stories) {
                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setBackgroundResource(R.drawable.bg_card_selected);
                int pad = (int) (16 * density);
                card.setPadding(pad, pad, pad, pad);
                LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                cardLp.topMargin = (int) (12 * density);
                card.setLayoutParams(cardLp);

                TextView title = new TextView(this);
                title.setTextSize(12);
                title.setTextColor(getColor(R.color.ash_gray));
                title.setText((String) story.get("title"));
                card.addView(title);

                TextView quote = new TextView(this);
                quote.setTextSize(18);
                quote.setTextColor(getColor(R.color.ink_black));
                quote.setTypeface(android.graphics.Typeface.SERIF, android.graphics.Typeface.ITALIC);
                quote.setText("“" + story.get("quote") + "”");
                LinearLayout.LayoutParams quoteLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                quoteLp.topMargin = (int) (8 * density);
                quote.setLayoutParams(quoteLp);
                card.addView(quote);

                TextView share = new TextView(this);
                share.setTextSize(13);
                share.setTextColor(getColor(R.color.signal_violet));
                share.setText(R.string.diary_share);
                LinearLayout.LayoutParams shareLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                shareLp.topMargin = (int) (12 * density);
                share.setLayoutParams(shareLp);
                share.setOnClickListener(v -> {
                    share.setText(R.string.diary_shared);
                    share.setOnClickListener(null);
                });
                card.addView(share);

                list.addView(card);
            }
        }
    }

    private void onDelegations(List<DocumentSnapshot> docs) {
        LinearLayout list = findViewById(R.id.delegationsList);
        TextView title = findViewById(R.id.delegationsTitle);
        list.removeAllViews();
        title.setVisibility(docs.isEmpty() ? View.GONE : View.VISIBLE);
        float density = getResources().getDisplayMetrics().density;
        for (DocumentSnapshot doc : docs) {
            boolean pending = "pending".equals(doc.getString("status"));

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundResource(pending ? R.drawable.bg_option_card : R.drawable.bg_card);
            int pad = (int) (16 * density);
            card.setPadding(pad, pad, pad, pad);
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cardLp.topMargin = (int) (8 * density);
            card.setLayoutParams(cardLp);

            TextView cardTitle = new TextView(this);
            cardTitle.setTextSize(15);
            cardTitle.setTextColor(getColor(R.color.ink_black));
            cardTitle.setText(doc.getString("title"));
            card.addView(cardTitle);

            String body = doc.getString("body");
            if (body != null && !body.isEmpty()) {
                TextView cardBody = new TextView(this);
                cardBody.setTextSize(13);
                cardBody.setTextColor(getColor(R.color.slate_gray));
                cardBody.setText(body);
                card.addView(cardBody);
            }

            if (pending) {
                TextView approve = new TextView(this);
                approve.setTextSize(15);
                approve.setTextColor(getColor(R.color.signal_violet));
                approve.setText(R.string.approve);
                int btnPad = (int) (8 * density);
                approve.setPadding(0, btnPad * 2, 0, 0);
                String docId = doc.getId();
                approve.setOnClickListener(v -> repository.completeDelegation(docId));
                card.addView(approve);
            } else {
                TextView done = new TextView(this);
                done.setTextSize(12);
                done.setTextColor(getColor(R.color.mint_pulse));
                done.setText(R.string.completed_label);
                card.addView(done);
            }
            list.addView(card);
        }
    }

    /** 7-day adherence grid per reminder (V2 §3.4) + derived health signal (§4.3). */
    private void buildAdherencePanel() {
        LinearLayout list = findViewById(R.id.adherenceList);
        float density = getResources().getDisplayMetrics().density;
        List<String[]> reminders = ReminderEngine.reminderLabels(this);
        for (String[] reminder : reminders) {
            TextView label = new TextView(this);
            label.setText(reminder[1]);
            label.setTextSize(12);
            label.setTextColor(getColor(R.color.ash_gray));
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            labelLp.topMargin = (int) (12 * density);
            label.setLayoutParams(labelLp);
            list.addView(label);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rowLp.topMargin = (int) (6 * density);
            row.setLayoutParams(rowLp);
            for (int day = 6; day >= 0; day--) {
                String state = ReminderEngine.adherenceFor(this, reminder[0], day);
                TextView cell = new TextView(this);
                cell.setTextSize(13);
                cell.setGravity(android.view.Gravity.CENTER);
                int cellSize = (int) (28 * density);
                LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(cellSize, cellSize);
                cellLp.setMarginEnd((int) (6 * density));
                cell.setLayoutParams(cellLp);
                cell.setBackgroundResource(R.drawable.bg_card);
                if ("ACKED".equals(state)) {
                    cell.setText("✓");
                    cell.setTextColor(getColor(R.color.mint_pulse));
                } else if ("MISSED".equals(state)) {
                    cell.setText("✗");
                    cell.setTextColor(getColor(R.color.slate_gray));
                } else {
                    cell.setText("·");
                    cell.setTextColor(getColor(R.color.mist_gray));
                }
                row.addView(cell);
            }
            list.addView(row);
        }

        // Komorbid shows only as a derived signal — never a condition list (§4.3)
        try {
            java.io.InputStream in = getAssets().open("v2/health_profile.json");
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            in.close();
            TailoringRules rules = new TailoringRules(
                    new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8));
            if (rules.hasDerivedSignal()) {
                findViewById(R.id.adherenceSignal).setVisibility(View.VISIBLE);
            }
        } catch (Exception ignored) {
        }
    }

    private void addEventRow(String verdict, String label, int tier) {        LinearLayout row = new LinearLayout(this);
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
