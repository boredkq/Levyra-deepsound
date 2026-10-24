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
