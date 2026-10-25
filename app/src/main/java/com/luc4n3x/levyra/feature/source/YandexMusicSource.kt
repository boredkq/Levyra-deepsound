package com.luc4n3x.levyra.feature.source

import com.luc4n3x.levyra.domain.SearchResults
import com.luc4n3x.levyra.domain.Track
import com.luc4n3x.levyra.feature.providers.LevyraCatalogProvider
import com.luc4n3x.levyra.feature.providers.LevyraPlaybackProvider

/**
 * [MusicSource] for Yandex Music. It wraps the existing catalog/playback provider
 * (`data.yandex.YandexMusicAudioProvider`, which already implements both interfaces), so this
 * class carries no network code of its own and stays unit-testable with plain fakes.
 *
 * M2 wires only what is verifiable without the live Yandex API: full-text [search] and the
 * per-origin playback primitives ([resolve]/[resolveForOffline]) routed by [owns]. Browse
 * surfaces (home, explore, radio, library, lyrics) and their Yandex endpoints are added in later
 * milestones, together with the capabilities they unlock.
 */
internal class YandexMusicSource(
    private val catalog: LevyraCatalogProvider,
    private val playback: LevyraPlaybackProvider,
    private val libraryProvider: SourceLibraryProvider? = null,
    private val homeProvider: SourceHomeProvider? = null
) : MusicSource {

    override val id: SourceId = SourceId.YANDEX

    // M3 adds the user library, M4 the home feed; radio stays in its own milestone.
    override fun capabilities(): Set<SourceCapability> = buildSet {
        if (libraryProvider != null) add(SourceCapability.LIBRARY)
        if (homeProvider != null) add(SourceCapability.HOME)
    }

    override fun owns(track: Track): Boolean =
        track.source == YANDEX_SOURCE || track.id.startsWith(YANDEX_ID_PREFIX)

    override suspend fun search(query: String, languageCode: String): SearchResults =
        catalog.searchEverything(query, languageCode)

    override suspend fun resolve(track: Track, videoMode: Boolean): Track =
        playback.resolve(track, videoMode)

    override suspend fun resolveForOffline(track: Track): Track =
        playback.resolveForOffline(track)

    override suspend fun library(): SourceLibrary =
        libraryProvider?.loadLibrary() ?: SourceLibrary()

    override suspend fun homeFeed(): List<com.luc4n3x.levyra.domain.HomeSection> =
        homeProvider?.loadHomeSections() ?: emptyList()

    private companion object {
        const val YANDEX_SOURCE = "yandex_music"
        const val YANDEX_ID_PREFIX = "yandex:"
    }
}
