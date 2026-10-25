package com.luc4n3x.levyra.data.yandex

import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

internal class YandexMusicClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .dns(YandexResilientDns)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    suspend fun startDeviceAuth(): YandexDeviceCode = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("device_id", UUID.randomUUID().toString().replace("-", ""))
            .add("device_name", "Levyra")
            .build()

        val urls = listOf(
            "https://oauth.yandex.ru/device/code",
            "https://oauth.yandex.com/device/code"
        )

        var lastException: Exception? = null
        for (url in urls) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .build()

                return@withContext httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Yandex device code error: ${response.code}")
                    val json = JSONObject(response.body?.string().orEmpty())
                    YandexDeviceCode(
                        deviceCode = json.getString("device_code"),
                        userCode = json.getString("user_code"),
                        verificationUrl = json.optString("verification_url", "https://ya.ru/device"),
                        expiresIn = json.optInt("expires_in", 300),
                        interval = json.optInt("interval", 5)
                    )
                }
            } catch (e: Exception) {
                lastException = e
            }
        }
        throw lastException ?: IOException("Failed to reach Yandex OAuth servers")
    }

    suspend fun pollDeviceAuth(deviceCode: String): String? = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("grant_type", "device_code")
            .add("code", deviceCode)
            .add("client_id", CLIENT_ID)
            .add("client_secret", CLIENT_SECRET)
            .build()

        val urls = listOf(
            "https://oauth.yandex.ru/token",
            "https://oauth.yandex.com/token"
        )

        var lastException: Exception? = null
        for (url in urls) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .build()

                return@withContext httpClient.newCall(request).execute().use { response ->
                    val json = JSONObject(response.body?.string().orEmpty())
                    if (response.isSuccessful) {
                        return@use json.optString("access_token").takeIf { it.isNotBlank() }
                    }
                    val error = json.optString("error")
                    if (error == "authorization_pending") return@use null
                    if (error == "slow_down") return@use null
                    throw IOException("Yandex auth error: $error (${json.optString("error_description")})")
                }
            } catch (e: Exception) {
                if (e.message?.contains("authorization_pending") == true || e.message?.contains("slow_down") == true) {
                    return@withContext null
                }
                lastException = e
            }
        }
        throw lastException ?: IOException("Failed to poll Yandex OAuth")
    }

    suspend fun fetchAccount(token: String): YandexAccount = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.music.yandex.net/account/status")
            .header("Authorization", "OAuth $token")
            .header("User-Agent", "Yandex-Music-API")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Failed to fetch Yandex account: ${response.code}")
            val json = JSONObject(response.body?.string().orEmpty())
            val result = json.optJSONObject("result") ?: JSONObject()
            val accountObj = result.optJSONObject("account") ?: JSONObject()
            val plusObj = result.optJSONObject("plus") ?: JSONObject()
            val uid = accountObj.optString("uid")
            val login = accountObj.optString("login")
            val displayName = accountObj.optString("displayName").ifBlank {
                "${accountObj.optString("firstName")} ${accountObj.optString("secondName")}".trim()
            }.ifBlank { login }

            val avatarUri = accountObj.optString("avatarUrl")
            val avatarUrl = if (avatarUri.isNotBlank()) {
                if (avatarUri.startsWith("http")) avatarUri else "https://$avatarUri"
            } else ""

            YandexAccount(
                uid = uid,
                login = login,
                displayName = displayName,
                avatarUrl = avatarUrl,
                hasPlus = plusObj.optBoolean("hasPlus", false)
            )
        }
    }

    suspend fun search(query: String, token: String? = null): List<YandexTrack> = withContext(Dispatchers.IO) {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val builder = Request.Builder()
            .url("https://api.music.yandex.net/search?type=track&nocorrect=false&text=$encodedQuery&page=0")
            .header("User-Agent", "Yandex-Music-API")
            .get()

        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "OAuth $token")
        }

        httpClient.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) return@use emptyList()
            val json = JSONObject(response.body?.string().orEmpty())
            val tracksObj = json.optJSONObject("result")?.optJSONObject("tracks") ?: return@use emptyList()
            val results = tracksObj.optJSONArray("results") ?: return@use emptyList()
            val list = mutableListOf<YandexTrack>()
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                parseTrackItem(item)?.let { list.add(it) }
            }
            list
        }
    }

    /**
     * The user's liked tracks, fully hydrated. `/users/{uid}/likes/tracks` returns ids only, so
     * the ids are batched through `/tracks` for metadata. Order is preserved. A blank [token] or
     * [uid] yields an empty list rather than an error, so a signed-out state degrades quietly.
     */
    suspend fun likedTracks(uid: String, token: String?): List<YandexTrack> = withContext(Dispatchers.IO) {
        if (uid.isBlank() || token.isNullOrBlank()) return@withContext emptyList()
        val ids = authedGet("https://api.music.yandex.net/users/${enc(uid)}/likes/tracks", token)
            ?.let(::parseLikedTrackIds).orEmpty()
        if (ids.isEmpty()) return@withContext emptyList()
        tracksByIds(ids, token)
    }

    /** Batch-fetch full track metadata for [ids] via POST `/tracks`, chunked and fetched in parallel. */
    suspend fun tracksByIds(ids: List<String>, token: String?): List<YandexTrack> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyList()
        val chunkResults = coroutineScope {
            ids.chunked(TRACK_BATCH_SIZE).map { chunk ->
                async {
                    val body = FormBody.Builder().add("track-ids", chunk.joinToString(",")).build()
                    val builder = Request.Builder()
                        .url("https://api.music.yandex.net/tracks")
                        .header("User-Agent", "Yandex-Music-API")
                        .post(body)
                    if (!token.isNullOrBlank()) builder.header("Authorization", "OAuth $token")
                    httpClient.newCall(builder.build()).execute().use { response ->
                        if (!response.isSuccessful) emptyList() else parseTrackArray(response.body?.string().orEmpty())
                    }
                }
            }.awaitAll()
        }
        val byId = HashMap<String, YandexTrack>()
        chunkResults.forEach { chunk -> chunk.forEach { byId[it.id] = it } }
        // Preserve the requested order; drop ids the server did not return (unavailable tracks).
        ids.mapNotNull { byId[it] }
    }

    /** The user's own and saved playlists (headers only; tracks are fetched by [playlistTracks]). */
    suspend fun userPlaylists(uid: String, token: String?): List<YandexPlaylist> = withContext(Dispatchers.IO) {
        if (uid.isBlank() || token.isNullOrBlank()) return@withContext emptyList()
        authedGet("https://api.music.yandex.net/users/${enc(uid)}/playlists/list", token)
            ?.let(::parsePlaylists).orEmpty()
    }

    /** Tracks of one playlist. Uses the embedded track objects when present, batching any id-only entries. */
    suspend fun playlistTracks(uid: String, kind: String, token: String?): List<YandexTrack> = withContext(Dispatchers.IO) {
        if (uid.isBlank() || kind.isBlank() || token.isNullOrBlank()) return@withContext emptyList()
        val body = authedGet("https://api.music.yandex.net/users/${enc(uid)}/playlists/${enc(kind)}", token)
            ?: return@withContext emptyList()
        val parsed = parsePlaylistTracks(body)
        if (parsed.missingIds.isEmpty()) return@withContext parsed.tracks
        // Some entries carried only an id; hydrate them and splice back in original position.
        val hydrated = tracksByIds(parsed.missingIds, token).associateBy { it.id }
        parsed.ordered.mapNotNull { entry ->
            when (entry) {
                is PlaylistEntry.Full -> entry.track
                is PlaylistEntry.IdOnly -> hydrated[entry.id]
            }
        }
    }

    /**
     * A batch of tracks from a rotor station (e.g. "user:onyourwave" — «Моя волна»). [queue] is the
     * id of the last track already played; passing it asks rotor for the continuation, which backs
     * the endless autoplay wave. Returns empty when signed out.
     */
    suspend fun rotorStationTracks(
        stationId: String,
        token: String?,
        queue: String? = null
    ): List<YandexTrack> = withContext(Dispatchers.IO) {
        if (token.isNullOrBlank()) return@withContext emptyList()
        val base = "https://api.music.yandex.net/rotor/station/${enc(stationId)}/tracks?settings2=true"
        val url = if (!queue.isNullOrBlank()) "$base&queue=${enc(queue)}" else base
        authedGet(url, token)?.let(::parseRotorSequence).orEmpty()
    }

    /** The personalized home feed (`/landing3`). Returns an empty landing when signed out. */
    suspend fun landing(token: String?): YandexLanding = withContext(Dispatchers.IO) {
        if (token.isNullOrBlank()) return@withContext YandexLanding()
        val url = "https://api.music.yandex.net/landing3?blocks=personalplaylists,chart,new-releases,new-playlists"
        authedGet(url, token)?.let(::parseLanding) ?: YandexLanding()
    }

    private fun authedGet(url: String, token: String?): String? {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", "Yandex-Music-API")
            .get()
        if (!token.isNullOrBlank()) builder.header("Authorization", "OAuth $token")
        return httpClient.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) null else response.body?.string()
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    suspend fun resolveStreamUrl(trackId: String, token: String? = null): String? = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url("https://api.music.yandex.net/tracks/${URLEncoder.encode(trackId, "UTF-8")}/download-info")
            .header("User-Agent", "Yandex-Music-API")
            .get()

        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "OAuth $token")
        }

        val downloadInfoUrl = httpClient.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val json = JSONObject(response.body?.string().orEmpty())
            val results = json.optJSONArray("result") ?: return@use null
            var bestUrl: String? = null
            var maxBitrate = -1
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                val codec = item.optString("codec")
                val bitrate = item.optInt("bitrateInKbps", 0)
                val url = item.optString("downloadInfoUrl")
                val isDirect = item.optBoolean("direct", false)
                val isPreview = item.optBoolean("preview", false)

                if (codec.equals("mp3", ignoreCase = true) && !isPreview) {
                    if (isDirect && url.isNotBlank()) return@use url
                    if (bitrate > maxBitrate) {
                        maxBitrate = bitrate
                        bestUrl = url
                    }
                }
            }
            bestUrl
        } ?: return@withContext null

        val xmlRequest = Request.Builder().url(downloadInfoUrl).get().build()
        httpClient.newCall(xmlRequest).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val xml = response.body?.string().orEmpty()
            val host = extractXmlTag(xml, "host") ?: return@use null
            val path = extractXmlTag(xml, "path") ?: return@use null
            val ts = extractXmlTag(xml, "ts") ?: return@use null
            val salt = extractXmlTag(xml, "s") ?: return@use null

            val signTarget = SIGN_SECRET + path.removePrefix("/") + salt
            val md5Digest = MessageDigest.getInstance("MD5").digest(signTarget.toByteArray(Charsets.UTF_8))
            val signature = md5Digest.joinToString("") { "%02x".format(it) }

            "https://$host/get-mp3/$signature/$ts$path?track-id=$trackId&play=false"
        }
    }

    private fun extractXmlTag(xml: String, tag: String): String? {
        val matcher = Pattern.compile("<$tag>(.*?)</$tag>").matcher(xml)
        return if (matcher.find()) matcher.group(1) else null
    }

    companion object {
        const val CLIENT_ID = "23cabbbdc6cd418abb4b39c32c41195d"
        const val CLIENT_SECRET = "53bc75238f0c4d08a118e51fe9203300"
        const val SIGN_SECRET = "XGRlBW9FXlekgbPrRHuSiA"
        private const val TRACK_BATCH_SIZE = 150
    }
}

