package com.gritnative.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.net.UnknownHostException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Firestore transport for the same document format the quickshell app uses:
 *
 *   collection: panadora
 *   document id: the signed-in user's uid
 *   fields: payload (JSON string), revision (int), updatedAt (int, ms)
 *
 * Every request carries the Firebase ID token, and the security rules require
 * `request.auth.uid == document id`. A sync is a GET, a record-by-record merge,
 * and a PATCH guarded by `currentDocument.updateTime` (or `exists=false` for
 * the first write).
 */
object FirestoreSync {
    private const val BASE =
        "https://firestore.googleapis.com/v1/projects/${FirebaseConfig.PROJECT_ID}/databases/${FirebaseConfig.DATABASE}/documents/${FirebaseConfig.COLLECTION}"

    private val json = GritNetJson
    private val mediaJson = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .writeTimeout(35, TimeUnit.SECONDS)
        .callTimeout(50, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val syncMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var autoLoopStarted = false

    fun configured(): Boolean = AuthService.isSignedIn()

    /**
     * Start the periodic background check. Manual syncs and the change-driven
     * debounce in StoreRepository go through [syncNow] directly.
     */
    fun startAutoSync() {
        if (autoLoopStarted) return
        autoLoopStarted = true
        scope.launch {
            // Let the local store finish loading and the app settle.
            delay(4_000)
            while (true) {
                val settings = StoreRepository.currentSettings
                if (settings.autoSync && AuthService.isSignedIn()) {
                    syncNow(silent = true)
                }
                delay(5_000L)
            }
        }
    }

    /**
     * Fetch, merge, push. Returns true only when Firestore was successfully
     * updated. A concurrent invocation is skipped rather than queued so a
     * background loop can never pile up requests.
     */
    suspend fun syncNow(silent: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (!AuthService.isSignedIn()) {
            if (!silent) {
                StoreRepository.markSyncMessage(SyncStatus.OFF, "Sign in to sync")
            }
            return@withContext false
        }
        if (!syncMutex.tryLock()) {
            if (!silent) StoreRepository.markSyncMessage(SyncStatus.SYNCING, "Sync already running")
            return@withContext false
        }
        try {
            StoreRepository.markSyncMessage(
                SyncStatus.SYNCING,
                if (silent) "Syncing in the background…" else "Contacting Firebase…"
            )
            if (!AuthService.ensureFreshToken()) {
                // A refresh failure is usually just a dead network. The refresh
                // token is still in the keystore, so the session stays signed in.
                StoreRepository.markSyncMessage(
                    SyncStatus.ERROR,
                    "Offline — your session is still signed in"
                )
                return@withContext false
            }
            val uid = AuthService.uid()
            val token = AuthService.idToken()
            if (uid.isBlank() || token.isBlank()) {
                StoreRepository.markSyncMessage(SyncStatus.OFF, "Sign in to sync")
                return@withContext false
            }

            var remote = pull(uid, token)
            var updateTime = remote.updateTime
            var revision = remote.revision
            var remoteStore = remote.store
            remoteStore?.let { StoreRepository.applyRemoteStore(it) }

            for (attempt in 0..2) {
                val local = StoreRepository.snapshotForSync()
                if (remoteStore != null &&
                    contentSignature(local) == contentSignature(remoteStore)
                ) {
                    finishSync(revision)
                    return@withContext true
                }
                val nextRevision = revision + 1
                val payload = json.encodeToString(Store.serializer(), local)
                val result = push(uid, token, updateTime, nextRevision, payload)
                if (result.ok) {
                    finishSync(nextRevision)
                    return@withContext true
                }
                if (!result.conflict) throw SyncException(result.message)
                if (attempt == 2) break
                remote = pull(uid, token)
                updateTime = remote.updateTime
                revision = remote.revision
                remoteStore = remote.store
                remoteStore?.let { StoreRepository.applyRemoteStore(it) }
            }
            throw SyncException("The data kept changing while syncing; try again")
        } catch (error: SyncException) {
            StoreRepository.markSyncMessage(SyncStatus.ERROR, error.message ?: "Sync failed")
            false
        } catch (error: Exception) {
            StoreRepository.markSyncMessage(SyncStatus.ERROR, networkMessage(error))
            false
        } finally {
            syncMutex.unlock()
        }
    }

    private fun finishSync(revision: Int) {
        StoreRepository.saveNow()
        val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        StoreRepository.updateSyncState(
            SyncUiState(
                status = SyncStatus.SYNCED,
                message = "Synced $now",
                lastSyncAt = StoreRepository.syncTimestamp()
            )
        )
        StoreRepository.emitEvent(GritEvent.SyncFinished)
    }

    /**
     * Content-only view of a store. The volatile top-level fields would make two
     * identical stores look different; without this every 5-second poll would
     * write the document again.
     */
    private fun contentSignature(store: Store): String =
        json.encodeToString(
            Store.serializer(),
            store.copy(updatedAt = 0L, device = "")
        )

    // ── HTTP ────────────────────────────────────────────────────────────

    private data class RemoteDocument(
        val store: Store?,
        val revision: Int,
        val updateTime: String
    )

    private data class PushResult(
        val ok: Boolean,
        val conflict: Boolean,
        val message: String
    )

    private fun documentUrl(uid: String): String {
        val encoded = URLEncoder.encode(uid, "UTF-8").replace("+", "%20")
        return "$BASE/$encoded?key=${FirebaseConfig.API_KEY}"
    }

    private fun pull(uid: String, token: String): RemoteDocument {
        val request = Request.Builder()
            .url(documentUrl(uid))
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (response.code == 404) return RemoteDocument(null, 0, "")
            if (!response.isSuccessful) throw SyncException(httpMessage(response.code, body))
            val root = try {
                JSONObject(body)
            } catch (_: Exception) {
                throw SyncException("Firebase returned something that is not JSON")
            }
            val fields = root.optJSONObject("fields")
            val payloadText = fields?.optJSONObject("payload")?.optString("stringValue").orEmpty()
            val revision = fields?.optJSONObject("revision")
                ?.optString("integerValue")
                ?.toIntOrNull() ?: 0
            val store = if (payloadText.isBlank()) {
                null
            } else {
                try {
                    json.decodeFromString(Store.serializer(), payloadText)
                } catch (_: Exception) {
                    throw SyncException("The Firebase document does not contain a valid Panadoro store")
                }
            }
            return RemoteDocument(
                store = store,
                revision = revision,
                updateTime = root.optString("updateTime")
            )
        }
    }

