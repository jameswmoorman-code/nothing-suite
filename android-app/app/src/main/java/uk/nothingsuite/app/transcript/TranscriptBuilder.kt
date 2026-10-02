package uk.nothingsuite.app.transcript

/**
 * Turns the stream of server frames into a readable two-way transcript.
 *   assistant → a CONCIERGE line (what the caller is hearing)
 *   delta     → append to the trailing unfinished CALLER line
 *   final     → freeze the trailing line with the corrected full text
 * Pure functions so the live screen and the inbox agree on what was said.
 */
object TranscriptBuilder {

    fun apply(lines: List<TranscriptLine>, frame: TranscriptFrame): List<TranscriptLine> = when (frame.type) {
        "assistant" -> addLine(lines, TranscriptLine(frame.text.orEmpty(), final = true, speaker = Speaker.CONCIERGE))
        "delta" -> appendDelta(lines, frame.text.orEmpty())
        "final" -> finaliseLine(lines, frame.text.orEmpty())
        else -> lines
    }

    fun addLine(lines: List<TranscriptLine>, line: TranscriptLine): List<TranscriptLine> {
        val out = lines.toMutableList()
        val last = out.lastOrNull()
        if (last != null && !last.final) out[out.lastIndex] = last.copy(final = true)
        out += line
        return out
    }

    fun appendDelta(lines: List<TranscriptLine>, delta: String): List<TranscriptLine> {
        val out = lines.toMutableList()
        val last = out.lastOrNull()
        if (last != null && !last.final && last.speaker == Speaker.CALLER) out[out.lastIndex] = last.copy(text = last.text + delta)
        else out += TranscriptLine(delta, final = false)
        return out
    }

    fun finaliseLine(lines: List<TranscriptLine>, full: String): List<TranscriptLine> {
        val out = lines.toMutableList()
        val last = out.lastOrNull()
        if (last != null && !last.final && last.speaker == Speaker.CALLER) out[out.lastIndex] = TranscriptLine(full.ifBlank { last.text }, final = true)
        else if (full.isNotBlank()) out += TranscriptLine(full, final = true)
        return out
    }

    /** One-line gist for an inbox card: the first thing the caller said, trimmed. */
    fun gist(lines: List<TranscriptLine>, max: Int = 110): String {
        val said = lines.filter { it.speaker == Speaker.CALLER && it.text.isNotBlank() }.joinToString(" ") { it.text.trim() }
        if (said.isBlank()) return "No message left"
        return if (said.length <= max) said else said.take(max).trimEnd() + "…"
    }
}
