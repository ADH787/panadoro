package com.gritnative.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
import java.time.LocalDate
import java.util.concurrent.TimeUnit

data class LeaderboardTag(
    val id: String,
    val name: String,
    val accent: String = "primary",
    val color: String = "",
    val shape: String = "Cookie7Sided",
    val icon: String = "local_fire_department",
    val updatedAt: Long = 0L,
    val daySeconds: Long = 0L,
    val weekSeconds: Long = 0L,
    val monthSeconds: Long = 0L
) {
    fun seconds(period: LeaderboardPeriod): Long = when (period) {
        LeaderboardPeriod.DAY -> daySeconds
        LeaderboardPeriod.WEEK -> weekSeconds
        LeaderboardPeriod.MONTH -> monthSeconds
    }
}

data class LeaderboardEntry(
    val uid: String,
    val displayName: String,
    val tags: List<LeaderboardTag>,
    val updatedAt: Long = 0L,
    val nameUpdatedAt: Long = 0L,
    val tagsUpdatedAt: Long = 0L,
    val dayKey: String = "",
    val weekKey: String = "",
    val monthKey: String = ""
) {
    fun seconds(period: LeaderboardPeriod, tagId: String? = null): Long =
        tags.filter { tagId == null || it.id == tagId }
            .sumOf { it.seconds(period) }
}

enum class LeaderboardPeriod(val label: String) {
    DAY("Today"),
    WEEK("Week"),
    MONTH("Month")
}

data class LeaderboardState(
    val entries: List<LeaderboardEntry> = emptyList(),
    val loading: Boolean = false,
    val error: String = "",
    val loadedAt: Long = 0L,
    val publishedAt: Long = 0L
)

/**
 * A deliberately small public mirror of the focus history. It never publishes
 * habits, tasks, notes or sessions: exactly the tags the user marked public and
 * their focus seconds for today, this week and this month.
 *
 * Publishing is a read-merge-write with a Firestore updateTime precondition:
 *
 *  - equal periods keep the larger second count, so a device that synced late
 *    cannot lower the board;
 *  - name and public-tag changes carry timestamps, and a device with a stale
 *    preference adopts the newer one instead of overwriting it;
 *  - a conflicting concurrent write re-reads and retries instead of silently
 *    replacing the other device's row.
 */
object LeaderboardService {
    private const val BASE =
        "https://firestore.googleapis.com/v1/projects/${FirebaseConfig.PROJECT_ID}/databases/${FirebaseConfig.DATABASE}/documents/panadora_leaderboard"

    private const val MAX_ATTEMPTS = 4

