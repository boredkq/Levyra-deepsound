package com.luc4n3x.levyra.data.soundcloud

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
import com.luc4n3x.levyra.data.FavoritesStore
import com.luc4n3x.levyra.data.PlaylistStore
import com.luc4n3x.levyra.domain.AlbumDetail
import com.luc4n3x.levyra.domain.AlbumHit
import com.luc4n3x.levyra.domain.HomeSection
import com.luc4n3x.levyra.domain.SearchResults
import com.luc4n3x.levyra.domain.Track
import com.luc4n3x.levyra.feature.providers.LevyraCatalogProvider
import com.luc4n3x.levyra.feature.providers.LevyraPlaybackProvider
import com.luc4n3x.levyra.feature.providers.LevyraProviderMissException
import com.luc4n3x.levyra.feature.source.SourceHomeProvider
import com.luc4n3x.levyra.feature.source.SourceId
import com.luc4n3x.levyra.feature.source.SourceLibrary
import com.luc4n3x.levyra.feature.source.SourceLibraryProvider
import com.luc4n3x.levyra.feature.source.SourceRadioProvider
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

internal class SoundCloudAudioProvider(
    private val client: SoundCloudClient = SoundCloudClient(),
    private val sessionStore: SoundCloudSessionStore? = null,
    private val favoritesStore: FavoritesStore? = null,
    private val playlistStore: PlaylistStore? = null
) : HighQualityAudioProvider,
    LevyraCatalogProvider,
    LevyraPlaybackProvider,
    SourceLibraryProvider,
    SourceHomeProvider,
    SourceRadioProvider {

    override val id: String = PROVIDER_ID
    override val displayName: String = "SoundCloud"
    override val priority: Int = 15

    override val sourceId: SourceId = SourceId.SOUNDCLOUD

    override fun owns(track: Track): Boolean =
        track.source == PROVIDER_ID || track.id.startsWith("soundcloud:")

    // ── HighQualityAudioProvider ──

    override suspend fun search(query: String, page: Int): ProviderSearchOutcome = try {
        val tracks = client.search(query, limit = 30)
        val candidates = tracks.map { track ->
            AlternativeTrackCandidate(
                providerId = id,
                providerTrackId = track.id,
                title = track.title,
                primaryArtists = listOf(track.artist),
                featuredArtists = emptyList(),
                album = "SoundCloud",
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
        val scTrack = client.getTrack(providerTrackId)
        if (scTrack != null) {
            ProviderLookupOutcome.Found(
                AlternativeTrackCandidate(
                    providerId = id,
                    providerTrackId = providerTrackId,
                    title = scTrack.title,
                    primaryArtists = listOf(scTrack.artist),
                    featuredArtists = emptyList(),
                    album = "SoundCloud",
                    durationSeconds = (scTrack.durationMs / 1000).toInt(),
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
        val scTrack = client.getTrack(trackId)
        val streamUrl = scTrack?.let { client.resolveStreamUrl(it) }
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
                    estimatedKbps = 160,
                    expiresAtMs = System.currentTimeMillis() + STREAM_TTL_MS
                )
            )
        } else {
            ProviderStreamOutcome.Unavailable(listOf(StreamRejection.NO_MEDIA))
        }
    } catch (e: Exception) {
        ProviderStreamOutcome.Failed(ProviderFailure.NETWORK)
    }

    // ── LevyraCatalogProvider ──

    override suspend fun searchEverything(query: String, languageCode: String): SearchResults {
        val scTracks = client.search(query, limit = 40)
        val songs = scTracks.map(::toDomainTrack)
        return SearchResults(topTrack = songs.firstOrNull(), songs = songs)
    }

    override suspend fun albumDetail(album: AlbumHit, languageCode: String): AlbumDetail? = null

    override suspend fun playlist(playlistId: String, languageCode: String, limit: Int): YoutubeMusicPlaylistDetail? = null

    // ── LevyraPlaybackProvider & Stream Cache ──

    private val streamCache = ConcurrentHashMap<String, CachedStream>()

    override suspend fun resolve(track: Track, videoMode: Boolean): Track {
        if (!owns(track)) {
            throw LevyraProviderMissException("Track is not from SoundCloud")
        }
        val rawId = track.id.removePrefix("soundcloud:")
        val now = System.currentTimeMillis()
        streamCache[rawId]?.takeIf { it.expiresAtMs > now }?.let { cached ->
            return track.copy(streamUrl = cached.url)
        }

        val scTrack = client.getTrack(rawId)
            ?: throw LevyraProviderMissException("SoundCloud track metadata missing")
        val url = client.resolveStreamUrl(scTrack)
            ?: throw LevyraProviderMissException("Failed to resolve SoundCloud stream URL")

        if (streamCache.size > STREAM_CACHE_LIMIT) streamCache.clear()
        streamCache[rawId] = CachedStream(url, now + STREAM_CACHE_TTL_MS)
        return track.copy(streamUrl = url)
    }

    override suspend fun resolveForOffline(track: Track): Track {
        if (!owns(track)) {
            throw LevyraProviderMissException("Track is not from SoundCloud")
        }
        val rawId = track.id.removePrefix("soundcloud:")
        val scTrack = client.getTrack(rawId)
            ?: throw LevyraProviderMissException("SoundCloud track metadata missing")
        val url = client.resolveStreamUrl(scTrack)
            ?: throw LevyraProviderMissException("Failed to resolve SoundCloud stream URL")
        return track.copy(streamUrl = url)
    }

    // ── SourceLibraryProvider ──

    override suspend fun loadLibrary(): SourceLibrary {
        val favorites = favoritesStore?.load(SourceId.SOUNDCLOUD) ?: emptyList()
        val playlists = playlistStore?.loadAll(SourceId.SOUNDCLOUD) ?: emptyList()
        return SourceLibrary(favorites = favorites, playlists = playlists)
    }

    suspend fun saveTrack(track: Track) {
        if (!owns(track)) return
        val normalized = if (track.source.isBlank()) track.copy(source = PROVIDER_ID) else track
        favoritesStore?.saveForSource(SourceId.SOUNDCLOUD, listOf(normalized))
    }

    // ── SourceHomeProvider ──

    override suspend fun loadHomeSections(): List<HomeSection> = coroutineScope {
        val popularDeferred = async { client.search("Russian Hits", limit = 20) }
        val electronicDeferred = async { client.search("Electronic Mix", limit = 20) }
        val chillDeferred = async { client.search("Ambient Chill", limit = 20) }

        val sections = mutableListOf<HomeSection>()

        val popular = popularDeferred.await().map(::toDomainTrack)
        if (popular.isNotEmpty()) {
            sections.add(HomeSection("Популярные треки", popular))
        }

        val electronic = electronicDeferred.await().map(::toDomainTrack)
        if (electronic.isNotEmpty()) {
            sections.add(HomeSection("Электронная музыка", electronic))
        }

        val chill = chillDeferred.await().map(::toDomainTrack)
        if (chill.isNotEmpty()) {
            sections.add(HomeSection("Релакс и чилл", chill))
        }

        sections
    }

    // ── SourceRadioProvider ──

    override suspend fun radioTracks(seedTrackId: String?, excludeIds: Set<String>, limit: Int): List<Track> {
        val rawTracks = if (!seedTrackId.isNullOrBlank()) {
            val related = client.getRelatedTracks(seedTrackId, limit = limit + 10)
            if (related.isNotEmpty()) related else client.search("Trending Music", limit = limit + 10)
        } else {
            client.search("Trending Music", limit = limit + 10)
        }

        return withContext(Dispatchers.Default) {
            rawTracks.asSequence()
                .map(::toDomainTrack)
                .filter { it.id !in excludeIds }
                .take(limit.coerceAtLeast(0))
                .toList()
        }
    }

    private fun toDomainTrack(sc: SoundCloudTrack): Track = Track(
        id = "soundcloud:${sc.id}",
        title = sc.title,
        artist = sc.artist,
        album = "SoundCloud",
        durationMs = sc.durationMs,
        streamUrl = "",
        videoUrl = "",
        thumbnailUrl = sc.coverUrl.replace("-t500x500", "-large"),
        largeThumbnailUrl = sc.coverUrl,
        source = PROVIDER_ID,
        moodTags = emptySet(),
        energy = 50,
        vocal = 50,
        replayScore = 0,
        cacheScore = 0,
        accentStart = 0,
        accentEnd = 0
    )

    private class CachedStream(val url: String, val expiresAtMs: Long)

    companion object {
        const val PROVIDER_ID = "soundcloud"
        private const val STREAM_TTL_MS = 2 * 60 * 60 * 1000L
        private const val STREAM_CACHE_TTL_MS = 60 * 60 * 1000L
        private const val STREAM_CACHE_LIMIT = 256
    }
}
