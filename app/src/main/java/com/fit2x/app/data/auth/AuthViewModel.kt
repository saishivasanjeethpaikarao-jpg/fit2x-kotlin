package com.fit2x.app.data.auth

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Auth screen state. MainActivity gates the whole app on this:
 * [SignedOut]/[SigningIn] -> LoginScreen, [SignedIn]/[Offline] -> main app,
 * [Checking] -> splash while we read the persisted session.
 */
sealed interface AuthState {
    data object Checking : AuthState
    data object SignedOut : AuthState
    data object SigningIn : AuthState
    data class SignedIn(val user: Fit2xUser) : AuthState
    data object Offline : AuthState
}

class AuthViewModel(private val repository: AuthRepository) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Checking)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    /** One-shot error messages for the login snackbar. */
    private val _errors = MutableSharedFlow<String>()
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    init {
        viewModelScope.launch {
            repository.observeAuthState().collect { user ->
                _authState.value = when {
                    user != null -> AuthState.SignedIn(user)
                    repository.isOfflineMode() -> AuthState.Offline
                    else -> AuthState.SignedOut
                }
            }
        }
    }

    /** Starts native Google Sign-In. No-op while a sign-in is already running. */
    fun signIn(activity: Activity) {
        if (_authState.value is AuthState.SigningIn) return
        _authState.value = AuthState.SigningIn
        viewModelScope.launch {
            repository.signInWithGoogle(activity).collect { result ->
                when (result) {
                    is AuthResult.Success ->
                        _authState.value = AuthState.SignedIn(result.user)
                    AuthResult.Cancelled ->
                        // User dismissed the picker — back to signed-out, silently.
                        _authState.value = AuthState.SignedOut
                    is AuthResult.Error -> {
                        _authState.value = AuthState.SignedOut
                        _errors.emit(result.message)
                    }
                }
            }
        }
    }

    fun continueOffline() {
        viewModelScope.launch {
            repository.setOfflineMode(true)
            _authState.value = AuthState.Offline
        }
    }

    fun signOut() {
        viewModelScope.launch {
            repository.signOut()
            // The auth-state listener flips us to SignedOut automatically.
        }
    }
}

class AuthViewModelFactory(private val repository: AuthRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
            return AuthViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
