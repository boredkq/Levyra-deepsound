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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the M1 invariant: with only YouTube registered and no switch, the app behaves exactly as
 * before — the active source is the [YoutubeMusicSource] and its search reaches the YouTube
 * catalog provider unchanged.
 */
class YoutubeDefaultRegressionTest {

    @Test
    fun defaultActiveIsYoutubeAndDelegatesToCatalog() = runBlocking {
        var searched = false
        val youtube = YoutubeMusicSource(
            catalog = object : LevyraCatalogProvider {
                override val id = "youtube_music"
                override val priority = 10
                override suspend fun searchEverything(query: String, languageCode: String): SearchResults {
                    searched = true
                    return SearchResults(topTrack = null, songs = emptyList())
                }
                override suspend fun albumDetail(album: AlbumHit, languageCode: String): AlbumDetail? = null
                override suspend fun playlist(playlistId: String, languageCode: String, limit: Int): YoutubeMusicPlaylistDetail? = null
            },
            playback = object : LevyraPlaybackProvider {
                override val id = "levyra_native"
                override val priority = 10
                override suspend fun resolve(track: Track, videoMode: Boolean) = track
                override suspend fun resolveForOffline(track: Track) = track
            }
        )
        val manager = SourceManager(listOf(youtube))

        assertEquals(SourceId.YOUTUBE, manager.activeId.value)
        assertSame(youtube, manager.active())

        manager.active().search("q", "en")
        assertTrue("active search must reach the YouTube catalog provider", searched)
    }
}
