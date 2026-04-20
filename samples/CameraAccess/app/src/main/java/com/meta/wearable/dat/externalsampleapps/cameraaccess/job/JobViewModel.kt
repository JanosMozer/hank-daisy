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
 * State for the dealership-tablet workflow: RO queue, active job selection,
 * per-job checklist progress, per-job bay notes, technician name. Lives
 * alongside WearablesViewModel — separate because it has nothing to do with
 * device/stream state.
 *
 * In-memory only (no Room/persistent store). A session lives for as long as
 * the process does, which matches the current use case (one tech, one bay,
 * one day). If persistence is needed later, swap the backing maps.
 */
data class JobUiState(
    val jobs: List<WorkOrder> = MOCK_JOBS,
    val selectedJobId: String? = null,
    val technicianName: String = "",
    /** Keyed by `${jobId}|${stepId}`. */
    val diagChecked: Map<String, Boolean> = emptyMap(),
    val repairChecked: Map<String, Boolean> = emptyMap(),
    val verifyChecked: Map<String, Boolean> = emptyMap(),
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

    fun toggleDiag(stepTitle: String) = toggleChecked(stepTitle) { s, k ->
        s.copy(diagChecked = s.diagChecked.toggle(k))
    }

    fun toggleRepair(stepId: String) = toggleChecked(stepId) { s, k ->
        s.copy(repairChecked = s.repairChecked.toggle(k))
    }

    fun toggleVerify(itemId: String) = toggleChecked(itemId) { s, k ->
        s.copy(verifyChecked = s.verifyChecked.toggle(k))
    }

    fun setBayNotes(text: String) {
        val jobId = _uiState.value.selectedJobId ?: return
        _uiState.update { state ->
            state.copy(bayNotes = state.bayNotes + (jobId to text))
        }
    }

    /** Bay notes for the currently active job (or empty). */
    val bayNotes: String
        get() {
            val s = _uiState.value
            val id = s.selectedJobId ?: return ""
            return s.bayNotes[id].orEmpty()
        }

    private inline fun toggleChecked(
        subKey: String,
        updater: (JobUiState, String) -> JobUiState,
    ) {
        val jobId = _uiState.value.selectedJobId ?: return
        val composite = "$jobId|$subKey"
        _uiState.update { state -> updater(state, composite) }
    }

    private fun Map<String, Boolean>.toggle(key: String): Map<String, Boolean> {
        val next = this.toMutableMap()
        next[key] = !(this[key] ?: false)
        return next
    }
}
