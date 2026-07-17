package com.amigos.kinbridge;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

/**
 * Repository layer for auth + user profiles.
 * Accounts are role-bound (elder / guardian / care): the role is written to
 * users/{uid} at creation, and sign-in verifies it matches the role chosen
 * on the role-select screen. Mismatched logins are signed out and reported.
 * Profile persistence stays non-blocking — auth never fails just because
 * Firestore is unreachable.
 */
public class UserRepository {

    private static final String TAG = "UserRepository";

    public interface Callback {
        void onSuccess(FirebaseUser user);

        void onError(String message);

        /** The account exists but belongs to a different role. */
        default void onRoleMismatch(String actualRole) {
        }
    }

    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public void createAccount(String name, String gender, String email, String password,
                              String role, Callback callback) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    ensureUserDocument(result.getUser(), name, gender, role);
                    callback.onSuccess(result.getUser());
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void signIn(String email, String password, String expectedRole, Callback callback) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> verifyRole(result.getUser(), expectedRole, callback))
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void signOut() {
        auth.signOut();
    }

    /** Role check: missing doc binds the role (first login); mismatched role rejects. */
    private void verifyRole(FirebaseUser user, String expectedRole, Callback callback) {
        DocumentReference ref = db.collection("users").document(user.getUid());
        ref.get().addOnSuccessListener(snapshot -> {
            if (!snapshot.exists()) {
                ensureUserDocument(user, null, null, expectedRole);
                callback.onSuccess(user);
                return;
            }
            String storedRole = snapshot.getString("role");
            if (storedRole == null || storedRole.isEmpty()) {
                // Legacy account without a role: bind it now.
                Map<String, Object> bind = new HashMap<>();
                bind.put("role", expectedRole);
                ref.set(bind, SetOptions.merge());
                callback.onSuccess(user);
            } else if (storedRole.equals(expectedRole)) {
                callback.onSuccess(user);
            } else {
                auth.signOut();
                callback.onRoleMismatch(storedRole);
            }
        }).addOnFailureListener(e -> {
            // Offline: role cannot be verified — allow, but log (same
            // resilience philosophy as profile writes; enforcement applies
            // whenever the backend is reachable).
            Log.w(TAG, "role check unavailable (offline?), allowing sign-in", e);
            callback.onSuccess(user);
        });
    }

    /** Creates the users/{uid} document the first time we see this account. */
    private void ensureUserDocument(FirebaseUser user, String name, String gender, String role) {
        DocumentReference ref = db.collection("users").document(user.getUid());
        ref.get().addOnSuccessListener(snapshot -> {
            if (!snapshot.exists()) {
                ref.set(userData(user, name, gender, role, true))
                        .addOnFailureListener(e -> Log.w(TAG, "user doc write failed", e));
            }
        }).addOnFailureListener(e -> {
            Log.w(TAG, "user doc read failed (offline?), queueing merge write", e);
            ref.set(userData(user, name, gender, role, name != null), SetOptions.merge())
                    .addOnFailureListener(e2 -> Log.w(TAG, "queued user doc write failed", e2));
        });
    }

    private Map<String, Object> userData(FirebaseUser user, String name, String gender,
                                         String role, boolean withCreatedAt) {
        Map<String, Object> data = new HashMap<>();
        data.put("uid", user.getUid());
        data.put("email", user.getEmail());
        if (name != null && !name.isEmpty()) {
            data.put("name", name);
        }
        if (gender != null && !gender.isEmpty()) {
            data.put("gender", gender);
        }
        if (role != null && !role.isEmpty()) {
            data.put("role", role);
        }
        if (withCreatedAt) {
            data.put("createdAt", FieldValue.serverTimestamp());
        }
        return data;
    }
}
