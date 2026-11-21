package com.gritnative.app.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

data class AuthState(
    val email: String = "",
    val uid: String = "",
    val signedIn: Boolean = false,
    val busy: Boolean = false,
    val error: String = ""
)

/**
 * Firebase Authentication over the Identity Toolkit REST API. Email/password
 * accounts are created and signed in directly; the ID token is used as the
 * Firestore bearer token and the refresh token keeps sessions alive.
 *
 * Both tokens are encrypted with the Android keystore. Only the email and uid
 * are stored in plain preferences.
 */
object AuthService {
    private const val PREF_EMAIL = "authEmail"
    private const val PREF_UID = "authUid"
    private const val PREF_EXPIRES = "authExpiresAt"
    private const val PREF_ID_TOKEN = "authIdToken"
    private const val PREF_REFRESH = "authRefreshToken"

    private const val IDENTITY = "https://identitytoolkit.googleapis.com/v1/"
    private const val SECURE_TOKEN = "https://securetoken.googleapis.com/v1/token"

    private val mediaJson = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .writeTimeout(35, TimeUnit.SECONDS)
        .callTimeout(50, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val refreshMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var prefs: SecurePrefs

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = SecurePrefs(context.applicationContext)
        val email = prefs.getString(PREF_EMAIL)
        val uid = prefs.getString(PREF_UID)
        val refresh = prefs.getSecret(PREF_REFRESH)
        _state.value = AuthState(
            email = email,
            uid = uid,
            signedIn = uid.isNotBlank() && refresh.isNotBlank()
        )
    }

    fun isSignedIn(): Boolean = _state.value.signedIn
    fun email(): String = _state.value.email
    fun uid(): String = _state.value.uid
    fun idToken(): String = prefs.getSecret(PREF_ID_TOKEN)

    suspend fun signIn(email: String, password: String): Boolean =
        authenticate("accounts:signInWithPassword", email, password)

    suspend fun createAccount(email: String, password: String): Boolean =
        authenticate("accounts:signUp", email, password)

