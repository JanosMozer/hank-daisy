/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meta.wearable.dat.externalsampleapps.cameraaccess.chat.ChatOnlyViewModel
import com.meta.wearable.dat.externalsampleapps.cameraaccess.stream.ChatMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A glasses-free Hank chat. Voice in (phone mic), voice out (phone speaker
 * or any active BT route), text input as an alternative. Saves the chat
 * as a Session via [onSessionEnd] when leaving the screen, just like the
 * stream path.
 */
@Composable
fun ChatOnlyScreen(
    onBack: () -> Unit,
    onSessionEnd: (List<ChatMessage>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: ChatOnlyViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    DisposableEffect(Unit) {
        vm.start()
        onDispose {
            val msgs = vm.uiState.value.chatMessages
            if (msgs.isNotEmpty()) onSessionEnd(msgs)
            vm.stop()
        }
    }

    LaunchedEffect(state.chatMessages.size) {
        if (state.chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(state.chatMessages.size - 1)
        }
    }

    // NOTE: do NOT put .imePadding() here — applying IME insets at the
    // root pushes the whole screen (top bar included) up by the keyboard
    // height, which feels jarring. Only the bottom input bar needs to lift.
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(AppColors.Background)
                .statusBarsPadding(),
    ) {
        // Top bar
        Row(
            modifier =
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .background(AppColors.SurfaceAlt, shape = RoundedCornerShape(10.dp))
                        .clickable(onClick = onBack)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = AppColors.TextPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Chat with Hank",
                    color = AppColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "voice + text · no camera",
                    color = AppColors.TextSecondary,
                    fontSize = 11.sp,
                )
            }
            StatusPill(
                isListening = state.isListening,
                isAnalyzing = state.isAnalyzing,
                isSpeaking = state.isHankSpeaking,
            )
        }

        // Messages
        Box(modifier = Modifier.weight(1f)) {
            if (state.chatMessages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Just start talking — or type below.",
                            color = AppColors.TextSecondary,
                            fontSize = 14.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "No glasses needed. Hank's listening.",
                            color = AppColors.TextMuted,
                            fontSize = 11.sp,
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    items(state.chatMessages) { msg -> ChatTurn(msg) }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }

        // Bottom input bar — IME inset applied here only so the keyboard
        // lifts the input above itself without disturbing the rest of the
        // screen. Navigation-bar inset stays scoped to the bottom too.
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(AppColors.Surface)
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(12.dp),
        ) {
            Spacer(
                modifier =
                    Modifier.fillMaxWidth().height(1.dp).background(AppColors.Border),
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("Type a message…", color = AppColors.TextMuted) },
                    singleLine = false,
                    maxLines = 4,
                    modifier = Modifier.weight(1f),
                    colors =
                        TextFieldDefaults.colors(
                            focusedContainerColor = AppColors.Background,
                            unfocusedContainerColor = AppColors.Background,
                            focusedTextColor = AppColors.TextPrimary,
                            unfocusedTextColor = AppColors.TextPrimary,
                            focusedIndicatorColor = AppColors.Accent,
                            unfocusedIndicatorColor = AppColors.Border,
                            cursorColor = AppColors.Accent,
                        ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions =
                        KeyboardActions(
                            onSend = {
                                if (input.isNotBlank()) {
                                    vm.sendTyped(input)
                                    input = ""
                                }
                            },
                        ),
                )
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier =
                        Modifier
                            .size(48.dp)
                            .background(AppColors.Accent, shape = CircleShape)
                            .clickable(enabled = input.isNotBlank()) {
                                if (input.isNotBlank()) {
                                    vm.sendTyped(input)
                                    input = ""
                                }
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Send,
                        contentDescription = "Send",
                        tint = AppColors.AccentOn,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

