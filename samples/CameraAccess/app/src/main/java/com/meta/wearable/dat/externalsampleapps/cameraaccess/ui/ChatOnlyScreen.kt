/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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

    // System photo picker — works on Android 13+ natively, back-compat
    // on older versions via Play Services. No storage permission needed.
    val pickImage =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia(),
            onResult = { uri -> if (uri != null) vm.attachImage(uri) },
        )

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

            // Attached-image preview — only visible after the user picks
            // an image via the 📎 button. Compact strip with a thumbnail
            // and an X to drop the attachment before sending.
            val attached = state.attachedImage
            if (attached != null) {
                Row(
                    modifier =
                        Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(AppColors.Background),
                    ) {
                        Image(
                            bitmap = attached.asImageBitmap(),
                            contentDescription = "Attached image preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(52.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Image attached",
                            color = AppColors.TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Sent with your next message",
                            color = AppColors.TextSecondary,
                            fontSize = 11.sp,
                        )
                    }
                    Box(
                        modifier =
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(AppColors.SurfaceAlt)
                                .clickable { vm.clearAttachedImage() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Remove attached image",
                            tint = AppColors.TextSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            val canSend = input.isNotBlank() || attached != null
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Attach image — left of the text field so it reads like
                // "add something, then type", matching most chat UIs.
                IconButton(
                    icon = Icons.Outlined.Image,
                    contentDescription = "Attach image",
                    highlighted = attached != null,
                    onClick = {
                        pickImage.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly,
                            ),
                        )
                    },
                )
                Spacer(Modifier.width(6.dp))
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
                                if (canSend) {
                                    vm.sendTyped(input)
                                    input = ""
                                }
                            },
                        ),
                )
                Spacer(Modifier.width(6.dp))
                // Mic — forces the recognizer into focused-listen mode.
                // Fills teal while state.isListening so the user can tell
                // at a glance whether Hank's actively capturing.
                IconButton(
                    icon = Icons.Outlined.Mic,
                    contentDescription = "Talk to Hank",
                    highlighted = state.isListening,
                    onClick = { vm.triggerVoiceCapture() },
                )
                Spacer(Modifier.width(6.dp))
                // Send — accent filled, same language as the Convos FAB.
                Box(
                    modifier =
                        Modifier
                            .size(48.dp)
                            .background(
                                if (canSend) AppColors.Accent
                                else AppColors.SurfaceAlt,
                                shape = CircleShape,
                            )
                            .clickable(enabled = canSend) {
                                if (canSend) {
                                    vm.sendTyped(input)
                                    input = ""
                                }
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Send,
                        contentDescription = "Send",
                        tint =
                            if (canSend) AppColors.AccentOn else AppColors.TextMuted,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

/** Square icon button used for attach + mic. Highlights accent-filled
 *  when [highlighted] so the "active" state reads at a glance (e.g.
 *  while the mic is actively listening, or while an image is staged). */
@Composable
private fun IconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (highlighted) AppColors.Accent else AppColors.SurfaceAlt
    val tint = if (highlighted) AppColors.AccentOn else AppColors.TextPrimary
    Box(
        modifier =
            Modifier
                .size(44.dp)
                .background(bg, shape = CircleShape)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

