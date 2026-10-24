package com.luc4n3x.levyra.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.luc4n3x.levyra.data.yandex.YandexMusicSessionStore
import com.luc4n3x.levyra.ui.soundcloud.SoundCloudAccountScreen
import com.luc4n3x.levyra.ui.yandex.YandexMusicAccountScreen

@Composable
internal fun LevyraExternalAccountOverlays(
    showYandex: Boolean,
    onCloseYandex: () -> Unit,
    showSoundCloud: Boolean,
    onCloseSoundCloud: () -> Unit
) {
    if (showYandex) {
        val yandexContext = LocalContext.current
        YandexMusicAccountScreen(
            sessionStore = YandexMusicSessionStore.getInstance(yandexContext),
            onClose = onCloseYandex
        )
    }
    if (showSoundCloud) {
        SoundCloudAccountScreen(
            onClose = onCloseSoundCloud
        )
    }
}
