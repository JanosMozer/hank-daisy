/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.mpi.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meta.wearable.dat.externalsampleapps.mpi.stream.ChatMessage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Unified renderer for a single conversation turn across History /
 * ChatOnly / stream overlay.
 *
 * - User turns stay as compact right-aligned bubbles (small role in the
 *   layout — the user's own words aren't what they need to read).
 * - Hank turns render as structured diagnostic blocks via
 *   [HankResponseView]: step cards, warning callouts, tables, etc.
 *
 * [forOverlay] tints the timestamp + "You" label brighter so they read
 * against a darkened camera overlay (stream mode). Everywhere else we
 * use normal muted colours for those labels.
 */
@Composable
fun ChatTurn(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    forOverlay: Boolean = false,
) {
    val isUser = message.role == ChatMessage.Role.USER
    val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(message.timestamp))
    val labelColor =
        if (forOverlay) androidx.compose.ui.graphics.Color(0xFFD1D5DB) else AppColors.TextMuted
    val tsColor =
        if (forOverlay) androidx.compose.ui.graphics.Color(0xFF9CA3AF) else AppColors.TextMuted

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        // Label + timestamp row.
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!isUser) {
                Text(
                    text = "HANK",
                    color = AppColors.Accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = ts,
                    color = tsColor,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
            } else {
                Text(
                    text = ts,
                    color = tsColor,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "You",
                    color = labelColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        if (isUser) {
            // User column — image attachment (if any) stacks above the
            // text bubble, both right-aligned and narrower than Hank's
            // structured cards so the eye goes to the guidance first.
            Column(
                modifier = Modifier.fillMaxWidth(0.72f),
                horizontalAlignment = Alignment.End,
            ) {
                if (message.imagePath != null) {
                    AttachedImage(path = message.imagePath)
                    Spacer(Modifier.height(4.dp))
                }
                if (message.text.isNotBlank()) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    AppColors.UserBubble,
                                    shape =
                                        RoundedCornerShape(
                                            topStart = 14.dp,
                                            topEnd = 14.dp,
                                            bottomStart = 14.dp,
                                            bottomEnd = 4.dp,
                                        ),
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = message.text,
                            color = AppColors.UserBubbleText,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        )
                    }
                }
            }
        } else {
            // Structured diagnostic blocks. Each block brings its own
            // frame (step cards / callouts / tables) so there's no outer
            // bubble — the cards ARE the conversation.
            HankResponseView(
                raw = message.text,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Inline preview for an attached image stored on-disk. Decoded off-screen
 *  via remember(path) so scrolling the chat doesn't re-decode on every
 *  recomposition. Rounded + heightIn so a tall photo doesn't dominate the
 *  scroll area. */
@Composable
private fun AttachedImage(path: String) {
    val bitmap =
        remember(path) {
            try {
                val f = File(path)
                if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
            } catch (_: Exception) {
                null
            }
        }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Attached image",
            contentScale = ContentScale.Fit,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .clip(RoundedCornerShape(14.dp)),
        )
    }
}

/**
 * Bordered empty-state panel used when there are no turns yet. Mirrors
 * the visual weight of an incoming [ChatTurn] so the chat surface never
 * collapses to zero height before Hank's first reply.
 */
@Composable
fun ChatEmptyState(
    message: String,
    secondary: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            color = AppColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (secondary != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = secondary,
                color = AppColors.TextSecondary,
                fontSize = 12.sp,
            )
        }
    }
}
