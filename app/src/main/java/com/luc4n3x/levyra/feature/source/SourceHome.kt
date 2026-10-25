package com.luc4n3x.levyra.feature.source

import com.luc4n3x.levyra.domain.HomeSection

/**
 * Loads a source's personalized home feed as the app's own [HomeSection] shelves, so Yandex's
 * landing (personal playlists, chart, new playlists) flows through the existing Home screen with no
 * layout change (M4). Kept separate from [MusicSource] like [SourceLibraryProvider] so the
 * network-backed provider implements it while the facade stays a thin, testable wrapper.
 */
interface SourceHomeProvider {
    suspend fun loadHomeSections(): List<HomeSection>
}
