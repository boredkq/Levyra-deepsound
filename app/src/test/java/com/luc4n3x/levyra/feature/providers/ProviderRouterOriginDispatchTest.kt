package com.luc4n3x.levyra.feature.providers

import com.luc4n3x.levyra.domain.Track
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Per-origin playback dispatch (M2): a track is only ever resolved by a provider that [owns] it,
 * so a Yandex track never hits the YouTube/native resolver and vice versa.
 */
class ProviderRouterOriginDispatchTest {

    @Test
    fun yandexTrack_goesOnlyToYandexProvider() = runBlocking {
        val native = recordingProvider("levyra_native", owns = { !isYandexOriginTrack(it) })
        val yandex = recordingProvider("yandex_music", owns = { isYandexOriginTrack(it) }, resolvesUrl = "http://ya/stream")
        val router = LevyraProviderRouter(emptyList(), listOf(native, yandex))

        val result = router.resolve(track(id = "yandex:1", source = "yandex_music"))

        assertEquals("http://ya/stream", result.streamUrl)
        assertTrue("yandex provider must be used", yandex.called)
        assertFalse("native resolver must NOT see a Yandex track", native.called)
    }

    @Test
    fun youtubeTrack_goesOnlyToNativeProvider() = runBlocking {
        val native = recordingProvider("levyra_native", owns = { !isYandexOriginTrack(it) }, resolvesUrl = "http://yt/stream")
        val yandex = recordingProvider("yandex_music", owns = { isYandexOriginTrack(it) })
        val router = LevyraProviderRouter(emptyList(), listOf(native, yandex))

        val result = router.resolve(track(id = "abc", source = "youtube"))

        assertEquals("http://yt/stream", result.streamUrl)
        assertTrue("native resolver must be used", native.called)
        assertFalse("yandex provider must NOT see a YouTube track", yandex.called)
    }

    private class RecordingProvider(
        override val id: String,
        private val ownsFn: (Track) -> Boolean,
        private val resolvesUrl: String
    ) : LevyraPlaybackProvider {
        override val priority: Int = 10
        var called = false
            private set
        override fun owns(track: Track): Boolean = ownsFn(track)
        override suspend fun resolve(track: Track, videoMode: Boolean): Track {
            called = true
            return track.copy(streamUrl = resolvesUrl)
        }
        override suspend fun resolveForOffline(track: Track): Track = resolve(track, false)
    }

    private fun recordingProvider(id: String, owns: (Track) -> Boolean, resolvesUrl: String = "http://x/stream") =
        RecordingProvider(id, owns, resolvesUrl)

    private fun track(id: String, source: String): Track = Track(
        id = id,
        title = "Track",
        artist = "Levyra",
        album = "Tests",
        durationMs = 180_000L,
        streamUrl = "",
        videoUrl = "",
        thumbnailUrl = "",
        largeThumbnailUrl = "",
        source = source,
        moodTags = emptySet(),
        energy = 50,
        vocal = 50,
        replayScore = 0,
        cacheScore = 0,
        accentStart = 0,
        accentEnd = 0
    )
}
