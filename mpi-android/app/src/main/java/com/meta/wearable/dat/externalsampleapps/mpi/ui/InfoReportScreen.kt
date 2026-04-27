package com.meta.wearable.dat.externalsampleapps.mpi.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meta.wearable.dat.externalsampleapps.mpi.session.CaptureReportBuilder
import com.meta.wearable.dat.externalsampleapps.mpi.session.Session

@Composable
fun InfoReportScreen(
    session: Session?,
    modifier: Modifier = Modifier,
) {
    if (session == null) {
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
                    "No completed capture yet. Finish a capture session and this tab will render the structured technical summary, checklist, and evidence from that last run.",
                color = AppColors.TextSecondary,
                fontSize = 14.sp,
                lineHeight = 21.sp,
            )
        }
        return
    }

    val report = remember(session) { CaptureReportBuilder.build(session) }

    Column(
        modifier =
            modifier
                .background(AppColors.Background)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Info & Report",
            color = AppColors.TextPrimary,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Structured output for the most recent completed capture session.",
            color = AppColors.TextSecondary,
            fontSize = 14.sp,
        )

        ReportSection(title = "Technical snapshot") {
            report.facts.forEach { fact ->
                Text(
                    text =
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                append("${fact.label}: ")
                            }
                            append(fact.value)
                        },
                    color = AppColors.TextPrimary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        ReportSection(title = "Concise diagnosis") {
            Text(
                text = report.summary,
                color = AppColors.TextPrimary,
                fontSize = 14.sp,
                lineHeight = 21.sp,
            )
        }

        ReportSection(title = "Checklist") {
            report.checklist.forEach { item ->
                Text(
                    text = "• $item",
                    color = AppColors.TextPrimary,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        ReportSection(title = "Inspection report") {
            if (report.evidence.isEmpty()) {
                Text(
                    text =
                        "No still evidence was captured in this session. Use the capture button during the next run to pin useful frames into the report.",
                    color = AppColors.TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
            } else {
                report.evidence.forEach { asset ->
                    val imagePath = asset.previewImagePath ?: asset.clipFramePaths.firstOrNull() ?: asset.filePath
                    val bitmap = remember(imagePath) { BitmapFactory.decodeFile(imagePath) }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = asset.caption.ifBlank { "Captured evidence" },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(210.dp)
                                    .background(AppColors.SurfaceAlt, RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = asset.caption.ifBlank { "Captured evidence" },
                            color = AppColors.TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (asset.kind.name != "IMAGE") {
                            Text(
                                text = asset.kind.name.lowercase(),
                                color = AppColors.TextSecondary,
                                fontSize = 12.sp,
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }
            }
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
                .background(AppColors.Surface, RoundedCornerShape(18.dp))
                .padding(16.dp),
    ) {
        Text(
            text = title.uppercase(),
            color = AppColors.TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(10.dp))
        content()
    }
}
