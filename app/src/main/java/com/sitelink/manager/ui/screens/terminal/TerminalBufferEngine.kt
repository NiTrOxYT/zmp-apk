package com.sitelink.manager.ui.screens.terminal

/**
 * TerminalBufferEngine maintains a terminal screen representation that handles
 * ANSI cursor controls, carriage returns (\r), backspaces (\b), line erases (\u001B[K),
 * and line feeds (\n) so bash readline history cycling and character deletion
 * update the screen accurately without stacking or trailing ghost characters.
 */
class TerminalBufferEngine(private val maxLines: Int = 2000) {

    private val lines = mutableListOf<StringBuilder>(StringBuilder())
    private var currentRow = 0
    private var currentCol = 0
    private var revision = 0L

    @Synchronized
    fun processChunk(chunk: String) {
        var i = 0
        val len = chunk.length

        while (i < len) {
            val c = chunk[i]

            // Check for ANSI Escape Sequence: \u001B[...]
            if (c == '\u001B' && i + 1 < len && chunk[i + 1] == '[') {
                var j = i + 2
                while (j < len && (chunk[j] in '0'..'9' || chunk[j] == ';' || chunk[j] == '?' || chunk[j] == ' ')) {
                    j++
                }
                if (j < len) {
                    val finalChar = chunk[j]
                    val seq = chunk.substring(i, j + 1)
                    val paramStr = chunk.substring(i + 2, j)
                    val count = paramStr.toIntOrNull() ?: 1

                    when (finalChar) {
                        'm' -> {
                            // Color/Style sequence: preserve in buffer for AnsiColorParser
                            currentLine().append(seq)
                        }
                        'K' -> {
                            // Erase in Line:
                            // \u001B[K or \u001B[0K: erase from currentCol to end of line
                            // \u001B[2K: erase entire line
                            val current = currentLine()
                            if (paramStr == "2") {
                                current.setLength(0)
                                currentCol = 0
                            } else {
                                val textLen = getVisualLength(current)
                                if (currentCol < textLen) {
                                    truncateToVisualCol(current, currentCol)
                                }
                            }
                        }
                        'A' -> {
                            // Cursor Up
                            currentRow = maxOf(0, currentRow - count)
                        }
                        'B' -> {
                            // Cursor Down
                            currentRow = minOf(lines.size - 1, currentRow + count)
                        }
                        'D' -> {
                            // Cursor Left
                            currentCol = maxOf(0, currentCol - count)
                        }
                        'C' -> {
                            // Cursor Right
                            currentCol += count
                        }
                        'H', 'f' -> {
                            // Cursor Position
                            val parts = paramStr.split(";")
                            val row = parts.getOrNull(0)?.toIntOrNull() ?: 1
                            val col = parts.getOrNull(1)?.toIntOrNull() ?: 1
                            currentRow = (row - 1).coerceIn(0, maxOf(0, lines.size - 1))
                            currentCol = maxOf(0, col - 1)
                        }
                    }
                    i = j + 1
                    continue
                }
            }

            when (c) {
                '\r' -> {
                    // Carriage return: move cursor back to beginning of the line
                    currentCol = 0
                }
                '\n' -> {
                    // Line feed: advance to next line
                    lines.add(StringBuilder())
                    if (lines.size > maxLines) {
                        lines.removeAt(0)
                    }
                    currentRow = lines.size - 1
                    currentCol = 0
                }
                '\b' -> {
                    // Backspace: move cursor left 1 column and delete char at cursor
                    if (currentCol > 0) {
                        currentCol--
                        val current = currentLine()
                        if (currentCol < getVisualLength(current)) {
                            truncateToVisualCol(current, currentCol)
                        }
                    }
                }
                '\u0007' -> {
                    // Bell: no-op
                }
                '\t' -> {
                    // Tab: advance to next 8-space tab stop
                    val nextStop = (currentCol / 8 + 1) * 8
                    val spaces = nextStop - currentCol
                    repeat(spaces) {
                        writeChar(' ')
                    }
                }
                else -> {
                    if (c >= ' ') {
                        writeChar(c)
                    }
                }
            }
            i++
        }
        revision++
    }

