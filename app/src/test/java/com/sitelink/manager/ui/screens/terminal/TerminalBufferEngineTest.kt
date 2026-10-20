package com.sitelink.manager.ui.screens.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TerminalBufferEngineTest {

    private lateinit var engine: TerminalBufferEngine

    @Before
    fun setup() {
        engine = TerminalBufferEngine()
    }

    @Test
    fun testBackspaceDeletesCharacter() {
        // Simulate shell output typing 'abc'
        engine.processChunk("support@host:~$ abc")
        assertEquals("support@host:~$ abc", engine.getFormattedText())

        // Simulate bash sending backspace sequence (\b \b) when user hits backspace
        engine.processChunk("\b \b")
        assertEquals("support@host:~$ ab", engine.getFormattedText())

        // Backspace again
        engine.processChunk("\b \b")
        assertEquals("support@host:~$ a", engine.getFormattedText())
    }

    @Test
    fun testHistoryCycleOverwritesLineWithoutGhostCharacters() {
        // 1st command was 'cd /usr'
        engine.processChunk("support@host:~$ cd /usr\n")
        engine.processChunk("support@host:/usr$ ls\n")

        // Now at prompt
        engine.processChunk("support@host:/usr$ ")

        // User presses Up Arrow 1st time: bash recalls 'ls'
        engine.processChunk("\rsupport@host:/usr$ ls\u001B[K")
        val linesAfterUp1 = engine.getFormattedText().lines()
        assertEquals("support@host:/usr$ ls", linesAfterUp1.last())

        // User presses Up Arrow 2nd time: bash recalls 'cd /usr'
        engine.processChunk("\rsupport@host:/usr$ cd /usr\u001B[K")
        val linesAfterUp2 = engine.getFormattedText().lines()
        // Must show EXACTLY 'cd /usr' and NOT 'cd /usrll'
        assertEquals("support@host:/usr$ cd /usr", linesAfterUp2.last())

        // User presses Down Arrow: bash recalls 'ls'
        engine.processChunk("\rsupport@host:/usr$ ls\u001B[K")
        val linesAfterDown = engine.getFormattedText().lines()
        // Must show EXACTLY 'ls' and truncate the longer previous command
        assertEquals("support@host:/usr$ ls", linesAfterDown.last())
    }

    @Test
    fun testCarriageReturnOverwritesFromStartOfLine() {
        engine.processChunk("Initial Long Text Here")
        engine.processChunk("\rShort")
        // "Short" overwrites first 5 characters of "Initial" (7 chars -> "Short" + "al")
        assertEquals("Shortal Long Text Here", engine.getFormattedText())

        // With \u001B[K (erase to end of line), remainder is cleared
        engine.processChunk("\u001B[K")
        assertEquals("Short", engine.getFormattedText())
    }

    @Test
    fun testEraseEntireLine() {
        engine.processChunk("support@host:~$ draft command")
        // \u001B[2K erases entire line
        engine.processChunk("\u001B[2K")
        assertEquals("", engine.getFormattedText())
    }

    @Test
    fun testPreservesAnsiColorsWhileHandlingCursorMoves() {
        // Red text followed by green text
        engine.processChunk("\u001B[31mError:\u001B[0m something went wrong")
        val formatted = engine.getFormattedText()
        assertTrue(formatted.contains("\u001B[31m"))
        assertTrue(formatted.contains("Error:"))
    }

    @Test
    fun testCursorMovementLeftAndRightWithArrowKeys() {
        engine.processChunk("support@host:~$ echo abc")
        val stateInitial = engine.getState()
        assertEquals(24, stateInitial.cursorCol)

        // Left Arrow 3 times (\u001B[D or \u001B[3D)
        engine.processChunk("\u001B[D\u001B[D\u001B[D")
        val stateAfterLeft = engine.getState()
        assertEquals(21, stateAfterLeft.cursorCol)
        assertTrue(stateAfterLeft.revision > stateInitial.revision)

        // Right Arrow 2 times
        engine.processChunk("\u001B[C\u001B[C")
        val stateAfterRight = engine.getState()
        assertEquals(23, stateAfterRight.cursorCol)
    }

    @Test
    fun testAnsiColorParserRendersCursor() {
        // Line with prompt and text
        val raw = "support@host:~$ hello"
        // Cursor at column 20 (on 'l')
        val parsedWithCursorOnChar = AnsiColorParser.parse(
            rawText = raw,
            cursorRow = 0,
            cursorCol = 18, // on 'e' in "hello"
            cursorVisible = true
        )
        // Annotated string should contain the full text
        assertEquals(raw, parsedWithCursorOnChar.text)

        // Cursor at end of line (col 21 >= length)
        val parsedWithCursorAtEnd = AnsiColorParser.parse(
            rawText = raw,
            cursorRow = 0,
            cursorCol = 21,
            cursorVisible = true
        )
        // Cursor at end appends '█' block
        assertEquals("$raw█", parsedWithCursorAtEnd.text)
    }

    @Test
    fun testBufferLimitingAndGetAllText() {
        // Create 20 lines without trailing blank line
        repeat(20) { idx ->
            if (idx > 0) engine.processChunk("\n")
            engine.processChunk("Line $idx")
        }
        val allText = engine.getAllText()
        assertTrue(allText.contains("Line 0"))
        assertTrue(allText.contains("Line 19"))

        // Limit to last 5 lines
        val limited = engine.getFormattedText(maxDisplayLines = 5)
        assertFalse(limited.contains("Line 0"))
        assertTrue(limited.contains("Line 19"))
        assertEquals(5, limited.lines().filter { it.isNotBlank() }.size)
    }
}
