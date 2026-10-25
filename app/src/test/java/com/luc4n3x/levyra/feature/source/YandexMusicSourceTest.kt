package com.luc4n3x.levyra.feature.source

import com.luc4n3x.levyra.domain.AlbumDetail
import com.luc4n3x.levyra.domain.AlbumHit
import com.luc4n3x.levyra.domain.SearchResults
import com.luc4n3x.levyra.domain.Track
import com.luc4n3x.levyra.data.YoutubeMusicPlaylistDetail
import com.luc4n3x.levyra.feature.providers.LevyraCatalogProvider
import com.luc4n3x.levyra.feature.providers.LevyraPlaybackProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class YandexMusicSourceTest {

    @Test
    fun owns_acceptsYandexAndRejectsOthers() {
        val source = YandexMusicSource(catalog { _, _ -> empty() }, playback())
        assertTrue(source.owns(track(id = "yandex:42", source = "youtube")))
        assertTrue(source.owns(track(id = "42", source = "yandex_music")))
        assertFalse(source.owns(track(id = "abc", source = "youtube")))
    }

    @Test
    fun id_isYandex() {
        assertSame(SourceId.YANDEX, YandexMusicSource(catalog { _, _ -> empty() }, playback()).id)
    }

    @Test
    fun search_delegatesToCatalog() = runBlocking {
        val expected = empty()
        val source = YandexMusicSource(catalog { _, _ -> expected }, playback())
        assertSame(expected, source.search("q", "ru"))
    }

    @Test
    fun resolve_delegatesToPlayback() = runBlocking {
        val resolved = track(id = "yandex:1", source = "yandex_music").copy(streamUrl = "http://x")
        val source = YandexMusicSource(catalog { _, _ -> empty() }, playback(onResolve = { _, _ -> resolved }))
        assertSame(resolved, source.resolve(track(id = "yandex:1", source = "yandex_music"), false))
    }

    private fun empty() = SearchResults(topTrack = null, songs = emptyList())

    private fun catalog(onSearch: suspend (String, String) -> SearchResults) =
        object : LevyraCatalogProvider {
            override val id = "yandex_music"
            override val priority = 20
            override suspend fun searchEverything(query: String, languageCode: String) = onSearch(query, languageCode)
            override suspend fun albumDetail(album: AlbumHit, languageCode: String): AlbumDetail? = null
            override suspend fun playlist(playlistId: String, languageCode: String, limit: Int): YoutubeMusicPlaylistDetail? = null
        }

    private fun playback(
        onResolve: suspend (Track, Boolean) -> Track = { t, _ -> t },
        onOffline: suspend (Track) -> Track = { it }
    ) = object : LevyraPlaybackProvider {
        override val id = "yandex_music"
        override val priority = 20
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
