package com.luc4n3x.levyra.data.yandex

data class YandexAccount(
    val uid: String = "",
    val login: String = "",
    val displayName: String = "",
    val avatarUrl: String = "",
    val hasPlus: Boolean = false
) {
    val isConnected: Boolean
        get() = uid.isNotBlank() || login.isNotBlank() || displayName.isNotBlank()
}

data class YandexTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val coverUrl: String,
    val available: Boolean = true
)

data class YandexDeviceCode(
    val deviceCode: String,
    val userCode: String,
    val verificationUrl: String,
    val expiresIn: Int = 300,
    val interval: Int = 5
)

/**
 * A user playlist as returned by `/users/{uid}/playlists/list`. [kind] is Yandex's per-user
 * playlist id (a number, kept as a string to match the rest of our id handling); it is combined
 * with the owner uid to fetch the playlist's tracks on demand.
 */
data class YandexPlaylist(
    val kind: String,
    val title: String,
    val trackCount: Int,
    val coverUrl: String
)
