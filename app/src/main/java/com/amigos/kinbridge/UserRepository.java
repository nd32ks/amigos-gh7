package com.amigos.kinbridge;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * Repository layer for auth + user profiles.
 * Firebase Auth signs the user in immediately after account creation; the
 * matching users/{uid} Firestore document is created on first login if absent.
 */
public class UserRepository {

    public interface Callback {
        void onSuccess(FirebaseUser user);

        void onError(String message);
    }

    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public void createAccount(String name, String email, String password, Callback callback) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> ensureUserDocument(result.getUser(), name, callback))
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void signIn(String email, String password, Callback callback) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> ensureUserDocument(result.getUser(), null, callback))
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void signOut() {
        auth.signOut();
    }

    /** Creates the users/{uid} document the first time we see this account. */
    private void ensureUserDocument(FirebaseUser user, String name, Callback callback) {
        DocumentReference ref = db.collection("users").document(user.getUid());
        ref.get().addOnSuccessListener(snapshot -> {
            if (snapshot.exists()) {
                callback.onSuccess(user);
                return;
            }
            Map<String, Object> data = new HashMap<>();
            data.put("uid", user.getUid());
            data.put("email", user.getEmail());
            if (name != null && !name.isEmpty()) {
                data.put("name", name);
            }
            data.put("createdAt", FieldValue.serverTimestamp());
            ref.set(data)
                    .addOnSuccessListener(unused -> callback.onSuccess(user))
                    .addOnFailureListener(e -> callback.onError(e.getMessage()));
        }).addOnFailureListener(e -> callback.onError(e.getMessage()));
    }
}
