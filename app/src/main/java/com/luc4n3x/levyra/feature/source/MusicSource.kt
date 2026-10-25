package com.luc4n3x.levyra.feature.source

import com.luc4n3x.levyra.domain.SearchResults
import com.luc4n3x.levyra.domain.Track

/**
 * A capability a [MusicSource] may or may not offer. The UI reads these to hide sections or
 * controls a source cannot serve (e.g. Yandex without Plus drops [RADIO]) without changing any
 * layout — see the Yandex source integration design, §7 degradation matrix.
 */
enum class SourceCapability { HOME, EXPLORE, CHARTS, RADIO, LIBRARY, LYRICS, LOGIN, VIDEO }

/**
 * Abstraction over a top-level music source (YouTube Music, Yandex Music, …). See the design doc
 * (§2). This M1 slice exposes only what the foundation wires: full-text [search] (per active
 * source) plus the per-origin playback primitives [resolve]/[resolveForOffline] selected by
 * [owns]. Browse surfaces (home, explore, radio, library, lyrics) are added to this interface in
 * their own milestones (M2/M4/M5), so the interface never carries a method no code calls yet.
 */
interface MusicSource {
    /** Stable id used by the source switch and persistence. */
    val id: SourceId

    /** What this source can currently do; may change with login/subscription/health. */
    fun capabilities(): Set<SourceCapability>

    /**
     * True when [track] originated from this source, so its playback, offline download and
     * lyrics must be handled here regardless of which source is currently active.
     */
    fun owns(track: Track): Boolean

    /** Full-text search for the active source. */
    suspend fun search(query: String, languageCode: String): SearchResults

    /** Resolve a playable [Track] (stream url filled) for playback. */
    suspend fun resolve(track: Track, videoMode: Boolean): Track

    /** Resolve a [Track] suitable for an offline download copy. */
    suspend fun resolveForOffline(track: Track): Track

    /**
     * The signed-in user's library for this source (liked songs + playlists), in domain types.
     * Defaults to empty so a source without a server-backed library (or a signed-out one) needs no
     * override; sources that offer [SourceCapability.LIBRARY] return real data here (M3).
     */
    suspend fun library(): SourceLibrary = SourceLibrary()

    /**
     * The source's personalized home feed as [com.luc4n3x.levyra.domain.HomeSection] shelves.
     * Defaults to empty; sources that offer [SourceCapability.HOME] return real shelves here (M4).
     */
    suspend fun homeFeed(): List<com.luc4n3x.levyra.domain.HomeSection> = emptyList()

    /**
     * The next batch of endless-radio/autoplay tracks (Yandex «Моя волна», M5). Defaults to empty;
     * sources that offer [SourceCapability.RADIO] return real tracks here.
     */
    suspend fun radio(seedTrackId: String?, excludeIds: Set<String>, limit: Int): List<Track> = emptyList()
}
