/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.mpi.session

data class InspectionTemplate(
    val id: String,
    val name: String,
    val sections: List<InspectionSection>,
)

data class InspectionSection(
    val id: String,
    val title: String,
    val findings: List<InspectionFindingTemplate>,
)

data class InspectionFindingTemplate(
    val id: String,
    val system: String,
    val component: String,
    val location: String = "",
    val recommendation: String = "",
    val defaultSeverity: FindingSeverity = FindingSeverity.YELLOW,
    val note: String = "",
)

object InspectionTemplates {
    val DEFAULT_TEMPLATE =
        InspectionTemplate(
            id = "mpi-default",
            name = "Dealership MPI",
            sections =
                listOf(
                    InspectionSection(
                        id = "safety",
                        title = "Safety",
                        findings =
                            listOf(
                                InspectionFindingTemplate(
                                    id = "tires-rear",
                                    system = "Tires",
                                    component = "Rear tires",
                                    location = "Rear left / rear right",
                                    recommendation = "Measure tread and document wear pattern",
                                    defaultSeverity = FindingSeverity.YELLOW,
                                    note = "Capture evidence once tread gauge is visible.",
                                ),
                                InspectionFindingTemplate(
                                    id = "brakes-front",
                                    system = "Brakes",
                                    component = "Front pads",
                                    recommendation = "Inspect pad life and rotor condition",
                                    defaultSeverity = FindingSeverity.YELLOW,
                                ),
                            ),
                    ),
                    InspectionSection(
                        id = "underhood",
                        title = "Underhood",
                        findings =
                            listOf(
                                InspectionFindingTemplate(
                                    id = "battery-12v",
                                    system = "Battery",
                                    component = "12V battery",
                                    recommendation = "Record tester result and health reading",
                                    defaultSeverity = FindingSeverity.GREEN,
                                ),
                                InspectionFindingTemplate(
                                    id = "fluids",
                                    system = "Fluids",
                                    component = "Fluid condition",
                                    recommendation = "Inspect levels and contamination",
                                    defaultSeverity = FindingSeverity.GREEN,
                                ),
                            ),
                    ),
                    InspectionSection(
                        id = "customer",
                        title = "Customer concern",
                        findings =
                            listOf(
                                InspectionFindingTemplate(
                                    id = "concern-verification",
                                    system = "Concern",
                                    component = "Customer concern verification",
                                    recommendation = "Document symptom reproduction and recommendation",
                                    defaultSeverity = FindingSeverity.YELLOW,
                                ),
                            ),
                    ),
                ),
        )

    fun instantiate(templateId: String = DEFAULT_TEMPLATE.id): List<InspectionFinding> {
        val template = if (templateId == DEFAULT_TEMPLATE.id) DEFAULT_TEMPLATE else DEFAULT_TEMPLATE
        return template.sections.flatMap { section ->
            section.findings.map { finding ->
                InspectionFinding(
                    id = finding.id,
                    system = finding.system,
                    component = finding.component,
                    location = finding.location,
                    recommendation = finding.recommendation,
                    severity = finding.defaultSeverity,
                    note = finding.note,
                )
            }
        }
    }
}
