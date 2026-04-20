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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meta.wearable.dat.externalsampleapps.cameraaccess.session.Session
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Home screen after splash. A list of past Hank sessions — each with an
 * auto-derived title (first user turn) and description (first Hank reply).
 * A big "+" FAB in the bottom-right starts a new session.
 */
/**
 * Chats tab — past sessions + a purple "+" FAB to start a new
 * text/voice chat (no glasses). The glasses-stream entry point
 * lives in ConvosScreen.
 */
@Composable
fun SessionsHomeScreen(
    sessions: List<Session>,
    userInitials: String,
    onOpenSession: (String) -> Unit,
    onNewChatOnly: () -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(AppColors.Background)
                .statusBarsPadding()
                .navigationBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Hank & Daisy",
                        color = AppColors.Accent,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Your conversations",
                        color = AppColors.TextSecondary,
                        fontSize = 13.sp,
                    )
                }
                Box(
                    modifier =
                        Modifier
                            .size(44.dp)
                            .background(AppColors.Accent, shape = CircleShape)
                            .clickable(onClick = onOpenProfile),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = userInitials.ifBlank { "?" },
                        color = AppColors.AccentOn,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))

            if (sessions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(bottom = 120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No sessions yet",
                            color = AppColors.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Tap + to start talking to Hank.",
                            color = AppColors.TextSecondary,
                            fontSize = 13.sp,
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 120.dp),
                ) {
                    items(sessions, key = { it.id }) { s ->
                        SessionCard(session = s, onClick = { onOpenSession(s.id) })
                    }
                }
            }
        }

        // Single purple FAB: start a new text/voice chat (no glasses).
        // The glasses stream entry point lives in the Convos tab.
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
                    .size(64.dp)
                    .background(AppColors.Accent, shape = CircleShape)
                    .clickable(onClick = onNewChatOnly),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "\uD83D\uDCAC",
                fontSize = 26.sp,
            )
        }
    }
}

@Composable
private fun SessionCard(session: Session, onClick: () -> Unit) {
    val ts = SimpleDateFormat("MMM d · HH:mm", Locale.US).format(Date(session.createdAt))
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(AppColors.Surface, shape = RoundedCornerShape(14.dp))
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = session.title,
                color = AppColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = ts,
                color = AppColors.TextMuted,
                fontSize = 11.sp,
            )
        }
        Text(
            text = session.description,
            color = AppColors.TextSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
        Text(
            text = "${session.messages.size} turns",
            color = AppColors.TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
