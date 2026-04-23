/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.hankdaisy.ui

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meta.wearable.dat.externalsampleapps.hankdaisy.stream.ChatMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full-screen chat view. Caller supplies the messages + optional callbacks.
 * Export actions (JSON, Summary-PDF) now live in a bottom action bar so the
 * top stays clean; the primary summary action is the accent-tinted one.
 */
@Composable
fun ChatHistoryScreen(
    title: String,
    messages: List<ChatMessage>,
    onBack: () -> Unit,
    onExport: (() -> Unit)? = null,
    onSummariseAndExport: (() -> Unit)? = null,
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
        // Top: back + title + turn count. No action buttons up here anymore.
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
            Column {
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
        }

        // Middle: scrollable messages.
        Box(modifier = Modifier.weight(1f)) {
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
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    items(messages) { msg -> ChatTurn(msg) }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }

        // Bottom action bar. Only rendered if there's anything to export.
        if (messages.isNotEmpty() && (onSummariseAndExport != null || onExport != null)) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(AppColors.Surface)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Spacer(
                    modifier =
                        Modifier.fillMaxWidth().height(1.dp).background(AppColors.Border),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (onSummariseAndExport != null) {
                        PrimaryActionButton(
                            icon = Icons.Outlined.Description,
                            label = "Summary PDF",
                            onClick = onSummariseAndExport,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (onExport != null) {
                        SecondaryActionButton(
                            icon = Icons.Outlined.IosShare,
                            label = "Export JSON",
                            onClick = onExport,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrimaryActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .background(AppColors.Accent, shape = RoundedCornerShape(14.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = AppColors.AccentOn, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = AppColors.AccentOn,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SecondaryActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .background(AppColors.SurfaceAlt, shape = RoundedCornerShape(14.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = AppColors.TextPrimary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = AppColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

