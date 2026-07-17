package com.amigos.kinbridge;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Elder home — the post-login surface for the elder role: welcome greeting
 * with honorific + name, elder feature cards (Kenang, Diary), and an account
 * sheet (info, text size, reset settings, logout). Not a cross-role menu —
 * it is the elder's own surface.
 */
public class ElderHomeActivity extends AppCompatActivity {

    private final UserRepository userRepository = new UserRepository();
    private final ElderRepository elderRepository = new ElderRepository();

    private String displayName;
    private String gender;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(FontScale.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_elder_home);

        findViewById(R.id.featureKenang).setOnClickListener(v ->
                startActivity(new Intent(this, CompanionActivity.class)));
        findViewById(R.id.featureDiary).setOnClickListener(v ->
                startActivity(new Intent(this, ElderDiaryActivity.class)));
        findViewById(R.id.featureQuestions).setOnClickListener(v ->
                startActivity(new Intent(this, GuidedQuestionActivity.class)));
        findViewById(R.id.accountButton).setOnClickListener(v -> showAccountSheet());

        loadGreeting();
    }

    private void loadGreeting() {
        userRepository.getUserProfile((name, userGender) -> {
            displayName = name;
            gender = userGender;
            if (displayName == null || displayName.isEmpty()) {
                // Fallback: the elder profile's preferred address (Ibu Sri)
                elderRepository.loadProfile(this, new ElderRepository.ProfileCallback() {
                    @Override
                    public void onLoaded(ElderProfile profile) {
                        displayName = profile.preferredAddress;
                        renderGreeting();
                    }

                    @Override
                    public void onError(String message) {
                        renderGreeting();
                    }
                });
            } else {
                renderGreeting();
            }
        });
    }

    private void renderGreeting() {
        String base = displayName != null && !displayName.isEmpty() ? displayName : "";
        String withHonorific;
        if ("female".equals(gender)) {
            withHonorific = getString(R.string.honorific_mrs, base);
        } else if ("male".equals(gender)) {
            withHonorific = getString(R.string.honorific_mr, base);
        } else {
            withHonorific = base;
        }
        ((TextView) findViewById(R.id.elderGreeting))
                .setText(getString(R.string.welcome_elder, withHonorific));
    }

    private void showAccountSheet() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_account);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        ((TextView) dialog.findViewById(R.id.accountName))
                .setText(displayName != null ? displayName : "");
        String email = com.google.firebase.auth.FirebaseAuth.getInstance()
                .getCurrentUser() != null
                ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getEmail() : "";
        String role = getSharedPreferences(OnboardingActivity.PREFS, MODE_PRIVATE)
                .getString(OnboardingActivity.KEY_ROLE, OnboardingActivity.ROLE_SENIOR);
        String genderLabel = gender != null ? " · " + gender : "";
        ((TextView) dialog.findViewById(R.id.accountDetails)).setText(
                email + " · " + OnboardingActivity.roleDisplayName(this, role) + genderLabel);

        SeekBar slider = dialog.findViewById(R.id.accountFontSlider);
        slider.setMax(FontScale.stepCount() - 1);
        slider.setProgress(getSharedPreferences(OnboardingActivity.PREFS, MODE_PRIVATE)
                .getInt(FontScale.KEY_FONT_STEP, 0));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                getSharedPreferences(OnboardingActivity.PREFS, MODE_PRIVATE)
                        .edit().putInt(FontScale.KEY_FONT_STEP, seekBar.getProgress()).apply();
                dialog.dismiss();
                recreateWithFade();
            }
        });

        dialog.findViewById(R.id.resetSettings).setOnClickListener(v -> {
            FontScale.reset(this);
            slider.setProgress(0);
            Toast.makeText(this, R.string.settings_reset_done, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            recreateWithFade();
        });

        Button logout = dialog.findViewById(R.id.accountLogout);
        logout.setOnClickListener(v -> {
            userRepository.signOut();
            FontScale.reset(this);
            dialog.dismiss();
            Intent intent = new Intent(this, OnboardingActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });

        dialog.show();
    }

    private void recreateWithFade() {
        findViewById(android.R.id.content).animate().alpha(0f).setDuration(150)
                .withEndAction(() -> {
                    recreate();
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                }).start();
    }
}
