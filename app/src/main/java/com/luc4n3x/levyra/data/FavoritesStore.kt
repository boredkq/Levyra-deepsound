package com.luc4n3x.levyra.data

import android.content.Context
import com.luc4n3x.levyra.data.local.FavoriteTrackEntity
import com.luc4n3x.levyra.data.local.LevyraDatabase
import com.luc4n3x.levyra.data.local.sourceId
import com.luc4n3x.levyra.data.local.toFavoriteTrackEntity
import com.luc4n3x.levyra.data.local.toTrack
import com.luc4n3x.levyra.domain.Track
import com.luc4n3x.levyra.feature.source.SourceId
import com.luc4n3x.levyra.feature.source.sourceId
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import timber.log.Timber

internal val favoritesStoreMutationMutex = Mutex()
private val favoritesTimestampGeneration = AtomicLong(0L)

internal fun invalidateFavoriteTimestampSnapshots() {
    favoritesTimestampGeneration.incrementAndGet()
}

private data class FavoriteTimestampSnapshot(
    val generation: Long,
    val values: Map<String, Long>
)

class FavoritesStore(context: Context) {
    private val appContext = context.applicationContext
    private val dao = LevyraDatabase.get(appContext).favoriteTracksDao()
    private val legacyPrefs = appContext.getSharedPreferences("levyra_favorites", Context.MODE_PRIVATE)
    private var timestampSnapshot: FavoriteTimestampSnapshot? = null

    fun load(source: SourceId = SourceId.YOUTUBE): List<Track> = runBlocking(Dispatchers.IO) {
        favoritesStoreMutationMutex.withLock { loadInternal(source) }
    }

    fun loadTimestamps(source: SourceId = SourceId.YOUTUBE): Map<String, Long> = runBlocking(Dispatchers.IO) {
        favoritesStoreMutationMutex.withLock { loadTimestampsInternal(source) }
    }

    suspend fun loadTimestampsSuspending(source: SourceId = SourceId.YOUTUBE): Map<String, Long> = withContext(Dispatchers.IO) {
        favoritesStoreMutationMutex.withLock { loadTimestampsInternal(source) }
    }

    fun save(tracks: List<Track>) {
        runBlocking(Dispatchers.IO) {
            favoritesStoreMutationMutex.withLock {
                val source = tracks.firstOrNull()?.sourceId() ?: SourceId.YOUTUBE
                saveForSourceInternal(source, tracks)
            }
        }
    }

    suspend fun saveSuspending(tracks: List<Track>) = withContext(Dispatchers.IO) {
        favoritesStoreMutationMutex.withLock {
            val source = tracks.firstOrNull()?.sourceId() ?: SourceId.YOUTUBE
            saveForSourceInternal(source, tracks)
        }
    }

    fun saveForSource(source: SourceId, tracks: List<Track>) {
        runBlocking(Dispatchers.IO) {
            favoritesStoreMutationMutex.withLock { saveForSourceInternal(source, tracks) }
        }
    }

    suspend fun saveForSourceSuspending(source: SourceId, tracks: List<Track>) = withContext(Dispatchers.IO) {
        favoritesStoreMutationMutex.withLock { saveForSourceInternal(source, tracks) }
    }

    suspend fun addFavorites(tracks: List<Track>): List<Track> = addMissingFavorites(tracks).favorites

    internal suspend fun addMissingFavorites(tracks: List<Track>): FavoriteAddition = withContext(Dispatchers.IO) {
        favoritesStoreMutationMutex.withLock {
            if (tracks.isEmpty()) {
                val current = loadInternal(failOnReadError = true)
                return@withLock FavoriteAddition(current, emptyList())
            }
            val targetSource = tracks.first().sourceId()
            val current = loadInternal(targetSource, failOnReadError = true)
            val added = missingFavoriteTracks(current, tracks)
            val updated = added + current
            if (added.isNotEmpty()) saveForSourceInternal(targetSource, updated)
            FavoriteAddition(updated, added)
        }
    }

    suspend fun toggleFavorite(track: Track): List<Track> = withContext(Dispatchers.IO) {
        favoritesStoreMutationMutex.withLock {
            val targetSource = track.sourceId()
            val current = loadInternal(targetSource)
            val targetKey = favoriteTrackKey(track)
            val existingIndex = current.indexOfFirst { favorite ->
                favoriteTrackKey(favorite) == targetKey
            }
            val normalizedTrack = if (track.source.isBlank()) {
                when (targetSource) {
                    SourceId.YANDEX -> track.copy(source = "yandex_music")
                    SourceId.SOUNDCLOUD -> track.copy(source = "soundcloud")
                    SourceId.YOUTUBE -> track.copy(source = "youtube")
                }
            } else track

            val updated = if (existingIndex >= 0) {
                current.filterIndexed { index, _ -> index != existingIndex }
            } else {
                listOf(normalizedTrack) + current
            }
            saveForSourceInternal(targetSource, updated)
            updated
        }
    }

