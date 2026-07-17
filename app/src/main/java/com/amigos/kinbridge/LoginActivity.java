package com.amigos.kinbridge;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {

    private final UserRepository userRepository = new UserRepository();

    /** Keeps the font panel open across the recreate that applies a new scale. */
    private static boolean fontPanelOpen;

    private String role;
    private EditText emailInput;
    private EditText passwordInput;
    private TextView emailError;
    private TextView passwordError;
    private Button signInButton;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(FontScale.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        role = getIntent().getStringExtra(OnboardingActivity.ROLE_EXTRA);
        if (role == null) {
            role = OnboardingActivity.ROLE_SENIOR;
        }
        ((TextView) findViewById(R.id.loginSubtitle)).setText(
                getString(R.string.login_as_role, OnboardingActivity.roleDisplayName(this, role)));

        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        emailError = findViewById(R.id.emailError);
        passwordError = findViewById(R.id.passwordError);

        signInButton = findViewById(R.id.signInButton);
        signInButton.setOnClickListener(v -> attemptLogin());

        TextView forgotPassword = findViewById(R.id.forgotPassword);
        forgotPassword.setOnClickListener(v ->
                Toast.makeText(this, R.string.reset_unavailable, Toast.LENGTH_SHORT).show());

        TextView createAccountLink = findViewById(R.id.createAccountLink);
        createAccountLink.setOnClickListener(v -> {
            Intent intent = new Intent(this, CreateAccountActivity.class);
            intent.putExtra(OnboardingActivity.ROLE_EXTRA, role);
            startActivity(intent);
        });

        setupFontControl();
    }

    /** Accessibility font-size control, always reachable via the "Aa" pill. */
    private void setupFontControl() {
        View fontPanel = findViewById(R.id.fontPanel);
        SeekBar slider = findViewById(R.id.loginFontSlider);

        if (fontPanelOpen) {
            fontPanel.setVisibility(View.VISIBLE);
        }
        findViewById(R.id.fontButton).setOnClickListener(v -> {
            fontPanelOpen = fontPanel.getVisibility() != View.VISIBLE;
            fontPanel.setVisibility(fontPanelOpen ? View.VISIBLE : View.GONE);
        });

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
                // Recreate so attachBaseContext wraps the new fontScale; fade the swap.
                findViewById(android.R.id.content).animate().alpha(0f).setDuration(150)
                        .withEndAction(() -> {
                            recreate();
                            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                        }).start();
            }
        });
    }

    private void attemptLogin() {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();

        boolean emailValid = Patterns.EMAIL_ADDRESS.matcher(email).matches();
        boolean passwordValid = password.length() >= 8;

        setFieldError(emailInput, emailError, !emailValid);
        setFieldError(passwordInput, passwordError, !passwordValid);

        if (!emailValid || !passwordValid) {
            return;
        }

        setLoading(true);
        userRepository.signIn(email, password, role, new UserRepository.Callback() {
            @Override
            public void onSuccess(com.google.firebase.auth.FirebaseUser user) {
                getSharedPreferences(OnboardingActivity.PREFS, MODE_PRIVATE)
                        .edit().putString(OnboardingActivity.KEY_ROLE, role).apply();
                Intent intent = new Intent(LoginActivity.this, SuccessActivity.class);
                intent.putExtra(SuccessActivity.EXTRA_EMAIL, email);
                startActivity(intent);
                finish();
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                Toast.makeText(LoginActivity.this, message, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onRoleMismatch(String actualRole) {
                setLoading(false);
                Toast.makeText(LoginActivity.this,
                        getString(R.string.error_role_mismatch,
                                OnboardingActivity.roleDisplayName(LoginActivity.this, actualRole)),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setLoading(boolean loading) {
        signInButton.setEnabled(!loading);
        signInButton.setText(loading ? R.string.signing_in : R.string.sign_in);
    }

    private void setFieldError(EditText field, TextView error, boolean hasError) {
        error.setVisibility(hasError ? View.VISIBLE : View.GONE);
        field.setBackgroundResource(hasError ? R.drawable.bg_input_error : R.drawable.bg_input);
    }
}
