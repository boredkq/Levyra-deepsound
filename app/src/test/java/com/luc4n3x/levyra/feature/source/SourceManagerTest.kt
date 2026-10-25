package com.luc4n3x.levyra.feature.source

import com.luc4n3x.levyra.domain.SearchResults
import com.luc4n3x.levyra.domain.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceManagerTest {

    @Test
    fun default_isYoutube() {
        val m = SourceManager(listOf(fakeSource(SourceId.YOUTUBE), fakeSource(SourceId.YANDEX)))
        assertEquals(SourceId.YOUTUBE, m.activeId.value)
        assertEquals(SourceId.YOUTUBE, m.active().id)
    }

    @Test
    fun switch_emitsNewActiveAndPersistsOnce() {
        val persisted = mutableListOf<SourceId>()
        val m = SourceManager(
            listOf(fakeSource(SourceId.YOUTUBE), fakeSource(SourceId.YANDEX)),
            onPersist = { persisted += it }
        )

        m.switch(SourceId.YANDEX)
        m.switch(SourceId.YANDEX) // repeat is a no-op

        assertEquals(SourceId.YANDEX, m.activeId.value)
        assertEquals(SourceId.YANDEX, m.active().id)
        assertEquals(listOf(SourceId.YANDEX), persisted)
    }

    @Test
    fun active_fallsBackToYoutubeWhenInitialUnregistered() {
        val m = SourceManager(listOf(fakeSource(SourceId.YOUTUBE)), initial = SourceId.YANDEX)
        assertEquals(SourceId.YOUTUBE, m.activeId.value)
        assertEquals(SourceId.YOUTUBE, m.active().id)
    }

    @Test
    fun switch_ignoresUnregisteredId() {
        val persisted = mutableListOf<SourceId>()
        val m = SourceManager(listOf(fakeSource(SourceId.YOUTUBE)), onPersist = { persisted += it })

        m.switch(SourceId.YANDEX)

        assertEquals(SourceId.YOUTUBE, m.activeId.value)
        assertTrue(persisted.isEmpty())
    }

    @Test
    fun active_returnsTheRegisteredInstance() {
        val youtube = fakeSource(SourceId.YOUTUBE)
        val m = SourceManager(listOf(youtube))
        assertSame(youtube, m.active())
    }

    private fun fakeSource(sid: SourceId): MusicSource = object : MusicSource {
        override val id = sid
        override fun capabilities(): Set<SourceCapability> = emptySet()
        override fun owns(track: Track): Boolean = false
        override suspend fun search(query: String, languageCode: String): SearchResults =
            SearchResults(topTrack = null, songs = emptyList())
        override suspend fun resolve(track: Track, videoMode: Boolean): Track = track
        override suspend fun resolveForOffline(track: Track): Track = track
    }
}
