package com.luc4n3x.levyra.data.yandex

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parsing of the Yandex library JSON shapes confirmed by the device-code recon (M3). These cover
 * the pure parse helpers so the network methods that call them stay trivial and untested-by-network.
 */
class YandexMusicClientParseTest {

    @Test
    fun likedTrackIds_readsLibraryTrackIds_inOrder() {
        val body = """
            {"result":{"library":{"uid":1,"revision":7,"tracks":[
              {"id":"111","albumId":"a1"},
              {"id":"222","albumId":"a2"},
              {"albumId":"a3"}
            ]}}}
        """.trimIndent()
        assertEquals(listOf("111", "222"), parseLikedTrackIds(body))
    }

    @Test
    fun likedTrackIds_missingLibrary_isEmpty() {
        assertTrue(parseLikedTrackIds("""{"result":{}}""").isEmpty())
    }

    @Test
    fun trackArray_mapsFullTrackObjects() {
        val body = """
            {"result":[
              {"id":"42","title":"Song","durationMs":180000,
               "artists":[{"name":"A"},{"name":"B"}],
               "albums":[{"title":"Alb","coverUri":"ya.net/%%"}]}
            ]}
        """.trimIndent()
        val tracks = parseTrackArray(body)
        assertEquals(1, tracks.size)
        val t = tracks.single()
        assertEquals("42", t.id)
        assertEquals("Song", t.title)
        assertEquals("A, B", t.artist)
        assertEquals("Alb", t.album)
        assertEquals(180000L, t.durationMs)
        assertEquals("https://ya.net/400x400", t.coverUrl)
    }

    @Test
    fun trackItem_withoutId_isSkipped() {
        val arr = parseTrackArray("""{"result":[{"title":"No id"}]}""")
        assertTrue(arr.isEmpty())
    }

    @Test
    fun trackItem_fallsBackForMissingFields() {
        val t = parseTrackItem(JSONObject("""{"id":"1","title":"X"}"""))!!
        assertEquals("Неизвестный исполнитель", t.artist)
        assertEquals("Без альбома", t.album)
        assertEquals("", t.coverUrl)
    }

    @Test
    fun playlists_readKindAsString_andCover() {
        val body = """
            {"result":[
              {"kind":1003,"title":"Мои","trackCount":12,"cover":{"uri":"c.net/%%"}},
              {"kind":5,"title":"","trackCount":0}
            ]}
        """.trimIndent()
        val pls = parsePlaylists(body)
        assertEquals(2, pls.size)
        assertEquals("1003", pls[0].kind)
        assertEquals("Мои", pls[0].title)
        assertEquals(12, pls[0].trackCount)
        assertEquals("https://c.net/400x400", pls[0].coverUrl)
        assertEquals("Плейлист", pls[1].title)
        assertEquals("", pls[1].coverUrl)
    }

    @Test
    fun playlistTracks_embeddedTracks_needNoHydration() {
        val body = """
            {"result":{"tracks":[
              {"id":"9","track":{"id":"9","title":"Emb","artists":[{"name":"A"}],"albums":[{"title":"Al"}]}}
            ]}}
        """.trimIndent()
        val parsed = parsePlaylistTracks(body)
        assertEquals(listOf("Emb"), parsed.tracks.map { it.title })
        assertTrue(parsed.missingIds.isEmpty())
    }

    @Test
    fun playlistTracks_idOnlyEntries_reportedAsMissing_inOrder() {
        val body = """
            {"result":{"tracks":[
              {"id":"9","track":{"id":"9","title":"Emb"}},
              {"id":"10"},
              {"trackId":"11"}
            ]}}
        """.trimIndent()
        val parsed = parsePlaylistTracks(body)
        assertEquals(listOf("Emb"), parsed.tracks.map { it.title })
        assertEquals(listOf("10", "11"), parsed.missingIds)
        assertEquals(3, parsed.ordered.size)
    }
}
