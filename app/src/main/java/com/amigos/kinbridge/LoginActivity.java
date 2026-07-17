package com.amigos.kinbridge;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {

    private final UserRepository userRepository = new UserRepository();

    private EditText emailInput;
    private EditText passwordInput;
    private TextView emailError;
    private TextView passwordError;
    private Button signInButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        LanguageToggle.bind(this);

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
        createAccountLink.setOnClickListener(v ->
                startActivity(new Intent(this, CreateAccountActivity.class)));
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
        userRepository.signIn(email, password, new UserRepository.Callback() {
            @Override
            public void onSuccess(com.google.firebase.auth.FirebaseUser user) {
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
