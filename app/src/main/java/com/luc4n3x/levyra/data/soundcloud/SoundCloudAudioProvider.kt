package com.luc4n3x.levyra.data.soundcloud

import com.luc4n3x.levyra.data.YoutubeMusicPlaylistDetail
import com.luc4n3x.levyra.domain.AlbumDetail
import com.luc4n3x.levyra.domain.AlbumHit
import com.luc4n3x.levyra.domain.SearchResults
import com.luc4n3x.levyra.domain.Track
import com.luc4n3x.levyra.feature.providers.LevyraCatalogProvider
import com.luc4n3x.levyra.feature.providers.LevyraPlaybackProvider
import com.luc4n3x.levyra.feature.providers.LevyraProviderMissException

internal class SoundCloudAudioProvider(
    private val client: SoundCloudClient = SoundCloudClient()
) : LevyraCatalogProvider, LevyraPlaybackProvider {

    override val id: String = PROVIDER_ID
    override val priority: Int = 30

    override suspend fun searchEverything(query: String, languageCode: String): SearchResults {
        val scTracks = client.search(query)
        val songs = scTracks.map { sc ->
            Track(
                id = "soundcloud:${sc.id}",
                title = sc.title,
                artist = sc.artist,
                album = "SoundCloud",
                durationMs = sc.durationMs,
                streamUrl = "",
                videoUrl = "",
                thumbnailUrl = sc.coverUrl,
                largeThumbnailUrl = sc.coverUrl,
                source = "soundcloud",
                moodTags = emptySet(),
                energy = 50,
                vocal = 50,
                replayScore = 0,
                cacheScore = 0,
                accentStart = 0,
                accentEnd = 0
            )
        }
        return SearchResults(topTrack = songs.firstOrNull(), songs = songs)
    }

    override suspend fun albumDetail(album: AlbumHit, languageCode: String): AlbumDetail? = null

    override suspend fun playlist(playlistId: String, languageCode: String, limit: Int): YoutubeMusicPlaylistDetail? = null

    override suspend fun resolve(track: Track, videoMode: Boolean): Track {
        if (!track.id.startsWith("soundcloud:") && track.source != "soundcloud") {
            throw LevyraProviderMissException("Track is not from SoundCloud")
        }
        val rawId = track.id.removePrefix("soundcloud:")
        val scTrack = client.getTrack(rawId)
            ?: throw LevyraProviderMissException("SoundCloud track metadata missing")
        val streamUrl = client.resolveStreamUrl(scTrack)
            ?: throw LevyraProviderMissException("Failed to resolve SoundCloud stream URL")
        return track.copy(streamUrl = streamUrl)
    }

    override suspend fun resolveForOffline(track: Track): Track = resolve(track, false)

    companion object {
        const val PROVIDER_ID = "soundcloud"
    }
}
