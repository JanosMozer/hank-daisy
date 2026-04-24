/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.mpi.ui

import android.graphics.BitmapFactory
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meta.wearable.dat.externalsampleapps.mpi.session.FindingSeverity
import com.meta.wearable.dat.externalsampleapps.mpi.session.InspectionEvidence
import com.meta.wearable.dat.externalsampleapps.mpi.session.InspectionFinding
import com.meta.wearable.dat.externalsampleapps.mpi.session.OrderStatus
import com.meta.wearable.dat.externalsampleapps.mpi.session.RepairOrder
import com.meta.wearable.dat.externalsampleapps.mpi.session.Session
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class DetailTab(val label: String) {
    OVERVIEW("Overview"),
    DIAGNOSIS("Findings"),
    NOTES("Notes"),
}

/**
 * Full-screen detail view for a single MPI inspection record.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailScreen(
    order: RepairOrder,
    sessions: List<Session>,
    onBack: () -> Unit,
    onStartDiagnosis: (String?) -> Unit,
    onOpenSession: (String) -> Unit,
    onUpdateOrder: (RepairOrder) -> Unit,
    onCloseOrder: () -> Unit,
    onReopenOrder: () -> Unit,
    onDeleteOrder: () -> Unit,
    onGenerateReport: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableStateOf(DetailTab.OVERVIEW) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(AppColors.Background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
    ) {
        TopBar(order = order, onBack = onBack)
        TabBar(current = tab, onSelect = { tab = it })

        Box(modifier = Modifier.weight(1f)) {
            when (tab) {
                DetailTab.OVERVIEW ->
                    OverviewTab(
                        order = order,
                        sessionCount = sessions.size,
                        onStartDiagnosis = { onStartDiagnosis(null) },
                        onCloseOrder = onCloseOrder,
                        onReopenOrder = onReopenOrder,
                        onDeleteOrder = onDeleteOrder,
                        onGenerateReport = onGenerateReport,
                    )
                DetailTab.DIAGNOSIS ->
                    DiagnosisTab(
                        order = order,
                        sessions = sessions,
                        onOpenSession = onOpenSession,
                        onStartDiagnosis = onStartDiagnosis,
                    )
                DetailTab.NOTES ->
                    NotesTab(
                        order = order,
                        onUpdateOrder = onUpdateOrder,
                    )
            }
        }
    }
}

@Composable
private fun TopBar(order: RepairOrder, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "< Back",
            color = AppColors.TextSecondary,
            fontSize = 15.sp,
            modifier = Modifier.clickable(onClick = onBack),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = order.vehicleDisplay,
                color = AppColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (order.customerName.isNotBlank()) {
                Text(
                    text = order.customerName,
                    color = AppColors.TextMuted,
                    fontSize = 11.sp,
                )
            }
        }
        // Placeholder spacer mirrors "< Back" width so the title stays
        // visually centred. If we add an overflow menu later this is
        // where it'd live.
        Spacer(Modifier.width(56.dp))
    }
}

@Composable
private fun TabBar(current: DetailTab, onSelect: (DetailTab) -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(AppColors.Surface)
                .padding(horizontal = 8.dp),
    ) {
        DetailTab.values().forEach { t ->
            val selected = t == current
            Column(
                modifier =
                    Modifier.weight(1f)
                        .clickable { onSelect(t) }
                        .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = t.label,
                    color = if (selected) AppColors.Accent else AppColors.TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier =
                        Modifier
                            .height(2.dp)
                            .fillMaxWidth(fraction = 0.7f)
                            .background(
                                if (selected) AppColors.Accent
                                else androidx.compose.ui.graphics.Color.Transparent,
                                shape = RoundedCornerShape(2.dp),
                            ),
                )
            }
        }
    }
}

@Composable
private fun OverviewTab(
    order: RepairOrder,
    sessionCount: Int,
    onStartDiagnosis: () -> Unit,
    onCloseOrder: () -> Unit,
    onReopenOrder: () -> Unit,
    onDeleteOrder: () -> Unit,
    onGenerateReport: (() -> Unit)?,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (order.status != OrderStatus.CLOSED) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(AppColors.Accent, shape = RoundedCornerShape(14.dp))
                        .clickable(onClick = onStartDiagnosis)
                        .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Start evidence capture",
                    color = AppColors.AccentOn,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        } else {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(AppColors.SurfaceAlt, shape = RoundedCornerShape(14.dp))
                        .clickable(onClick = onReopenOrder)
                        .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Reopen inspection",
                    color = AppColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        InfoCard(title = "Vehicle") {
            KeyValue("Year / make / model", order.vehicleDisplay)
            if (order.repairOrderNumber.isNotBlank()) KeyValue("RO number", order.repairOrderNumber)
            if (order.mileage.isNotBlank()) KeyValue("Mileage", order.mileage)
            if (order.vehicleVin.isNotBlank()) KeyValue("VIN", order.vehicleVin)
            if (order.licensePlate.isNotBlank()) KeyValue("Plate", order.licensePlate)
        }

        if (
            order.customerName.isNotBlank() ||
                order.customerPhone.isNotBlank() ||
                order.advisorName.isNotBlank() ||
                order.technicianName.isNotBlank()
        ) {
            InfoCard(title = "People") {
                if (order.customerName.isNotBlank()) KeyValue("Name", order.customerName)
                if (order.customerPhone.isNotBlank()) KeyValue("Phone", order.customerPhone)
                if (order.advisorName.isNotBlank()) KeyValue("Advisor", order.advisorName)
                if (order.technicianName.isNotBlank()) KeyValue("Technician", order.technicianName)
            }
        }

        if (order.presentingIssue.isNotBlank()) {
            InfoCard(title = "Inspection scope") {
                Text(
                    text = order.presentingIssue,
                    color = AppColors.TextPrimary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            }
        }

        InfoCard(title = "Stats") {
            KeyValue("Status", order.status.label)
            KeyValue("Finding summary", order.findingSummary)
            KeyValue("Evidence sessions", "$sessionCount ${if (sessionCount == 1) "session" else "sessions"}")
            KeyValue(
                "Created",
                SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.US).format(Date(order.createdAt)),
            )
            if (order.closedAt != null) {
                KeyValue(
                    "Closed",
                    SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.US).format(Date(order.closedAt)),
                )
            }
        }

        if (onGenerateReport != null) {
            SecondaryButton(
                label = "Generate inspection HTML",
                onClick = onGenerateReport,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (order.status != OrderStatus.CLOSED) {
                SecondaryButton(
                    label = "Mark inspection closed",
                    onClick = onCloseOrder,
                    modifier = Modifier.weight(1f),
                )
            }
            SecondaryButton(
                label = "Delete",
                onClick = onDeleteOrder,
                destructive = true,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(60.dp))
    }
}

@Composable
private fun DiagnosisTab(
    order: RepairOrder,
    sessions: List<Session>,
    onOpenSession: (String) -> Unit,
    onStartDiagnosis: (String?) -> Unit,
) {
    if (order.findings.isEmpty() && order.diagnoses.isEmpty() && sessions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "No findings yet",
                    color = AppColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Start evidence capture from Overview and document findings here.",
                    color = AppColors.TextSecondary,
                    fontSize = 13.sp,
                )
            }
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (order.findings.isNotEmpty()) {
            item {
                SectionHeader("Inspection findings")
            }
            items(order.findings, key = { it.id }) { finding ->
                FindingCard(
                    finding = finding,
                    onCapture = { onStartDiagnosis(finding.id) },
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
        if (order.diagnoses.isNotEmpty()) {
            item {
                SectionHeader("Diagnostic steps")
            }
            items(order.diagnoses, key = { it.id }) { d ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(AppColors.Surface, shape = RoundedCornerShape(12.dp))
                            .padding(14.dp),
                ) {
                    Text(
                        text = d.step,
                        color = AppColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = d.result,
                        color = AppColors.TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = d.outcome.label,
                        color =
                            when (d.outcome) {
                                com.meta.wearable.dat.externalsampleapps.mpi.session.DiagnosisOutcome.PASS ->
                                    AppColors.Accent
                                com.meta.wearable.dat.externalsampleapps.mpi.session.DiagnosisOutcome.FAIL ->
                                    androidx.compose.ui.graphics.Color(0xFFB91C1C)
                                else -> AppColors.TextMuted
                            },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
        if (sessions.isNotEmpty()) {
            item {
                SectionHeader("Evidence sessions")
            }
            items(sessions, key = { it.id }) { s ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(AppColors.Surface, shape = RoundedCornerShape(12.dp))
                            .clickable { onOpenSession(s.id) }
                            .padding(14.dp),
                ) {
                    Text(
                        text = s.title,
                        color = AppColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = s.description,
                        color = AppColors.TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text =
                            "${s.messages.size} turns · " +
                                SimpleDateFormat("MMM d · HH:mm", Locale.US)
                                    .format(Date(s.createdAt)),
                        color = AppColors.TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        item { Spacer(Modifier.height(60.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesTab(order: RepairOrder, onUpdateOrder: (RepairOrder) -> Unit) {
    var notes by remember(order.id) { mutableStateOf(order.notes) }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
    ) {
        OutlinedTextField(
            value = notes,
            onValueChange = {
                notes = it
                onUpdateOrder(order.copy(notes = it))
            },
            label = { Text("Inspection notes", fontSize = 12.sp) },
            singleLine = false,
            modifier = Modifier.fillMaxSize().padding(bottom = 60.dp),
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AppColors.Accent,
                    unfocusedBorderColor = AppColors.Border,
                    focusedLabelColor = AppColors.Accent,
                    unfocusedLabelColor = AppColors.TextSecondary,
                    focusedTextColor = AppColors.TextPrimary,
                    unfocusedTextColor = AppColors.TextPrimary,
                    cursorColor = AppColors.Accent,
                ),
        )
    }
}

@Composable
private fun FindingCard(
    finding: InspectionFinding,
    onCapture: () -> Unit,
) {
    val (bg, fg) =
        when (finding.severity) {
            FindingSeverity.GREEN ->
                androidx.compose.ui.graphics.Color(0xFFE8F5E9) to androidx.compose.ui.graphics.Color(0xFF1B5E20)
            FindingSeverity.YELLOW ->
                androidx.compose.ui.graphics.Color(0xFFFFF8E1) to androidx.compose.ui.graphics.Color(0xFF8D6E00)
            FindingSeverity.RED ->
                androidx.compose.ui.graphics.Color(0xFFFFEBEE) to androidx.compose.ui.graphics.Color(0xFFB71C1C)
        }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(AppColors.Surface, shape = RoundedCornerShape(12.dp))
                .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${finding.system} · ${finding.component}",
                color = AppColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier.background(bg, shape = RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = finding.severity.label,
                    color = fg,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (finding.location.isNotBlank()) KeyValue("Location", finding.location)
        if (finding.measurement.isNotBlank()) KeyValue("Measurement", finding.measurement)
        if (finding.recommendation.isNotBlank()) KeyValue("Recommendation", finding.recommendation)
        if (finding.note.isNotBlank()) {
            Text(
                text = finding.note,
                color = AppColors.TextMuted,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        }
        if (finding.evidenceAssets.isNotEmpty() || finding.linkedSessionIds.isNotEmpty()) {
            Text(
                text =
                    "${finding.evidenceAssets.size} media ${if (finding.evidenceAssets.size == 1) "file" else "files"} · " +
                        "${finding.linkedSessionIds.size} linked ${if (finding.linkedSessionIds.size == 1) "session" else "sessions"}",
                color = AppColors.TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (finding.evidenceAssets.isNotEmpty()) {
            EvidencePreviewRow(finding.evidenceAssets)
        }
        SecondaryButton(
            label = "Capture evidence",
            onClick = onCapture,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun EvidencePreviewRow(evidenceAssets: List<InspectionEvidence>) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().height((evidenceAssets.size.coerceAtMost(2) * 96).dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(evidenceAssets, key = { it.id }) { asset ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(AppColors.SurfaceAlt, shape = RoundedCornerShape(10.dp))
                        .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val previewPath = asset.previewImagePath ?: asset.filePath
                val bitmap = remember(previewPath) { previewPath.takeIf { it.isNotBlank() }?.let(BitmapFactory::decodeFile) }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = asset.caption,
                        modifier = Modifier.width(92.dp).height(64.dp).background(AppColors.Background, RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier.width(92.dp).height(64.dp).background(AppColors.Background, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = asset.kind.name, color = AppColors.TextMuted, fontSize = 10.sp)
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = when {
                            asset.kind == com.meta.wearable.dat.externalsampleapps.mpi.session.EvidenceKind.VIDEO && asset.clipFramePaths.isNotEmpty() ->
                                "Clip preview"
                            asset.kind == com.meta.wearable.dat.externalsampleapps.mpi.session.EvidenceKind.AUDIO ->
                                "Audio note"
                            asset.kind == com.meta.wearable.dat.externalsampleapps.mpi.session.EvidenceKind.IMAGE -> "Image evidence"
                            else -> asset.kind.name
                        },
                        color = AppColors.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text =
                            asset.caption.ifBlank {
                                if (asset.durationMs > 0) "${String.format(Locale.US, "%.1fs", asset.durationMs / 1000.0)} clip"
                                else "Captured evidence"
                            },
                        color = AppColors.TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                    if (asset.clipFramePaths.isNotEmpty()) {
                        Text(
                            text = "${asset.clipFramePaths.size} frames · ${asset.clipFps} fps",
                            color = AppColors.TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title.uppercase(),
            color = AppColors.TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(AppColors.Surface, shape = RoundedCornerShape(12.dp))
                    .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun KeyValue(key: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = key,
            color = AppColors.TextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            color = AppColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun SectionHeader(label: String) {
    Text(
        text = label.uppercase(),
        color = AppColors.TextMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 6.dp),
    )
}

@Composable
private fun SecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
) {
    val fg = if (destructive) androidx.compose.ui.graphics.Color(0xFFB91C1C) else AppColors.TextPrimary
    Box(
        modifier =
            modifier
                .background(AppColors.Surface, shape = RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
