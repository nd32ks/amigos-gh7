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
 * Firebase Auth signs the user in immediately after account creation; the
 * matching users/{uid} Firestore document is created on first login if absent.
 * Profile persistence is deliberately non-blocking: auth never fails just
 * because Firestore is unreachable — writes are queued and sync when the
 * client comes online.
 */
public class UserRepository {

    private static final String TAG = "UserRepository";

    public interface Callback {
        void onSuccess(FirebaseUser user);

        void onError(String message);
    }

    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public void createAccount(String name, String gender, String email, String password, Callback callback) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    ensureUserDocument(result.getUser(), name, gender);
                    callback.onSuccess(result.getUser());
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void signIn(String email, String password, Callback callback) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    ensureUserDocument(result.getUser(), null, null);
                    callback.onSuccess(result.getUser());
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void signOut() {
        auth.signOut();
    }

    /**
     * Best-effort creation of users/{uid}. If the read fails (client offline,
     * database not provisioned yet), a merge write is queued instead — Firestore
     * persists it locally and syncs once the backend is reachable.
     */
    private void ensureUserDocument(FirebaseUser user, String name, String gender) {
        DocumentReference ref = db.collection("users").document(user.getUid());
        ref.get().addOnSuccessListener(snapshot -> {
            if (!snapshot.exists()) {
                ref.set(userData(user, name, gender, true))
                        .addOnFailureListener(e -> Log.w(TAG, "user doc write failed", e));
            }
        }).addOnFailureListener(e -> {
            Log.w(TAG, "user doc read failed (offline?), queueing merge write", e);
            ref.set(userData(user, name, gender, name != null), SetOptions.merge())
                    .addOnFailureListener(e2 -> Log.w(TAG, "queued user doc write failed", e2));
        });
    }

    private Map<String, Object> userData(FirebaseUser user, String name, String gender,
                                         boolean withCreatedAt) {
        Map<String, Object> data = new HashMap<>();
        data.put("uid", user.getUid());
        data.put("email", user.getEmail());
        if (name != null && !name.isEmpty()) {
            data.put("name", name);
        }
        if (gender != null && !gender.isEmpty()) {
            data.put("gender", gender);
        }
        if (withCreatedAt) {
            data.put("createdAt", FieldValue.serverTimestamp());
        }
        return data;
    }
}
