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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The "dealership tablet" screen. One scrollable column covering:
 *  - context header (RO, VIN, vehicle, status, tech name)
 *  - progress bar
 *  - live stream + chat launcher
 *  - diagnosis, repair, verify checklists
 *  - bay notes
 *  - closure report button
 *
 * The bay UI's right-column checklist + overview are merged into one vertical
 * flow because phones are narrow and tabs break the natural top-to-bottom
 * workflow.
 */
@Composable
fun ActiveJobScreen(
    state: JobUiState,
    onBack: () -> Unit,
    onTechnicianNameChange: (String) -> Unit,
    onToggleDiag: (String) -> Unit,
    onToggleRepair: (String) -> Unit,
    onToggleVerify: (String) -> Unit,
    onBayNotesChange: (String) -> Unit,
    onStartStream: () -> Unit,
    onGenerateClosure: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val job = state.activeJob ?: return
    val repairSteps = remember(job.id) { repairStepsForJob(job) }

    val diagDone = UNKNOWN_CODE_WALKTHROUGH.count { state.diagChecked["${job.id}|${it.title}"] == true }
    val repairDone = repairSteps.count { state.repairChecked["${job.id}|${it.id}"] == true }
    val verifyDone = VERIFY_ITEMS_DEFAULT.count { state.verifyChecked["${job.id}|${it.id}"] == true }
    val totalItems = UNKNOWN_CODE_WALKTHROUGH.size + repairSteps.size + VERIFY_ITEMS_DEFAULT.size
    val doneItems = diagDone + repairDone + verifyDone
    val progress = if (totalItems > 0) doneItems.toFloat() / totalItems else 0f

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
        // Back + RO title
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier
                        .background(Color(0xFF1F2937), shape = RoundedCornerShape(8.dp))
                        .clickable(onClick = onBack)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(text = "‹ Queue", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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

        // Vehicle context
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
            Text(text = "Concern", color = Color(0xFF6B7280), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Text(text = job.concern, color = Color(0xFFE5E7EB), fontSize = 13.sp)
            if (job.writerNotes.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(text = "Writer notes", color = Color(0xFF6B7280), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                Text(text = job.writerNotes, color = Color(0xFFD1D5DB), fontSize = 12.sp)
            }
            if (job.dtcs.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(text = "DTCs", color = Color(0xFF6B7280), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    job.dtcs.forEach { DtcRowView(it) }
                }
            }
        }

        // Technician name
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.technicianName,
            onValueChange = onTechnicianNameChange,
            label = { Text("Technician name (for closure PDF)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = darkTextFieldColors(),
        )

        // Progress
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Overall", color = Color(0xFF9CA3AF), fontSize = 12.sp)
            Text(
                text = "${(progress * 100).toInt()}%  ·  Diag $diagDone/${UNKNOWN_CODE_WALKTHROUGH.size}  ·  Repair $repairDone/${repairSteps.size}  ·  Verify $verifyDone/${VERIFY_ITEMS_DEFAULT.size}",
                color = Color(0xFFD1D5DB),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = Color(0xFFA78BFA),
            trackColor = Color(0xFF1F2937),
        )

        // Live stream launcher
        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            text = "📹  Start live stream + Hank chat",
            onClick = onStartStream,
            bg = Color(0xFF4C1D95),
            fg = Color.White,
        )

        // Diag
        Spacer(Modifier.height(16.dp))
        SectionHeader(title = "Diagnose", subtitle = "Work the code — don't guess.")
        UNKNOWN_CODE_WALKTHROUGH.forEach { step ->
            val key = "${job.id}|${step.title}"
            CheckboxRow(
                checked = state.diagChecked[key] == true,
                onToggle = { onToggleDiag(step.title) },
                title = step.title,
                body = step.body,
                detail = "Pitfall — ${step.pitfall}",
            )
        }

        // Repair
        Spacer(Modifier.height(12.dp))
        SectionHeader(
            title = "Repair",
            subtitle = if (job.dtcs.any { it.code == "P0299" }) "P0299 flow"
                       else if ("adas" in (job.concern + job.writerNotes).lowercase() || "windshield" in (job.concern + job.writerNotes).lowercase()) "ADAS calibration flow"
                       else "Generic repair flow",
        )
        repairSteps.forEach { step ->
            val key = "${job.id}|${step.id}"
            CheckboxRow(
                checked = state.repairChecked[key] == true,
                onToggle = { onToggleRepair(step.id) },
                title = step.title,
                body = step.detail,
            )
        }

        // Verify
        Spacer(Modifier.height(12.dp))
        SectionHeader(title = "Verify", subtitle = "Most-skipped step. Don't.")
        VERIFY_ITEMS_DEFAULT.forEach { item ->
            val key = "${job.id}|${item.id}"
            CheckboxRow(
                checked = state.verifyChecked[key] == true,
                onToggle = { onToggleVerify(item.id) },
                title = item.label,
                body = null,
            )
        }

        // Bay notes
        Spacer(Modifier.height(16.dp))
        SectionHeader(title = "Bay notes", subtitle = "Added to the closure report.")
        OutlinedTextField(
            value = state.bayNotes[job.id].orEmpty(),
            onValueChange = onBayNotesChange,
            label = { Text("What you did, what you saw, what to verify next") },
            modifier = Modifier.fillMaxWidth().height(130.dp),
            colors = darkTextFieldColors(),
        )

        // Closure
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
private fun SectionHeader(title: String, subtitle: String?) {
    Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    if (subtitle != null) {
        Text(subtitle, color = Color(0xFF6B7280), fontSize = 11.sp)
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun CheckboxRow(
    checked: Boolean,
    onToggle: () -> Unit,
    title: String,
    body: String?,
    detail: String? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF111318), shape = RoundedCornerShape(10.dp))
                .clickable { onToggle() }
                .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors =
                CheckboxDefaults.colors(
                    checkedColor = Color(0xFFA78BFA),
                    uncheckedColor = Color(0xFF4B5563),
                    checkmarkColor = Color.White,
                ),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 4.dp, top = 10.dp)) {
            Text(
                text = title,
                color = if (checked) Color(0xFF9CA3AF) else Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (body != null) {
                Spacer(Modifier.height(2.dp))
                Text(text = body, color = Color(0xFFD1D5DB), fontSize = 11.sp)
            }
            if (detail != null) {
                Spacer(Modifier.height(2.dp))
                Text(text = detail, color = Color(0xFF6B7280), fontSize = 10.sp)
            }
        }
    }
    Spacer(Modifier.height(6.dp))
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

