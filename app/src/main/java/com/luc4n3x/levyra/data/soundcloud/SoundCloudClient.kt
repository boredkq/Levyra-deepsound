package com.luc4n3x.levyra.data.soundcloud

import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

internal class SoundCloudClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    @Volatile
    private var cachedClientId: String? = null

    @Volatile
    private var clientIdExpiresAtMs: Long = 0L

    suspend fun getClientId(): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val existing = cachedClientId
        if (existing != null && now < clientIdExpiresAtMs) {
            return@withContext existing
        }

        val discovered = discoverClientId()
        val selected = discovered ?: FALLBACK_CLIENT_ID
        cachedClientId = selected
        clientIdExpiresAtMs = now + 6 * 60 * 60 * 1000L
        selected
    }

    private fun discoverClientId(): String? {
        return try {
            val request = Request.Builder()
                .url("https://soundcloud.com/")
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            val pageHtml = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.string().orEmpty()
            }

            val scriptMatcher = Pattern.compile("<script\\b[^>]*?\\bsrc=([\"'])(.*?)\\1", Pattern.CASE_INSENSITIVE)
                .matcher(pageHtml)

            val scriptUrls = mutableListOf<String>()
            while (scriptMatcher.find()) {
                val src = scriptMatcher.group(2) ?: continue
                if (src.contains("sndcdn.com") || src.endsWith(".js")) {
                    scriptUrls.add(if (src.startsWith("http")) src else "https://soundcloud.com$src")
                }
            }

            val assetCandidates = scriptUrls.filter { it.contains("sndcdn.com/assets/") || it.contains("/assets/") }
                .takeLast(10)
                .reversed()

            for (url in (assetCandidates.ifEmpty { scriptUrls.takeLast(6).reversed() })) {
                val scriptContent = try {
                    val sReq = Request.Builder().url(url).header("User-Agent", USER_AGENT).get().build()
                    httpClient.newCall(sReq).execute().use { it.body?.string().orEmpty() }
                } catch (e: Exception) {
                    continue
                }

                val idMatcher = Pattern.compile("client_id\\s*[:=]\\s*[\"']?([0-9a-zA-Z]{32})[\"']?", Pattern.CASE_INSENSITIVE)
                    .matcher(scriptContent)
                if (idMatcher.find()) {
                    return idMatcher.group(1)
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun search(query: String, limit: Int = 30): List<SoundCloudTrack> = withContext(Dispatchers.IO) {
        var id = getClientId()
        var results = searchWithId(query, id, limit)
        if (results == null) {
            // Retry with fallback
            cachedClientId = null
            id = FALLBACK_CLIENT_ID
            results = searchWithId(query, id, limit)
        }
        results ?: emptyList()
    }

    private fun searchWithId(query: String, clientId: String, limit: Int): List<SoundCloudTrack>? {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "https://api-v2.soundcloud.com/search/tracks?q=$encodedQuery&client_id=$clientId&limit=$limit&offset=0"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                if (response.code == 401) return null
                if (!response.isSuccessful) return emptyList()
                val json = JSONObject(response.body?.string().orEmpty())
                val collection = json.optJSONArray("collection") ?: return emptyList()
                val list = mutableListOf<SoundCloudTrack>()
                for (i in 0 until collection.length()) {
                    val item = collection.optJSONObject(i) ?: continue
                    parseTrack(item)?.let { list.add(it) }
                }
                list
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getRelatedTracks(trackId: String, limit: Int = 30): List<SoundCloudTrack> = withContext(Dispatchers.IO) {
        var id = getClientId()
        val url = "https://api-v2.soundcloud.com/tracks/$trackId/related?client_id=$id&limit=$limit"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (response.code == 401) {
                    cachedClientId = null
                    id = FALLBACK_CLIENT_ID
                    val retryReq = Request.Builder()
                        .url("https://api-v2.soundcloud.com/tracks/$trackId/related?client_id=$id&limit=$limit")
                        .header("User-Agent", USER_AGENT)
                        .get()
                        .build()
                    return@withContext httpClient.newCall(retryReq).execute().use { retryResp ->
                        if (!retryResp.isSuccessful) return@use emptyList()
                        parseTrackCollection(retryResp.body?.string().orEmpty())
                    }
                }
                if (!response.isSuccessful) return@withContext emptyList()
                parseTrackCollection(response.body?.string().orEmpty())
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseTrackCollection(body: String): List<SoundCloudTrack> {
        val json = JSONObject(body)
        val collection = json.optJSONArray("collection") ?: return emptyList()
        val list = mutableListOf<SoundCloudTrack>()
        for (i in 0 until collection.length()) {
            val item = collection.optJSONObject(i) ?: continue
            parseTrack(item)?.let { list.add(it) }
        }
        return list
    }

    suspend fun resolveStreamUrl(track: SoundCloudTrack): String? = withContext(Dispatchers.IO) {
        val validTranscodings = track.transcodings.filter { item ->
            val proto = item.protocol.lowercase()
            val preset = item.preset.lowercase()
            !proto.contains("ctr") && !proto.contains("cbc") && !proto.contains("drm") && !preset.contains("abr_sq")
        }

        val chosen = validTranscodings.find { it.protocol == "progressive" }
            ?: validTranscodings.find { it.protocol == "hls" && it.preset == "aac_160k" }
            ?: validTranscodings.find { it.protocol == "hls" && it.preset == "aac_96k" }
            ?: validTranscodings.find { it.protocol == "hls" }
            ?: validTranscodings.firstOrNull()
            ?: return@withContext null

        var id = getClientId()
        var streamUrl = fetchTranscodingStream(chosen.url, id, track.trackAuth)
        if (streamUrl == null) {
            cachedClientId = null
            id = FALLBACK_CLIENT_ID
            streamUrl = fetchTranscodingStream(chosen.url, id, track.trackAuth)
        }
        streamUrl
    }

    private fun fetchTranscodingStream(url: String, clientId: String, trackAuth: String): String? {
        val authParam = if (trackAuth.isNotBlank()) "&track_authorization=${URLEncoder.encode(trackAuth, "UTF-8")}" else ""
        val separator = if (url.contains("?")) "&" else "?"
        val fullUrl = "$url${separator}client_id=$clientId$authParam"

        val request = Request.Builder()
            .url(fullUrl)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val json = JSONObject(response.body?.string().orEmpty())
                json.optString("url").takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getTrack(trackId: String): SoundCloudTrack? = withContext(Dispatchers.IO) {
        var id = getClientId()
        val url = "https://api-v2.soundcloud.com/tracks/$trackId?client_id=$id"
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).get().build()
        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val json = JSONObject(response.body?.string().orEmpty())
                parseTrack(json)
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun resolveUrl(permalinkUrl: String): SoundCloudTrack? = withContext(Dispatchers.IO) {
        var id = getClientId()
        val encoded = URLEncoder.encode(permalinkUrl, "UTF-8")
        val url = "https://api-v2.soundcloud.com/resolve?url=$encoded&client_id=$id"
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).get().build()
        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val json = JSONObject(response.body?.string().orEmpty())
                parseTrack(json)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseTrack(item: JSONObject): SoundCloudTrack? {
        val id = item.optString("id")
        if (id.isBlank()) return null
        val title = item.optString("title")
        val durationMs = item.optLong("duration", 0L)
        val userObj = item.optJSONObject("user") ?: JSONObject()
        val artist = userObj.optString("username", "SoundCloud Artist")

        val rawCover = item.optString("artwork_url").ifBlank {
            userObj.optString("avatar_url")
        }
        val coverUrl = if (rawCover.isNotBlank()) rawCover.replace("-large", "-t500x500") else ""
        val permalinkUrl = item.optString("permalink_url")
        val trackAuth = item.optString("track_authorization")
        val streamable = item.optBoolean("streamable", true)

        val media = item.optJSONObject("media")
        val transcodingsArr = media?.optJSONArray("transcodings") ?: JSONArray()
        val transcodings = mutableListOf<SoundCloudTranscoding>()
        for (t in 0 until transcodingsArr.length()) {
            val tObj = transcodingsArr.optJSONObject(t) ?: continue
            val tUrl = tObj.optString("url")
            val preset = tObj.optString("preset")
            val formatObj = tObj.optJSONObject("format") ?: JSONObject()
            val protocol = formatObj.optString("protocol")
            val mimeType = formatObj.optString("mime_type")
            if (tUrl.isNotBlank()) {
                transcodings.add(SoundCloudTranscoding(tUrl, protocol, preset, mimeType))
            }
        }

        return SoundCloudTrack(
            id = id,
            title = title,
            artist = artist,
            durationMs = durationMs,
            coverUrl = coverUrl,
            permalinkUrl = permalinkUrl,
            streamable = streamable,
            transcodings = transcodings,
            trackAuth = trackAuth
        )
    }

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        private const val FALLBACK_CLIENT_ID = "V7f0p2q7JD4voO1Esqg6bwwtNYcYAtLC"
    }
}
