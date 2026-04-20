/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meta.wearable.dat.externalsampleapps.cameraaccess.stream.ChatMessage

/**
 * ChatGPT-style scrollable conversation history.
 * - User messages right-aligned, blue.
 * - Hank's messages left-aligned, dark gray.
 * - Auto-scrolls to the bottom when new messages arrive.
 */
@Composable
fun ChatPanel(
    messages: List<ChatMessage>,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Box(
        modifier =
            modifier
                .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
                .padding(8.dp),
    ) {
        if (messages.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Hank's listening — just start talking.",
                    color = Color(0xFF9CA3AF),
                    fontSize = 13.sp,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(messages) { msg -> Bubble(msg) }
            }
        }
    }
}

@Composable
private fun Bubble(message: ChatMessage) {
    val isUser = message.role == ChatMessage.Role.USER
    val bg = if (isUser) Color(0xFF2563EB) else Color(0xFF1F2937)
    val align = if (isUser) Alignment.End else Alignment.Start
    val label = if (isUser) "You" else "Hank"
    val labelColor = if (isUser) Color(0xFFBFDBFE) else Color(0xFF9CA3AF)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = align,
    ) {
        Text(
            text = label,
            color = labelColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
            Box(
                modifier =
                    Modifier
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
                        .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = message.text,
                    color = Color.White,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}
