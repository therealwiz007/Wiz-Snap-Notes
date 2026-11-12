package com.wizlabs.snapnotes

import java.util.Calendar

/** Pure text helpers for search, highlighting and recipient parsing (unit-tested on the JVM). */
object SearchText {
    private val splitter = Regex("[^\\p{L}\\p{N}@._-]+")
    private val emailRx = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    /** Words the user typed, lower-cased, at most 8. */
    fun terms(query: String): List<String> =
        query.lowercase().split(splitter).map { it.trim('.', '_', '-', '@') }.filter { it.isNotEmpty() }.distinct().take(8)

    fun isAscii(s: String) = s.all { it.code < 128 }

    /**
     * FTS4 query: every word must match, each as a prefix ("noz" finds "nozzle"). Words are
     * further split on FTS separators so punctuation can never break the query syntax.
     */
    fun ftsQuery(terms: List<String>): String =
        terms.flatMap { it.split(Regex("[^a-z0-9]+")) }
            .filter { it.isNotEmpty() }
            .joinToString(" ") { "$it*" }

    /** Character ranges in [text] that match any of [terms] (case-insensitive), merged and sorted. */
    fun matches(text: String, terms: List<String>): List<IntRange> {
        if (terms.isEmpty() || text.isEmpty()) return emptyList()
        val lower = text.lowercase()
        val found = ArrayList<IntRange>()
        for (t in terms) {
            var i = lower.indexOf(t)
            while (i >= 0) {
                found += i until i + t.length
                i = lower.indexOf(t, i + t.length)
            }
        }
        found.sortBy { it.first }
        val merged = ArrayList<IntRange>()
        for (r in found) {
            val last = merged.lastOrNull()
            if (last != null && r.first <= last.last + 1) {
                merged[merged.size - 1] = last.first..maxOf(last.last, r.last)
            } else merged += r
        }
        return merged
    }

    /** A short slice of [body] around the first match, so search results show why they matched. */
    fun snippet(body: String, terms: List<String>, max: Int = 90): String {
        val flat = body.replace('\n', ' ').trim()
        if (flat.length <= max) return flat
        val first = matches(flat, terms).firstOrNull()?.first ?: 0
        val start = (first - max / 3).coerceAtLeast(0)
        val end = (start + max).coerceAtMost(flat.length)
        return (if (start > 0) "…" else "") + flat.substring(start, end).trim() + (if (end < flat.length) "…" else "")
    }

    fun isEmail(s: String) = emailRx.matches(s.trim())

    /** Splits pasted or typed text like "a@x.com, b@y.in; c@z.org" into addresses. */
    fun splitEmails(s: String): List<String> =
        s.split(',', ';', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }

    fun rangeStart(range: DateRange, now: Long): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (range.days > 1) c.add(Calendar.DAY_OF_YEAR, -(range.days - 1))
        return c.timeInMillis
    }
}
