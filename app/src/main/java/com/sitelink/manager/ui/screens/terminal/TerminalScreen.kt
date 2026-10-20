package com.sitelink.manager.ui.screens.terminal

import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sitelink.manager.ui.theme.StatusConnected
import com.sitelink.manager.ui.theme.StatusDisconnected
import com.sitelink.manager.ui.theme.TerminalBackground
import com.sitelink.manager.ui.theme.TerminalForeground

private const val SENTINEL = "\u200B"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    viewModel: TerminalViewModel,
    onNavigateBack: () -> Unit
) {
    val site by viewModel.site.collectAsState()
    val terminalBuffer by viewModel.terminalBuffer.collectAsState()
    val isTerminalActive by viewModel.isTerminalActive.collectAsState()
    val terminalTitle by viewModel.terminalTitle.collectAsState()

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    val terminalScrollState = rememberScrollState()

    var showCopyMenu by remember { mutableStateOf(false) }
    var showSelectLinesDialog by remember { mutableStateOf(false) }

    // Sentinel text field state to reliably detect keystrokes and backspace
    var inputState by remember { mutableStateOf(TextFieldValue(SENTINEL, TextRange(SENTINEL.length))) }

    // Blinking cursor transition
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorBlink"
    )

    // Auto-scroll terminal to bottom when new text arrives
    LaunchedEffect(terminalBuffer) {
        if (terminalScrollState.maxValue > 0) {
            terminalScrollState.animateScrollTo(terminalScrollState.maxValue)
        }
    }

    // Auto-focus direct shell input on screen launch
    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
            keyboardController?.show()
        } catch (_: Exception) {}
    }

    // Listen for clean exit event
    LaunchedEffect(Unit) {
        viewModel.exitEvent.collect {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (terminalTitle.isNotEmpty()) terminalTitle else "${viewModel.sessionType} Terminal",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isTerminalActive) StatusConnected else StatusDisconnected)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isTerminalActive) "Connected (Port ${viewModel.targetPort})" else "Disconnected",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isTerminalActive) StatusConnected else StatusDisconnected
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.exitShell() }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Exit")
                    }
                },
                actions = {
                    // Copy action with dropdown menu
                    Box {
                        IconButton(onClick = { showCopyMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Lines",
                                tint = Color(0xFFC9D1D9)
                            )
                        }
                        DropdownMenu(
                            expanded = showCopyMenu,
                            onDismissRequest = { showCopyMenu = false },
                            modifier = Modifier.background(Color(0xFF21262D))
                        ) {
                            DropdownMenuItem(
                                text = { Text("Copy All Output", color = Color.White) },
                                onClick = {
                                    showCopyMenu = false
                                    clipboardManager.setText(AnnotatedString(terminalBuffer))
                                    Toast.makeText(context, "All output copied to clipboard", Toast.LENGTH_SHORT).show()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Copy Last 10 Lines", color = Color.White) },
                                onClick = {
                                    showCopyMenu = false
                                    val last10 = viewModel.getLastLines(10)
                                    clipboardManager.setText(AnnotatedString(last10))
                                    Toast.makeText(context, "Last 10 lines copied to clipboard", Toast.LENGTH_SHORT).show()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Copy Last 25 Lines", color = Color.White) },
                                onClick = {
                                    showCopyMenu = false
                                    val last25 = viewModel.getLastLines(25)
                                    clipboardManager.setText(AnnotatedString(last25))
                                    Toast.makeText(context, "Last 25 lines copied to clipboard", Toast.LENGTH_SHORT).show()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Select Lines to Copy...", color = MaterialTheme.colorScheme.primary) },
                                onClick = {
                                    showCopyMenu = false
                                    showSelectLinesDialog = true
                                }
                            )
                        }
                    }

                    // Keyboard trigger icon
                    IconButton(onClick = {
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Keyboard,
                            contentDescription = "Show Keyboard",
                            tint = Color(0xFFC9D1D9)
                        )
                    }

                    // Exit Shell Button
                    Button(
                        onClick = { viewModel.exitShell() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Exit Shell")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF161B22),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(TerminalBackground)
        ) {
            // 1. Direct interactive terminal display with full selection & copy support
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    }
                    .padding(8.dp)
                    .verticalScroll(terminalScrollState)
            ) {
                SelectionContainer {
                    val parsedText = AnsiColorParser.parse(terminalBuffer, defaultColor = TerminalForeground)
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = parsedText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                        if (isTerminalActive) {
                            Text(
                                text = "█",
                                color = MaterialTheme.colorScheme.primary.copy(alpha = cursorAlpha),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // Invisible Direct Shell Input Receiver
                BasicTextField(
                    value = inputState,
                    onValueChange = { newValue ->
                        val oldText = inputState.text
                        val newText = newValue.text

                        if (newText.length > oldText.length) {
                            val added = newText.substring(oldText.length)
                            if (added == "\n" || added == "\r") {
                                viewModel.sendEnter()
                            } else {
                                viewModel.sendInput(added)
                            }
                        } else if (newText.length < oldText.length) {
                            val deleteCount = oldText.length - newText.length
                            repeat(deleteCount) {
                                viewModel.sendBackspace()
                            }
                        }
                        // Reset sentinel to ensure future backspace/input events are always delivered
                        inputState = TextFieldValue(SENTINEL, TextRange(SENTINEL.length))
                    },
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .size(1.dp)
                        .alpha(0f)
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown) {
                                when (event.key) {
                                    Key.Enter, Key.NumPadEnter -> {
                                        viewModel.sendEnter()
                                        true
                                    }
                                    Key.Backspace -> {
                                        viewModel.sendBackspace()
                                        true
                                    }
                                    Key.Tab -> {
                                        viewModel.sendTab()
                                        true
                                    }
                                    Key.Escape -> {
                                        viewModel.sendEsc()
                                        true
                                    }
                                    Key.DirectionUp -> {
                                        viewModel.sendArrowUp()
                                        true
                                    }
                                    Key.DirectionDown -> {
                                        viewModel.sendArrowDown()
                                        true
                                    }
                                    Key.DirectionLeft -> {
                                        viewModel.sendArrowLeft()
                                        true
                                    }
                                    Key.DirectionRight -> {
                                        viewModel.sendArrowRight()
                                        true
                                    }
                                    else -> false
                                }
                            } else false
                        },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.None,
                        autoCorrectEnabled = false
                    ),
                    keyboardActions = KeyboardActions(
                        onAny = { viewModel.sendEnter() }
                    )
                )
            }

            // 2. Terminal Virtual Keys Toolbar (Cycles commands with ▲ and ▼, special control keys)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF161B22))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TerminalKeyButton(
                    label = "KBD",
                    onClick = {
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    }
                )
                TerminalKeyButton(label = "ESC", onClick = { viewModel.sendEsc() })
                TerminalKeyButton(label = "TAB", onClick = { viewModel.sendTab() })
                TerminalKeyButton(label = "CTRL+C", onClick = { viewModel.sendCtrlC() }, highlight = true)
                TerminalKeyButton(label = "CTRL+D", onClick = { viewModel.sendCtrlD() })
                TerminalKeyButton(label = "▲", onClick = { viewModel.sendArrowUp() })
                TerminalKeyButton(label = "▼", onClick = { viewModel.sendArrowDown() })
                TerminalKeyButton(label = "◄", onClick = { viewModel.sendArrowLeft() })
                TerminalKeyButton(label = "►", onClick = { viewModel.sendArrowRight() })
                TerminalKeyButton(label = "CLEAR", onClick = { viewModel.sendClear() })
                TerminalKeyButton(
                    label = "COPY",
                    onClick = { showCopyMenu = true }
                )
            }
        }
    }

    // Modal Dialog to inspect and copy specific lines from the terminal
    if (showSelectLinesDialog) {
        val lines = remember(terminalBuffer) {
            terminalBuffer.lines().filter { it.isNotBlank() }
        }

        AlertDialog(
            onDismissRequest = { showSelectLinesDialog = false },
            title = {
                Text("Copy Terminal Lines (${lines.size} lines)", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        text = "Tap any line below to copy it directly to your clipboard:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 350.dp)
                            .background(Color(0xFF0D1117), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        itemsIndexed(lines) { index, line ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        clipboardManager.setText(AnnotatedString(line))
                                        Toast.makeText(context, "Copied line ${index + 1}", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1}: ",
                                    color = Color(0xFF6E7681),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = line,
                                    color = Color(0xFFC9D1D9),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(terminalBuffer))
                        showSelectLinesDialog = false
                        Toast.makeText(context, "All lines copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Copy All")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showSelectLinesDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun TerminalKeyButton(
    label: String,
    onClick: () -> Unit,
    highlight: Boolean = false
) {
    FilledTonalButton(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (highlight) MaterialTheme.colorScheme.error.copy(alpha = 0.25f) else Color(0xFF21262D),
            contentColor = if (highlight) MaterialTheme.colorScheme.error else Color(0xFFC9D1D9)
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        modifier = Modifier.height(34.dp)
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
