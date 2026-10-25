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

/**
 * A reference to a playlist on the Yandex landing feed. [uid] is the owner id (the signed-in user
 * for personal playlists, an editor for public ones) and is combined with [kind] to fetch tracks.
 */
data class YandexPlaylistRef(
    val uid: String,
    val kind: String,
    val title: String
)

/**
 * The parsed Yandex home feed (`/landing3`). Blocks we can express as track shelves: [chart] holds
 * ready tracks; [personalPlaylists] and [newPlaylists] are references whose tracks are fetched on
 * demand. Album "new releases" are intentionally left out until an album-tracks endpoint is added.
 */
data class YandexLanding(
    val chart: List<YandexTrack> = emptyList(),
    val personalPlaylists: List<YandexPlaylistRef> = emptyList(),
    val newPlaylists: List<YandexPlaylistRef> = emptyList()
)