    suspend fun setFavorite(track: Track, favorite: Boolean): List<Track> = withContext(Dispatchers.IO) {
        favoritesStoreMutationMutex.withLock {
            val targetSource = track.sourceId()
            val current = loadInternal(targetSource, failOnReadError = true)
            val normalizedTrack = if (track.source.isBlank()) {
                when (targetSource) {
                    SourceId.YANDEX -> track.copy(source = "yandex_music")
                    SourceId.SOUNDCLOUD -> track.copy(source = "soundcloud")
                    SourceId.YOUTUBE -> track.copy(source = "youtube")
                }
            } else track
            val updated = favoriteTracksWithMembership(current, normalizedTrack, favorite)
            if (updated != current) saveForSourceInternal(targetSource, updated)
            updated
        }
    }

    internal fun observeMembership(source: SourceId? = null): Flow<FavoriteMembership> = dao.observeIdentities()
        .onStart { favoritesStoreMutationMutex.withLock { loadInternal(source ?: SourceId.YOUTUBE) } }
        .map { rows ->
            val matching = if (source != null) {
                rows.filter { row ->
                    when (source) {
                        SourceId.YANDEX -> row.id.startsWith("yandex:") || row.source.equals("yandex_music", ignoreCase = true)
                        SourceId.SOUNDCLOUD -> row.id.startsWith("soundcloud:") || row.source.equals("soundcloud", ignoreCase = true)
                        SourceId.YOUTUBE -> !row.id.startsWith("yandex:") && !row.id.startsWith("soundcloud:") &&
                            !row.source.equals("yandex_music", ignoreCase = true) && !row.source.equals("soundcloud", ignoreCase = true)
                    }
                }
            } else {
                rows
            }
            FavoriteMembership(matching.mapTo(hashSetOf()) { favoriteTrackKey(it.id, it.artist, it.title) })
        }
        .flowOn(Dispatchers.IO)

    suspend fun toggleFavorites(tracks: List<Track>, fallbackSource: SourceId = SourceId.YOUTUBE): List<Track> = withContext(Dispatchers.IO) {
        favoritesStoreMutationMutex.withLock {
            if (tracks.isEmpty()) return@withContext emptyList()
            val targetSource = tracks.firstOrNull()?.sourceId() ?: fallbackSource
            val current = loadInternal(targetSource)
            val updated = toggleFavoriteTracks(current, tracks)
            if (updated != current) saveForSourceInternal(targetSource, updated)
            updated
        }
    }

    private suspend fun loadInternal(source: SourceId = SourceId.YOUTUBE, failOnReadError: Boolean = false): List<Track> {
        val allEntities = runCatching { dao.all() }
            .onFailure { Timber.w(it, "Favorite tracks load failed") }
            .getOrElse { error -> if (failOnReadError) throw error else return emptyList() }
        rememberTimestampSnapshot(allEntities.associate { it.id to it.createdAt })
        if (allEntities.isNotEmpty()) {
            completeLegacyMigration()
            return allEntities.filter { it.sourceId() == source }.map { it.toTrack() }
        }
        if (legacyPrefs.getBoolean(MIGRATION_COMPLETE_KEY, false)) return emptyList()

        val legacy = loadLegacyForMigration() ?: return emptyList()
        saveForSourceInternal(SourceId.YOUTUBE, legacy, allEntities)
        completeLegacyMigration()
        return if (source == SourceId.YOUTUBE) legacy else emptyList()
    }

    private suspend fun loadTimestampsInternal(source: SourceId = SourceId.YOUTUBE): Map<String, Long> {
        return runCatching {
            dao.all().filter { it.sourceId() == source }.associate { it.id to it.createdAt }
        }.getOrDefault(emptyMap())
    }

    private fun currentTimestampSnapshot(): Map<String, Long>? {
        val generation = favoritesTimestampGeneration.get()
        return timestampSnapshot
            ?.takeIf { it.generation == generation }
            ?.values
    }

    private fun rememberTimestampSnapshot(values: Map<String, Long>) {
        timestampSnapshot = FavoriteTimestampSnapshot(
            generation = favoritesTimestampGeneration.get(),
            values = values
        )
    }

