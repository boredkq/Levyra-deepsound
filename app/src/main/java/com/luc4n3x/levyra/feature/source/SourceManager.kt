package com.luc4n3x.levyra.feature.source

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the registry of available [MusicSource]s and the currently active one (design doc §3).
 *
 * The active id is persisted through [onPersist] — wired to `LevyraPreferences` at the app layer —
 * so this manager carries no Android/DataStore dependency and stays unit-testable. An initial or
 * requested id that is not registered falls back to [SourceId.YOUTUBE], which is always expected
 * to be present.
 */
class SourceManager(
    sources: List<MusicSource>,
    initial: SourceId = SourceId.YOUTUBE,
    private val onPersist: (SourceId) -> Unit = {}
) {
    private val registry: Map<SourceId, MusicSource> = sources.associateBy { it.id }

    private val _activeId = MutableStateFlow(resolveInitial(initial))

    /** Current active source id; Compose observes this to reload browse surfaces. */
    val activeId: StateFlow<SourceId> = _activeId.asStateFlow()

    /** The active [MusicSource], falling back to YouTube, then to the first registered source. */
    fun active(): MusicSource =
        registry[_activeId.value]
            ?: registry[SourceId.YOUTUBE]
            ?: registry.values.first()

    /** Switch the active source. No-op (and no persist) when [id] is unregistered or unchanged. */
    fun switch(id: SourceId) {
        if (!registry.containsKey(id) || _activeId.value == id) return
        _activeId.value = id
        onPersist(id)
    }

    private fun resolveInitial(initial: SourceId): SourceId =
        if (registry.containsKey(initial)) initial else SourceId.YOUTUBE
}
