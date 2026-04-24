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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meta.wearable.dat.externalsampleapps.mpi.session.OrderStatus
import com.meta.wearable.dat.externalsampleapps.mpi.session.RepairOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Inspection records tab.
 *
 * This is the first dedicated MPI home for service-visit records tied to a
 * repair order, vehicle, customer, and structured inspection findings.
 */
@Composable
fun OrdersScreen(
    orders: List<RepairOrder>,
    openSessionCount: (String) -> Int,
    onOpenOrder: (String) -> Unit,
    onNewOrder: () -> Unit,
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
            Text(
                text = "Inspections",
                color = AppColors.Accent,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text =
                    if (orders.isEmpty()) "Every service visit inspection lands here."
                    else "${orders.size} ${if (orders.size == 1) "inspection" else "inspections"}",
                color = AppColors.TextSecondary,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(18.dp))

            if (orders.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(bottom = 120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No inspections yet",
                            color = AppColors.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Tap + to create an inspection record.",
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
                    items(orders, key = { it.id }) { o ->
                        OrderCard(
                            order = o,
                            sessionCount = openSessionCount(o.id),
                            onClick = { onOpenOrder(o.id) },
                        )
                    }
                }
            }
        }

        // + FAB creates a new inspection record.
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
                    .size(64.dp)
                    .background(AppColors.Accent, shape = CircleShape)
                    .clickable(onClick = onNewOrder),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "+",
                color = AppColors.AccentOn,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun OrderCard(
    order: RepairOrder,
    sessionCount: Int,
    onClick: () -> Unit,
) {
    val ts = SimpleDateFormat("MMM d · HH:mm", Locale.US).format(Date(order.updatedAt))
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
                text = order.vehicleDisplay,
                color = AppColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            StatusChip(status = order.status)
        }
        Text(
            text =
                listOfNotNull(
                        order.subtitleDisplay.takeIf { it != "No details yet" },
                        order.identityLine.ifBlank { null },
                    )
                    .joinToString(" · "),
            color = AppColors.TextSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text =
                    if (sessionCount == 0) order.findingSummary
                    else "${order.findingSummary} · $sessionCount evidence ${if (sessionCount == 1) "session" else "sessions"}",
                color = AppColors.TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = ts,
                color = AppColors.TextMuted,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun StatusChip(status: OrderStatus) {
    val (bg, fg) =
        when (status) {
            OrderStatus.OPEN -> AppColors.AccentSoft to AppColors.Accent
            OrderStatus.IN_PROGRESS -> AppColors.Accent to AppColors.AccentOn
            OrderStatus.CLOSED -> AppColors.SurfaceAlt to AppColors.TextSecondary
        }
    Box(
        modifier = Modifier.background(bg, shape = RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = status.label,
            color = fg,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
