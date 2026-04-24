/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.mpi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meta.wearable.dat.externalsampleapps.mpi.session.AvatarColors
import com.meta.wearable.dat.externalsampleapps.mpi.session.HankPersona
import com.meta.wearable.dat.externalsampleapps.mpi.session.UserProfile
import com.meta.wearable.dat.externalsampleapps.mpi.session.Verbosity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProfileScreen(
    profile: UserProfile,
    sessionCount: Int,
    onBack: () -> Unit,
    onSave: (UserProfile) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf(profile.name) }
    var role by remember { mutableStateOf(profile.role) }
    var email by remember { mutableStateOf(profile.email) }
    var pronouns by remember { mutableStateOf(profile.pronouns) }
    var bio by remember { mutableStateOf(profile.bio) }
    var avatarColor by remember { mutableStateOf(profile.avatarColor) }
    var persona by remember { mutableStateOf(profile.hankPersona) }
    var verbosity by remember { mutableStateOf(profile.verbosity) }
    var useMyName by remember { mutableStateOf(profile.useMyName) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(AppColors.Background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
    ) {
        // Top bar
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
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
            Text(
                text = "Profile",
                color = AppColors.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        // Avatar
        Spacer(Modifier.height(20.dp))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                modifier =
                    Modifier
                        .size(96.dp)
                        .background(Color(avatarColor), shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initialsOf(name).ifBlank { "?" },
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        Section(title = "About you") {
            Field("Name", name, { name = it })
            Spacer(Modifier.height(10.dp))
            Field("Pronouns", pronouns, { pronouns = it }, placeholder = "they/them, she/her, …")
            Spacer(Modifier.height(10.dp))
            Field("Role", role, { role = it }, placeholder = "Lead mechanic, dad, hobbyist…")
            Spacer(Modifier.height(10.dp))
            Field("Email", email, { email = it })
            Spacer(Modifier.height(10.dp))
            FieldMultiline(
                "Bio",
                bio,
                { bio = it },
                placeholder = "A line or two so Hank knows who he's talking to.",
            )
        }

        Spacer(Modifier.height(14.dp))

        Section(title = "Avatar colour") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                AvatarColors.forEach { c ->
                    val selected = c == avatarColor
                    Box(
                        modifier =
                            Modifier
                                .size(36.dp)
                                .background(Color(c), shape = CircleShape)
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color =
                                        if (selected) AppColors.TextPrimary
                                        else AppColors.Border,
                                    shape = CircleShape,
                                )
                                .clickable { avatarColor = c },
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Section(title = "Hank's style") {
            ChipGroup(
                label = "Persona",
                options = HankPersona.values().map { it.label },
                selectedIndex = persona.ordinal,
                onSelect = { persona = HankPersona.values()[it] },
            )
            Text(
                text = persona.tag,
                color = AppColors.TextMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
            )
            Spacer(Modifier.height(12.dp))
            ChipGroup(
                label = "Reply length",
                options = Verbosity.values().map { it.label },
                selectedIndex = verbosity.ordinal,
                onSelect = { verbosity = Verbosity.values()[it] },
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Address me by name",
                        color = AppColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Hank uses your first name in replies.",
                        color = AppColors.TextSecondary,
                        fontSize = 11.sp,
                    )
                }
                Switch(
                    checked = useMyName,
                    onCheckedChange = { useMyName = it },
                    colors =
                        SwitchDefaults.colors(
                            checkedThumbColor = AppColors.AccentOn,
                            checkedTrackColor = AppColors.Accent,
                            uncheckedThumbColor = AppColors.Surface,
                            uncheckedTrackColor = AppColors.Border,
                        ),
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Section(title = "Stats") {
            StatRow(
                "Member since",
                if (profile.createdAt == 0L) "—"
                else SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(profile.createdAt)),
            )
            Spacer(Modifier.height(6.dp))
            StatRow("Sessions saved", sessionCount.toString())
        }

        Spacer(Modifier.height(20.dp))

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(Color(avatarColor), shape = RoundedCornerShape(14.dp))
                    .clickable {
                        onSave(
                            UserProfile(
                                name = name.trim(),
                                role = role.trim(),
                                email = email.trim(),
                                pronouns = pronouns.trim(),
                                bio = bio.trim(),
                                avatarColor = avatarColor,
                                hankPersona = persona,
                                verbosity = verbosity,
                                useMyName = useMyName,
                                createdAt = profile.createdAt,
                            ),
                        )
                        onBack()
                    }
                    .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Save",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Text(
        text = title.uppercase(),
        color = AppColors.TextMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(6.dp))
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(AppColors.Surface, shape = RoundedCornerShape(12.dp))
                .padding(16.dp),
    ) {
        content()
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = AppColors.TextMuted) } },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = lightTextFieldColors(),
    )
}

@Composable
private fun FieldMultiline(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = AppColors.TextMuted) } },
        singleLine = false,
        maxLines = 4,
        modifier = Modifier.fillMaxWidth().height(100.dp),
        colors = lightTextFieldColors(),
    )
}

@Composable
private fun ChipGroup(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Text(label, color = AppColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(AppColors.SurfaceAlt, shape = RoundedCornerShape(10.dp))
                .padding(3.dp),
    ) {
        options.forEachIndexed { i, opt ->
            val sel = i == selectedIndex
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .background(
                            if (sel) AppColors.Accent else AppColors.SurfaceAlt,
                            shape = RoundedCornerShape(8.dp),
                        )
                        .clickable { onSelect(i) }
                        .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = opt,
                    color = if (sel) AppColors.AccentOn else AppColors.TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = AppColors.TextSecondary, fontSize = 13.sp)
        Text(value, color = AppColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun initialsOf(name: String): String =
    name.trim().split(" ").take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")

@Composable
private fun lightTextFieldColors() =
    TextFieldDefaults.colors(
        focusedContainerColor = AppColors.Background,
        unfocusedContainerColor = AppColors.Background,
        focusedTextColor = AppColors.TextPrimary,
        unfocusedTextColor = AppColors.TextPrimary,
        focusedLabelColor = AppColors.Accent,
        unfocusedLabelColor = AppColors.TextSecondary,
        focusedIndicatorColor = AppColors.Accent,
        unfocusedIndicatorColor = AppColors.Border,
        cursorColor = AppColors.Accent,
    )
