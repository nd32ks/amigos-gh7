package com.amigos.kinbridge;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.List;
import java.util.Map;

/**
 * Elder diary view (Buku Harian) — her own entries in large, calm type.
 * Verbatim quotes stay in her language, always (V2.1 §1.1).
 */
public class ElderDiaryActivity extends AppCompatActivity {

    private final ElderRepository repository = new ElderRepository();
    private ListenerRegistration diaryRegistration;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(FontScale.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_elder_diary);
        findViewById(R.id.diaryBack).setOnClickListener(v -> finish());
    }

    @Override
    protected void onStart() {
        super.onStart();
        diaryRegistration = repository.listenDiary(this::render);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (diaryRegistration != null) {
            diaryRegistration.remove();
        }
    }

    @SuppressWarnings("unchecked")
    private void render(List<DocumentSnapshot> docs) {
        LinearLayout list = findViewById(R.id.elderDiaryList);
        list.removeAllViews();
        findViewById(R.id.elderDiaryEmpty).setVisibility(docs.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
        float density = getResources().getDisplayMetrics().density;
        boolean indonesian = AppCompatDelegate.getApplicationLocales()
                .toLanguageTags().startsWith("id");

        for (DocumentSnapshot doc : docs) {
            TextView date = new TextView(this);
            date.setTextSize(22);
            date.setTypeface(Typeface.SERIF);
            date.setTextColor(getColor(R.color.ink_black));
            date.setText(doc.getString("date") != null ? doc.getString("date") : "");
            LinearLayout.LayoutParams dateLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            dateLp.topMargin = (int) (20 * density);
            date.setLayoutParams(dateLp);
            list.addView(date);

            TextView summary = new TextView(this);
            summary.setTextSize(17);
            summary.setTextColor(getColor(R.color.ash_gray));
            summary.setText(indonesian ? doc.getString("summaryId") : doc.getString("summaryEn"));
            LinearLayout.LayoutParams sumLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            sumLp.topMargin = (int) (8 * density);
            summary.setLayoutParams(sumLp);
            list.addView(summary);

            List<Map<String, Object>> stories = (List<Map<String, Object>>) doc.get("stories");
            if (stories == null) {
                continue;
            }
            for (Map<String, Object> story : stories) {
                TextView quote = new TextView(this);
                quote.setTextSize(19);
                quote.setTypeface(Typeface.SERIF, Typeface.ITALIC);
                quote.setTextColor(getColor(R.color.ink_black));
                quote.setBackgroundResource(R.drawable.bg_card_selected);
                int pad = (int) (16 * density);
                quote.setPadding(pad, pad, pad, pad);
                quote.setText("“" + story.get("quote") + "”");
                LinearLayout.LayoutParams quoteLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                quoteLp.topMargin = (int) (12 * density);
                quote.setLayoutParams(quoteLp);
                list.addView(quote);
            }
        }
    }
}
