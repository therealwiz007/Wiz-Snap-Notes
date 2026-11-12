package com.wizlabs.snapnotes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTextTest {
    @Test fun termsAreLowercasedAndDeduped() {
        assertEquals(listOf("nozzle", "3", "leak"), SearchText.terms("  Nozzle 3, LEAK leak "))
    }

    @Test fun ftsQueryUsesPrefixesAndDropsPunctuation() {
        assertEquals("tank* 2* hp* cl*", SearchText.ftsQuery(SearchText.terms("Tank-2 \"hp.cl\"")))
        assertEquals("", SearchText.ftsQuery(SearchText.terms("  ***  ")))
    }

    @Test fun hindiIsNotAscii() {
        assertFalse(SearchText.isAscii("पंप"))
        assertTrue(SearchText.isAscii("pump"))
    }

    @Test fun matchesMergeOverlaps() {
        val r = SearchText.matches("Nozzle nozzle", listOf("noz", "nozzle"))
        assertEquals(listOf(0..5, 7..12), r)
    }

    @Test fun emailsSplitAndValidate() {
        assertEquals(listOf("a@x.com", "b@y.in", "bad"), SearchText.splitEmails("a@x.com, b@y.in; bad"))
        assertTrue(SearchText.isEmail("dealer.ops@hpcl.co.in"))
        assertFalse(SearchText.isEmail("dealer@hpcl"))
    }

    @Test fun snippetCentersOnMatch() {
        val body = "a ".repeat(80) + "leak at nozzle" + " b".repeat(80)
        val s = SearchText.snippet(body, listOf("leak"), 40)
        assertTrue(s.contains("leak"))
        assertTrue(s.startsWith("…"))
    }
}
