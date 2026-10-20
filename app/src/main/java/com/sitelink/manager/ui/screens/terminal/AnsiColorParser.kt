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

    fun parse(rawText: String, defaultColor: Color = Color(0xFFE6EDF3)): AnnotatedString {
        return buildAnnotatedString {
            var currentColor = defaultColor
            var isBold = false
            var lastIndex = 0

            ANSI_REGEX.findAll(rawText).forEach { match ->
                val start = match.range.first
                if (start > lastIndex) {
                    val textChunk = rawText.substring(lastIndex, start)
                    pushStyle(
                        SpanStyle(
                            color = currentColor,
                            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
                        )
                    )
                    append(textChunk)
                    pop()
                }

                // Parse codes
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

            if (lastIndex < rawText.length) {
                pushStyle(
                    SpanStyle(
                        color = currentColor,
                        fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
                    )
                )
                append(rawText.substring(lastIndex))
                pop()
            }
        }
    }
}
