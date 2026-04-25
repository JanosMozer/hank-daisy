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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meta.wearable.dat.externalsampleapps.hankdaisy.session.OrderStatus
import com.meta.wearable.dat.externalsampleapps.hankdaisy.session.RepairOrder
import com.meta.wearable.dat.externalsampleapps.hankdaisy.session.Session
import com.meta.wearable.dat.externalsampleapps.hankdaisy.session.WorkDomain
import com.meta.wearable.dat.externalsampleapps.hankdaisy.session.displayName
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class DetailTab { OVERVIEW, DIAGNOSIS, NOTES }

private fun DetailTab.label(workDomain: WorkDomain): String =
    when (this) {
        DetailTab.OVERVIEW -> "Overview"
        DetailTab.DIAGNOSIS -> workDomain.sessionTabLabel
        DetailTab.NOTES -> "Notes"
    }

/**
 * Full-screen detail view for a single [RepairOrder].
 *
 * Three tabs — Overview (vehicle + customer + primary action), Diagnosis
 * (linked sessions + structured diagnosis entries once Phase 3 lands),
 * Notes (free-form notepad). "Start diagnosis" on Overview is the primary
 * CTA: it tags the next saved session to this order via [onStartDiagnosis].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailScreen(
    order: RepairOrder,
    workDomain: WorkDomain,
    sessions: List<Session>,
    onBack: () -> Unit,
    onStartDiagnosis: () -> Unit,
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
        TopBar(order = order, workDomain = workDomain, onBack = onBack)
        TabBar(current = tab, workDomain = workDomain, onSelect = { tab = it })

        Box(modifier = Modifier.weight(1f)) {
            when (tab) {
                DetailTab.OVERVIEW ->
                    OverviewTab(
                        order = order,
                        workDomain = workDomain,
                        sessionCount = sessions.size,
                        onStartDiagnosis = onStartDiagnosis,
                        onCloseOrder = onCloseOrder,
                        onReopenOrder = onReopenOrder,
                        onDeleteOrder = onDeleteOrder,
                        onGenerateReport = onGenerateReport,
                    )
                DetailTab.DIAGNOSIS ->
                    DiagnosisTab(
                        order = order,
                        workDomain = workDomain,
                        sessions = sessions,
                        onOpenSession = onOpenSession,
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
private fun TopBar(order: RepairOrder, workDomain: WorkDomain, onBack: () -> Unit) {
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
                text = order.displayName(workDomain),
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
private fun TabBar(
    current: DetailTab,
    workDomain: WorkDomain,
    onSelect: (DetailTab) -> Unit,
) {
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
                    text = t.label(workDomain),
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
    workDomain: WorkDomain,
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
        // Primary CTA — biggest button on the screen, matches the FAB
        // language from the other tabs. Closed orders grey out and swap
        // to a "reopen" affordance.
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
                    text = workDomain.primaryActionLabel,
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
                    text = "Reopen order",
                    color = AppColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        InfoCard(title = workDomain.orderSectionTitle) {
            KeyValue(workDomain.primaryDescriptorLabel, order.displayName(workDomain))
            if (order.vehicleVin.isNotBlank()) {
                KeyValue(workDomain.primaryIdLabel, order.vehicleVin)
            }
            if (order.licensePlate.isNotBlank()) {
                KeyValue(workDomain.secondaryIdLabel, order.licensePlate)
            }
        }

        if (order.customerName.isNotBlank() || order.customerPhone.isNotBlank()) {
            InfoCard(title = "Customer") {
                if (order.customerName.isNotBlank()) KeyValue("Name", order.customerName)
                if (order.customerPhone.isNotBlank()) KeyValue("Phone", order.customerPhone)
            }
        }

        if (order.presentingIssue.isNotBlank()) {
            InfoCard(title = "Presenting issue") {
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
            KeyValue(
                workDomain.sessionStatsLabel,
                "$sessionCount ${if (sessionCount == 1) "session" else "sessions"}",
            )
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

        // Export: generate a PDF order report from Hank's Gemini-synthesised
        // notes + any structured diagnoses + the session trail. Sits between
        // the primary CTA and the destructive actions so the eye lands here
        // naturally when the job is winding down.
        if (onGenerateReport != null) {
            SecondaryButton(
                label = "Generate report PDF",
                onClick = onGenerateReport,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Secondary actions footer. Close/delete are destructive enough
        // that they live apart from the primary CTA.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (order.status != OrderStatus.CLOSED) {
                SecondaryButton(
                    label = "Mark closed",
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
    workDomain: WorkDomain,
    sessions: List<Session>,
    onOpenSession: (String) -> Unit,
) {
    if (order.diagnoses.isEmpty() && sessions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "No sessions yet",
                    color = AppColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Start from Overview — steps and session notes land here.",
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
        if (order.diagnoses.isNotEmpty()) {
            item {
                SectionHeader(
                    if (workDomain == WorkDomain.GENERAL_PURPOSE) "Steps" else "Diagnostic steps",
                )
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
                                com.meta.wearable.dat.externalsampleapps.hankdaisy.session.DiagnosisOutcome.PASS ->
                                    AppColors.Accent
                                com.meta.wearable.dat.externalsampleapps.hankdaisy.session.DiagnosisOutcome.FAIL ->
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
                SectionHeader(workDomain.sessionStatsLabel)
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
    // Local state so typing is snappy; we persist through the parent
    // on every change. Debouncing could come later if write pressure
    // becomes noticeable, but SharedPreferences handles this fine.
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
            label = { Text("Notes about this order", fontSize = 12.sp) },
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
