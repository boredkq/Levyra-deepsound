package com.luc4n3x.levyra.feature.source

import com.luc4n3x.levyra.domain.AlbumDetail
import com.luc4n3x.levyra.domain.AlbumHit
import com.luc4n3x.levyra.domain.SearchResults
import com.luc4n3x.levyra.domain.Track
import com.luc4n3x.levyra.data.YoutubeMusicPlaylistDetail
import com.luc4n3x.levyra.feature.providers.LevyraCatalogProvider
import com.luc4n3x.levyra.feature.providers.LevyraPlaybackProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class YoutubeMusicSourceTest {

    @Test
    fun search_delegatesToCatalogProvider() = runBlocking {
        val expected = SearchResults(topTrack = null, songs = emptyList())
        var seen: Pair<String, String>? = null
        val source = YoutubeMusicSource(
            catalog = catalog { query, lang -> seen = query to lang; expected },
            playback = playback()
        )

        val result = source.search("daft punk", "en")

        assertSame(expected, result)
        assertEquals("daft punk" to "en", seen)
    }

    @Test
    fun owns_acceptsYoutubeTracksAndRejectsYandex() {
        val source = YoutubeMusicSource(catalog { _, _ -> empty() }, playback())
        assertTrue(source.owns(track(id = "abc", source = "youtube")))
        assertFalse(source.owns(track(id = "yandex:123", source = "youtube")))
        assertFalse(source.owns(track(id = "abc", source = "yandex_music")))
    }

    @Test(expected = IOException::class)
    fun search_propagatesProviderError() = runBlocking {
        val source = YoutubeMusicSource(
            catalog = catalog { _, _ -> throw IOException("boom") },
            playback = playback()
        )
        source.search("q", "en")
        Unit
    }

    @Test
    fun resolve_delegatesToPlaybackProvider() = runBlocking {
        val resolved = track(id = "abc", source = "youtube").copy(streamUrl = "http://x")
        val source = YoutubeMusicSource(
            catalog { _, _ -> empty() },
            playback(onResolve = { _, _ -> resolved })
        )
        assertSame(resolved, source.resolve(track(id = "abc", source = "youtube"), false))
    }

    // --- fakes & factories ---------------------------------------------------

    private fun empty() = SearchResults(topTrack = null, songs = emptyList())

    private fun catalog(onSearch: suspend (String, String) -> SearchResults) =
        object : LevyraCatalogProvider {
            override val id = "youtube_music"
            override val priority = 10
            override suspend fun searchEverything(query: String, languageCode: String) =
                onSearch(query, languageCode)
            override suspend fun albumDetail(album: AlbumHit, languageCode: String): AlbumDetail? = null
            override suspend fun playlist(playlistId: String, languageCode: String, limit: Int): YoutubeMusicPlaylistDetail? = null
        }

    private fun playback(
        onResolve: suspend (Track, Boolean) -> Track = { t, _ -> t },
        onOffline: suspend (Track) -> Track = { it }
    ) = object : LevyraPlaybackProvider {
        override val id = "levyra_native"
        override val priority = 10
        override suspend fun resolve(track: Track, videoMode: Boolean) = onResolve(track, videoMode)
        override suspend fun resolveForOffline(track: Track) = onOffline(track)
    }

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
