package com.luc4n3x.levyra.data.soundcloud

data class SoundCloudTrack(
    val id: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val coverUrl: String,
    val permalinkUrl: String = "",
    val streamable: Boolean = true,
    val transcodings: List<SoundCloudTranscoding> = emptyList(),
    val trackAuth: String = ""
)

data class SoundCloudTranscoding(
    val url: String,
    val protocol: String,
    val preset: String,
    val mimeType: String
)

data class SoundCloudAccount(
    val id: Long = 0L,
    val username: String = "",
    val displayName: String = "",
    val avatarUrl: String = "",
    val isPro: Boolean = false
)
