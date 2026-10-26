package com.luc4n3x.levyra.feature.source

import com.luc4n3x.levyra.domain.HomeSection
import com.luc4n3x.levyra.domain.SearchResults
import com.luc4n3x.levyra.domain.Track
import com.luc4n3x.levyra.feature.providers.LevyraCatalogProvider
import com.luc4n3x.levyra.feature.providers.LevyraPlaybackProvider

/**
 * [MusicSource] for SoundCloud. Wraps [LevyraCatalogProvider] and [LevyraPlaybackProvider]
 * (implemented by [com.luc4n3x.levyra.data.soundcloud.SoundCloudAudioProvider]) to deliver
 * search, playback, curated home shelves and related-track radio.
 */
internal class SoundCloudMusicSource(
    private val catalog: LevyraCatalogProvider,
    private val playback: LevyraPlaybackProvider,
    private val libraryProvider: SourceLibraryProvider? = null,
    private val homeProvider: SourceHomeProvider? = null,
    private val radioProvider: SourceRadioProvider? = null
) : MusicSource {

    override val id: SourceId = SourceId.SOUNDCLOUD

    override fun capabilities(): Set<SourceCapability> = buildSet {
        add(SourceCapability.EXPLORE)
        if (libraryProvider != null) add(SourceCapability.LIBRARY)
        if (homeProvider != null) add(SourceCapability.HOME)
        if (radioProvider != null) add(SourceCapability.RADIO)
    }

    override fun owns(track: Track): Boolean =
        track.source == SOUNDCLOUD_SOURCE || track.id.startsWith(SOUNDCLOUD_ID_PREFIX)

    override suspend fun search(query: String, languageCode: String): SearchResults =
        catalog.searchEverything(query, languageCode)

    override suspend fun resolve(track: Track, videoMode: Boolean): Track =
        playback.resolve(track, videoMode)

    override suspend fun resolveForOffline(track: Track): Track =
        playback.resolveForOffline(track)

    override suspend fun library(): SourceLibrary =
        libraryProvider?.loadLibrary() ?: SourceLibrary()

    override suspend fun homeFeed(): List<HomeSection> =
        homeProvider?.loadHomeSections() ?: emptyList()

    override suspend fun radio(seedTrackId: String?, excludeIds: Set<String>, limit: Int): List<Track> =
        radioProvider?.radioTracks(seedTrackId, excludeIds, limit) ?: emptyList()

    private companion object {
        const val SOUNDCLOUD_SOURCE = "soundcloud"
        const val SOUNDCLOUD_ID_PREFIX = "soundcloud:"
    }
}
