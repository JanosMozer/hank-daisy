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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Home view after device registration. Shows the queue of open ROs like the
 * left sidebar of the bay UI's TechnicianApp. Tapping a card calls
 * [onSelectJob] to open the ActiveJobScreen for that RO.
 */
@Composable
fun JobQueueScreen(
    state: JobUiState,
    onSelectJob: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color(0xFF0B0B0D))
                .statusBarsPadding()
                .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Hank",
                color = Color(0xFFA78BFA),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Default,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier =
                    Modifier
                        .background(
                            Color(0xFF1F2937),
                            shape = RoundedCornerShape(6.dp),
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    text = "Copilot · for technicians",
                    color = Color(0xFF9CA3AF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Open ROs",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Badge(count = state.jobs.size)
        }
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.jobs, key = { it.id }) { job ->
                JobCard(job = job, onClick = { onSelectJob(job.id) })
            }
        }
    }
}

@Composable
private fun Badge(count: Int) {
    Box(
        modifier =
            Modifier
                .background(Color(0xFF1F2937), shape = RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = count.toString(),
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun JobCard(job: WorkOrder, onClick: () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF111318), shape = RoundedCornerShape(12.dp))
                .clickable { onClick() }
                .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "RO ${job.ro}",
                color = Color(0xFF9CA3AF),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BayPill(bay = job.bay)
                StatusPill(status = job.status)
            }
        }
        Text(
            text = job.vehicle,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (job.dtcs.isEmpty()) {
            Text(
                text = "No codes on RO",
                color = Color(0xFF6B7280),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        } else {
            Text(
                text = job.dtcs.joinToString(", ") { it.code },
                color = Color(0xFFD1D5DB),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun BayPill(bay: String) {
    Box(
        modifier =
            Modifier
                .background(Color(0xFF1F2937), shape = RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = "Bay $bay",
            color = Color(0xFFD1D5DB),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
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

