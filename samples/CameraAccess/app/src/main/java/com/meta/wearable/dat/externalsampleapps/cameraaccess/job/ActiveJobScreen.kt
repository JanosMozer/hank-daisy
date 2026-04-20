/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.job

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The "dealership tablet" screen, stripped down per user feedback. One
 * scrollable column covering: context header, technician name, live stream +
 * Hank chat launcher, bay notes, closure PDF button. No preset checklists —
 * the free-form conversation with Hank is the work log; bay notes are the
 * summary that goes on the paperwork.
 */
@Composable
fun ActiveJobScreen(
    state: JobUiState,
    onBack: () -> Unit,
    onTechnicianNameChange: (String) -> Unit,
    onBayNotesChange: (String) -> Unit,
    onStartStream: () -> Unit,
    onGenerateClosure: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val job = state.activeJob ?: return

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color(0xFF0B0B0D))
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
    ) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier
                        .background(Color(0xFF1F2937), shape = RoundedCornerShape(8.dp))
                        .clickable(onClick = onBack)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "‹ Queue",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = "RO ${job.ro}",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.width(8.dp))
            StatusPill(status = job.status)
        }

        Spacer(Modifier.height(14.dp))
        Panel {
            Text(
                text = job.vehicle,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "VIN ${job.vin}  ·  Bay ${job.bay}",
                color = Color(0xFF9CA3AF),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Concern",
                color = Color(0xFF6B7280),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(text = job.concern, color = Color(0xFFE5E7EB), fontSize = 13.sp)
            if (job.writerNotes.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Writer notes",
                    color = Color(0xFF6B7280),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(text = job.writerNotes, color = Color(0xFFD1D5DB), fontSize = 12.sp)
            }
            if (job.dtcs.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "DTCs",
                    color = Color(0xFF6B7280),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    job.dtcs.forEach { DtcRowView(it) }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.technicianName,
            onValueChange = onTechnicianNameChange,
            label = { Text("Technician name (for closure PDF)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = darkTextFieldColors(),
        )

        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            text = "📹  Start live stream + Hank chat",
            onClick = onStartStream,
            bg = Color(0xFF4C1D95),
            fg = Color.White,
        )

        Spacer(Modifier.height(16.dp))
        Text(
            text = "Bay notes",
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Goes on the closure PDF. Everything else you can just talk to Hank about.",
            color = Color(0xFF6B7280),
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = state.bayNotes[job.id].orEmpty(),
            onValueChange = onBayNotesChange,
            label = { Text("What you did, what you saw, what to verify next") },
            modifier = Modifier.fillMaxWidth().height(180.dp),
            colors = darkTextFieldColors(),
        )

        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            text = "📄  Generate closure report (PDF)",
            onClick = onGenerateClosure,
            bg = Color(0xFF10B981),
            fg = Color(0xFF0B0B0D),
        )

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun DtcRowView(d: DtcRow) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = d.code,
            color = Color(0xFFFDE68A),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(72.dp),
        )
        Text(
            text = d.description,
            color = Color(0xFFD1D5DB),
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
        if (d.pending) {
            Spacer(Modifier.width(6.dp))
            Box(
                modifier =
                    Modifier
                        .background(Color(0xFFB45309), shape = RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
            ) {
                Text(text = "pending", color = Color.White, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF111318), shape = RoundedCornerShape(12.dp))
                .padding(14.dp),
        content = { content() },
    )
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit, bg: Color, fg: Color) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(bg, shape = RoundedCornerShape(12.dp))
                .clickable { onClick() }
                .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatusPill(status: JobStatus) {
    Box(
        modifier =
            Modifier
                .background(Color(status.color).copy(alpha = 0.25f), shape = RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = status.label,
            color = Color(status.color),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun darkTextFieldColors() =
    TextFieldDefaults.colors(
        focusedContainerColor = Color(0xFF111318),
        unfocusedContainerColor = Color(0xFF111318),
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedLabelColor = Color(0xFFA78BFA),
        unfocusedLabelColor = Color(0xFF6B7280),
        focusedIndicatorColor = Color(0xFFA78BFA),
        unfocusedIndicatorColor = Color(0xFF374151),
        cursorColor = Color(0xFFA78BFA),
    )
