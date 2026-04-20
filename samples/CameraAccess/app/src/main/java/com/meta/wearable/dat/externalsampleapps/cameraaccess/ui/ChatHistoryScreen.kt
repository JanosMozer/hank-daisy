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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meta.wearable.dat.externalsampleapps.cameraaccess.stream.ChatMessage
import com.meta.wearable.dat.externalsampleapps.cameraaccess.stream.StreamViewModel
import com.meta.wearable.dat.externalsampleapps.cameraaccess.wearables.WearablesViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full-screen chat history. Shows every turn on both sides — user and Hank —
 * with timestamps, role labels, and an Export button. Pulls its state from the
 * same (activity-scoped) StreamViewModel that feeds the in-stream overlay,
 * so the conversation persists across Stream/ActiveJob/ChatHistory navigation.
 */
@Composable
fun ChatHistoryScreen(
    wearablesViewModel: WearablesViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val streamViewModel: StreamViewModel =
        viewModel(
            factory =
                StreamViewModel.Factory(
                    application = (LocalActivity.current as ComponentActivity).application,
                    wearablesViewModel = wearablesViewModel,
                ),
        )
    val uiState by streamViewModel.uiState.collectAsStateWithLifecycle()
    val messages = uiState.chatMessages
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color(0xFF0B0B0D))
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
                        .background(Color(0xFF1F2937), shape = RoundedCornerShape(8.dp))
                        .clickable(onClick = onBack)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "‹ Back",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Conversation",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${messages.size} turns",
                    color = Color(0xFF9CA3AF),
                    fontSize = 11.sp,
                )
            }
            if (messages.isNotEmpty()) {
                Box(
                    modifier =
                        Modifier
                            .background(Color(0xFF374151), shape = RoundedCornerShape(8.dp))
                            .clickable { streamViewModel.exportSession() }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "↑ Export",
                        color = Color.White,
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
                    text =
                        "No conversation yet. Start the live stream from the job " +
                            "screen and say anything — every turn gets logged here.",
                    color = Color(0xFF9CA3AF),
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
    val bg = if (isUser) Color(0xFF2563EB) else Color(0xFF1F2937)
    val align = if (isUser) Alignment.End else Alignment.Start
    val label = if (isUser) "You" else "Hank"
    val labelColor = if (isUser) Color(0xFFBFDBFE) else Color(0xFF9CA3AF)
    val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(message.timestamp))

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = align,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!isUser) {
                Text(
                    text = label,
                    color = labelColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = ts,
                    color = Color(0xFF4B5563),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
            } else {
                Text(
                    text = ts,
                    color = Color(0xFF4B5563),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = label,
                    color = labelColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
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
            Text(
                text = message.text,
                color = Color.White,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        }
    }
}

