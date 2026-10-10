package com.hcdc.legalease.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import com.hcdc.legalease.core.domain.di.ApplicationScope
import com.hcdc.legalease.core.domain.usecase.DeleteAnalysisUseCase
import com.hcdc.legalease.core.domain.usecase.GetHistoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryItem(val analysisId: String, val title: String)

data class HistoryUiState(val query: String = "")

sealed interface HistoryEvent {
    /** A row was removed; offer Undo until the snackbar is dismissed. */
    data object ShowUndo : HistoryEvent
}

/**
 * Paged history with deferred deletion: a deleted row disappears at once, but is only removed from
 * storage when the Undo snackbar times out, another row is deleted, or the screen goes away.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    getHistory: GetHistoryUseCase,
    private val deleteAnalysis: DeleteAnalysisUseCase,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private val _events = Channel<HistoryEvent>(Channel.BUFFERED)
    val events: Flow<HistoryEvent> = _events.receiveAsFlow()

    /** Awaiting the Undo window. */
    private var pendingDeletion: String? = null

    /** Rows hidden from the list: the pending one plus any committed but not yet gone from the pager. */
    private val hidden = MutableStateFlow<Set<String>>(emptySet())

    @OptIn(ExperimentalCoroutinesApi::class)
    val items: Flow<PagingData<HistoryItem>> = _uiState
        .map { it.query }
        .flatMapLatest { getHistory(it) }
        .map { page -> page.map { HistoryItem(it.id, it.documentName) } }
        .cachedIn(viewModelScope)
        .combine(hidden) { page, hiddenIds -> page.filter { it.analysisId !in hiddenIds } }

    fun onQueryChange(query: String) = _uiState.update { it.copy(query = query) }

    fun onDelete(analysisId: String) {
        commitPendingDeletion()
        pendingDeletion = analysisId
        hidden.update { it + analysisId }
        viewModelScope.launch { _events.send(HistoryEvent.ShowUndo) }
    }

    fun onUndo() {
        val id = pendingDeletion ?: return
        pendingDeletion = null
        hidden.update { it - id }
    }

    fun onUndoExpired() = commitPendingDeletion()

    override fun onCleared() {
        commitPendingDeletion()
    }

    /** Runs in the application scope so leaving the screen cannot cancel a confirmed deletion. */
    private fun commitPendingDeletion() {
        val id = pendingDeletion ?: return
        pendingDeletion = null
        appScope.launch { deleteAnalysis(id) }
    }
}
