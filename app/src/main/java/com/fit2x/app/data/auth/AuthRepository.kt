package com.fit2x.app.data.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.fit2x.app.data.local.UserProfileDao
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await

/**
 * Signed-in (or offline) user identity used across the app.
 */
data class Fit2xUser(
    val uid: String,
    val name: String,
    val email: String,
    val photoUrl: String?
)

/** Result of one Google sign-in attempt. */
sealed interface AuthResult {
    data class Success(val user: Fit2xUser) : AuthResult
    data class Error(val message: String) : AuthResult
    data object Cancelled : AuthResult
}

/**
 * Google Sign-In via the classic play-services-auth API (stable).
 * Links the Google ID token to Firebase Auth, then persists the user to
 * Firestore (`users/{uid}/fit2x/state`) and the local Room [UserProfile].
 *
 * Everything remote is best-effort: sign-in succeeds as long as Firebase
 * Auth succeeds. Firestore/Room writes never fail the sign-in.
 */
class AuthRepository(
    private val appContext: Context,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val profileDao: UserProfileDao
) {
    private val prefs by lazy {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun googleSignInClient(): GoogleSignInClient? {
        val serverClientId = runCatching {
            val resId = appContext.resources.getIdentifier(
                "default_web_client_id", "string", appContext.packageName
            )
            if (resId != 0) appContext.getString(resId) else null
        }.getOrNull()
        if (serverClientId.isNullOrBlank()) return null

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(serverClientId)
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(appContext, gso)
    }

    /** Returns the sign-in Intent to launch via ActivityResultLauncher. Null if not configured. */
    fun getSignInIntent(): Intent? {
        return googleSignInClient()?.signInIntent
    }

    /** Call this from the ActivityResult callback with the returned Intent. */
    fun handleSignInResult(data: Intent?): Flow<AuthResult> = flow {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        val account: GoogleSignInAccount = try {
            task.getResult(ApiException::class.java)
        } catch (e: ApiException) {
            if (e.statusCode == 12501) { // SIGN_IN_CANCELLED
                emit(AuthResult.Cancelled)
            } else {
                emit(AuthResult.Error("Google sign-in failed: ${e.message ?: "error ${e.statusCode}"}"))
            }
            return@flow
        }

        val idToken = account.idToken
        if (idToken.isNullOrBlank()) {
            emit(AuthResult.Error("Couldn't get a sign-in token from Google. Please try again."))
            return@flow
        }

        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
        val authResult = try {
            auth.signInWithCredential(firebaseCredential).await()
        } catch (e: FirebaseNetworkException) {
            emit(AuthResult.Error("No internet connection. Check your network and try again."))
            return@flow
        } catch (e: FirebaseAuthException) {
            emit(AuthResult.Error("Sign-in failed: ${e.message ?: "unknown error"}"))
            return@flow
        } catch (e: Exception) {
            emit(AuthResult.Error("Sign-in failed: ${e.message ?: "unknown error"}"))
            return@flow
        }

        val fbUser = authResult.user
            ?: run {
                emit(AuthResult.Error("Sign-in didn't return a user. Please try again."))
                return@flow
            }

        val user = Fit2xUser(
            uid = fbUser.uid,
            name = fbUser.displayName.orEmpty(),
            email = fbUser.email.orEmpty(),
            photoUrl = fbUser.photoUrl?.toString()
        )
        persistUser(user)
        setOfflineMode(false)
        emit(AuthResult.Success(user))
    }.flowOn(Dispatchers.IO)

    /** Firebase auth-state as a cold flow of [Fit2xUser]. */
    fun observeAuthState(): Flow<Fit2xUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val fbUser = firebaseAuth.currentUser
            trySend(
                fbUser?.let {
                    Fit2xUser(
                        uid = it.uid,
                        name = it.displayName.orEmpty(),
                        email = it.email.orEmpty(),
                        photoUrl = it.photoUrl?.toString()
                    )
                }
            )
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    /** Full sign-out: Firebase, Google, offline flag, local identity. */
    suspend fun signOut() {
        auth.signOut()
        try {
            googleSignInClient()?.signOut()?.await()
        } catch (_: Exception) {
            // Best-effort
        }
        setOfflineMode(false)
        val current = profileDao.observeProfile().firstOrNull()
        if (current != null) {
            profileDao.upsert(current.copy(name = "", email = "", photoUrl = null))
        }
    }

    fun isOfflineMode(): Boolean = prefs.getBoolean(KEY_OFFLINE_MODE, false)

    fun setOfflineMode(offline: Boolean) {
        prefs.edit().putBoolean(KEY_OFFLINE_MODE, offline).apply()
    }

    fun isGoogleSignInConfigured(): Boolean = googleSignInClient() != null

    /**
     * Best-effort persistence. Firestore write merges into
     * `users/{uid}/fit2x/state` (same path the web app uses); the Room
     * write preserves the user's existing local prefs (theme, goals).
     * Failures are swallowed — the user is already signed in.
     */
    private suspend fun persistUser(user: Fit2xUser) {
        try {
            firestore.collection("users")
                .document(user.uid)
                .collection("fit2x")
                .document("state")
                .set(
                    mapOf(
                        "name" to user.name,
                        "email" to user.email,
                        "photoUrl" to user.photoUrl,
                        "lastLoginAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                .await()
        } catch (_: Exception) {
            // Offline-first: a failed Firestore write must not fail sign-in.
        }
        try {
            val existing = profileDao.observeProfile().firstOrNull()
            if (existing != null) {
                profileDao.upsert(
                    existing.copy(name = user.name, email = user.email, photoUrl = user.photoUrl)
                )
            } else {
                profileDao.upsert(
                    com.fit2x.app.data.local.UserProfile(
                        name = user.name,
                        email = user.email,
                        photoUrl = user.photoUrl
                    )
                )
            }
        } catch (_: Exception) {
            // Same: local write failure must not fail sign-in.
        }
    }

    private companion object {
        const val PREFS_NAME = "fit2x_auth"
        const val KEY_OFFLINE_MODE = "offline_mode"
    }
}