    private val mediaJson = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .writeTimeout(35, TimeUnit.SECONDS)
        .callTimeout(50, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var autoLoopStarted = false

    private val _state = MutableStateFlow(LeaderboardState())
    val state: StateFlow<LeaderboardState> = _state.asStateFlow()

    private data class RemoteBoard(
        val updateTime: String,
        val displayName: String,
        val nameUpdatedAt: Long,
        val tags: List<LeaderboardTag>,
        val tagsUpdatedAt: Long,
        val dayKey: String,
        val weekKey: String,
        val monthKey: String,
        val updatedAt: Long
    )

    private class HttpFailure(val code: Int, val body: String) : IOException()

    /**
     * Keeps the public mirror warm while the app runs. Store edits also publish
     * through a debounce in GritApp, so this is only a safety net.
     */
    fun startAutoPublish() {
        if (autoLoopStarted) return
        autoLoopStarted = true
        scope.launch {
            delay(30_000)
            while (true) {
                // Publishing every minute would waste the free Firestore write
                // quota. Edits publish through the debounce in GritApp; this
                // loop only keeps a quiet install fresh.
                if (AuthService.isSignedIn() &&
                    StoreRepository.currentSettings.publicTagIds.isNotEmpty()
                ) {
                    publish()
                }
                delay(600_000)
            }
        }
    }

    suspend fun publish(): Boolean = withContext(Dispatchers.IO) {
        if (!AuthService.isSignedIn()) return@withContext false
        if (!AuthService.ensureFreshToken()) return@withContext false
        val uid = AuthService.uid()
        val token = AuthService.idToken()
        if (uid.isBlank() || token.isBlank()) return@withContext false

        repeat(MAX_ATTEMPTS) {
            val remote = try {
                fetchRemote(uid, token)
            } catch (failure: HttpFailure) {
                _state.value = _state.value.copy(error = httpMessage(failure.code, failure.body))
                return@withContext false
            } catch (error: Exception) {
                _state.value = _state.value.copy(error = networkMessage(error))
                return@withContext false
            }

            if (remote != null) {
                val activeIds = StoreRepository.currentStore.activeTags.map { it.id }.toSet()
                val remoteIds = remote.tags.map { it.id }.filter { it in activeIds }.toSet()
                StoreRepository.adoptLeaderboardSettings(
                    displayName = remote.displayName,
                    nameUpdatedAt = remote.nameUpdatedAt,
                    publicTagIds = remoteIds,
                    tagsUpdatedAt = remote.tagsUpdatedAt
                )
            }

            val settings = StoreRepository.currentSettings
            val store = StoreRepository.currentStore
            val localTags = publicTags(store, settings)
            val today = dateKey()
            val weekKey = thisWeekDays(today, settings.weekStartMonday).firstOrNull() ?: today
            val monthKey = LocalDate.now().withDayOfMonth(1).toString().take(7)
            // If the board holds a newer name choice than this device knows
            // about, publish the board's name instead of our own fallback.
            // Otherwise a client that has not adopted the newest name yet
            // could quietly revert the row while carrying its timestamp
            // forward — and the row would look "fresh" to everyone.
            val effectiveName = if (remote != null && remote.nameUpdatedAt > settings.displayNameUpdatedAt) {
                remote.displayName.ifBlank { displayName(settings, AuthService.email()) }
            } else {
                displayName(settings, AuthService.email())
            }

            if (localTags.isEmpty()) {
                if (remote == null) {
                    _state.value = _state.value.copy(
                        publishedAt = System.currentTimeMillis(),
                        error = ""
                    )
                    return@withContext true
                }
                val deleted = try {
                    deleteRemote(uid, token, remote.updateTime)
                } catch (error: Exception) {
                    _state.value = _state.value.copy(error = networkMessage(error))
                    return@withContext false
                }
                when (deleted) {
                    true -> {
                        _state.value = _state.value.copy(
                            publishedAt = System.currentTimeMillis(),
                            error = ""
                        )
                        return@withContext true
                    }
                    null -> Unit // conflict: re-read and decide again
                    false -> {
                        _state.value = _state.value.copy(
                            error = "Could not remove the public leaderboard entry"
                        )
                        return@withContext false
                    }
                }
                return@repeat
            }

            val mergedTags = mergeTags(localTags, remote, today, weekKey, monthKey)
            if (mergedTags.isEmpty()) {
                if (remote == null) return@withContext true
                val deleted = runCatching { deleteRemote(uid, token, remote.updateTime) }.getOrNull()
                if (deleted == true) {
                    _state.value = _state.value.copy(
                        publishedAt = System.currentTimeMillis(),
                        error = ""
                    )
                    return@withContext true
                }
                return@repeat
            }

            val nameUpdatedAt = maxOf(settings.displayNameUpdatedAt, remote?.nameUpdatedAt ?: 0L)
            val tagsUpdatedAt = maxOf(settings.publicTagsUpdatedAt, remote?.tagsUpdatedAt ?: 0L)
            val body = JSONObject()
                .put(
                    "fields",
                    JSONObject()
                        .put("displayName", stringValue(effectiveName))
                        .put("nameUpdatedAt", integerValue(nameUpdatedAt))
                        .put("tagsUpdatedAt", integerValue(tagsUpdatedAt))
                        .put("dayKey", stringValue(today))
                        .put("weekKey", stringValue(weekKey))
                        .put("monthKey", stringValue(monthKey))
                        .put("updatedAt", integerValue(System.currentTimeMillis()))
                        .put("device", stringValue(StoreRepository.deviceId))
                        .put(
                            "tags",
                            JSONObject().put(
                                "mapValue",
                                JSONObject().put("fields", tagsToFields(mergedTags))
                            )
                        )
                )
                .toString()

            val url = buildString {
                append(BASE)
                append("/")
                append(encode(uid))
                append("?key=")
                append(FirebaseConfig.API_KEY)
                append("&updateMask.fieldPaths=displayName")
                append("&updateMask.fieldPaths=nameUpdatedAt")
                append("&updateMask.fieldPaths=tagsUpdatedAt")
                append("&updateMask.fieldPaths=dayKey")
                append("&updateMask.fieldPaths=weekKey")
                append("&updateMask.fieldPaths=monthKey")
                append("&updateMask.fieldPaths=updatedAt")
                append("&updateMask.fieldPaths=device")
                append("&updateMask.fieldPaths=tags")
                if (remote != null && remote.updateTime.isNotBlank()) {
                    append("&currentDocument.updateTime=")
                    append(encode(remote.updateTime))
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

            try {
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        _state.value = _state.value.copy(
                            publishedAt = System.currentTimeMillis(),
                            error = ""
                        )
                        return@withContext true
                    }
                    if (isConflict(response.code, text)) return@repeat
                    _state.value = _state.value.copy(error = httpMessage(response.code, text))
                    return@withContext false
                }
            } catch (error: Exception) {
                _state.value = _state.value.copy(error = networkMessage(error))
                return@withContext false
            }
        }

        _state.value = _state.value.copy(
            error = "The leaderboard kept changing on another device. Try again."
        )
        false
    }

    suspend fun refresh(): Boolean = withContext(Dispatchers.IO) {
        if (!AuthService.isSignedIn()) {
            _state.value = LeaderboardState(error = "Sign in to join the leaderboard")
            return@withContext false
        }
        if (!AuthService.ensureFreshToken()) {
            _state.value = _state.value.copy(
                loading = false,
                error = "Your session is still here; Firebase could not refresh it right now"
            )
            return@withContext false
        }
        val token = AuthService.idToken()
        if (token.isBlank()) {
            _state.value = _state.value.copy(loading = false, error = "Sign in to join the leaderboard")
            return@withContext false
        }
        _state.value = _state.value.copy(loading = true, error = "")
        val entries = ArrayList<LeaderboardEntry>()
        var pageToken = ""
        try {
            do {
                val url = buildString {
                    append(BASE)
                    append("?pageSize=200&key=")
                    append(FirebaseConfig.API_KEY)
                    if (pageToken.isNotBlank()) {
                        append("&pageToken=")
                        append(encode(pageToken))
                    }
                }
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        _state.value = _state.value.copy(
                            loading = false,
                            error = httpMessage(response.code, text)
                        )
                        return@withContext false
                    }
                    val root = JSONObject(text)
                    val documents = root.optJSONArray("documents")
                    if (documents != null) {
                        for (index in 0 until documents.length()) {
                            parseEntry(documents.optJSONObject(index))?.let(entries::add)
                        }
                    }
                    pageToken = root.optString("nextPageToken")
                }
            } while (pageToken.isNotBlank() && entries.size < 1_000)

            val own = entries.firstOrNull { it.uid == AuthService.uid() }
            if (own != null) {
                val activeIds = StoreRepository.currentStore.activeTags.map { it.id }.toSet()
                StoreRepository.adoptLeaderboardSettings(
                    displayName = own.displayName,
                    nameUpdatedAt = own.nameUpdatedAt,
                    publicTagIds = own.tags.map { it.id }.filter { it in activeIds }.toSet(),
                    tagsUpdatedAt = own.tagsUpdatedAt
                )
            }

            _state.value = LeaderboardState(
                entries = entries.sortedByDescending { it.updatedAt },
                loading = false,
                error = "",
                loadedAt = System.currentTimeMillis(),
                publishedAt = _state.value.publishedAt
            )
            true
        } catch (error: Exception) {
            _state.value = _state.value.copy(
                loading = false,
                error = networkMessage(error)
            )
            false
        }
    }

    suspend fun clearRemote(uid: String, token: String) = withContext(Dispatchers.IO) {
        runCatching { deleteRemote(uid, token, "") }
    }

    /** Drops the in-memory board when the account signs out. */
    fun clearLocal() {
        _state.value = LeaderboardState()
    }

    fun displayName(settings: AppSettings = StoreRepository.currentSettings, email: String = AuthService.email()): String {
        val custom = settings.displayName.trim()
        if (custom.isNotBlank()) return custom
        val local = email.substringBefore('@').trim()
        if (local.isBlank()) return "Panadoro user"
        return local.replace('.', ' ').replace('_', ' ')
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
            .take(40)
            .ifBlank { "Panadoro user" }
    }

    private fun publicTags(store: Store, settings: AppSettings): List<LeaderboardTag> {
        val ids = settings.publicTagIds
        if (ids.isEmpty()) return emptyList()
        val todayKey = dateKey()
        val weekDays = thisWeekDays(todayKey, settings.weekStartMonday).toHashSet()
        val monthStart = LocalDate.now().withDayOfMonth(1).toString()
        val sessions = store.activeSessions.filter { it.phase == "focus" }
        return store.activeTags.filter { it.id in ids }.map { tag ->
            var day = 0L
            var week = 0L
            var month = 0L
            for (session in sessions) {
                if (session.tagId != tag.id) continue
                val key = dateKey(session.endedAt)
                val seconds = session.durationSec.coerceAtLeast(0).toLong()
                if (key == todayKey) day += seconds
                if (key in weekDays) week += seconds
                if (key >= monthStart && key <= todayKey) month += seconds
            }
            LeaderboardTag(
                id = tag.id,
                name = tag.name,
                accent = tag.accent,
                color = tag.color,
                shape = tag.shape,
                icon = tag.icon,
                updatedAt = tag.updatedAt,
                daySeconds = day,
                weekSeconds = week,
                monthSeconds = month
            )
        }
    }

    /**
     * Reconcile two devices publishing the same account. A late sync must not
     * lower a period's seconds, and a newer public-tag/name preference wins.
     */
    private fun mergeTags(
        localTags: List<LeaderboardTag>,
        remote: RemoteBoard?,
        dayKey: String,
        weekKey: String,
        monthKey: String
    ): List<LeaderboardTag> {
        if (remote == null) return localTags
        val localById = localTags.associateBy { it.id }
        val remoteById = remote.tags.associateBy { it.id }
        val localAt = StoreRepository.currentSettings.publicTagsUpdatedAt
        val ids = when {
            localAt > remote.tagsUpdatedAt -> localById.keys
            localAt < remote.tagsUpdatedAt -> remoteById.keys
            else -> (localById.keys + remoteById.keys)
        }
        val out = ArrayList<LeaderboardTag>()
        for (id in ids) {
            val local = localById[id]
            val remoteTag = remoteById[id]
            if (local == null && remoteTag == null) continue
            val metadata = when {
                local == null -> remoteTag!!
                remoteTag == null -> local
                remoteTag.updatedAt > local.updatedAt -> remoteTag
                else -> local
            }
            val sameDay = remote.dayKey == dayKey
            val sameWeek = remote.weekKey == weekKey
            val sameMonth = remote.monthKey == monthKey
            out.add(
                metadata.copy(
                    daySeconds = if (local != null) {
                        if (sameDay) maxOf(local.daySeconds, remoteTag?.daySeconds ?: 0L)
                        else local.daySeconds
                    } else if (sameDay) remoteTag?.daySeconds ?: 0L else 0L,
                    weekSeconds = if (local != null) {
                        if (sameWeek) maxOf(local.weekSeconds, remoteTag?.weekSeconds ?: 0L)
                        else local.weekSeconds
                    } else if (sameWeek) remoteTag?.weekSeconds ?: 0L else 0L,
                    monthSeconds = if (local != null) {
                        if (sameMonth) maxOf(local.monthSeconds, remoteTag?.monthSeconds ?: 0L)
                        else local.monthSeconds
                    } else if (sameMonth) remoteTag?.monthSeconds ?: 0L else 0L
                )
            )
        }
        return out
    }

    private fun tagsToFields(tags: List<LeaderboardTag>): JSONObject {
        val fields = JSONObject()
        for (tag in tags) {
            fields.put(
                tag.id,
                JSONObject().put(
                    "mapValue",
                    JSONObject().put(
                        "fields",
                        JSONObject()
                            .put("name", stringValue(tag.name))
                            .put("accent", stringValue(tag.accent))
                            .put("color", stringValue(tag.color))
                            .put("shape", stringValue(tag.shape))
                            .put("icon", stringValue(tag.icon))
                            .put("updatedAt", integerValue(tag.updatedAt))
                            .put("day", integerValue(tag.daySeconds))
                            .put("week", integerValue(tag.weekSeconds))
                            .put("month", integerValue(tag.monthSeconds))
                    )
                )
            )
        }
        return fields
    }

    private fun fetchRemote(uid: String, token: String): RemoteBoard? {
        val request = Request.Builder()
            .url("$BASE/${encode(uid)}?key=${FirebaseConfig.API_KEY}")
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (response.code == 404) return null
            if (!response.isSuccessful) throw HttpFailure(response.code, text)
            val document = JSONObject(text)
            val fields = document.optJSONObject("fields") ?: throw HttpFailure(500, "{}")
            val tags = ArrayList<LeaderboardTag>()
            val tagFields = fields.optJSONObject("tags")
                ?.optJSONObject("mapValue")
                ?.optJSONObject("fields")
            if (tagFields != null) {
                val keys = tagFields.keys()
                while (keys.hasNext()) {
                    val tagId = keys.next()
                    val value = tagFields.optJSONObject(tagId)
                        ?.optJSONObject("mapValue")
                        ?.optJSONObject("fields") ?: continue
                    tags.add(parseTag(tagId, value))
                }
            }
            return RemoteBoard(
                updateTime = document.optString("updateTime"),
                displayName = fields.optJSONObject("displayName")?.optString("stringValue").orEmpty(),
                nameUpdatedAt = longField(fields, "nameUpdatedAt"),
                tags = tags.sortedByDescending { it.daySeconds + it.weekSeconds + it.monthSeconds },
                tagsUpdatedAt = longField(fields, "tagsUpdatedAt"),
                dayKey = fields.optJSONObject("dayKey")?.optString("stringValue").orEmpty(),
                weekKey = fields.optJSONObject("weekKey")?.optString("stringValue").orEmpty(),
                monthKey = fields.optJSONObject("monthKey")?.optString("stringValue").orEmpty(),
                updatedAt = longField(fields, "updatedAt")
            )
        }
    }

    private fun parseTag(id: String, fields: JSONObject): LeaderboardTag =
        LeaderboardTag(
            id = id,
            name = fields.optJSONObject("name")?.optString("stringValue").orEmpty().ifBlank { "Focus" },
            accent = fields.optJSONObject("accent")?.optString("stringValue").orEmpty().ifBlank { "primary" },
            color = fields.optJSONObject("color")?.optString("stringValue").orEmpty(),
            shape = fields.optJSONObject("shape")?.optString("stringValue").orEmpty().ifBlank { "Cookie7Sided" },
            icon = fields.optJSONObject("icon")?.optString("stringValue").orEmpty().ifBlank { "local_fire_department" },
            updatedAt = longField(fields, "updatedAt"),
            daySeconds = longField(fields, "day"),
            weekSeconds = longField(fields, "week"),
            monthSeconds = longField(fields, "month")
        )

    private fun longField(fields: JSONObject, key: String): Long =
        fields.optJSONObject(key)?.optString("integerValue")?.toLongOrNull() ?: 0L

    private fun parseEntry(document: JSONObject?): LeaderboardEntry? {
        if (document == null) return null
        val uid = document.optString("name").substringAfterLast('/')
        if (uid.isBlank()) return null
        val fields = document.optJSONObject("fields") ?: return null
        val tagFields = fields.optJSONObject("tags")
            ?.optJSONObject("mapValue")
            ?.optJSONObject("fields") ?: return null
        val tags = ArrayList<LeaderboardTag>()
        val keys = tagFields.keys()
        while (keys.hasNext()) {
            val tagId = keys.next()
            val value = tagFields.optJSONObject(tagId)
                ?.optJSONObject("mapValue")
                ?.optJSONObject("fields") ?: continue
            tags.add(parseTag(tagId, value))
        }
        if (tags.isEmpty()) return null
        return LeaderboardEntry(
            uid = uid,
            displayName = fields.optJSONObject("displayName")
                ?.optString("stringValue").orEmpty().ifBlank { "Panadoro user" },
            tags = tags,
            updatedAt = longField(fields, "updatedAt"),
            nameUpdatedAt = longField(fields, "nameUpdatedAt"),
            tagsUpdatedAt = longField(fields, "tagsUpdatedAt"),
            dayKey = fields.optJSONObject("dayKey")?.optString("stringValue").orEmpty(),
            weekKey = fields.optJSONObject("weekKey")?.optString("stringValue").orEmpty(),
            monthKey = fields.optJSONObject("monthKey")?.optString("stringValue").orEmpty()
        )
    }

    /**
     * @return true when deleted, null when the updateTime precondition failed,
     *         false for a real error.
     */
    private fun deleteRemote(uid: String, token: String, updateTime: String): Boolean? {
        if (uid.isBlank() || token.isBlank()) return false
        val url = buildString {
            append(BASE)
            append("/")
            append(encode(uid))
            append("?key=")
            append(FirebaseConfig.API_KEY)
            if (updateTime.isNotBlank()) {
                append("&currentDocument.updateTime=")
                append(encode(updateTime))
            }
        }
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $token")
            .delete()
            .build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful || response.code == 404) return true
            val text = response.body?.string().orEmpty()
            if (isConflict(response.code, text)) return null
            _state.value = _state.value.copy(error = httpMessage(response.code, text))
            return false
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

    private fun stringValue(value: String): JSONObject =
        JSONObject().put("stringValue", value.take(80))

    private fun integerValue(value: Long): JSONObject =
        JSONObject().put("integerValue", value.toString())

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun httpMessage(code: Int, body: String): String {
        val detail = try {
            JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
        } catch (_: Exception) {
            ""
        }
        val suffix = if (detail.isBlank()) "" else ": $detail"
        return when (code) {
            401 -> "Session expired. Sign in again$suffix"
            403 -> "The leaderboard rules are not deployed yet$suffix"
            else -> "Could not load the leaderboard (HTTP $code)$suffix"
        }
    }

    private fun networkMessage(error: Exception): String = when (error) {
        is SocketTimeoutException -> "Firebase did not answer in time"
        is UnknownHostException -> "Could not reach Firebase"
        is IOException -> "Could not reach Firebase"
        else -> error.message?.takeIf { it.isNotBlank() } ?: "Leaderboard failed"
    }
}
