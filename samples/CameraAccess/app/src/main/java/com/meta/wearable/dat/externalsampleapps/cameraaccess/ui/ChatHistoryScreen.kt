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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meta.wearable.dat.externalsampleapps.cameraaccess.stream.ChatMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full-screen chat view. Caller supplies the list of messages plus an
 * optional Export callback. Works for both the active stream's live chat
 * (observing StreamViewModel.uiState.chatMessages) and a saved past session.
 */
@Composable
fun ChatHistoryScreen(
    title: String,
    messages: List<ChatMessage>,
    onBack: () -> Unit,
    onExport: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(AppColors.Background)
                .statusBarsPadding()
                .navigationBarsPadding(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .background(AppColors.SurfaceAlt, shape = RoundedCornerShape(8.dp))
                        .clickable(onClick = onBack)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "‹ Back",
                    color = AppColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = AppColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${messages.size} turns",
                    color = AppColors.TextSecondary,
                    fontSize = 11.sp,
                )
            }
            if (onExport != null && messages.isNotEmpty()) {
                Box(
                    modifier =
                        Modifier
                            .background(AppColors.SurfaceAlt, shape = RoundedCornerShape(8.dp))
                            .clickable(onClick = onExport)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "↑ Export",
                        color = AppColors.TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        if (messages.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No turns yet.",
                    color = AppColors.TextSecondary,
                    fontSize = 13.sp,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                items(messages) { msg -> ChatRow(msg) }
                item { Spacer(Modifier.height(24.dp)) }
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
    val labelColor = AppColors.TextMuted
    val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(message.timestamp))

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = align,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!isUser) {
                Text(label, color = labelColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(6.dp))
                Text(ts, color = AppColors.TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            } else {
                Text(ts, color = AppColors.TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.width(6.dp))
                Text(label, color = labelColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
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
