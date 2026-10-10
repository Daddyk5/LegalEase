package com.hcdc.legalease.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hcdc.legalease.core.designsystem.component.buttons.PrimaryButton
import com.hcdc.legalease.core.designsystem.component.buttons.SecondaryButton
import com.hcdc.legalease.core.designsystem.component.cards.SummaryCard
import com.hcdc.legalease.core.designsystem.component.spacers.VerticalSpacer
import com.hcdc.legalease.core.designsystem.R as DesignR

/**
 * The privacy notice: what is processed, where, and for how long. Shown before first use, and from
 * Privacy & data. [onExit] is shown only on first launch, where declining closes the app.
 */
@Composable
fun PrivacyNoticeScreen(onAccept: () -> Unit, onExit: (() -> Unit)? = null) {
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
            Text(
                text = stringResource(R.string.privacy_title),
                style = MaterialTheme.typography.displayLarge.copy(color = MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            Image(
                painter = painterResource(DesignR.drawable.lawyer),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(80.dp),
            )
        }
        VerticalSpacer(10.dp)
        HorizontalDivider(color = Color.Gray.copy(alpha = .3f), thickness = 1.dp)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
        ) {
            Text(stringResource(R.string.privacy_intro), style = MaterialTheme.typography.bodyLarge)
            VerticalSpacer(8.dp)
            listOf(
                R.string.privacy_what_title to R.string.privacy_what_body,
                R.string.privacy_where_title to R.string.privacy_where_body,
                R.string.privacy_how_long_title to R.string.privacy_how_long_body,
                R.string.privacy_rights_title to R.string.privacy_rights_body,
            ).forEach { (title, body) -> SummaryCard(title = stringResource(title), text = stringResource(body)) }
        }
        PrimaryButton(text = stringResource(R.string.privacy_accept), onClick = onAccept)
        onExit?.let {
            VerticalSpacer(8.dp)
            SecondaryButton(text = stringResource(R.string.privacy_exit), onClick = it)
        }
    }
}
