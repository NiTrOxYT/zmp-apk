package com.sitelink.manager.ui.screens.dashboard

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sitelink.manager.domain.model.SshSessionState
import com.sitelink.manager.domain.model.TunnelInfo
import com.sitelink.manager.domain.model.TunnelStatus
import com.sitelink.manager.ui.screens.auth.HostKeyTrustDialog
import com.sitelink.manager.ui.screens.auth.SshAuthDialog
import com.sitelink.manager.ui.theme.AccentBmAmber
import com.sitelink.manager.ui.theme.AccentBmAmberContainer
import com.sitelink.manager.ui.theme.AccentVmEmerald
import com.sitelink.manager.ui.theme.AccentVmEmeraldContainer
import com.sitelink.manager.ui.theme.AccentZmpBlue
import com.sitelink.manager.ui.theme.AccentZmpBlueContainer
import com.sitelink.manager.ui.theme.StatusConnected
import com.sitelink.manager.ui.theme.StatusConnecting
import com.sitelink.manager.ui.theme.StatusDisconnected
import com.sitelink.manager.ui.theme.StatusError

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteDashboardScreen(
    viewModel: SiteDashboardViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToTerminal: (Long, String, Int) -> Unit,
    onNavigateToSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val site by viewModel.site.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val isAuthError by viewModel.isAuthError.collectAsState()
    val showAuthDialog by viewModel.showAuthDialog.collectAsState()
    val sessionState by viewModel.sessionState.collectAsState()
    val activeTunnels by viewModel.activeTunnels.collectAsState()
    val hostVerificationEvent by viewModel.hostVerificationEvent.collectAsState()

    var showUpdatePasswordDialog by remember { mutableStateOf(false) }

    // Listen for navigation / browser launch events
    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                is DashboardNavigationEvent.OpenBrowser -> {
                    viewModel.launchUrlInCustomTab(context, event.url)
                }
                is DashboardNavigationEvent.NavigateToTerminal -> {
                    onNavigateToTerminal(event.siteId, event.type, event.port)
                }
            }
        }
    }

    // Host key verification dialog
    hostVerificationEvent?.let { result ->
        HostKeyTrustDialog(
            verificationResult = result,
            onTrust = { viewModel.trustHostKey(it) },
            onDismiss = { viewModel.dismissHostVerification() }
        )
    }

    // SSH Auth dialog (only shown if credentials are not configured)
    if (showAuthDialog) {
        SshAuthDialog(
            officeHost = viewModel.sshRepository.serverConfigManager.getHost(),
            officeUser = viewModel.sshRepository.serverConfigManager.getUsername(),
            hasKeystoreKey = viewModel.keyStoreManager.hasKeyPair(),
            onConnect = { viewModel.onAuthDialogSubmit(it) },
            onDismiss = { viewModel.dismissAuthDialog() }
        )
    }

    // Quick Update Password Dialog on Authentication Failure
    if (showUpdatePasswordDialog) {
        var newPass by remember { mutableStateOf("") }
        var newPassVisible by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showUpdatePasswordDialog = false },
            icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Update Office SSH Password", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "SSH Authentication failed on the office server (111.93.205.187). The password may have expired or changed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newPass,
                        onValueChange = { newPass = it },
                        label = { Text("New SSH Password") },
                        placeholder = { Text("Enter updated password") },
                        singleLine = true,
                        visualTransformation = if (newPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            val icon = if (newPassVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                            IconButton(onClick = { newPassVisible = !newPassVisible }) {
                                Icon(imageVector = icon, contentDescription = null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPass.isNotBlank()) {
                            viewModel.updateSavedPasswordAndRetry(newPass)
                            showUpdatePasswordDialog = false
                            Toast.makeText(context, "Password updated in Keystore!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = newPass.isNotBlank()
                ) {
                    Text("Save & Reconnect")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdatePasswordDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = site?.name ?: "Site Dashboard",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (sessionState is SshSessionState.Connected) {
                        OutlinedButton(
                            onClick = { viewModel.disconnectOffice() },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Text("Disconnect SSH")
                        }
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            val currentSite = site
            if (currentSite == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Scaffold
            }

            // 1. Prominent Site Header Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentSite.name,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Dns,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = currentSite.ipAddress,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        // SSH Office Relay status badge
                        val (statusColor, statusLabel) = when (sessionState) {
                            is SshSessionState.Connected -> StatusConnected to "SSH Online"
                            is SshSessionState.Connecting -> StatusConnecting to "SSH Connecting"
                            is SshSessionState.Error -> StatusError to "SSH Error"
                            is SshSessionState.HostVerificationRequired -> StatusConnecting to "Verifying"
                            SshSessionState.Disconnected -> StatusDisconnected to "SSH Standby"
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = statusColor.copy(alpha = 0.15f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(statusColor)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = statusLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = statusColor
                                )
                            }
                        }
                    }

                    if (currentSite.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = currentSite.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (currentSite.notes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Notes: ${currentSite.notes}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Status / Error alerts
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = errorMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        if (isAuthError) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { showUpdatePasswordDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Update Password")
                                }

                                OutlinedButton(
                                    onClick = onNavigateToSettings,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Settings")
                                }
                            }
                        }
                    }
                }
            }

            if (isLoading && statusMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = statusMessage ?: "",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 2. Section: ZMP PORTAL (Ports 8080, 8082, 8083)
            Text(
                text = "ZMP PORTAL FORWARDING",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = AccentZmpBlue
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tunnels loopback port 127.0.0.1 directly through Office SSH to site /zmp/login.do",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            listOf(8080, 8082, 8083).forEach { port ->
                val tunnelKey = "${currentSite.id}_$port"
                val activeTunnel = activeTunnels[tunnelKey]

                ZmpPortCard(
                    port = port,
                    tunnel = activeTunnel,
                    onConnect = { viewModel.openZmp(port, context) },
                    onOpenBrowser = { tunnel ->
                        viewModel.launchUrlInCustomTab(context, tunnel.localUrl)
                    },
                    onDisconnect = { viewModel.disconnectZmp(port) }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. Section: REMOTE TERMINALS (VM: 2222, BM: 2223)
            Text(
                text = "HOST TERMINAL ACCESS",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Interactive SSH sessions nested from Office Server to hotel target appliances",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // VM Button Card (Port 2222)
            TerminalActionCard(
                title = "Virtual Machine (VM)",
                subtitle = "Port 2222 • Virtualized site appliance console",
                port = 2222,
                color = AccentVmEmerald,
                containerColor = AccentVmEmeraldContainer,
                onClick = { viewModel.openTerminal("VM", 2222) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // BM Button Card (Port 2223)
            TerminalActionCard(
                title = "Bare Metal (BM)",
                subtitle = "Port 2223 • Hardware host server management console",
                port = 2223,
                color = AccentBmAmber,
                containerColor = AccentBmAmberContainer,
                onClick = { viewModel.openTerminal("BM", 2223) }
            )
        }
    }
}

@Composable
fun ZmpPortCard(
    port: Int,
    tunnel: TunnelInfo?,
    onConnect: () -> Unit,
    onOpenBrowser: (TunnelInfo) -> Unit,
    onDisconnect: () -> Unit
) {
    val isConnected = tunnel?.status == TunnelStatus.CONNECTED

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(AccentZmpBlueContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = AccentZmpBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "ZMP Portal :$port",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            if (isConnected) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = StatusConnected.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "LIVE",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = StatusConnected,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (isConnected) "Listening: 127.0.0.1:${tunnel?.localPort}" else "Forwarding to site :$port",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!isConnected) {
                    Button(
                        onClick = onConnect,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentZmpBlue),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Connect $port")
                    }
                } else {
                    Row {
                        IconButton(onClick = onDisconnect) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Disconnect Tunnel",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        Button(
                            onClick = { tunnel?.let { onOpenBrowser(it) } },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentZmpBlue),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Open")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TerminalActionCard(
    title: String,
    subtitle: String,
    port: Int,
    color: Color,
    containerColor: Color,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(containerColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = color),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Open :$port")
            }
        }
    }
}