/**
 * Map one Yandex track JSON object (shared by search `result.tracks.results[]`, batch `/tracks`
 * `result[]`, and playlist `result.tracks[].track`) to a [YandexTrack]. Returns null when the
 * object has no usable id.
 */
internal fun parseTrackItem(item: JSONObject): YandexTrack? {
    val id = item.optString("id").ifBlank { item.optString("realId") }
    if (id.isBlank()) return null
    val artistsArr = item.optJSONArray("artists") ?: JSONArray()
    val artists = buildList {
        for (a in 0 until artistsArr.length()) {
            artistsArr.optJSONObject(a)?.optString("name")?.takeIf { it.isNotBlank() }?.let { add(it) }
        }
    }
    val artist = if (artists.isNotEmpty()) artists.joinToString(", ") else "Неизвестный исполнитель"
    val albumsArr = item.optJSONArray("albums") ?: JSONArray()
    val album = albumsArr.optJSONObject(0)?.optString("title")?.takeIf { it.isNotBlank() } ?: "Без альбома"
    val coverUri = item.optString("coverUri").ifBlank {
        albumsArr.optJSONObject(0)?.optString("coverUri").orEmpty()
    }
    val coverUrl = if (coverUri.isNotBlank()) "https://${coverUri.replace("%%", "400x400")}" else ""
    return YandexTrack(
        id = id,
        title = item.optString("title"),
        artist = artist,
        album = album,
        durationMs = item.optLong("durationMs", 0L),
        coverUrl = coverUrl,
        available = item.optBoolean("available", true)
    )
}

