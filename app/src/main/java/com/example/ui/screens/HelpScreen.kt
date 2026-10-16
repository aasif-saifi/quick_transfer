package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onBack: () -> Unit) {
    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Help & Troubleshooting",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // How to transfer offline
            Text(
                text = "How Quick Transfer Works",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            )

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StepItem(
                        icon = Icons.Default.Wifi,
                        title = "1. Connect Devices Locally",
                        description = "Both devices should be connected to the same Wi-Fi router, or one device can turn on Mobile Hotspot (no cellular data needed) and the other connects to it."
                    )
                    StepItem(
                        icon = Icons.Default.QrCode,
                        title = "2. Send & Receive",
                        description = "The recipient taps 'Receive' to become discoverable or display their QR code. The sender selects files, taps 'Find Devices', and selects the recipient or scans their QR code."
                    )
                    StepItem(
                        icon = Icons.Default.Security,
                        title = "3. Verify & Transfer",
                        description = "The recipient reviews the sender's device and incoming files before accepting. Files stream directly over local TCP sockets with SHA-256 integrity verification."
                    )
                }
            }

            // Permissions explanation
            Text(
                text = "Required Permissions Explained",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            )

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PermissionExplanationRow(
                        icon = Icons.Default.WifiTethering,
                        title = "Wi-Fi & Nearby Devices",
                        description = "Used to discover and establish direct peer-to-peer data sockets without going through external servers."
                    )
                    PermissionExplanationRow(
                        icon = Icons.Default.Bluetooth,
                        title = "Bluetooth & BLE",
                        description = "Helps discover nearby hardware devices when Wi-Fi multicast is restricted by certain routers."
                    )
                    PermissionExplanationRow(
                        icon = Icons.Default.CameraAlt,
                        title = "Camera",
                        description = "Only used to scan the recipient's pairing QR code for instant zero-configuration connection."
                    )
                    PermissionExplanationRow(
                        icon = Icons.Default.Notifications,
                        title = "Notifications",
                        description = "Enables foreground service progress indicators so long transfers continue uninterrupted if you switch apps."
                    )
                }
            }

            // Troubleshooting FAQs
            Text(
                text = "Common Troubleshooting",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            )

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TroubleshootItem(
                        question = "Device doesn't appear in the scan list?",
                        answer = "1. Make sure the receiving phone has the app open on the 'Receive' screen.\n2. Tap the QR Code icon and scan the receiver's screen directly.\n3. Alternatively, tap 'Direct IP' in discovery and type the receiver's IP displayed on their Receive screen."
                    )
                    TroubleshootItem(
                        question = "Transfer interrupted or connection failed?",
                        answer = "Ensure both phones stay within Wi-Fi range. Some guest or public Wi-Fi networks block device-to-device communication (AP Isolation). In this case, simply enable Portable Hotspot on one phone and connect the other to it."
                    )
                    TroubleshootItem(
                        question = "Does this use mobile data or internet?",
                        answer = "No. Quick Transfer operates exclusively over local socket connections (Wi-Fi, Hotspot, and Wi-Fi Direct). Zero cellular data is consumed."
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun StepItem(icon: ImageVector, title: String, description: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
            Spacer(modifier = Modifier.height(2.dp))
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PermissionExplanationRow(icon: ImageVector, title: String, description: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            Spacer(modifier = Modifier.height(2.dp))
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun TroubleshootItem(question: String, answer: String) {
    Column {
        Text(question, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
        Spacer(modifier = Modifier.height(3.dp))
        Text(answer, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
