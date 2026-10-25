package com.luc4n3x.levyra.feature.source

import com.luc4n3x.levyra.domain.Track

/**
 * Supplies an endless "radio" / autoplay stream for a source — Yandex's «Моя волна»
 * (`user:onyourwave`) rotor station (M5). The app's existing continuous-radio engine calls this to
 * refill the queue, so the feature rides the current player UI with no new screen.
 */
interface SourceRadioProvider {
    /**
     * The next batch of radio tracks. [seedTrackId] is the source-native id of the last/seed track
     * (used to advance the rotor queue; may be null for a cold start). [excludeIds] are domain ids
     * already queued, to avoid repeats. Returns at most [limit] tracks.
     */
    suspend fun radioTracks(seedTrackId: String?, excludeIds: Set<String>, limit: Int): List<Track>
}
