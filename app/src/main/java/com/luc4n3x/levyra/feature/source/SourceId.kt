package com.luc4n3x.levyra.feature.source

/**
 * Identifies a top-level music source the user can switch between (see the Yandex
 * source integration design, §2). Persisted by [name] in preferences and parsed back
 * with [from], which tolerates unknown or missing values by defaulting to [YOUTUBE] —
 * the same contract [com.luc4n3x.levyra.domain.LevyraSkin] and the other experience
 * enums use.
 */
enum class SourceId(val label: String) {
    YOUTUBE("YouTube Music"),
    YANDEX("Яндекс Музыка"),
    SOUNDCLOUD("SoundCloud");

    companion object {
        /** Case-insensitive name lookup; null, blank or unrecognized input resolves to [YOUTUBE]. */
        fun from(value: String?): SourceId =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: YOUTUBE
    }
}
