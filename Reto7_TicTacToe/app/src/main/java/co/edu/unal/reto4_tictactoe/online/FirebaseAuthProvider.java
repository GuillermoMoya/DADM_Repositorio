package co.edu.unal.reto4_tictactoe.online;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * {@link AuthProvider} backed by Firebase Anonymous Authentication.
 *
 * <p>Provides a stable device {@code uid} via {@link FirebaseAuth#signInAnonymously()} without
 * requiring user profiles (out of scope). This is the only online-layer class that imports the
 * Firebase Auth SDK, keeping the rest of the feature decoupled from Firebase (design
 * "Components and Interfaces" §1).
 *
 * <p>Maps requirements: 2.1 (identified Player) and 22.2 (reads/writes restricted to identified
 * users).
 */
public final class FirebaseAuthProvider implements AuthProvider {

    private final FirebaseAuth auth;

    /**
     * Creates a provider using the default {@link FirebaseAuth} instance.
     */
    public FirebaseAuthProvider() {
        this(FirebaseAuth.getInstance());
    }

    /**
     * Creates a provider over the given {@link FirebaseAuth} instance.
     *
     * @param auth the Firebase authentication entry point to use
     */
    public FirebaseAuthProvider(FirebaseAuth auth) {
        this.auth = auth;
    }

    @Override
    public void ensureSignedIn(Callback<String> onReady, Callback<Throwable> onError) {
        FirebaseUser existing = auth.getCurrentUser();
        if (existing != null) {
            // Already identified: reuse the stable uid without a new sign-in round trip.
            onReady.onResult(existing.getUid());
            return;
        }
        auth.signInAnonymously()
                .addOnSuccessListener(result -> {
                    FirebaseUser user = auth.getCurrentUser();
                    String uid = user != null ? user.getUid() : null;
                    if (uid != null) {
                        onReady.onResult(uid);
                    } else {
                        onError.onResult(new IllegalStateException(
                                "Anonymous sign-in succeeded but no current user/uid is available"));
                    }
                })
                .addOnFailureListener((@NonNull Exception e) -> onError.onResult(e));
    }

    @Override
    public String currentUid() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getUid() : null;
    }
}
