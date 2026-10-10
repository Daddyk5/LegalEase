package com.hcdc.legalease.feature.generator

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hcdc.legalease.core.designsystem.component.cards.HistoryCard
import com.hcdc.legalease.core.designsystem.component.spacers.VerticalSpacer
import com.hcdc.legalease.core.domain.model.ContractType
import com.hcdc.legalease.core.domain.model.GeneratedContract
import com.hcdc.legalease.core.domain.usecase.GetContractsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import com.hcdc.legalease.core.designsystem.R as DesignR

@HiltViewModel
class GeneratorHomeViewModel @Inject constructor(getContracts: GetContractsUseCase) : ViewModel() {
    /** Latest version of each saved contract, newest first. */
    val contracts: StateFlow<List<GeneratedContract>> =
        getContracts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

@Composable
fun GeneratorHomeScreen(
    onOpenEditor: (ContractEditorRoute) -> Unit,
    viewModel: GeneratorHomeViewModel = hiltViewModel(),
) {
    val contracts by viewModel.contracts.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.generator_title),
                    style = MaterialTheme.typography.displayLarge.copy(color = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.semantics { heading() },
                )
                VerticalSpacer(5.dp)
                Text(
                    text = stringResource(R.string.generator_subtitle),
                    style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onBackground),
                )
            }
            Image(
                painter = painterResource(DesignR.drawable.lawyer),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(100.dp),
            )
        }
        HorizontalDivider(color = Color.Gray.copy(alpha = .3f), thickness = 1.dp)
        VerticalSpacer(15.dp)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { SectionLabel(stringResource(R.string.generator_templates)) }
            items(ContractType.entries) { type ->
                HistoryCard(
                    title = stringResource(type.templateName),
                    onClick = { onOpenEditor(ContractEditorRoute(type = type.name)) },
                )
            }
            if (contracts.isNotEmpty()) {
                item {
                    VerticalSpacer(10.dp)
                    SectionLabel(stringResource(R.string.generator_saved))
                }
                items(contracts, key = { it.id }) { contract ->
                    HistoryCard(
                        title = stringResource(R.string.generator_saved_item, contract.title, contract.version),
                        onClick = { onOpenEditor(ContractEditorRoute(versionId = contract.id)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.primary),
        modifier = Modifier.semantics { heading() },
    )
}
