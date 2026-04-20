/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.job

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * State for the dealership-tablet workflow. Minimal after stripping the
 * preset checklists: RO queue, selection, technician name, per-job bay
 * notes, per-job repair-start timestamp. In-memory only (session-scoped).
 */
data class JobUiState(
    val jobs: List<WorkOrder> = MOCK_JOBS,
    val selectedJobId: String? = null,
    val technicianName: String = "",
    /** Keyed by jobId. */
    val bayNotes: Map<String, String> = emptyMap(),
    val repairStartedAt: Map<String, Long> = emptyMap(),
) {
    val activeJob: WorkOrder?
        get() = jobs.firstOrNull { it.id == selectedJobId }
}

class JobViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(JobUiState())
    val uiState: StateFlow<JobUiState> = _uiState.asStateFlow()

    fun selectJob(id: String?) {
        _uiState.update { state ->
            if (id == null) return@update state.copy(selectedJobId = null)
            // Start the repair clock the first time an RO is opened.
            val started = state.repairStartedAt[id] ?: System.currentTimeMillis()
            state.copy(
                selectedJobId = id,
                repairStartedAt = state.repairStartedAt + (id to started),
            )
        }
    }

    fun setTechnicianName(name: String) {
        _uiState.update { it.copy(technicianName = name) }
    }

    fun setBayNotes(text: String) {
        val jobId = _uiState.value.selectedJobId ?: return
        _uiState.update { state ->
            state.copy(bayNotes = state.bayNotes + (jobId to text))
        }
    }
}
