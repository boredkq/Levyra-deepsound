package com.luc4n3x.levyra.feature.providers

import com.luc4n3x.levyra.data.YoutubeMusicPlaylistDetail
import com.luc4n3x.levyra.domain.AlbumDetail
import com.luc4n3x.levyra.domain.AlbumHit
import com.luc4n3x.levyra.domain.SearchResults
import com.luc4n3x.levyra.domain.Track
import com.luc4n3x.levyra.feature.source.SourceId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * M4 search: catalog search returns results from the *active* source, not whichever provider
 * happens to rank first. Switching the active source switches the results.
 */
class ProviderRouterActiveSourceTest {

    @Test
    fun activeYandex_returnsYandexResults() = runBlocking {
        val router = LevyraProviderRouter(
            catalogProviders = listOf(youtubeCatalog(), yandexCatalog()),
            playbackProviders = emptyList(),
            activeSource = { SourceId.YANDEX }
        )
        val result = router.searchEverything("q", "ru")
        assertEquals("yandex:song", result.songs.single().id)
    }

    @Test
    fun activeYoutube_returnsYoutubeResults() = runBlocking {
        val router = LevyraProviderRouter(
            catalogProviders = listOf(youtubeCatalog(), yandexCatalog()),
            playbackProviders = emptyList(),
            activeSource = { SourceId.YOUTUBE }
        )
        val result = router.searchEverything("q", "en")
        assertEquals("yt:song", result.songs.single().id)
    }

    private fun youtubeCatalog() = catalog(SourceId.YOUTUBE, "yt:song")
    private fun yandexCatalog() = catalog(SourceId.YANDEX, "yandex:song")

    private fun catalog(source: SourceId, songId: String) = object : LevyraCatalogProvider {
        override val id = source.name.lowercase()
        override val priority = if (source == SourceId.YOUTUBE) 10 else 20
        override val sourceId: SourceId = source
        override suspend fun searchEverything(query: String, languageCode: String): SearchResults {
            val song = track(songId)
            return SearchResults(topTrack = song, songs = listOf(song))
        }
        override suspend fun albumDetail(album: AlbumHit, languageCode: String): AlbumDetail? = null
        override suspend fun playlist(playlistId: String, languageCode: String, limit: Int): YoutubeMusicPlaylistDetail? = null
    }

    private fun track(id: String): Track = Track(
        id = id,
        title = "Track",
        artist = "Levyra",
        album = "Tests",
        durationMs = 180_000L,
        streamUrl = "",
        videoUrl = "",
        thumbnailUrl = "",
        largeThumbnailUrl = "",
        source = if (id.startsWith("yandex")) "yandex_music" else "youtube",
        moodTags = emptySet(),
        energy = 50,
        vocal = 50,
        replayScore = 0,
        cacheScore = 0,
        accentStart = 0,
        accentEnd = 0
    )
}