/** Parse the id-only track list from `/users/{uid}/likes/tracks`. */
internal fun parseLikedTrackIds(body: String): List<String> {
    val tracks = JSONObject(body)
        .optJSONObject("result")
        ?.optJSONObject("library")
        ?.optJSONArray("tracks")
        ?: return emptyList()
    return buildList {
        for (i in 0 until tracks.length()) {
            val obj = tracks.optJSONObject(i) ?: continue
            // Entries are {id, albumId, ...}; the track id alone is enough to batch-hydrate.
            obj.optString("id").takeIf { it.isNotBlank() }?.let { add(it) }
        }
    }
}

/** Parse a batch `/tracks` response (`result` is a flat array of full track objects). */
internal fun parseTrackArray(body: String): List<YandexTrack> {
    val arr = JSONObject(body).optJSONArray("result") ?: return emptyList()
    return buildList {
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.let(::parseTrackItem)?.let { add(it) }
        }
    }
}

/** Parse `/users/{uid}/playlists/list` playlist headers. */
internal fun parsePlaylists(body: String): List<YandexPlaylist> {
    val arr = JSONObject(body).optJSONArray("result") ?: return emptyList()
    return buildList {
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            // kind is numeric in the API; keep it as a string to match our id handling.
            val kind = if (item.has("kind")) item.get("kind").toString() else ""
            if (kind.isBlank()) continue
            val coverUri = item.optJSONObject("cover")?.optString("uri").orEmpty()
            add(
                YandexPlaylist(
                    kind = kind,
                    title = item.optString("title").ifBlank { "Плейлист" },
                    trackCount = item.optInt("trackCount", 0),
                    coverUrl = if (coverUri.isNotBlank()) "https://${coverUri.replace("%%", "400x400")}" else ""
                )
            )
        }
    }
}

