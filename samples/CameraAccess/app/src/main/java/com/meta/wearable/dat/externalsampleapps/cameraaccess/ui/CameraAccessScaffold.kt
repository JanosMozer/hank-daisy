/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.meta.wearable.dat.externalsampleapps.cameraaccess.BuildConfig
import com.meta.wearable.dat.externalsampleapps.cameraaccess.session.SessionViewModel
import com.meta.wearable.dat.externalsampleapps.cameraaccess.session.ThemeMode
import com.meta.wearable.dat.externalsampleapps.cameraaccess.stream.GeminiService
import com.meta.wearable.dat.externalsampleapps.cameraaccess.wearables.WearablesViewModel
import kotlinx.coroutines.launch

private fun initialsOf(name: String): String =
    name.trim().split(" ").take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraAccessScaffold(
    viewModel: WearablesViewModel,
    onRequestWearablesPermission: suspend (Permission) -> PermissionStatus,
    modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val sessionVm: SessionViewModel = viewModel()
  val sessionState by sessionVm.uiState.collectAsStateWithLifecycle()
  val geminiService = remember { GeminiService() }
  val scope = rememberCoroutineScope()
  val activity = LocalActivity.current as? ComponentActivity

  LaunchedEffect(uiState.recentError) {
    uiState.recentError?.let { errorMessage ->
      snackbarHostState.showSnackbar(errorMessage)
      viewModel.clearRecentError()
    }
  }

  LaunchedEffect(sessionState.streamRequested) {
    if (sessionState.streamRequested && !uiState.isStreaming) {
      viewModel.navigateToStreaming(onRequestWearablesPermission)
      sessionVm.clearStreamRequest()
    }
  }

  val systemDark = isSystemInDarkTheme()
  val basePalette =
      when (sessionState.settings.themeMode) {
        ThemeMode.DARK -> DarkPalette
        ThemeMode.LIGHT -> LightPalette
        ThemeMode.SYSTEM -> if (systemDark) DarkPalette else LightPalette
      }
  // High-contrast tweaks: collapse the secondary/muted greys toward the
  // primary text colour and darken the border so dividers + caption text
  // stand out for users who need the extra contrast.
  val palette =
      if (sessionState.settings.highContrast) {
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
          fontScale = baseDensity.fontScale * sessionState.settings.textScale.factor,
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
          uiState.isStreaming ->
              StreamScreen(
                  wearablesViewModel = viewModel,
                  onSessionEnd = { sessionVm.saveStreamSession(it) },
              )
          !uiState.isRegistered ->
              HomeScreen(viewModel = viewModel)
          sessionState.profileOpen ->
              ProfileScreen(
                  profile = sessionState.userProfile,
                  onBack = { sessionVm.closeProfile() },
                  onSave = { sessionVm.updateProfile(it) },
              )
          sessionState.viewingSessionId != null -> {
            val session =
                sessionState.sessions.firstOrNull { it.id == sessionState.viewingSessionId }
            if (session != null) {
              val ctx = activity?.applicationContext
              ChatHistoryScreen(
                  title = session.title,
                  messages = session.messages,
                  onBack = { sessionVm.closeSession() },
                  onSummariseAndExport =
                      if (ctx != null) {
                        {
                          scope.launch {
                            ChatSummaryPdf.summariseAndShare(
                                context = ctx,
                                session = session,
                                gemini = geminiService,
                            )
                          }
                        }
                      } else null,
              )
            } else {
              sessionVm.closeSession()
              MainTabs(
                  sessionVm = sessionVm,
                  onOpenProfile = { sessionVm.openProfile() },
              )
            }
          }
          else -> MainTabs(sessionVm = sessionVm, onOpenProfile = { sessionVm.openProfile() })
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
                      contentDescription = "Camera Access error",
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

/** Tab host — chat list / tips / settings / profile — sharing a bottom nav. */
@Composable
private fun MainTabs(
    sessionVm: SessionViewModel,
    onOpenProfile: () -> Unit,
) {
  val state by sessionVm.uiState.collectAsStateWithLifecycle()
  Column(modifier = Modifier.fillMaxSize()) {
    Box(modifier = Modifier.weight(1f)) {
      when (state.currentTab) {
        AppTab.CHATS ->
            SessionsHomeScreen(
                sessions = state.sessions,
                userInitials = initialsOf(state.userProfile.name),
                onOpenSession = { sessionVm.openSession(it) },
                onNewSession = { sessionVm.requestNewSession() },
                onOpenProfile = onOpenProfile,
            )
        AppTab.TIPS -> TipsScreen()
        AppTab.SETTINGS ->
            SettingsScreen(
                settings = state.settings,
                onThemeChange = { sessionVm.updateSettings(state.settings.copy(themeMode = it)) },
                onTextScaleChange = {
                  sessionVm.updateSettings(state.settings.copy(textScale = it))
                },
                onHighContrastChange = {
                  sessionVm.updateSettings(state.settings.copy(highContrast = it))
                },
                onHapticChange = {
                  sessionVm.updateSettings(state.settings.copy(hapticFeedback = it))
                },
            )
      }
    }
    BottomNav(current = state.currentTab, onSelect = { sessionVm.selectTab(it) })
  }
}