    /**
     * Add an email/password credential to the signed-in account. This is what
     * lets an account created with another provider gain email sign-in later.
     */
    suspend fun linkEmail(email: String, password: String): Boolean = withContext(Dispatchers.IO) {
        if (!isSignedIn()) {
            setError("Sign in first")
            return@withContext false
        }
        if (!ensureFreshToken()) {
            setError("Please sign in again")
            return@withContext false
        }
        val body = JSONObject()
            .put("email", email.trim())
            .put("password", password)
            .put("returnSecureToken", true)
            .toString()
        val request = Request.Builder()
            .url("$IDENTITY/accounts:update?key=${FirebaseConfig.API_KEY}")
            .post(body.toRequestBody(mediaJson))
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    setError(messageFor(errorCode(text), response.code))
                    return@withContext false
                }
                val json = JSONObject(text)
                val emailOut = json.optString("email", email.trim())
                prefs.putString(PREF_EMAIL, emailOut)
                if (json.has("idToken")) {
                    prefs.putSecret(PREF_ID_TOKEN, json.optString("idToken"))
                    prefs.putString(
                        PREF_EXPIRES,
                        (System.currentTimeMillis() +
                            json.optLong("expiresIn", 3600L) * 1000L).toString()
                    )
                }
                _state.value = _state.value.copy(email = emailOut, error = "")
                true
            }
        } catch (error: Exception) {
            setError(networkMessage(error))
            false
        }
    }

    fun signOut() {
        // Take the public leaderboard entry down with the session. The request
        // uses the token that is still valid at this moment.
        val uid = _state.value.uid
        val token = prefs.getSecret(PREF_ID_TOKEN)
        if (uid.isNotBlank() && token.isNotBlank()) {
            scope.launch { LeaderboardService.clearRemote(uid, token) }
        }
        prefs.putString(PREF_EMAIL, "")
        prefs.putString(PREF_UID, "")
        prefs.putString(PREF_EXPIRES, "0")
        prefs.putSecret(PREF_ID_TOKEN, "")
        prefs.putSecret(PREF_REFRESH, "")
        _state.value = AuthState()
        LeaderboardService.clearLocal()
        StoreRepository.markSyncMessage(SyncStatus.OFF, "Sign in to sync")
    }

    /** Refresh the ID token when it is close to expiring. */
    suspend fun ensureFreshToken(): Boolean = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            if (!_state.value.signedIn) return@withLock false
            val expires = prefs.getString(PREF_EXPIRES).toLongOrNull() ?: 0L
            if (expires > System.currentTimeMillis() + 90_000L) return@withLock true
            refreshLocked()
        }
    }

    suspend fun forceRefresh(): Boolean = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            if (!_state.value.signedIn) false else refreshLocked()
        }
    }

    private fun refreshLocked(): Boolean {
        val refresh = prefs.getSecret(PREF_REFRESH)
        if (refresh.isBlank()) return false
        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refresh)
            .build()
        val request = Request.Builder()
            .url("$SECURE_TOKEN?key=${FirebaseConfig.API_KEY}")
            .post(body)
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val code = errorCode(text)
                    val ended = code.contains("TOKEN_EXPIRED") ||
                        code.contains("INVALID_REFRESH_TOKEN") ||
                        code.contains("INVALID_GRANT") ||
                        code.contains("USER_DISABLED") ||
                        code.contains("USER_NOT_FOUND")
                    if (ended) {
                        // A revoked password or deleted account is the only case
                        // that ends a session. Network trouble never does.
                        signOut()
                        _state.value = _state.value.copy(error = "Your session ended. Sign in again.")
                    }
                    return false
                }
                val json = JSONObject(text)
                val idToken = json.optString("id_token")
                if (idToken.isBlank()) return false
                prefs.putSecret(PREF_ID_TOKEN, idToken)
                json.optString("refresh_token").takeIf { it.isNotBlank() }?.let {
                    prefs.putSecret(PREF_REFRESH, it)
                }
                prefs.putString(
                    PREF_EXPIRES,
                    (System.currentTimeMillis() +
                        json.optLong("expires_in", 3600L) * 1000L).toString()
                )
                json.optString("user_id").takeIf { it.isNotBlank() }?.let {
                    prefs.putString(PREF_UID, it)
                    _state.value = _state.value.copy(uid = it)
                }
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun authenticate(path: String, email: String, password: String): Boolean =
        withContext(Dispatchers.IO) {
            val clean = email.trim()
            if (clean.isBlank() || password.isEmpty()) {
                setError("Enter an email and password")
                return@withContext false
            }
            _state.value = _state.value.copy(busy = true, error = "")
            try {
                val body = JSONObject()
                    .put("email", clean)
                    .put("password", password)
                    .put("returnSecureToken", true)
                    .toString()
                val request = Request.Builder()
                    .url("$IDENTITY$path?key=${FirebaseConfig.API_KEY}")
                    .post(body.toRequestBody(mediaJson))
                    .build()
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        setError(messageFor(errorCode(text), response.code))
                        return@withContext false
                    }
                    val json = JSONObject(text)
                    val uid = json.optString("localId")
                    val refresh = json.optString("refreshToken")
                    val idToken = json.optString("idToken")
                    if (uid.isBlank() || refresh.isBlank() || idToken.isBlank()) {
                        setError("Firebase returned an incomplete session")
                        return@withContext false
                    }
                    prefs.putString(PREF_EMAIL, clean)
                    prefs.putString(PREF_UID, uid)
                    prefs.putSecret(PREF_ID_TOKEN, idToken)
                    prefs.putSecret(PREF_REFRESH, refresh)
                    prefs.putString(
                        PREF_EXPIRES,
                        (System.currentTimeMillis() +
                            json.optLong("expiresIn", 3600L) * 1000L).toString()
                    )
                    _state.value = AuthState(
                        email = clean,
                        uid = uid,
                        signedIn = true,
                        busy = true,
                        error = ""
                    )
                    true
                }
            } catch (error: Exception) {
                setError(networkMessage(error))
                false
            } finally {
                _state.value = _state.value.copy(busy = false)
            }
        }

    fun clearError() {
        if (_state.value.error.isNotBlank()) {
            _state.value = _state.value.copy(error = "")
        }
    }

    private fun setError(message: String) {
        _state.value = _state.value.copy(error = message)
    }

    private fun errorCode(body: String): String = try {
        JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
    } catch (_: Exception) {
        ""
    }

    private fun messageFor(code: String, httpCode: Int): String = when {
        code.startsWith("EMAIL_EXISTS") -> "An account with this email already exists"
        code.startsWith("EMAIL_NOT_FOUND") ||
            code.startsWith("INVALID_LOGIN_CREDENTIALS") ||
            code.startsWith("INVALID_PASSWORD") -> "Wrong email or password"
        code.startsWith("INVALID_EMAIL") -> "That email address is not valid"
        code.startsWith("WEAK_PASSWORD") -> "Use at least 6 characters"
        code.startsWith("MISSING_PASSWORD") -> "Enter a password"
        code.startsWith("TOO_MANY_ATTEMPTS") -> "Too many attempts. Try again later"
        code.startsWith("USER_DISABLED") -> "This account is disabled"
        code.startsWith("OPERATION_NOT_ALLOWED") -> "Email sign-in is not enabled in Firebase"
        code.isNotBlank() -> code
        httpCode == 401 || httpCode == 403 -> "Firebase rejected the sign-in"
        else -> "Sign-in failed (HTTP $httpCode)"
    }

    private fun networkMessage(error: Exception): String = when (error) {
        is SocketTimeoutException -> "Firebase did not answer in time"
        is UnknownHostException -> "Could not reach Firebase"
        is IOException -> "Could not reach Firebase"
        else -> error.message?.takeIf { it.isNotBlank() } ?: "Sign-in failed"
    }
}
