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
import kotlinx.coroutines.withContext

internal class YandexMusicAudioProvider(
    private val client: YandexMusicClient,
    private val sessionStore: YandexMusicSessionStore
) : HighQualityAudioProvider, LevyraCatalogProvider, LevyraPlaybackProvider,
    com.luc4n3x.levyra.feature.source.SourceLibraryProvider,
    com.luc4n3x.levyra.feature.source.SourceHomeProvider,
    com.luc4n3x.levyra.feature.source.SourceRadioProvider {

    override val id: String = PROVIDER_ID
    override val displayName: String = "Яндекс Музыка"
    override val priority: Int = 20

    override val sourceId: com.luc4n3x.levyra.feature.source.SourceId =
        com.luc4n3x.levyra.feature.source.SourceId.YANDEX

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
        val songs = client.search(query, token).map(::toDomainTrack)
        return SearchResults(topTrack = songs.firstOrNull(), songs = songs)
    }

    override suspend fun loadLibrary(): com.luc4n3x.levyra.feature.source.SourceLibrary {
        val currentToken = token ?: return com.luc4n3x.levyra.feature.source.SourceLibrary()
        val uid = sessionStore.loadAccount()?.uid.orEmpty()
        if (uid.isBlank()) return com.luc4n3x.levyra.feature.source.SourceLibrary()

        // Fetch (network, on IO inside the client) the likes and the playlist headers only. Playlist
        // tracks are loaded lazily by [loadPlaylistTracks] when a playlist is opened, so a large
        // library (thousands of likes, many playlists) does not stall the Library tab on connect.
        val likes = client.likedTracks(uid, currentToken)
        val playlistHeaders = client.userPlaylists(uid, currentToken)
        return withContext(kotlinx.coroutines.Dispatchers.Default) {
            com.luc4n3x.levyra.feature.source.SourceLibrary(
                favorites = likes.map(::toDomainTrack),
                playlists = playlistHeaders.map { yp ->
                    com.luc4n3x.levyra.domain.Playlist(
                        id = "yandex:playlist:${yp.kind}",
                        name = yp.title,
                        coverUrl = yp.coverUrl,
                        tracks = emptyList(),
                        createdAt = 0L,
                        updatedAt = 0L,
                        remoteTrackCount = yp.trackCount
                    )
                }
            )
        }
    }

    /** Lazily load the tracks of a Yandex playlist (id "yandex:playlist:<kind>") shown in the library. */
    suspend fun loadPlaylistTracks(playlistId: String): List<Track> {
        val currentToken = token ?: return emptyList()
        val uid = sessionStore.loadAccount()?.uid.orEmpty()
        val kind = playlistId.removePrefix("yandex:playlist:")
        if (uid.isBlank() || kind.isBlank() || kind == playlistId) return emptyList()
        val tracks = client.playlistTracks(uid, kind, currentToken)
        return withContext(kotlinx.coroutines.Dispatchers.Default) { tracks.map(::toDomainTrack) }
    }

    override suspend fun loadHomeSections(): List<com.luc4n3x.levyra.domain.HomeSection> {
        val currentToken = token ?: return emptyList()
        val landing = client.landing(currentToken)
        val sections = mutableListOf<com.luc4n3x.levyra.domain.HomeSection>()
        if (landing.chart.isNotEmpty()) {
            val chart = withContext(kotlinx.coroutines.Dispatchers.Default) { landing.chart.map(::toDomainTrack) }
            sections.add(com.luc4n3x.levyra.domain.HomeSection("Чарт", chart))
        }
        // Personal + new editorial playlists become one track shelf each (titles from the feed).
        (landing.personalPlaylists + landing.newPlaylists).forEach { ref ->
            val raw = client.playlistTracks(ref.uid, ref.kind, currentToken)
            val tracks = withContext(kotlinx.coroutines.Dispatchers.Default) { raw.map(::toDomainTrack) }
            if (tracks.isNotEmpty()) {
                sections.add(com.luc4n3x.levyra.domain.HomeSection(ref.title, tracks))
            }
        }
        return sections
    }

    override suspend fun radioTracks(seedTrackId: String?, excludeIds: Set<String>, limit: Int): List<Track> {
        val currentToken = token ?: return emptyList()
        // «Моя волна»: the personal wave; the seed id advances the rotor queue for continuation.
        val raw = client.rotorStationTracks(MY_WAVE_STATION, currentToken, queue = seedTrackId)
        return withContext(kotlinx.coroutines.Dispatchers.Default) {
            raw.asSequence()
                .map(::toDomainTrack)
                .filter { it.id !in excludeIds }
                .take(limit.coerceAtLeast(0))
                .toList()
        }
    }

    private fun toDomainTrack(yt: YandexTrack): Track = Track(
        id = "yandex:${yt.id}",
        title = yt.title,
        artist = yt.artist,
        album = yt.album,
        durationMs = yt.durationMs,
        streamUrl = "",
        videoUrl = "",
        thumbnailUrl = yt.coverUrl,
        largeThumbnailUrl = yt.coverUrl,
        source = PROVIDER_ID,
        moodTags = emptySet(),
        energy = 50,
        vocal = 50,
        replayScore = 0,
        cacheScore = 0,
        accentStart = 0,
        accentEnd = 0
    )

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
        private const val MY_WAVE_STATION = "user:onyourwave"
    }
}
