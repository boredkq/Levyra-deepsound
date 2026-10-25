package com.luc4n3x.levyra.feature.source

import org.junit.Assert.assertEquals
import org.junit.Test

class SourceIdTest {

    @Test
    fun from_knownName_returnsEnum() {
        assertEquals(SourceId.YANDEX, SourceId.from("YANDEX"))
        assertEquals(SourceId.YANDEX, SourceId.from("yandex"))
        assertEquals(SourceId.YOUTUBE, SourceId.from("YOUTUBE"))
    }

    @Test
    fun from_unknownOrNull_defaultsToYoutube() {
        assertEquals(SourceId.YOUTUBE, SourceId.from(null))
        assertEquals(SourceId.YOUTUBE, SourceId.from(""))
        assertEquals(SourceId.YOUTUBE, SourceId.from("spotify"))
    }
}