/**
 * Parse `/landing3` into the track-shelf-friendly [YandexLanding]. `result.blocks[]` each carry a
 * `type` and `entities[]`; entity payloads differ per block (chart wraps a track, playlist blocks
 * carry a playlist object — nested under `data` for personal playlists).
 */
internal fun parseLanding(body: String): YandexLanding {
    val blocks = JSONObject(body).optJSONObject("result")?.optJSONArray("blocks")
        ?: return YandexLanding()
    val chart = mutableListOf<YandexTrack>()
    val personal = mutableListOf<YandexPlaylistRef>()
    val newPlaylists = mutableListOf<YandexPlaylistRef>()
    for (i in 0 until blocks.length()) {
        val block = blocks.optJSONObject(i) ?: continue
        val entities = block.optJSONArray("entities") ?: continue
        when (block.optString("type")) {
            "chart" -> for (e in 0 until entities.length()) {
                val track = entities.optJSONObject(e)?.optJSONObject("data")?.optJSONObject("track")
                track?.let(::parseTrackItem)?.let(chart::add)
            }
            "personal-playlists" -> for (e in 0 until entities.length()) {
                // Playlist object is nested one level deeper for personal playlists.
                val inner = entities.optJSONObject(e)?.optJSONObject("data")?.optJSONObject("data")
                playlistRef(inner)?.let(personal::add)
            }
            "new-playlists" -> for (e in 0 until entities.length()) {
                playlistRef(entities.optJSONObject(e)?.optJSONObject("data"))?.let(newPlaylists::add)
            }
        }
    }
    return YandexLanding(chart = chart, personalPlaylists = personal, newPlaylists = newPlaylists)
}

