/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.hankdaisy.ui

import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meta.wearable.dat.externalsampleapps.hankdaisy.chat.ChatOnlyViewModel
import com.meta.wearable.dat.externalsampleapps.hankdaisy.stream.ChatMessage
import java.io.File
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

    val context = LocalContext.current

    // System photo picker — works on Android 13+ natively, back-compat
    // on older versions via Play Services. No storage permission needed.
    val pickImage =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia(),
            onResult = { uri -> if (uri != null) vm.attachImage(uri) },
        )

    // Camera capture — writes to a temp JPEG in cacheDir whose URI we
    // hand to the camera app via FileProvider. We hold the URI in
    // `captureUri` so the result handler can re-read it after the user
    // returns from the camera. No CAMERA permission needed because the
    // intent delegates capture to a system camera app.
    val captureUri = remember { mutableStateOf<Uri?>(null) }
    val takePhoto =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.TakePicture(),
            onResult = { success ->
                if (success) captureUri.value?.let { uri -> vm.attachImage(uri) }
            },
        )

    // Generic file picker. We accept any mime so the user can pick PDFs,
    // CSVs, etc., but Hank only sees images today — anything else gets
    // a Toast nudge instead of a silent no-op.
    val pickFile =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
            onResult = { uri ->
                if (uri == null) return@rememberLauncherForActivityResult
                val mime = context.contentResolver.getType(uri)
                if (mime?.startsWith("image/") == true) {
                    vm.attachImage(uri)
                } else {
                    Toast.makeText(
                            context,
                            "Only images can be sent to Hank right now.",
                            Toast.LENGTH_SHORT,
                        )
                        .show()
                }
            },
        )

    var attachMenuOpen by remember { mutableStateOf(false) }

    // Spawn a fresh capture URI + launch the camera app. The file MUST
    // live under one of the directories declared in res/xml/file_paths
    // so FileProvider can serve it back to the camera app — `images/` is
    // the existing cache-path entry, so we land captures there.
    fun launchCamera() {
        val dir = File(context.cacheDir, "images").apply { mkdirs() }
        val file = File(dir, "camera_${System.currentTimeMillis()}.jpg")
        val uri =
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
        captureUri.value = uri
        takePhoto.launch(uri)
    }

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
                // + opens a popover above the button with three ways to
                // attach: take a photo, pick from the gallery, pick a
                // generic file. Box anchors the DropdownMenu directly
                // above the IconButton.
                Box {
                    IconButton(
                        icon = Icons.Outlined.Add,
                        contentDescription = "Attach",
                        highlighted = attachMenuOpen || attached != null,
                        onClick = { attachMenuOpen = true },
                    )
                    DropdownMenu(
                        expanded = attachMenuOpen,
                        onDismissRequest = { attachMenuOpen = false },
                        modifier =
                            Modifier.background(AppColors.Surface),
                    ) {
                        DropdownMenuItem(
                            text = { Text("Take photo", color = AppColors.TextPrimary) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.PhotoCamera,
                                    contentDescription = null,
                                    tint = AppColors.Accent,
                                )
                            },
                            onClick = {
                                attachMenuOpen = false
                                launchCamera()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Upload image", color = AppColors.TextPrimary) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Image,
                                    contentDescription = null,
                                    tint = AppColors.Accent,
                                )
                            },
                            onClick = {
                                attachMenuOpen = false
                                pickImage.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                                    ),
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Upload file", color = AppColors.TextPrimary) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.AttachFile,
                                    contentDescription = null,
                                    tint = AppColors.Accent,
                                )
                            },
                            onClick = {
                                attachMenuOpen = false
                                pickFile.launch("*/*")
                            },
                        )
                    }
                }
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
