package com.teledrive.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.teledrive.app.core.ota.OtaUpdateState
import com.teledrive.app.ui.components.StorageStatsCard
import com.teledrive.app.ui.components.UpdateDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val otaState by viewModel.otaUpdateState.collectAsState()
    val otaUrl by viewModel.otaUpdateUrl.collectAsState(initial = "")
    val autoCheck by viewModel.autoCheckUpdates.collectAsState(initial = true)

    var showLogoutDialog by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var customUrlInput by remember { mutableStateOf("") }

    // Notify if app is up to date after manual check
    LaunchedEffect(otaState) {
        if (otaState is OtaUpdateState.UpToDate) {
            Toast.makeText(context, "TeleDrive is up to date!", Toast.LENGTH_SHORT).show()
        }
    }

    // Modal Update Dialog
    UpdateDialog(
        state = otaState,
        currentVersion = viewModel.currentVersionName,
        onStartDownload = { info -> viewModel.startDownload(info) },
        onInstall = { apkFile -> viewModel.installUpdate(apkFile) },
        onCancelDownload = { viewModel.cancelDownload() },
        onDismiss = { viewModel.dismissUpdate() },
        onRetry = { viewModel.checkForUpdates() }
    )

    // Edit Update URL Dialog
    if (showUrlDialog) {
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text("Update Source URL") },
            text = {
                Column {
                    Text(
                        text = "Enter a GitHub Releases API endpoint or direct update.json URL:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = customUrlInput,
                        onValueChange = { customUrlInput = it },
                        placeholder = { Text("https://api.github.com/repos/.../releases/latest") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showUrlDialog = false
                    if (customUrlInput.isNotBlank()) {
                        viewModel.setOtaUpdateUrl(customUrlInput)
                        Toast.makeText(context, "Update URL saved.", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Logout") },
            text = { Text("Are you sure you want to logout? All local cache will be cleared.") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    viewModel.logout {
                        onLogout()
                    }
                }) {
                    Text("Logout", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                SectionTitle("Account")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (uiState.username.isNotBlank()) uiState.username.take(2).uppercase().removePrefix("@") else "TD",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(text = uiState.phoneNumber, style = MaterialTheme.typography.titleMedium)
                        if (uiState.username.isNotBlank()) {
                            Text(
                                text = if (uiState.username.startsWith("@")) uiState.username else "@${uiState.username}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = { showLogoutDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Logout")
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            }

            item {
                SectionTitle("Storage")
                StorageStatsCard(stats = uiState.storageStats)
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            }

            item {
                SectionTitle("Appearance")
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        listOf(
                            Pair("system", "System default"),
                            Pair("light", "Light theme"),
                            Pair("dark", "Dark theme")
                        ).forEach { (mode, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = uiState.themeMode == mode,
                                    onClick = { viewModel.setThemeMode(mode) }
                                )
                                Text(
                                    text = label,
                                    modifier = Modifier.padding(start = 8.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            }

            // OTA Updates Section
            item {
                SectionTitle("Updates")
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        // Check for Updates Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (otaState !is OtaUpdateState.Checking && otaState !is OtaUpdateState.Downloading) {
                                        viewModel.checkForUpdates()
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SystemUpdate,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = "Check for updates",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = when (otaState) {
                                            is OtaUpdateState.Checking -> "Checking for updates…"
                                            is OtaUpdateState.UpdateAvailable -> "New version available!"
                                            is OtaUpdateState.Downloading -> "Downloading update…"
                                            is OtaUpdateState.ReadyToInstall -> "Ready to install"
                                            is OtaUpdateState.UpToDate -> "TeleDrive is up to date"
                                            is OtaUpdateState.Error -> "Check failed (tap to retry)"
                                            else -> "Current: v${viewModel.currentVersionName} (Build ${viewModel.currentVersionCode})"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = when (otaState) {
                                            is OtaUpdateState.UpdateAvailable -> MaterialTheme.colorScheme.primary
                                            is OtaUpdateState.ReadyToInstall -> Color(0xFF4CAF50)
                                            is OtaUpdateState.Error -> MaterialTheme.colorScheme.error
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }

                            if (otaState is OtaUpdateState.Checking || otaState is OtaUpdateState.Downloading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

                        // Auto-check Switch Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setAutoCheckUpdates(!autoCheck) }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Auto-check on startup",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Periodically check for new releases in background",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = autoCheck,
                                onCheckedChange = { viewModel.setAutoCheckUpdates(it) }
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

                        // Update Server URL Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    customUrlInput = otaUrl
                                    showUrlDialog = true
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Update Source URL",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = otaUrl.ifBlank { "Default GitHub Releases" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit URL",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            }

            item {
                SectionTitle("About")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "TeleDrive v${viewModel.currentVersionName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Unlimited Telegram cloud storage client for Android",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}