private fun playlistRef(obj: JSONObject?): YandexPlaylistRef? {
    if (obj == null) return null
    val uid = if (obj.has("uid")) obj.get("uid").toString() else ""
    val kind = if (obj.has("kind")) obj.get("kind").toString() else ""
    if (uid.isBlank() || kind.isBlank()) return null
    return YandexPlaylistRef(uid = uid, kind = kind, title = obj.optString("title").ifBlank { "Плейлист" })
}

/** Parse a rotor tracks response (`result.sequence[].track`) into tracks, in order. */
internal fun parseRotorSequence(body: String): List<YandexTrack> {
    val sequence = JSONObject(body).optJSONObject("result")?.optJSONArray("sequence")
        ?: return emptyList()
    return buildList {
        for (i in 0 until sequence.length()) {
            val track = sequence.optJSONObject(i)?.optJSONObject("track")
            track?.let(::parseTrackItem)?.let(::add)
        }
    }
}

/** One entry of a playlist's track list: either a fully embedded track or just an id to hydrate. */
internal sealed interface PlaylistEntry {
    data class Full(val track: YandexTrack) : PlaylistEntry
    data class IdOnly(val id: String) : PlaylistEntry
}

internal data class ParsedPlaylistTracks(val ordered: List<PlaylistEntry>) {
    val tracks: List<YandexTrack> get() = ordered.filterIsInstance<PlaylistEntry.Full>().map { it.track }
    val missingIds: List<String> get() = ordered.filterIsInstance<PlaylistEntry.IdOnly>().map { it.id }
}

/**
 * Parse `/users/{uid}/playlists/{kind}` into ordered entries. Each `result.tracks[]` element holds
 * either an embedded `track` object or, for lazily-loaded playlists, only a numeric `id`.
 */
internal fun parsePlaylistTracks(body: String): ParsedPlaylistTracks {
    val arr = JSONObject(body).optJSONObject("result")?.optJSONArray("tracks")
        ?: return ParsedPlaylistTracks(emptyList())
    val ordered = buildList {
        for (i in 0 until arr.length()) {
            val entry = arr.optJSONObject(i) ?: continue
            val embedded = entry.optJSONObject("track")
            if (embedded != null) {
                parseTrackItem(embedded)?.let { add(PlaylistEntry.Full(it)) }
            } else {
                val id = if (entry.has("id")) entry.get("id").toString() else entry.optString("trackId")
                id.takeIf { it.isNotBlank() }?.let { add(PlaylistEntry.IdOnly(it)) }
            }
        }
    }
    return ParsedPlaylistTracks(ordered)
}
