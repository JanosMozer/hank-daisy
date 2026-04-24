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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import com.meta.wearable.dat.externalsampleapps.mpi.session.RepairOrder

/**
 * Full-screen form for creating a new MPI inspection record.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewOrderScreen(
    onCancel: () -> Unit,
    onCreate: (RepairOrder) -> Unit,
    modifier: Modifier = Modifier,
) {
    var repairOrderNumber by remember { mutableStateOf("") }
    var mileage by remember { mutableStateOf("") }
    var advisorName by remember { mutableStateOf("") }
    var technicianName by remember { mutableStateOf("") }
    var vehicleYear by remember { mutableStateOf("") }
    var vehicleMake by remember { mutableStateOf("") }
    var vehicleModel by remember { mutableStateOf("") }
    var vehicleVin by remember { mutableStateOf("") }
    var licensePlate by remember { mutableStateOf("") }
    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var presentingIssue by remember { mutableStateOf("") }

    val canCreate =
        listOf(repairOrderNumber, vehicleMake, vehicleModel, customerName, presentingIssue)
            .any { it.isNotBlank() }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(AppColors.Background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
    ) {
        // Top bar: cancel on the left, create on the right.
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Cancel",
                color = AppColors.TextSecondary,
                fontSize = 15.sp,
                modifier = Modifier.clickable(onClick = onCancel),
            )
            Text(
                text = "New inspection",
                color = AppColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Create",
                color = if (canCreate) AppColors.Accent else AppColors.TextMuted,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier =
                    Modifier.clickable(enabled = canCreate) {
                        val order =
                            RepairOrder.blank().copy(
                                repairOrderNumber = repairOrderNumber.trim(),
                                mileage = mileage.trim(),
                                advisorName = advisorName.trim(),
                                technicianName = technicianName.trim(),
                                vehicleMake = vehicleMake.trim(),
                                vehicleModel = vehicleModel.trim(),
                                vehicleYear = vehicleYear.trim(),
                                vehicleVin = vehicleVin.trim(),
                                licensePlate = licensePlate.trim(),
                                customerName = customerName.trim(),
                                customerPhone = customerPhone.trim(),
                                presentingIssue = presentingIssue.trim(),
                            )
                        onCreate(order)
                    },
            )
        }

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            FormSection(title = "Visit") {
                Field(
                    label = "RO number",
                    value = repairOrderNumber,
                    onChange = { repairOrderNumber = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(
                        label = "Mileage",
                        value = mileage,
                        onChange = { mileage = it },
                        modifier = Modifier.weight(1f),
                    )
                    Field(
                        label = "Advisor",
                        value = advisorName,
                        onChange = { advisorName = it },
                        modifier = Modifier.weight(1.2f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Field(
                    label = "Technician",
                    value = technicianName,
                    onChange = { technicianName = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            FormSection(title = "Vehicle") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(
                        label = "Year",
                        value = vehicleYear,
                        onChange = { vehicleYear = it },
                        modifier = Modifier.weight(1f),
                    )
                    Field(
                        label = "Make",
                        value = vehicleMake,
                        onChange = { vehicleMake = it },
                        modifier = Modifier.weight(1.4f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Field(
                    label = "Model",
                    value = vehicleModel,
                    onChange = { vehicleModel = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Field(
                    label = "VIN (optional)",
                    value = vehicleVin,
                    onChange = { vehicleVin = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Field(
                    label = "License plate (optional)",
                    value = licensePlate,
                    onChange = { licensePlate = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            FormSection(title = "Customer") {
                Field(
                    label = "Name",
                    value = customerName,
                    onChange = { customerName = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Field(
                    label = "Phone (optional)",
                    value = customerPhone,
                    onChange = { customerPhone = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            FormSection(title = "Customer concern") {
                Field(
                    label =
                        "Describe the customer concern or requested inspection scope.",
                    value = presentingIssue,
                    onChange = { presentingIssue = it },
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                    singleLine = false,
                )
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun FormSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title.uppercase(),
            color = AppColors.TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(AppColors.Surface, shape = RoundedCornerShape(12.dp))
                    .padding(12.dp),
        ) {
            Column { content() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 12.sp) },
        singleLine = singleLine,
        modifier = modifier,
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
