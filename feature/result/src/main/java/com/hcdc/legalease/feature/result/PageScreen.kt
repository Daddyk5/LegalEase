package com.hcdc.legalease.feature.result

import com.hcdc.legalease.core.designsystem.component.SecureContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.hcdc.legalease.core.designsystem.component.CustomLoading
import com.hcdc.legalease.core.designsystem.component.spacers.VerticalSpacer
import com.hcdc.legalease.core.domain.engine.PageImageRenderer
import com.hcdc.legalease.core.domain.engine.RenderedPage
import com.hcdc.legalease.core.domain.result.AppError
import com.hcdc.legalease.core.domain.result.AppResult
import com.hcdc.legalease.core.domain.usecase.GetDocumentPageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed interface PageUiState {
    data object Loading : PageUiState
    data class Shown(val imagePath: String) : PageUiState
    data class Error(val error: AppError) : PageUiState
}

@HiltViewModel
class PageViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    getPage: GetDocumentPageUseCase,
    private val renderer: PageImageRenderer,
) : ViewModel() {

    val page: Int = checkNotNull(savedStateHandle[PageRoute.ARG_PAGE])
    private val analysisId: String = checkNotNull(savedStateHandle[PageRoute.ARG_ANALYSIS_ID])
    private var rendered: RenderedPage? = null

    private val _uiState = MutableStateFlow<PageUiState>(PageUiState.Loading)
    val uiState: StateFlow<PageUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = when (val result = getPage(analysisId, page)) {
                is AppResult.Success -> PageUiState.Shown(result.value.imagePath).also { rendered = result.value }
                is AppResult.Failure -> PageUiState.Error(result.error)
            }
        }
    }

    /** Rendered PDF pages are temporary files; remove them when the viewer closes. */
    override fun onCleared() {
        rendered?.let(renderer::discard)
    }
}

/** Shows one page of the original document so a quoted clause can be checked in context. Pinch to zoom. */
@Composable
fun PageScreen(viewModel: PageViewModel = hiltViewModel()) {
    // Contract content: no screenshots, recordings, or recents thumbnail.
    SecureContent()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        Text(
            text = stringResource(R.string.page_title, viewModel.page),
            style = MaterialTheme.typography.displayLarge.copy(color = MaterialTheme.colorScheme.primary)
        )
        VerticalSpacer(10.dp)
        HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f), thickness = 1.dp)
        VerticalSpacer(15.dp)
        when (val current = state) {
            PageUiState.Loading -> CustomLoading()
            is PageUiState.Error -> Text(
                text = stringResource(R.string.result_error_not_found),
                color = MaterialTheme.colorScheme.error,
            )
            is PageUiState.Shown -> ZoomablePage(current.imagePath, viewModel.page)
        }
    }
}

@Composable
private fun ZoomablePage(path: String, page: Int) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { _: Offset, zoom: Float, pan: Offset, _: Float ->
        scale = (scale * zoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
        offset = if (scale == MIN_ZOOM) Offset.Zero else offset + pan
    }
    Box(modifier = Modifier.fillMaxSize().transformable(transform), contentAlignment = Alignment.TopCenter) {
        AsyncImage(
            model = File(path),
            contentDescription = stringResource(R.string.page_content_description, page),
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 5f
