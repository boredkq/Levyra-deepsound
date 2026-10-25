package com.luc4n3x.levyra.data.yandex

import com.luc4n3x.levyra.data.YoutubeMusicPlaylistDetail
import com.luc4n3x.levyra.data.hqaudio.AlternativeTrackCandidate
import com.luc4n3x.levyra.data.hqaudio.AudioQualityTier
import com.luc4n3x.levyra.data.hqaudio.HighQualityAudioProvider
import com.luc4n3x.levyra.data.hqaudio.ProviderFailure
import com.luc4n3x.levyra.data.hqaudio.ProviderLookupOutcome
import com.luc4n3x.levyra.data.hqaudio.ProviderSearchOutcome
import com.luc4n3x.levyra.data.hqaudio.ProviderStreamOutcome
import com.luc4n3x.levyra.data.hqaudio.ResolvedHighQualityStream
import com.luc4n3x.levyra.data.hqaudio.StreamRejection
import com.luc4n3x.levyra.domain.AlbumDetail
import com.luc4n3x.levyra.domain.AlbumHit
import com.luc4n3x.levyra.domain.SearchResults
import com.luc4n3x.levyra.domain.Track
import com.luc4n3x.levyra.feature.providers.LevyraCatalogProvider
import com.luc4n3x.levyra.feature.providers.LevyraPlaybackProvider
import com.luc4n3x.levyra.feature.providers.LevyraProviderMissException

internal class YandexMusicAudioProvider(
    private val client: YandexMusicClient,
    private val sessionStore: YandexMusicSessionStore
) : HighQualityAudioProvider, LevyraCatalogProvider, LevyraPlaybackProvider {

    override val id: String = PROVIDER_ID
    override val displayName: String = "Яндекс Музыка"
    override val priority: Int = 20

    // Per-origin dispatch: only ever handle Yandex-origin tracks.
    override fun owns(track: Track): Boolean =
        track.source == PROVIDER_ID || track.id.startsWith("yandex:")

    private val token: String?
        get() = sessionStore.loadToken()

    override suspend fun search(query: String, page: Int): ProviderSearchOutcome = try {
        val tracks = client.search(query, token)
        val candidates = tracks.map { track ->
            AlternativeTrackCandidate(
                providerId = id,
                providerTrackId = track.id,
                title = track.title,
                primaryArtists = listOf(track.artist),
                featuredArtists = emptyList(),
                album = track.album,
                durationSeconds = (track.durationMs / 1000).toInt(),
                explicit = null,
                offers320 = true,
                mediaToken = track.id
            )
        }
        ProviderSearchOutcome.Found(candidates)
    } catch (e: Exception) {
        ProviderSearchOutcome.Failed(ProviderFailure.NETWORK)
    }

    override suspend fun lookup(providerTrackId: String): ProviderLookupOutcome = try {
        val streamUrl = client.resolveStreamUrl(providerTrackId, token)
        if (streamUrl != null) {
            ProviderLookupOutcome.Found(
                AlternativeTrackCandidate(
                    providerId = id,
                    providerTrackId = providerTrackId,
                    title = "",
                    primaryArtists = emptyList(),
                    featuredArtists = emptyList(),
                    album = "",
                    durationSeconds = 0,
                    explicit = null,
                    offers320 = true,
                    mediaToken = providerTrackId
                )
            )
        } else {
            ProviderLookupOutcome.Missing
        }
    } catch (e: Exception) {
        ProviderLookupOutcome.Failed(ProviderFailure.NETWORK)
    }

    override suspend fun resolveStream(candidate: AlternativeTrackCandidate): ProviderStreamOutcome = try {
        val trackId = candidate.mediaToken.ifBlank { candidate.providerTrackId }
        val streamUrl = client.resolveStreamUrl(trackId, token)
        if (streamUrl != null) {
            ProviderStreamOutcome.Resolved(
                ResolvedHighQualityStream(
                    providerId = id,
                    providerTrackId = trackId,
                    url = streamUrl,
                    tier = AudioQualityTier.KBPS_320,
                    mimeType = "audio/mpeg",
                    container = "mp3",
                    codec = "mp3",
                    contentLength = 0L,
                    estimatedKbps = 320,
                    expiresAtMs = System.currentTimeMillis() + STREAM_TTL_MS
                )
            )
        } else {
            ProviderStreamOutcome.Unavailable(listOf(StreamRejection.NO_MEDIA))
        }
    } catch (e: Exception) {
        ProviderStreamOutcome.Failed(ProviderFailure.NETWORK)
    }

    override suspend fun searchEverything(query: String, languageCode: String): SearchResults {
        val tracks = client.search(query, token)
        val songs = tracks.map { yt ->
            Track(
                id = "yandex:${yt.id}",
                title = yt.title,
                artist = yt.artist,
                album = yt.album,
                durationMs = yt.durationMs,
                streamUrl = "",
                videoUrl = "",
                thumbnailUrl = yt.coverUrl,
                largeThumbnailUrl = yt.coverUrl,
                source = "yandex_music",
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
        if (!track.id.startsWith("yandex:") && track.source != "yandex_music") {
            throw LevyraProviderMissException("Track is not from Yandex Music")
        }
        val rawId = track.id.removePrefix("yandex:")
        val url = client.resolveStreamUrl(rawId, token)
            ?: throw LevyraProviderMissException("Failed to resolve Yandex stream")
        return track.copy(streamUrl = url)
    }

    override suspend fun resolveForOffline(track: Track): Track = resolve(track, false)

    companion object {
        const val PROVIDER_ID = "yandex_music"
        private const val STREAM_TTL_MS = 2 * 60 * 60 * 1000L
    }
}