    private fun push(
        uid: String,
        token: String,
        updateTime: String,
        revision: Int,
        payload: String
    ): PushResult {
        val body = JSONObject()
            .put(
                "fields",
                JSONObject()
                    .put("payload", JSONObject().put("stringValue", payload))
                    .put("revision", JSONObject().put("integerValue", revision.toString()))
                    .put(
                        "updatedAt",
                        JSONObject().put("integerValue", System.currentTimeMillis().toString())
                    )
            )
            .toString()

        val url = buildString {
            append(documentUrl(uid))
            append("&updateMask.fieldPaths=payload")
            append("&updateMask.fieldPaths=revision")
            append("&updateMask.fieldPaths=updatedAt")
            if (updateTime.isNotBlank()) {
                append("&currentDocument.updateTime=")
                append(URLEncoder.encode(updateTime, "UTF-8"))
            } else {
                append("&currentDocument.exists=false")
            }
        }
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $token")
            .patch(body.toRequestBody(mediaJson))
            .build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) return PushResult(true, false, "")
            val errorBody = response.body?.string().orEmpty()
            val conflict = isConflict(response.code, errorBody)
            return PushResult(
                ok = false,
                conflict = conflict,
                message = if (conflict) "The data changed on another device while syncing"
                else httpMessage(response.code, errorBody)
            )
        }
    }

    private fun isConflict(code: Int, body: String): Boolean {
        if (code == 409 || code == 412) return true
        if (code != 400) return false
        return try {
            JSONObject(body).optJSONObject("error")?.optString("status") == "FAILED_PRECONDITION"
        } catch (_: Exception) {
            false
        }
    }

    private fun httpMessage(code: Int, body: String): String {
        val detail = try {
            JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
        } catch (_: Exception) {
            ""
        }
        val suffix = if (detail.isBlank()) "" else ": $detail"
        return when (code) {
            401 -> "Session expired. Sign in again$suffix"
            403 -> "Firebase rejected the account$suffix"
            404 -> "No data found for this account yet"
            else -> "Firebase sync failed (HTTP $code)$suffix"
        }
    }

    private fun networkMessage(error: Exception): String = when (error) {
        is SocketTimeoutException -> "Firebase did not answer in time"
        is UnknownHostException -> "Could not reach Firebase"
        is IOException -> "Could not reach Firebase"
        else -> error.message?.takeIf { it.isNotBlank() } ?: "Sync failed"
    }

    private class SyncException(message: String) : Exception(message)
}
