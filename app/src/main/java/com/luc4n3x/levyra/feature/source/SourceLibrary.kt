package com.luc4n3x.levyra.feature.source

import com.luc4n3x.levyra.domain.Playlist
import com.luc4n3x.levyra.domain.Track

/**
 * A source's user library, expressed in the app's own domain types so it flows unchanged through
 * the existing Library screen and every customization that reads `favorites`/`playlists`. See the
 * Yandex source integration design, §6 (library is per active source; local files stay shared).
 *
 * [favorites] are the source's liked songs ("Мне нравится" for Yandex); [playlists] are the
 * user's own and saved playlists.
 */
data class SourceLibrary(
    val favorites: List<Track> = emptyList(),
    val playlists: List<Playlist> = emptyList()
)

/**
 * Loads the signed-in user's [SourceLibrary] for a source. Kept separate from [MusicSource] so the
 * network-backed providers can implement it while the source facade stays a thin, testable wrapper.
 */
interface SourceLibraryProvider {
    suspend fun loadLibrary(): SourceLibrary
}
