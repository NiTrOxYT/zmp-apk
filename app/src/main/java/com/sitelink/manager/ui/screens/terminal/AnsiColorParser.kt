package com.sitelink.manager.ui.screens.terminal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight

object AnsiColorParser {

    private val ANSI_REGEX = Regex("\u001B\\[[0-9;]*[a-zA-Z]")

    private val ANSI_COLORS = mapOf(
        30 to Color(0xFF484F58), // Black
        31 to Color(0xFFFA7970), // Red
        32 to Color(0xFF7CE38B), // Green
        33 to Color(0xFFFAA356), // Yellow
        34 to Color(0xFF77BDFB), // Blue
        35 to Color(0xFFCEA5FB), // Magenta
        36 to Color(0xFFA2D2FB), // Cyan
        37 to Color(0xFFB1BAC4), // White
        90 to Color(0xFF6E7681), // Bright Black / Gray
        91 to Color(0xFFFF9B9B), // Bright Red
        92 to Color(0xFF8CF89C), // Bright Green
        93 to Color(0xFFFFBF80), // Bright Yellow
        94 to Color(0xFF91CBFF), // Bright Blue
        95 to Color(0xFFDBB8FF), // Bright Magenta
        96 to Color(0xFFBCE1FF), // Bright Cyan
        97 to Color(0xFFF0F6FC)  // Bright White
    )

    fun parse(
        rawText: String,
        defaultColor: Color = Color(0xFFE6EDF3),
        cursorRow: Int = -1,
        cursorCol: Int = -1,
        cursorVisible: Boolean = false,
        cursorColor: Color = Color(0xFF58A6FF)
    ): AnnotatedString {
        val lines = rawText.split("\n")
        return buildAnnotatedString {
            var currentColor = defaultColor
            var isBold = false

            lines.forEachIndexed { lineIdx, line ->
                val isCursorLine = cursorVisible && (lineIdx == cursorRow)
                var visualCol = 0
                var lastIndex = 0

                ANSI_REGEX.findAll(line).forEach { match ->
                    val start = match.range.first
                    if (start > lastIndex) {
                        val textChunk = line.substring(lastIndex, start)
                        visualCol = appendTextWithCursor(
                            textChunk = textChunk,
                            startVisualCol = visualCol,
                            isCursorLine = isCursorLine,
                            cursorCol = cursorCol,
                            currentColor = currentColor,
                            isBold = isBold,
                            cursorColor = cursorColor
                        )
                    }

                    // Parse escape codes
                    val codeStr = match.value.removePrefix("\u001B[").removeSuffix("m")
                    if (match.value.endsWith("m")) {
                        val codes = codeStr.split(";").mapNotNull { it.toIntOrNull() }
                        if (codes.isEmpty() || codes.contains(0)) {
                            currentColor = defaultColor
                            isBold = false
                        }
                        if (codes.contains(1)) {
                            isBold = true
                        }
                        codes.forEach { code ->
                            ANSI_COLORS[code]?.let { color ->
                                currentColor = color
                            }
                        }
                    }
                    lastIndex = match.range.last + 1
                }

                if (lastIndex < line.length) {
                    val textChunk = line.substring(lastIndex)
                    visualCol = appendTextWithCursor(
                        textChunk = textChunk,
                        startVisualCol = visualCol,
                        isCursorLine = isCursorLine,
                        cursorCol = cursorCol,
                        currentColor = currentColor,
                        isBold = isBold,
                        cursorColor = cursorColor
                    )
                }

                // If cursor is at or past the end of the line text
                if (isCursorLine && cursorCol >= visualCol) {
                    // Pad spaces if cursor is past end
                    while (visualCol < cursorCol) {
                        append(" ")
                        visualCol++
                    }
                    pushStyle(
                        SpanStyle(
                            color = cursorColor,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    append("█")
                    pop()
                }

                // Append newline between lines
                if (lineIdx < lines.size - 1) {
                    append("\n")
                }
            }
        }
    }

    private fun androidx.compose.ui.text.AnnotatedString.Builder.appendTextWithCursor(
        textChunk: String,
        startVisualCol: Int,
        isCursorLine: Boolean,
        cursorCol: Int,
        currentColor: Color,
        isBold: Boolean,
        cursorColor: Color
    ): Int {
        val chunkLen = textChunk.length
        val endVisualCol = startVisualCol + chunkLen

        // Fast path: Not the cursor line, or cursor is outside this chunk
        if (!isCursorLine || cursorCol < startVisualCol || cursorCol >= endVisualCol) {
            pushStyle(
                SpanStyle(
                    color = currentColor,
                    fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
                )
            )
            append(textChunk)
            pop()
            return endVisualCol
        }

        // Cursor IS inside this chunk!
        val cursorOffset = cursorCol - startVisualCol

        // 1. Text before cursor
        if (cursorOffset > 0) {
            pushStyle(
                SpanStyle(
                    color = currentColor,
                    fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
                )
            )
            append(textChunk.substring(0, cursorOffset))
            pop()
        }

        // 2. Character under cursor (inverted block cursor)
        pushStyle(
            SpanStyle(
                color = Color.Black,
                background = cursorColor,
                fontWeight = FontWeight.Bold
            )
        )
        append(textChunk[cursorOffset])
        pop()

        // 3. Text after cursor
        if (cursorOffset + 1 < chunkLen) {
            pushStyle(
                SpanStyle(
                    color = currentColor,
                    fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
                )
            )
            append(textChunk.substring(cursorOffset + 1))
            pop()
        }

        return endVisualCol
    }
}
