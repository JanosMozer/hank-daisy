/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.mpi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.meta.wearable.dat.externalsampleapps.mpi.stream.ChatMessage

/**
 * Live conversation overlay on top of the camera feed.
 *
 * Renders each turn through [ChatTurn] with `forOverlay=true` — Hank's
 * side becomes structured diagnostic cards (step headers, callouts,
 * tables), the tech's side stays as a compact bubble. The outer black-
 * tinted container gives the cards contrast against the live video.
 *
 * Auto-scrolls to the bottom whenever a new turn comes in.
 */
@Composable
fun ChatPanel(
    messages: List<ChatMessage>,
    modifier: Modifier = Modifier,
    onExport: (() -> Unit)? = null,
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
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                .padding(10.dp),
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(messages) { msg -> ChatTurn(msg, forOverlay = true) }
            }
            // Small "Export" pill in the top-right corner so the user can
            // send the session JSON to the bay UI's Import Session button.
            if (onExport != null) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 2.dp, end = 2.dp)
                            .background(
                                Color(0xFF374151).copy(alpha = 0.92f),
                                shape = RoundedCornerShape(10.dp),
                            )
                            .clickable { onExport() }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "\u2B06 Export",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
