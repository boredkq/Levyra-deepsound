package com.luc4n3x.levyra.data.yandex

import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

internal class YandexMusicClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
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

        val request = Request.Builder()
            .url("https://oauth.yandex.ru/device/code")
            .post(body)
            .build()

        httpClient.newCall(request).execute().use { response ->
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
    }

    suspend fun pollDeviceAuth(deviceCode: String): String? = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("grant_type", "device_code")
            .add("code", deviceCode)
            .add("client_id", CLIENT_ID)
            .add("client_secret", CLIENT_SECRET)
            .build()

        val request = Request.Builder()
            .url("https://oauth.yandex.ru/token")
            .post(body)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val json = JSONObject(response.body?.string().orEmpty())
            if (response.isSuccessful) {
                return@use json.optString("access_token").takeIf { it.isNotBlank() }
            }
            val error = json.optString("error")
            if (error == "authorization_pending") return@use null
            if (error == "slow_down") return@use null
            throw IOException("Yandex auth error: $error (${json.optString("error_description")})")
        }
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
                val id = item.optString("id")
                val title = item.optString("title")
                val durationMs = item.optLong("durationMs", 0L)
                val artistsArr = item.optJSONArray("artists") ?: JSONArray()
                val artists = mutableListOf<String>()
                for (a in 0 until artistsArr.length()) {
                    artistsArr.optJSONObject(a)?.optString("name")?.let { artists.add(it) }
                }
                val artist = if (artists.isNotEmpty()) artists.joinToString(", ") else "Неизвестный исполнитель"

                val albumsArr = item.optJSONArray("albums") ?: JSONArray()
                val album = albumsArr.optJSONObject(0)?.optString("title") ?: "Без альбома"
                val coverUri = item.optString("coverUri").ifBlank {
                    albumsArr.optJSONObject(0)?.optString("coverUri").orEmpty()
                }
                val coverUrl = if (coverUri.isNotBlank()) "https://${coverUri.replace("%%", "400x400")}" else ""

                list.add(
                    YandexTrack(
                        id = id,
                        title = title,
                        artist = artist,
                        album = album,
                        durationMs = durationMs,
                        coverUrl = coverUrl,
                        available = item.optBoolean("available", true)
                    )
                )
            }
            list
        }
    }

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
    }
}
