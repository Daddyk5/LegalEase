package com.hcdc.legalease.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hcdc.legalease.core.domain.usecase.GetHistoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HistoryItem(val analysisId: String, val title: String)

data class HistoryUiState(
    val query: String = "",
    val items: List<HistoryItem> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    getHistory: GetHistoryUseCase,
) : ViewModel() {

    private val query = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    private val items = query.flatMapLatest { getHistory(it) }
        .map { analyses -> analyses.map { HistoryItem(it.id, it.documentName) } }

    val uiState: StateFlow<HistoryUiState> = combine(query, items) { query, items ->
        HistoryUiState(query = query, items = items, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), HistoryUiState())

    fun onQueryChange(newQuery: String) {
        query.value = newQuery
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
