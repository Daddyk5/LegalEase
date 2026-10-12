package com.hcdc.legalease.feature.history

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.hcdc.legalease.core.designsystem.component.cards.HistoryCard
import com.hcdc.legalease.core.designsystem.component.searchbar.ScanSearchBar
import com.hcdc.legalease.core.designsystem.component.spacers.VerticalSpacer

@Composable
fun HistoryScreen(
    onOpenAnalysis: (analysisId: String) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val items = viewModel.items.collectAsLazyPagingItems()
    val snackbarHostState = remember { SnackbarHostState() }
    val deletedMessage = stringResource(R.string.history_deleted)
    val undoLabel = stringResource(R.string.history_undo)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                HistoryEvent.ShowUndo -> {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    val result = snackbarHostState.showSnackbar(
                        message = deletedMessage,
                        actionLabel = undoLabel,
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.onUndo() else viewModel.onUndoExpired()
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(0.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "My Scans",
                        style = MaterialTheme.typography.displayLarge.copy(
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                    VerticalSpacer(5.dp)
                    Text(
                        text = "Your contract history in one place.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )
                }
                Image(
                    painter = painterResource(R.drawable.together),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(100.dp)
                )
            }

            HorizontalDivider(
                color = Color.Gray.copy(alpha = .3f),
                thickness = 1.dp,
            )
            VerticalSpacer(height = 15.dp)
            ScanSearchBar(
                query = uiState.query,
                onQueryChange = viewModel::onQueryChange
            )
            VerticalSpacer(height = 15.dp)

            val loaded = items.loadState.refresh is LoadState.NotLoading
            if (loaded && items.itemCount == 0) {
                Text(
                    text = if (uiState.query.isBlank()) {
                        stringResource(R.string.history_empty)
                    } else {
                        stringResource(R.string.history_no_matches, uiState.query)
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onBackground
                    ),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    textAlign = TextAlign.Center
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(15.dp),
            ) {
                items(count = items.itemCount, key = items.itemKey { it.analysisId }) { index ->
                    val item = items[index] ?: return@items
                    DeletableHistoryCard(
                        item = item,
                        onClick = { onOpenAnalysis(item.analysisId) },
                        onDelete = { viewModel.onDelete(item.analysisId) },
                    )
                }
            }
        }
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/**
 * The existing [HistoryCard], swipeable right-to-left to delete. TalkBack users get the same
 * action as a custom accessibility action, since swipe gestures are not available to them.
 */
@Composable
private fun DeletableHistoryCard(item: HistoryItem, onClick: () -> Unit, onDelete: () -> Unit) {
    val deleteLabel = stringResource(R.string.history_delete)
    val dismissState = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        onDismiss = { if (it == SwipeToDismissBoxValue.EndToStart) onDelete() },
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
        modifier = Modifier.semantics {
            customActions = listOf(
                CustomAccessibilityAction(deleteLabel) {
                    onDelete()
                    true
                }
            )
        },
    ) {
        HistoryCard(title = item.title, onClick = onClick)
    }
}
