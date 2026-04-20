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

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(AppColors.Background)
                .statusBarsPadding()
                .imePadding()
                .navigationBarsPadding(),
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
                    items(state.chatMessages) { msg -> ChatRow(msg) }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }

        // Bottom input bar
        Column(
            modifier =
                Modifier.fillMaxWidth().background(AppColors.Surface).padding(12.dp),
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

@Composable
private fun ChatRow(message: ChatMessage) {
    val isUser = message.role == ChatMessage.Role.USER
    val bg = if (isUser) AppColors.UserBubble else AppColors.HankBubble
    val fg = if (isUser) AppColors.UserBubbleText else AppColors.HankBubbleText
    val align = if (isUser) Alignment.End else Alignment.Start
    val label = if (isUser) "You" else "Hank"
    val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(message.timestamp))

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = align) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!isUser) {
                Text(label, color = AppColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(6.dp))
                Text(ts, color = AppColors.TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            } else {
                Text(ts, color = AppColors.TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.width(6.dp))
                Text(label, color = AppColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(3.dp))
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(0.9f)
                    .background(
                        bg,
                        shape =
                            RoundedCornerShape(
                                topStart = 14.dp,
                                topEnd = 14.dp,
                                bottomStart = if (isUser) 14.dp else 4.dp,
                                bottomEnd = if (isUser) 4.dp else 14.dp,
                            ),
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(text = message.text, color = fg, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}
