package com.meta.wearable.dat.externalsampleapps.mpi.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.meta.wearable.dat.externalsampleapps.mpi.BuildConfig
import com.meta.wearable.dat.externalsampleapps.mpi.session.CaptureVideoSource
import com.meta.wearable.dat.externalsampleapps.mpi.session.SessionViewModel
import com.meta.wearable.dat.externalsampleapps.mpi.session.ThemeMode
import com.meta.wearable.dat.externalsampleapps.mpi.wearables.WearablesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HankDaisyScaffold(
    viewModel: WearablesViewModel,
    onRequestWearablesPermission: suspend (Permission) -> PermissionStatus,
    modifier: Modifier = Modifier,
) {
    val wearablesState by viewModel.uiState.collectAsStateWithLifecycle()
    val sessionVm: SessionViewModel = viewModel()
    val sessionState by sessionVm.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val activity = LocalActivity.current as? ComponentActivity

    LaunchedEffect(wearablesState.recentError) {
        wearablesState.recentError?.let { errorMessage ->
            snackbarHostState.showSnackbar(errorMessage)
            viewModel.clearRecentError()
        }
    }

    val systemDark = isSystemInDarkTheme()
    val basePalette =
        when (sessionState.config.general.themeMode) {
            ThemeMode.LIGHT -> LightPalette
            ThemeMode.DARK -> DarkPalette
            ThemeMode.SYSTEM -> if (systemDark) DarkPalette else LightPalette
        }
    val palette =
        if (sessionState.config.general.highContrast) {
            basePalette.copy(
                SurfaceAlt = basePalette.Surface,
                Border = basePalette.Accent,
                TextSecondary = basePalette.TextPrimary,
                TextMuted = basePalette.TextPrimary,
            )
        } else {
            basePalette
        }

    val baseDensity = LocalDensity.current
    val scaledDensity =
        Density(
            density = baseDensity.density,
            fontScale = baseDensity.fontScale * sessionState.config.general.textScale.factor,
        )

    CompositionLocalProvider(
        LocalAppPalette provides palette,
        LocalDensity provides scaledDensity,
    ) {
        Surface(modifier = modifier.fillMaxSize(), color = AppColors.Background) {
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    wearablesState.isStreaming &&
                        sessionState.config.capture.videoSource == CaptureVideoSource.GLASSES ->
                        StreamScreen(
                            wearablesViewModel = viewModel,
                            onSessionEnd = { messages, evidence ->
                                sessionVm.saveStreamSession(messages, evidence)
                            },
                            onHankModeChange = { mode ->
                                sessionVm.updateCaptureConfig { it.copy(hankMode = mode) }
                            },
                        )

                    sessionState.isPhoneCaptureActive ->
                        PhoneCameraStreamScreen(
                            onBack = { sessionVm.endPhoneCapture() },
                            onSessionEnd = { messages, evidence ->
                                sessionVm.saveStreamSession(messages, evidence)
                            },
                            onHankModeChange = { mode ->
                                sessionVm.updateCaptureConfig { it.copy(hankMode = mode) }
                            },
                        )

                    else ->
                        Column(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.weight(1f, fill = true)) {
                                when (sessionState.currentTab) {
                                    AppTab.CAPTURE ->
                                        CaptureScreen(
                                            config = sessionState.config,
                                            isGlassesRegistered = wearablesState.isRegistered,
                                            hasActiveGlasses = wearablesState.hasActiveDevice,
                                            onConfigChange = { sessionVm.updateConfig(it) },
                                            onConnectGlasses = {
                                                activity?.let { viewModel.startRegistration(it) }
                                            },
                                            onStartCapture = {
                                                if (sessionState.config.capture.videoSource == CaptureVideoSource.GLASSES) {
                                                    viewModel.navigateToStreaming(onRequestWearablesPermission)
                                                } else {
                                                    sessionVm.beginPhoneCapture()
                                                }
                                            },
                                        )

                                    AppTab.REPORT ->
                                        InfoReportScreen(session = sessionVm.latestSession())

                                    AppTab.SETTINGS ->
                                        SettingsScreen(
                                            config = sessionState.config,
                                            onConfigChange = { sessionVm.updateConfig(it) },
                                        )
                                }
                            }
                            BottomNav(
                                current = sessionState.currentTab,
                                onSelect = { sessionVm.selectTab(it) },
                            )
                        }
                }

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )

                if (BuildConfig.DEBUG) {
                    FloatingActionButton(
                        onClick = { viewModel.showDebugMenu() },
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.align(Alignment.TopEnd),
                    ) {
                        Icon(Icons.Filled.BugReport, contentDescription = "Debug")
                    }
                }

                if (wearablesState.isDebugMenuVisible) {
                    ModalBottomSheet(
                        onDismissRequest = { viewModel.hideDebugMenu() },
                    ) {
                        MockDeviceKitScreen()
                    }
                }
            }
        }
    }
}
