package com.example

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.navigation.NavRoutes
import com.example.ui.screens.ActiveTransferScreen
import com.example.ui.screens.DiscoveryScreen
import com.example.ui.screens.FilePickerScreen
import com.example.ui.screens.HelpScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.IncomingRequestDialog
import com.example.ui.screens.PairingScreen
import com.example.ui.screens.QrPairingScreen
import com.example.ui.screens.ReceivedFilesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.TransferCompletedScreen
import com.example.ui.screens.TransferFailedScreen
import com.example.ui.theme.QuickTransferTheme
import com.example.viewmodel.HistoryViewModel
import com.example.viewmodel.SettingsViewModel
import com.example.viewmodel.TransferViewModel

class MainActivity : ComponentActivity() {

    private val transferViewModel: TransferViewModel by viewModels()
    private val historyViewModel: HistoryViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIncomingShareIntent(intent)

        setContent {
            val prefs by settingsViewModel.preferences.collectAsState()
            val isDark = prefs.darkTheme ?: isSystemInDarkTheme()

            QuickTransferTheme(darkTheme = isDark) {
                // Request runtime permissions gracefully on startup
                RequestAppPermissions()

                val navController = rememberNavController()
                val incomingReq by transferViewModel.incomingRequest.collectAsState()

                Scaffold(modifier = Modifier.fillMaxSize()) { _ ->
                    NavHost(
                        navController = navController,
                        startDestination = NavRoutes.SPLASH
                    ) {
                        composable(NavRoutes.SPLASH) {
                            SplashScreen(
                                onSplashFinished = {
                                    navController.navigate(NavRoutes.HOME) {
                                        popUpTo(NavRoutes.SPLASH) { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable(NavRoutes.HOME) {
                            HomeScreen(
                                transferViewModel = transferViewModel,
                                historyViewModel = historyViewModel,
                                onNavigateToSend = {
                                    navController.navigate(NavRoutes.FILE_PICKER)
                                },
                                onNavigateToReceive = {
                                    navController.navigate(NavRoutes.PAIRING)
                                },
                                onNavigateToReceivedFiles = {
                                    navController.navigate(NavRoutes.RECEIVED_FILES)
                                },
                                onNavigateToHistory = {
                                    navController.navigate(NavRoutes.HISTORY)
                                },
                                onNavigateToQrPairing = {
                                    navController.navigate(NavRoutes.QR_PAIRING)
                                },
                                onNavigateToSettings = {
                                    navController.navigate(NavRoutes.SETTINGS)
                                },
                                onNavigateToHelp = {
                                    navController.navigate(NavRoutes.HELP)
                                }
                            )
                        }

                        composable(NavRoutes.FILE_PICKER) {
                            FilePickerScreen(
                                transferViewModel = transferViewModel,
                                onBack = { navController.popBackStack() },
                                onProceedToDiscovery = {
                                    navController.navigate(NavRoutes.DISCOVERY)
                                }
                            )
                        }

                        composable(NavRoutes.DISCOVERY) {
                            DiscoveryScreen(
                                transferViewModel = transferViewModel,
                                onBack = { navController.popBackStack() },
                                onDeviceSelected = {
                                    navController.navigate(NavRoutes.ACTIVE_TRANSFER)
                                },
                                onNavigateToQrScan = {
                                    navController.navigate(NavRoutes.QR_PAIRING)
                                }
                            )
                        }

                        composable(NavRoutes.PAIRING) {
                            PairingScreen(
                                transferViewModel = transferViewModel,
                                onBack = { navController.popBackStack() },
                                onNavigateToQrDisplay = {
                                    navController.navigate(NavRoutes.QR_PAIRING)
                                }
                            )
                        }

                        composable(NavRoutes.QR_PAIRING) {
                            QrPairingScreen(
                                transferViewModel = transferViewModel,
                                onBack = { navController.popBackStack() },
                                onSessionPaired = {
                                    navController.navigate(NavRoutes.ACTIVE_TRANSFER)
                                }
                            )
                        }

                        composable(NavRoutes.ACTIVE_TRANSFER) {
                            ActiveTransferScreen(
                                transferViewModel = transferViewModel,
                                onTransferCompleted = {
                                    navController.navigate(NavRoutes.TRANSFER_COMPLETED) {
                                        popUpTo(NavRoutes.ACTIVE_TRANSFER) { inclusive = true }
                                    }
                                },
                                onTransferFailed = {
                                    navController.navigate(NavRoutes.TRANSFER_FAILED) {
                                        popUpTo(NavRoutes.ACTIVE_TRANSFER) { inclusive = true }
                                    }
                                },
                                onCancelled = {
                                    navController.navigate(NavRoutes.HOME) {
                                        popUpTo(NavRoutes.HOME) { inclusive = false }
                                    }
                                }
                            )
                        }

                        composable(NavRoutes.TRANSFER_COMPLETED) {
                            TransferCompletedScreen(
                                transferViewModel = transferViewModel,
                                onDone = {
                                    transferViewModel.resetTransferState()
                                    navController.navigate(NavRoutes.HOME) {
                                        popUpTo(NavRoutes.HOME) { inclusive = false }
                                    }
                                },
                                onViewReceivedFiles = {
                                    transferViewModel.resetTransferState()
                                    navController.navigate(NavRoutes.RECEIVED_FILES) {
                                        popUpTo(NavRoutes.HOME) { inclusive = false }
                                    }
                                }
                            )
                        }

                        composable(NavRoutes.TRANSFER_FAILED) {
                            TransferFailedScreen(
                                transferViewModel = transferViewModel,
                                onRetry = {
                                    navController.navigate(NavRoutes.DISCOVERY) {
                                        popUpTo(NavRoutes.TRANSFER_FAILED) { inclusive = true }
                                    }
                                },
                                onBackToHome = {
                                    transferViewModel.resetTransferState()
                                    navController.navigate(NavRoutes.HOME) {
                                        popUpTo(NavRoutes.HOME) { inclusive = false }
                                    }
                                }
                            )
                        }

                        composable(NavRoutes.RECEIVED_FILES) {
                            ReceivedFilesScreen(
                                historyViewModel = historyViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(NavRoutes.HISTORY) {
                            HistoryScreen(
                                historyViewModel = historyViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(NavRoutes.SETTINGS) {
                            SettingsScreen(
                                settingsViewModel = settingsViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(NavRoutes.HELP) {
                            HelpScreen(onBack = { navController.popBackStack() })
                        }
                    }

                    // Global incoming request dialog
                    incomingReq?.let { req ->
                        IncomingRequestDialog(
                            request = req,
                            onAccept = {
                                req.onAccept()
                                navController.navigate(NavRoutes.ACTIVE_TRANSFER)
                            },
                            onReject = {
                                req.onReject()
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingShareIntent(intent)
    }

    private fun handleIncomingShareIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val type = intent.type

        if (Intent.ACTION_SEND == action && type != null) {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            uri?.let {
                transferViewModel.addSelectedUris(listOf(it))
            }
        } else if (Intent.ACTION_SEND_MULTIPLE == action && type != null) {
            val uris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
            }
            uris?.let {
                transferViewModel.addSelectedUris(it)
            }
        }
    }
}

@Composable
fun RequestAppPermissions() {
    val permissionsToRequest = mutableListOf<String>()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissionsToRequest.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
        permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permissions handled conditionally
    }

    LaunchedEffect(Unit) {
        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}
