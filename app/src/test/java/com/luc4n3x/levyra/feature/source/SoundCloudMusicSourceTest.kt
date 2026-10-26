package com.luc4n3x.levyra.feature.source

import com.luc4n3x.levyra.data.YoutubeMusicPlaylistDetail
import com.luc4n3x.levyra.domain.AlbumDetail
import com.luc4n3x.levyra.domain.AlbumHit
import com.luc4n3x.levyra.domain.HomeSection
import com.luc4n3x.levyra.domain.SearchResults
import com.luc4n3x.levyra.domain.Track
import com.luc4n3x.levyra.feature.providers.LevyraCatalogProvider
import com.luc4n3x.levyra.feature.providers.LevyraPlaybackProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundCloudMusicSourceTest {

    @Test
    fun owns_acceptsSoundCloudAndRejectsOthers() {
        val source = SoundCloudMusicSource(catalog { _, _ -> empty() }, playback())
        assertTrue(source.owns(track(id = "soundcloud:123456", source = "youtube")))
        assertTrue(source.owns(track(id = "123456", source = "soundcloud")))
        assertFalse(source.owns(track(id = "abc", source = "youtube")))
        assertFalse(source.owns(track(id = "yandex:42", source = "yandex_music")))
    }

    @Test
    fun id_isSoundCloud() {
        assertSame(SourceId.SOUNDCLOUD, SoundCloudMusicSource(catalog { _, _ -> empty() }, playback()).id)
    }

    @Test
    fun search_delegatesToCatalog() = runBlocking {
        val expected = empty()
        val source = SoundCloudMusicSource(catalog { _, _ -> expected }, playback())
        assertSame(expected, source.search("query", "ru"))
    }

    @Test
    fun resolve_delegatesToPlayback() = runBlocking {
        val resolved = track(id = "soundcloud:1", source = "soundcloud").copy(streamUrl = "http://stream")
        val source = SoundCloudMusicSource(catalog { _, _ -> empty() }, playback(onResolve = { _, _ -> resolved }))
        assertSame(resolved, source.resolve(track(id = "soundcloud:1", source = "soundcloud"), false))
    }

    @Test
    fun capabilities_offersExplore_withoutOptionalProviders() {
        val source = SoundCloudMusicSource(catalog { _, _ -> empty() }, playback())
        assertEquals(setOf(SourceCapability.EXPLORE), source.capabilities())
    }

    @Test
    fun capabilities_offersHome_withHomeProvider() {
        val source = SoundCloudMusicSource(
            catalog { _, _ -> empty() },
            playback(),
            homeProvider = home(emptyList())
        )
        assertEquals(setOf(SourceCapability.EXPLORE, SourceCapability.HOME), source.capabilities())
    }

    @Test
    fun homeFeed_delegatesToProvider() = runBlocking {
        val expected = listOf(
            HomeSection(
                title = "Популярные треки",
                tracks = listOf(track(id = "soundcloud:1", source = "soundcloud"))
            )
        )
        val source = SoundCloudMusicSource(catalog { _, _ -> empty() }, playback(), homeProvider = home(expected))
        assertSame(expected, source.homeFeed())
    }

    @Test
    fun capabilities_offersRadio_withRadioProvider() {
        val source = SoundCloudMusicSource(
            catalog { _, _ -> empty() },
            playback(),
            radioProvider = radio(emptyList())
        )
        assertEquals(setOf(SourceCapability.EXPLORE, SourceCapability.RADIO), source.capabilities())
    }

    @Test
    fun radio_delegatesToProvider_passingArgs() = runBlocking {
        val expected = listOf(track(id = "soundcloud:99", source = "soundcloud"))
        var seenSeed: String? = null
        var seenExclude: Set<String> = emptySet()
        var seenLimit = -1
        val provider = object : SourceRadioProvider {
            override suspend fun radioTracks(seedTrackId: String?, excludeIds: Set<String>, limit: Int): List<Track> {
                seenSeed = seedTrackId
                seenExclude = excludeIds
                seenLimit = limit
                return expected
            }
        }
        val source = SoundCloudMusicSource(catalog { _, _ -> empty() }, playback(), radioProvider = provider)
        assertSame(expected, source.radio(seedTrackId = "99", excludeIds = setOf("x"), limit = 10))
        assertEquals("99", seenSeed)
        assertEquals(setOf("x"), seenExclude)
        assertEquals(10, seenLimit)
    }

    private fun empty() = SearchResults(topTrack = null, songs = emptyList())

    private fun home(result: List<HomeSection>) = object : SourceHomeProvider {
        override suspend fun loadHomeSections(): List<HomeSection> = result
    }

    private fun radio(result: List<Track>) = object : SourceRadioProvider {
        override suspend fun radioTracks(seedTrackId: String?, excludeIds: Set<String>, limit: Int): List<Track> = result
    }

    private fun catalog(onSearch: suspend (String, String) -> SearchResults) =
        object : LevyraCatalogProvider {
            override val id = "soundcloud"
            override val priority = 15
            override suspend fun searchEverything(query: String, languageCode: String) = onSearch(query, languageCode)
            override suspend fun albumDetail(album: AlbumHit, languageCode: String): AlbumDetail? = null
            override suspend fun playlist(playlistId: String, languageCode: String, limit: Int): YoutubeMusicPlaylistDetail? = null
        }

    private fun playback(
        onResolve: suspend (Track, Boolean) -> Track = { t, _ -> t },
        onOffline: suspend (Track) -> Track = { it }
    ) = object : LevyraPlaybackProvider {
        override val id = "soundcloud"
        override val priority = 15
        override suspend fun resolve(track: Track, videoMode: Boolean) = onResolve(track, videoMode)
        override suspend fun resolveForOffline(track: Track) = onOffline(track)
    }

    private fun track(id: String, source: String): Track = Track(
        id = id,
        title = "Track",
        artist = "Artist",
        album = "SoundCloud",
        durationMs = 200_000L,
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
