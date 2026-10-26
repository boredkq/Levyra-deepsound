package com.luc4n3x.levyra.feature.source

import com.luc4n3x.levyra.domain.SearchResults
import com.luc4n3x.levyra.domain.Track
import com.luc4n3x.levyra.feature.providers.LevyraCatalogProvider
import com.luc4n3x.levyra.feature.providers.LevyraPlaybackProvider

/**
 * [MusicSource] that preserves today's YouTube Music behavior by delegating to the existing
 * catalog and playback providers. This is the wrapper step of M1: no behavior change, errors and
 * empty results pass straight through from the underlying providers.
 */
internal class YoutubeMusicSource(
    private val catalog: LevyraCatalogProvider,
    private val playback: LevyraPlaybackProvider
) : MusicSource {

    override val id: SourceId = SourceId.YOUTUBE

    override fun capabilities(): Set<SourceCapability> = SourceCapability.entries.toSet()

    override fun owns(track: Track): Boolean =
        track.source != YANDEX_SOURCE && !track.id.startsWith(YANDEX_ID_PREFIX) &&
        track.source != SOUNDCLOUD_SOURCE && !track.id.startsWith(SOUNDCLOUD_ID_PREFIX)

    override suspend fun search(query: String, languageCode: String): SearchResults =
        catalog.searchEverything(query, languageCode)

    override suspend fun resolve(track: Track, videoMode: Boolean): Track =
        playback.resolve(track, videoMode)

    override suspend fun resolveForOffline(track: Track): Track =
        playback.resolveForOffline(track)

    private companion object {
        const val YANDEX_SOURCE = "yandex_music"
        const val YANDEX_ID_PREFIX = "yandex:"
        const val SOUNDCLOUD_SOURCE = "soundcloud"
        const val SOUNDCLOUD_ID_PREFIX = "soundcloud:"
    }
}