    private fun currentLine(): StringBuilder {
        if (lines.isEmpty()) lines.add(StringBuilder())
        if (currentRow !in lines.indices) {
            currentRow = lines.size - 1
        }
        return lines[currentRow]
    }

    private fun writeChar(c: Char) {
        val current = currentLine()
        val visualLen = getVisualLength(current)

        if (currentCol < visualLen) {
            replaceCharAtVisualCol(current, currentCol, c)
        } else {
            while (getVisualLength(current) < currentCol) {
                current.append(' ')
            }
            current.append(c)
        }
        currentCol++
    }

    @Synchronized
    fun getFormattedText(maxDisplayLines: Int = 500): String {
        val takeLines = if (lines.size > maxDisplayLines) lines.takeLast(maxDisplayLines) else lines
        return takeLines.joinToString("\n") { it.toString() }
    }

    @Synchronized
    fun getAllText(): String {
        return lines.joinToString("\n") { it.toString() }
    }

    @Synchronized
    fun getState(maxDisplayLines: Int = 500): TerminalState {
        val totalLines = lines.size
        val offset = if (totalLines > maxDisplayLines) totalLines - maxDisplayLines else 0
        val adjustedCursorRow = maxOf(0, currentRow - offset)
        return TerminalState(
            formattedText = getFormattedText(maxDisplayLines),
            cursorRow = adjustedCursorRow,
            cursorCol = currentCol,
            revision = revision
        )
    }

    @Synchronized
    fun clear() {
        lines.clear()
        lines.add(StringBuilder())
        currentRow = 0
        currentCol = 0
        revision++
    }

    companion object {
        fun getVisualLength(sb: StringBuilder): Int {
            var len = 0
            var i = 0
            val sbLen = sb.length
            while (i < sbLen) {
                if (sb[i] == '\u001B' && i + 1 < sbLen && sb[i + 1] == '[') {
                    var j = i + 2
                    while (j < sbLen && (sb[j] in '0'..'9' || sb[j] == ';' || sb[j] == '?' || sb[j] == ' ')) j++
                    if (j < sbLen) {
                        i = j + 1
                        continue
                    }
                }
                len++
                i++
            }
            return len
        }

        fun truncateToVisualCol(sb: StringBuilder, targetVisualCol: Int) {
            var visualCol = 0
            var i = 0
            val sbLen = sb.length
            var cutIndex = sbLen

            while (i < sbLen) {
                if (visualCol == targetVisualCol) {
                    cutIndex = i
                    break
                }
                if (sb[i] == '\u001B' && i + 1 < sbLen && sb[i + 1] == '[') {
                    var j = i + 2
                    while (j < sbLen && (sb[j] in '0'..'9' || sb[j] == ';' || sb[j] == '?' || sb[j] == ' ')) j++
                    if (j < sbLen) {
                        i = j + 1
                        continue
                    }
                }
                visualCol++
                i++
            }
            if (cutIndex < sb.length) {
                sb.setLength(cutIndex)
            }
        }

        fun replaceCharAtVisualCol(sb: StringBuilder, targetVisualCol: Int, newChar: Char) {
            var visualCol = 0
            var i = 0
            val sbLen = sb.length

            while (i < sbLen) {
                if (sb[i] == '\u001B' && i + 1 < sbLen && sb[i + 1] == '[') {
                    var j = i + 2
                    while (j < sbLen && (sb[j] in '0'..'9' || sb[j] == ';' || sb[j] == '?' || sb[j] == ' ')) j++
                    if (j < sbLen) {
                        i = j + 1
                        continue
                    }
                }
                if (visualCol == targetVisualCol) {
                    sb.setCharAt(i, newChar)
                    return
                }
                visualCol++
                i++
            }
            sb.append(newChar)
        }
    }
}

/**
 * Immutable snapshot of terminal screen and cursor state for UI rendering
 */
data class TerminalState(
    val formattedText: String = "",
    val cursorRow: Int = 0,
    val cursorCol: Int = 0,
    val revision: Long = 0L
)
