package com.meta.wearable.dat.externalsampleapps.mpi.ui

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.meta.wearable.dat.externalsampleapps.mpi.session.DefaultMpiReportGenerationService
import com.meta.wearable.dat.externalsampleapps.mpi.session.EvidenceKind
import com.meta.wearable.dat.externalsampleapps.mpi.session.MpiEvidenceItem
import com.meta.wearable.dat.externalsampleapps.mpi.session.MpiInspectionStatus
import com.meta.wearable.dat.externalsampleapps.mpi.session.MpiReport
import com.meta.wearable.dat.externalsampleapps.mpi.session.MpiReportBuilder
import com.meta.wearable.dat.externalsampleapps.mpi.session.Session
import com.meta.wearable.dat.externalsampleapps.mpi.stream.GeminiService
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun InfoReportScreen(
    session: Session?,
    modifier: Modifier = Modifier,
) {
    if (session == null) {
        EmptyReportState(modifier)
        return
    }

    val context = LocalContext.current.applicationContext
    val exportScope = rememberCoroutineScope()
    var isExportingPdf by remember { mutableStateOf(false) }
    val mpiReport by produceState(
        initialValue = MpiReportBuilder.build(session),
        key1 = session.id,
    ) {
        value =
            DefaultMpiReportGenerationService(GeminiService(context))
                .generateFromSession(session)
    }
    val report = remember(mpiReport) { mpiReport.toUiMpiReport() }
    val expandedSections =
        remember(report) {
            mutableStateMapOf<String, Boolean>().apply {
                report.sections.forEach { section ->
                    put(section.id, !section.collapsedByDefault)
                }
            }
        }
    val expandedItems = remember(report) { mutableStateMapOf<String, Boolean>() }
    val selectedStatuses =
        remember(report) {
            mutableStateMapOf<String, UiInspectionStatus>().apply {
                report.sections.flatMap { it.items }.forEach { item ->
                    put(item.id, item.status)
                }
            }
        }
    val measurementEdits =
        remember(report) {
            mutableStateMapOf<String, String>().apply {
                report.sections.flatMap { it.items }.forEach { item ->
                    if (item.value.isNotBlank()) put(item.id, item.value)
                }
            }
        }
    val technicianNotes =
        remember(report) {
            mutableStateMapOf<String, String>().apply {
                report.sections.flatMap { it.items }.forEach { item ->
                    put(item.id, item.technicianNote)
                }
            }
        }
    var inspectionStory by remember(report) { mutableStateOf(report.inspectionStory) }

    Column(
        modifier =
            modifier
                .background(AppColors.Background)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column {
                Text(
                    text = "Infer & Report",
                    color = AppColors.TextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Smartphone MPI summary for the last capture.",
                    color = AppColors.TextSecondary,
                    fontSize = 12.sp,
                )
            }
            StatusChip(report.reportStatus.ifBlank { "Draft" })
        }

        ReportSection(title = "Technical snapshot") {
            report.facts.forEach { fact ->
                FactLine(fact)
            }
        }

        ReportSection(title = "Concise diagnosis") {
            Text(
                text = report.conciseDiagnosis,
                color = AppColors.TextPrimary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(
                text = "MPI CHECKLIST",
                color = AppColors.TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
            report.sections.forEach { section ->
                val sectionExpanded = expandedSections[section.id] == true
                MpiSectionCard(
                    section = section,
                    expanded = sectionExpanded,
                    onToggle = { expandedSections[section.id] = !sectionExpanded },
                    expandedItems = expandedItems,
                    selectedStatuses = selectedStatuses,
                    measurementEdits = measurementEdits,
                    technicianNotes = technicianNotes,
                )
            }
        }

        ReportSection(title = "Inspection story") {
            CompactTextField(
                value = inspectionStory,
                onValueChange = { inspectionStory = it },
                placeholder = "Write the final customer/advisor narrative.",
                minHeight = 118.dp,
            )
        }

        ExportPdfButton(
            isExporting = isExportingPdf,
            onClick = {
                if (isExportingPdf) return@ExportPdfButton
                val exportData =
                    report.toPdfExportData(
                        session = session,
                        selectedStatuses = selectedStatuses,
                        measurementEdits = measurementEdits,
                        technicianNotes = technicianNotes,
                        inspectionStory = inspectionStory,
                    )
                exportScope.launch {
                    isExportingPdf = true
                    val shared = MpiReportPdf.exportAndShare(context, exportData)
                    isExportingPdf = false
                    if (!shared) {
                        Toast.makeText(
                            context,
                            "Could not export MPI PDF.",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            },
        )
    }
}

@Composable
private fun EmptyReportState(modifier: Modifier) {
    Column(
        modifier =
            modifier
                .background(AppColors.Background)
                .statusBarsPadding()
                .padding(24.dp),
    ) {
        Text(
            text = "Info & Report",
            color = AppColors.TextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text =
                "No completed capture yet. Finish a capture session and this tab will render the structured technical summary, MPI checklist, and evidence from that last run.",
            color = AppColors.TextSecondary,
            fontSize = 14.sp,
            lineHeight = 21.sp,
        )
    }
}

@Composable
private fun MpiSectionCard(
    section: UiMpiSection,
    expanded: Boolean,
    onToggle: () -> Unit,
    expandedItems: MutableMap<String, Boolean>,
    selectedStatuses: MutableMap<String, UiInspectionStatus>,
    measurementEdits: MutableMap<String, String>,
    technicianNotes: MutableMap<String, String>,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(AppColors.Surface, RoundedCornerShape(16.dp))
                .border(1.dp, AppColors.Border, RoundedCornerShape(16.dp)),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = section.title,
                    color = AppColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                SectionCounts(section.items)
            }
            Text(
                text = if (expanded) "^" else "v",
                color = AppColors.TextMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        if (expanded) {
            section.items.forEachIndexed { index, item ->
                val itemExpanded = expandedItems[item.id] == true
                val selected = selectedStatuses[item.id] ?: item.status
                MpiItemRow(
                    item = item,
                    selectedStatus = selected,
                    expanded = itemExpanded,
                    measurementValue = measurementEdits[item.id] ?: item.value,
                    technicianNote = technicianNotes[item.id].orEmpty(),
                    onStatusChange = { selectedStatuses[item.id] = it },
                    onToggle = { expandedItems[item.id] = !itemExpanded },
                    onMeasurementChange = { measurementEdits[item.id] = it },
                    onTechnicianNoteChange = { technicianNotes[item.id] = it },
                )
                if (index != section.items.lastIndex) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(AppColors.Border),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionCounts(items: List<UiMpiItem>) {
    val green = items.count { it.status == UiInspectionStatus.GREEN }
    val yellow = items.count { it.status == UiInspectionStatus.YELLOW }
    val red = items.count { it.status == UiInspectionStatus.RED }
    val review = items.count { it.needsReview || it.status == UiInspectionStatus.UNKNOWN }
    Text(
        text = "$green green  $yellow yellow  $red red  $review review",
        color = AppColors.TextSecondary,
        fontSize = 11.sp,
        lineHeight = 15.sp,
    )
}

@Composable
private fun MpiItemRow(
    item: UiMpiItem,
    selectedStatus: UiInspectionStatus,
    expanded: Boolean,
    measurementValue: String,
    technicianNote: String,
    onStatusChange: (UiInspectionStatus) -> Unit,
    onToggle: () -> Unit,
    onMeasurementChange: (String) -> Unit,
    onTechnicianNoteChange: (String) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(if (expanded) AppColors.SurfaceAlt else AppColors.Surface),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusSelector(selectedStatus = selectedStatus, onStatusChange = onStatusChange)
            Text(
                text = item.label,
                color = AppColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            MeasurementBadge(value = measurementValue, unit = item.unit)
            EvidenceBadge(item.evidence.size)
            if (item.needsReview) {
                TinyBadge("Review", AppColors.AccentSoft, AppColors.Accent)
            }
            Text(
                text = if (expanded) "^" else "v",
                color = AppColors.TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        if (expanded) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DetailBlock("Auto comment", item.autoComment.ifBlank { defaultAutoComment(item, selectedStatus) })
                if (item.unit.isNotBlank()) {
                    LabeledTextField(
                        label = "Measurement",
                        value = measurementValue,
                        suffix = item.unit,
                        placeholder = "Enter value",
                        onValueChange = onMeasurementChange,
                    )
                }
                LabeledTextField(
                    label = "Technician note",
                    value = technicianNote,
                    placeholder = "Add technician note",
                    onValueChange = onTechnicianNoteChange,
                )
                DetailBlock(
                    label = "Advisor wording",
                    body = item.advisorWording.ifBlank { defaultAdvisorWording(item, selectedStatus, measurementValue) },
                )
                EvidenceStrip(item.evidence)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TinyBadge("Confidence ${confidenceLabel(item.confidence)}", AppColors.Surface, AppColors.TextSecondary)
                    if (item.needsReview) {
                        TinyBadge("Needs review", Color(0xFFFFF7ED), Color(0xFFC2410C))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusSelector(
    selectedStatus: UiInspectionStatus,
    onStatusChange: (UiInspectionStatus) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        listOf(UiInspectionStatus.GREEN, UiInspectionStatus.YELLOW, UiInspectionStatus.RED).forEach { status ->
            val selected = selectedStatus == status
            val shape = RoundedCornerShape(3.dp)
            val color = statusColor(status)
            Box(
                modifier =
                    Modifier
                        .size(14.dp)
                        .clip(shape)
                        .background(if (selected) color else color.copy(alpha = 0.16f))
                        .border(if (selected) 2.dp else 1.dp, if (selected) color else AppColors.Border, shape)
                        .clickable { onStatusChange(status) },
            )
        }
    }
}

@Composable
private fun MeasurementBadge(
    value: String,
    unit: String,
) {
    if (value.isBlank() && unit.isBlank()) return
    val display =
        when {
            value.isBlank() -> unit
            unit == "/32" -> "$value/32"
            unit.isBlank() -> value
            else -> "$value $unit"
        }
    TinyBadge(display, AppColors.SurfaceAlt, AppColors.TextPrimary)
}

@Composable
private fun EvidenceBadge(count: Int) {
    TinyBadge("EV $count", AppColors.SurfaceAlt, AppColors.TextSecondary)
}

@Composable
private fun TinyBadge(
    text: String,
    background: Color,
    foreground: Color,
) {
    Text(
        text = text,
        color = foreground,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier =
            Modifier
                .background(background, RoundedCornerShape(999.dp))
                .border(1.dp, AppColors.Border, RoundedCornerShape(999.dp))
                .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@Composable
private fun DetailBlock(
    label: String,
    body: String,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(AppColors.Surface, RoundedCornerShape(10.dp))
                .border(1.dp, AppColors.Border, RoundedCornerShape(10.dp))
                .padding(9.dp),
    ) {
        Text(
            text = label.uppercase(),
            color = AppColors.TextMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = body,
            color = AppColors.TextPrimary,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
    }
}

@Composable
private fun LabeledTextField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    suffix: String = "",
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label.uppercase(),
            color = AppColors.TextMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompactTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = placeholder,
                modifier = Modifier.weight(1f),
            )
            if (suffix.isNotBlank()) {
                Text(
                    text = suffix,
                    color = AppColors.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    minHeight: androidx.compose.ui.unit.Dp = 38.dp,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle =
            TextStyle(
                color = AppColors.TextPrimary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            ),
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .background(AppColors.Surface, RoundedCornerShape(10.dp))
                .border(1.dp, AppColors.Border, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 9.dp),
        decorationBox = { innerTextField ->
            Box {
                if (value.isBlank()) {
                    Text(
                        text = placeholder,
                        color = AppColors.TextMuted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                }
                innerTextField()
            }
        },
    )
}

@Composable
private fun EvidenceStrip(evidence: List<UiEvidence>) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = "EVIDENCE",
            color = AppColors.TextMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (evidence.isEmpty()) {
            Text(
                text = "No linked evidence yet.",
                color = AppColors.TextSecondary,
                fontSize = 12.sp,
            )
            return
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            evidence.forEach { asset ->
                EvidenceThumb(asset)
            }
        }
    }
}

@Composable
private fun EvidenceThumb(asset: UiEvidence) {
    val imagePath = asset.thumbnailPath ?: asset.filePath
    val bitmap = remember(imagePath) { imagePath?.let { BitmapFactory.decodeFile(it) } }
    Column(
        modifier =
            Modifier
                .width(96.dp)
                .background(AppColors.Surface, RoundedCornerShape(10.dp))
                .border(1.dp, AppColors.Border, RoundedCornerShape(10.dp))
                .padding(5.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = asset.caption.ifBlank { "Inspection evidence" },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AppColors.SurfaceAlt),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AppColors.SurfaceAlt),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = asset.type.uppercase(Locale.US).take(5),
                    color = AppColors.TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Text(
            text = asset.caption.ifBlank { asset.source.ifBlank { asset.type } },
            color = AppColors.TextPrimary,
            fontSize = 10.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 13.sp,
        )
        val timeRange = asset.timeRangeLabel()
        if (timeRange.isNotBlank()) {
            Text(
                text = timeRange,
                color = AppColors.TextMuted,
                fontSize = 9.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ReportSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(AppColors.Surface, RoundedCornerShape(16.dp))
                .border(1.dp, AppColors.Border, RoundedCornerShape(16.dp))
                .padding(14.dp),
    ) {
        Text(
            text = title.uppercase(),
            color = AppColors.TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(9.dp))
        content()
    }
}

@Composable
private fun FactLine(fact: UiReportFact) {
    Text(
        text =
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append("${fact.label}: ")
                }
                append(fact.value)
            },
        color = AppColors.TextPrimary,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    )
    Spacer(Modifier.height(5.dp))
}

@Composable
private fun StatusChip(text: String) {
    Text(
        text = text.uppercase(Locale.US),
        color = AppColors.Accent,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier =
            Modifier
                .background(AppColors.AccentSoft, RoundedCornerShape(999.dp))
                .padding(horizontal = 9.dp, vertical = 5.dp),
    )
}

@Composable
private fun statusColor(status: UiInspectionStatus): Color =
    when (status) {
        UiInspectionStatus.GREEN -> Color(0xFF16A34A)
        UiInspectionStatus.YELLOW -> Color(0xFFF59E0B)
        UiInspectionStatus.RED -> Color(0xFFDC2626)
        UiInspectionStatus.UNKNOWN -> AppColors.TextMuted
    }

@Composable
private fun ExportPdfButton(
    isExporting: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(AppColors.Accent, RoundedCornerShape(16.dp))
                .clickable(enabled = !isExporting, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isExporting) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = AppColors.AccentOn,
            )
            Spacer(Modifier.width(9.dp))
        }
        Text(
            text = if (isExporting) "Preparing PDF..." else "Export full MPI PDF",
            color = AppColors.AccentOn,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun UiMpiReport.toPdfExportData(
    session: Session,
    selectedStatuses: Map<String, UiInspectionStatus>,
    measurementEdits: Map<String, String>,
    technicianNotes: Map<String, String>,
    inspectionStory: String,
): MpiReportPdfData {
    val sections =
        sections.map { section ->
            MpiReportPdfSection(
                title = section.title,
                items =
                    section.items.map { item ->
                        val status = selectedStatuses[item.id] ?: item.status
                        val measurementValue = measurementEdits[item.id] ?: item.value
                        val technicianNote = technicianNotes[item.id].orEmpty()
                        val includeAuto =
                            item.autoComment.isNotBlank() ||
                                item.needsReview ||
                                status == UiInspectionStatus.YELLOW ||
                                status == UiInspectionStatus.RED
                        val includeAdvisor =
                            item.advisorWording.isNotBlank() ||
                                status == UiInspectionStatus.YELLOW ||
                                status == UiInspectionStatus.RED
                        MpiReportPdfItem(
                            label = item.label,
                            status = status.toPdfStatus(),
                            measurement = measurementValue,
                            unit = item.unit,
                            needsReview = item.needsReview,
                            autoComment =
                                if (includeAuto) {
                                    item.autoComment.ifBlank { defaultAutoComment(item, status) }
                                } else {
                                    ""
                                },
                            technicianNote = technicianNote,
                            advisorWording =
                                if (includeAdvisor) {
                                    item.advisorWording.ifBlank {
                                        defaultAdvisorWording(item, status, measurementValue)
                                    }
                                } else {
                                    ""
                                },
                            photoEvidence =
                                item.evidence
                                    .filter { it.type.equals("image", ignoreCase = true) }
                                    .map { it.toPdfEvidence() },
                        )
                    },
            )
        }
    val sessionPhotos =
        session.evidenceAssets
            .filter { it.kind == EvidenceKind.IMAGE }
            .map {
                MpiReportPdfEvidence(
                    id = it.id,
                    caption = it.caption,
                    filePath = it.filePath,
                    thumbnailPath = it.previewImagePath,
                    source = "capture",
                )
            }
    val itemPhotos =
        sections
            .flatMap { section -> section.items }
            .flatMap { item -> item.photoEvidence }
    val photoEvidence =
        (itemPhotos + sessionPhotos)
            .distinctBy { it.filePath ?: it.thumbnailPath ?: it.id }

    return MpiReportPdfData(
        title = "Multipoint Inspection Report",
        facts = facts.map { MpiReportPdfFact(it.label, it.value) },
        conciseDiagnosis = conciseDiagnosis,
        sections = sections,
        inspectionStory = inspectionStory,
        reportStatus = reportStatus,
        photoEvidence = photoEvidence,
    )
}

private fun UiInspectionStatus.toPdfStatus(): MpiReportPdfStatus =
    when (this) {
        UiInspectionStatus.GREEN -> MpiReportPdfStatus.GREEN
        UiInspectionStatus.YELLOW -> MpiReportPdfStatus.YELLOW
        UiInspectionStatus.RED -> MpiReportPdfStatus.RED
        UiInspectionStatus.UNKNOWN -> MpiReportPdfStatus.UNKNOWN
    }

private fun UiEvidence.toPdfEvidence(): MpiReportPdfEvidence =
    MpiReportPdfEvidence(
        id = id,
        caption = caption,
        filePath = filePath,
        thumbnailPath = thumbnailPath,
        source = source,
    )

private fun MpiReport.toUiMpiReport(): UiMpiReport =
    UiMpiReport(
        facts =
            listOf(
                UiReportFact("Capture date", technicalSnapshot.captureDate),
                UiReportFact("Mode", technicalSnapshot.mode),
                UiReportFact("Domain", technicalSnapshot.domain),
                UiReportFact("Video source", technicalSnapshot.videoSource),
                UiReportFact("Audio source", technicalSnapshot.audioSource),
                UiReportFact("Speech route", technicalSnapshot.speechRoute),
            ),
        conciseDiagnosis = conciseDiagnosis,
        sections =
            sections.map { section ->
                UiMpiSection(
                    id = section.id,
                    title = section.title,
                    collapsedByDefault = section.collapsedByDefault,
                    items =
                        section.items.map { item ->
                            UiMpiItem(
                                id = item.id,
                                label = item.label,
                                status = item.status.toUiStatus(),
                                value = item.value.orEmpty(),
                                unit = item.unit.orEmpty().normalizeUnit(),
                                needsReview = item.needsReview,
                                confidence = item.confidence,
                                autoComment = item.comments.autoComment.orEmpty(),
                                technicianNote = item.comments.technicianNote.orEmpty(),
                                advisorWording = item.comments.advisorWording.orEmpty(),
                                evidence = item.evidence.map { it.toUiEvidence() },
                            )
                        },
                )
            },
        inspectionStory = inspectionStory,
        reportStatus = reportStatus.name.lowercase(Locale.US).replace('_', ' '),
    )

private fun MpiInspectionStatus.toUiStatus(): UiInspectionStatus =
    when (this) {
        MpiInspectionStatus.GREEN -> UiInspectionStatus.GREEN
        MpiInspectionStatus.YELLOW -> UiInspectionStatus.YELLOW
        MpiInspectionStatus.RED -> UiInspectionStatus.RED
        MpiInspectionStatus.UNKNOWN -> UiInspectionStatus.UNKNOWN
    }

private fun MpiEvidenceItem.toUiEvidence(): UiEvidence =
    UiEvidence(
        id = id,
        type = type.name.lowercase(Locale.US),
        caption = caption.orEmpty(),
        thumbnailPath = thumbnailUri,
        filePath = uri,
        startTimeMs = startTimeMs,
        endTimeMs = endTimeMs,
        source = source.name.lowercase(Locale.US),
        confidence = confidence,
    )

private fun defaultAutoComment(
    item: UiMpiItem,
    selectedStatus: UiInspectionStatus,
): String =
    when (selectedStatus) {
        UiInspectionStatus.GREEN -> "${item.label} is marked OK."
        UiInspectionStatus.YELLOW -> "${item.label} needs attention soon or technician confirmation."
        UiInspectionStatus.RED -> "${item.label} is flagged red and should be reviewed before customer approval."
        UiInspectionStatus.UNKNOWN -> "${item.label} was not clearly confirmed in the available capture."
    }

private fun defaultAdvisorWording(
    item: UiMpiItem,
    selectedStatus: UiInspectionStatus,
    measurementValue: String,
): String {
    val measurement =
        when {
            measurementValue.isBlank() -> ""
            item.unit == "/32" -> " ($measurementValue/32)"
            item.unit.isNotBlank() -> " ($measurementValue ${item.unit})"
            else -> " ($measurementValue)"
        }
    return when (selectedStatus) {
        UiInspectionStatus.GREEN -> "${item.label} checked OK$measurement."
        UiInspectionStatus.YELLOW -> "Review ${item.label}$measurement with the customer and monitor or quote according to shop policy."
        UiInspectionStatus.RED -> "Recommend technician confirmation and correction for ${item.label}$measurement before delivery."
        UiInspectionStatus.UNKNOWN -> "${item.label} could not be verified from the available capture; technician confirmation is recommended."
    }
}

private fun confidenceLabel(confidence: Double?): String =
    when {
        confidence == null -> "n/a"
        confidence >= 0.75 -> "high"
        confidence >= 0.5 -> "med"
        else -> "low"
    }

private fun String.normalizeUnit(): String =
    when (uppercase(Locale.US)) {
        "/32 INCH", "/32 IN", "32ND", "32NDS" -> "/32"
        "PSI" -> "PSI"
        "MM" -> "mm"
        "V", "VOLT", "VOLTS" -> "V"
        else -> this
    }

private fun UiEvidence.timeRangeLabel(): String {
    val start = startTimeMs
    val end = endTimeMs
    return when {
        start != null && end != null -> "${start.msLabel()}-${end.msLabel()}"
        end != null && type == "video" -> end.msLabel()
        else -> ""
    }
}

private fun Long.msLabel(): String {
    val totalSeconds = this / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(Locale.US, minutes, seconds)
}

private data class UiMpiReport(
    val facts: List<UiReportFact>,
    val conciseDiagnosis: String,
    val sections: List<UiMpiSection>,
    val inspectionStory: String,
    val reportStatus: String,
)

private data class UiReportFact(
    val label: String,
    val value: String,
)

private data class UiMpiSection(
    val id: String,
    val title: String,
    val items: List<UiMpiItem>,
    val collapsedByDefault: Boolean,
)

private data class UiMpiItem(
    val id: String,
    val label: String,
    val status: UiInspectionStatus,
    val value: String,
    val unit: String,
    val needsReview: Boolean,
    val confidence: Double?,
    val autoComment: String,
    val technicianNote: String,
    val advisorWording: String,
    val evidence: List<UiEvidence>,
)

private data class UiEvidence(
    val id: String,
    val type: String,
    val caption: String,
    val thumbnailPath: String?,
    val filePath: String?,
    val startTimeMs: Long?,
    val endTimeMs: Long?,
    val source: String,
    val confidence: Double?,
)

private enum class UiInspectionStatus {
    GREEN,
    YELLOW,
    RED,
    UNKNOWN,
}