    private suspend fun saveForSourceInternal(
        source: SourceId,
        tracks: List<Track>,
        currentEntities: List<FavoriteTrackEntity>? = null
    ) {
        try {
            val allEntities = currentEntities ?: dao.all()
            val otherEntities = allEntities.filter { it.sourceId() != source }
            val existingTimestamps = allEntities.associate { it.id to it.createdAt }
            val now = System.currentTimeMillis()
            val newEntities = tracks.mapIndexed { index, track ->
                val timestamp = existingTimestamps[track.id]?.takeIf { it > 0L } ?: (now - index)
                val normalizedTrack = when {
                    track.source.isNotBlank() -> track
                    source == SourceId.YANDEX -> track.copy(source = "yandex_music")
                    source == SourceId.SOUNDCLOUD -> track.copy(source = "soundcloud")
                    else -> track.copy(source = "youtube")
                }
                normalizedTrack.toFavoriteTrackEntity(timestamp)
            }
            val combined = otherEntities + newEntities
            dao.replaceAll(combined)
            val generation = favoritesTimestampGeneration.incrementAndGet()
            timestampSnapshot = FavoriteTimestampSnapshot(
                generation = generation,
                values = combined.associate { it.id to it.createdAt }
            )
            completeLegacyMigration()
        } catch (error: Throwable) {
            Timber.w(error, "Favorite tracks save failed for source $source")
            throw error
        }
    }

    private fun loadLegacyForMigration(): List<Track>? {
        val raw = legacyPrefs.getString(KEY, null) ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let(TrackJson::fromJson)
            }
        }.onFailure { Timber.w(it, "Legacy favorites migration failed") }
            .getOrNull()
    }

    private fun completeLegacyMigration() {
        if (legacyPrefs.getBoolean(MIGRATION_COMPLETE_KEY, false) && !legacyPrefs.contains(KEY)) return
        val committed = legacyPrefs.edit()
            .remove(KEY)
            .putBoolean(MIGRATION_COMPLETE_KEY, true)
            .commit()
        check(committed) { "Unable to complete legacy favorites migration" }
    }

    private companion object {
        const val KEY = "liked_tracks"
        const val MIGRATION_COMPLETE_KEY = "liked_tracks_migrated_to_room"
    }
}

internal fun toggleFavoriteTracks(current: List<Track>, targets: List<Track>): List<Track> {
    val targetsByKey = LinkedHashMap<FavoriteTrackKey, Track>()
    targets.forEach { track -> targetsByKey.putIfAbsent(favoriteTrackKey(track), track) }
    if (targetsByKey.isEmpty()) return current

    val currentKeys = current.mapTo(hashSetOf(), ::favoriteTrackKey)
    val shouldAdd = targetsByKey.keys.any { it !in currentKeys }
    return if (shouldAdd) {
        targetsByKey.values + current.filterNot { favoriteTrackKey(it) in targetsByKey }
    } else {
        current.filterNot { favoriteTrackKey(it) in targetsByKey }
    }
}

internal data class FavoriteAddition(val favorites: List<Track>, val added: List<Track>)

internal fun missingFavoriteTracks(current: List<Track>, targets: List<Track>): List<Track> {
    val existingKeys = current.mapTo(hashSetOf(), ::favoriteTrackKey)
    return targets.filter { it.id.isNotBlank() && existingKeys.add(favoriteTrackKey(it)) }
}

internal fun areAllFavoriteTracks(current: List<Track>, targets: List<Track>): Boolean {
    if (targets.isEmpty()) return false
    val currentKeys = current.mapTo(hashSetOf(), ::favoriteTrackKey)
    return targets.all { favoriteTrackKey(it) in currentKeys }
}

internal fun favoriteTracksWithMembership(current: List<Track>, track: Track, favorite: Boolean): List<Track> {
    val key = favoriteTrackKey(track)
    val present = current.any { favoriteTrackKey(it) == key }
    return when {
        present == favorite -> current
        favorite -> listOf(track) + current
        else -> current.filterNot { favoriteTrackKey(it) == key }
    }
}

internal class FavoriteMembership(private val keys: Set<FavoriteTrackKey>) {
    fun contains(track: Track): Boolean = favoriteTrackKey(track) in keys

    fun sameTracksAs(tracks: List<Track>): Boolean = tracks.mapTo(hashSetOf(), ::favoriteTrackKey) == keys

    companion object {
        internal fun of(tracks: List<Track>): FavoriteMembership =
            FavoriteMembership(tracks.mapTo(hashSetOf(), ::favoriteTrackKey))
    }
}

internal data class FavoriteTrackKey(
    val id: String?,
    val artist: String,
    val title: String
)

private fun favoriteTrackKey(track: Track): FavoriteTrackKey = favoriteTrackKey(track.id, track.artist, track.title)

private fun favoriteTrackKey(id: String, artist: String, title: String): FavoriteTrackKey {
    val normalizedId = id.trim().lowercase(Locale.ROOT)
    return if (normalizedId.isNotEmpty()) {
        FavoriteTrackKey(id = normalizedId, artist = "", title = "")
    } else {
        FavoriteTrackKey(
            id = null,
            artist = artist.trim().lowercase(Locale.ROOT),
            title = title.trim().lowercase(Locale.ROOT)
        )
    }
}
