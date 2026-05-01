/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.hankdaisy.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.meta.wearable.dat.externalsampleapps.hankdaisy.BuildConfig
import com.meta.wearable.dat.externalsampleapps.hankdaisy.session.CaptureMode
import com.meta.wearable.dat.externalsampleapps.hankdaisy.session.SessionViewModel
import com.meta.wearable.dat.externalsampleapps.hankdaisy.session.ThemeMode
import com.meta.wearable.dat.externalsampleapps.hankdaisy.wearables.WearablesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HankDaisyScaffold(
    viewModel: WearablesViewModel,
    onRequestWearablesPermission: suspend (Permission) -> PermissionStatus,
    modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val sessionVm: SessionViewModel = viewModel()
  val sessionState by sessionVm.uiState.collectAsStateWithLifecycle()
  val captureMode = sessionState.config.demo.captureMode

  LaunchedEffect(uiState.recentError) {
    uiState.recentError?.let { errorMessage ->
      snackbarHostState.showSnackbar(errorMessage)
      viewModel.clearRecentError()
    }
  }

  // Fallback listener for any code path that still sets streamRequested —
  // kept for safety even though the + button now calls navigateToStreaming
  // directly.
  LaunchedEffect(sessionState.streamRequested) {
    if (sessionState.streamRequested && !uiState.isStreaming) {
      viewModel.navigateToStreaming(onRequestWearablesPermission)
      sessionVm.clearStreamRequest()
    }
  }

  // Direct lambda the demo entrypoint fires — avoids the streamRequested
  // indirection so the stream start is unconditional.
  val startGlassesStream: () -> Unit = {
    viewModel.navigateToStreaming(onRequestWearablesPermission)
  }

  val systemDark = isSystemInDarkTheme()
  val basePalette =
      when (sessionState.config.general.themeMode) {
        ThemeMode.DARK -> DarkPalette
        ThemeMode.LIGHT -> LightPalette
        ThemeMode.SYSTEM -> if (systemDark) DarkPalette else LightPalette
      }
  // High-contrast tweaks: collapse the secondary/muted greys toward the
  // primary text colour and darken the border so dividers + caption text
  // stand out for users who need the extra contrast.
  val palette =
      if (sessionState.config.general.highContrast) {
        basePalette.copy(
            TextSecondary = basePalette.TextPrimary,
            TextMuted = basePalette.TextPrimary.copy(alpha = 0.75f),
            Border = basePalette.TextPrimary.copy(alpha = 0.45f),
        )
      } else {
        basePalette
      }
  // Text-scale: rebuild Density with a multiplied fontScale so EVERY .sp
  // text in the tree resizes uniformly without touching individual screens.
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
          !sessionState.splashShown ->
              SplashScreen(onFinished = { sessionVm.markSplashDone() })
          uiState.isStreaming && captureMode == CaptureMode.GLASSES ->
              StreamScreen(
                  wearablesViewModel = viewModel,
              )
          uiState.isStreaming && captureMode == CaptureMode.PHONE_CAMERA ->
              PhoneCameraStreamScreen(
                  wearablesViewModel = viewModel,
              )
          !uiState.isRegistered &&
              captureMode == CaptureMode.GLASSES &&
              sessionState.currentTab == TopLevelTab.DEMO ->
              HomeScreen(
                  viewModel = viewModel,
                  onUsePhoneMode = {
                    sessionVm.updateConfig(
                        sessionState.config.copy(
                            demo = sessionState.config.demo.copy(captureMode = CaptureMode.PHONE_CAMERA),
                        ),
                    )
                  },
              )
          else ->
              MainTabs(
                  sessionVm = sessionVm,
                  onStartStream = startGlassesStream,
                  isStreamingNow = uiState.isStreaming,
              )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 32.dp),
            snackbar = { data ->
              Snackbar(
                  shape = RoundedCornerShape(24.dp),
                  containerColor = MaterialTheme.colorScheme.errorContainer,
                  contentColor = MaterialTheme.colorScheme.onErrorContainer,
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                      imageVector = Icons.Default.Error,
                      contentDescription = "HankDaisy error",
                      tint = MaterialTheme.colorScheme.error,
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(data.visuals.message)
                }
              }
            },
        )

        if (BuildConfig.DEBUG) {
          FloatingActionButton(
              onClick = { viewModel.showDebugMenu() },
              modifier = Modifier.align(Alignment.CenterEnd),
          ) {
            Icon(Icons.Default.BugReport, contentDescription = "Debug Menu")
          }

          if (uiState.isDebugMenuVisible) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.hideDebugMenu() },
                sheetState = bottomSheetState,
                modifier = Modifier.fillMaxSize(),
            ) {
              MockDeviceKitScreen(modifier = Modifier.fillMaxSize())
            }
          }
        }
      }
    }
  }
}

/** Top-level shell: Demo, Pipeline, Settings. */
@Composable
private fun MainTabs(
    sessionVm: SessionViewModel,
    onStartStream: () -> Unit,
    isStreamingNow: Boolean = false,
) {
  val state by sessionVm.uiState.collectAsStateWithLifecycle()
  val selectedTab = state.currentTab
  Column(modifier = Modifier.fillMaxSize()) {
    Box(modifier = Modifier.weight(1f)) {
      when (selectedTab) {
        TopLevelTab.DEMO ->
            DemoScreen(
                config = state.config,
                isStreaming = isStreamingNow,
                onStartDemo = onStartStream,
                onCaptureModeChange = {
                  sessionVm.updateConfig(
                      state.config.copy(
                          demo = state.config.demo.copy(captureMode = it),
                      ),
                  )
                },
            )
        TopLevelTab.PIPELINE ->
            PipelineScreen(
                config = state.config,
                currentSection = state.pipelineSection,
                onSectionChange = { sessionVm.selectPipelineSection(it) },
                onConfigChange = { sessionVm.updateConfig(it) },
            )
        TopLevelTab.SETTINGS ->
            SettingsScreen(
                settings = state.config.general,
                onWorkDomainChange = {
                  sessionVm.updateConfig(
                      state.config.copy(
                          general = state.config.general.copy(workDomain = it),
                      ),
                  )
                },
                onDemoCommentaryModeChange = {
                  sessionVm.updateConfig(
                      state.config.copy(
                          general = state.config.general.copy(demoCommentaryMode = it),
                      ),
                  )
                },
                onThemeChange = {
                  sessionVm.updateConfig(
                      state.config.copy(general = state.config.general.copy(themeMode = it)),
                  )
                },
                onTextScaleChange = {
                  sessionVm.updateConfig(
                      state.config.copy(general = state.config.general.copy(textScale = it)),
                  )
                },
                onHighContrastChange = {
                  sessionVm.updateConfig(
                      state.config.copy(
                          general = state.config.general.copy(highContrast = it),
                      ),
                  )
                },
                onHapticChange = {
                  sessionVm.updateConfig(
                      state.config.copy(
                          general = state.config.general.copy(hapticFeedback = it),
                      ),
                  )
                },
            )
      }
    }
    BottomNav(
        current = selectedTab,
        tabs = TopLevelTab.values().toList(),
        onSelect = { sessionVm.selectTab(it) },
    )
  }
}
