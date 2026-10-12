package com.hcdc.legalease.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hcdc.legalease.core.designsystem.component.buttons.PrimaryButton
import com.hcdc.legalease.core.designsystem.component.buttons.SecondaryButton
import com.hcdc.legalease.core.designsystem.component.spacers.VerticalSpacer
import com.hcdc.legalease.core.domain.model.DeviceIntegrity
import com.hcdc.legalease.core.domain.model.RetentionPeriod
import com.hcdc.legalease.core.domain.result.AppError

@Composable
fun SettingsScreen(
    onOpenNotice: () -> Unit,
    onSignedOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val shareTitle = stringResource(R.string.settings_export_share)
    val signOutLabel = stringResource(R.string.settings_sign_out)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.ShareExport -> share(context, event.file, shareTitle)
                SettingsEvent.AccountDeleted, SettingsEvent.SignedOut -> onSignedOut()
            }
        }
    }

    val message = state.message?.let { messageText(it) }
    LaunchedEffect(message) {
        if (message == null) return@LaunchedEffect
        val recentLogin = state.message == SettingsMessage.RecentLoginRequired
        val result = snackbar.showSnackbar(
            message = message,
            actionLabel = signOutLabel.takeIf { recentLogin },
            duration = if (recentLogin) SnackbarDuration.Long else SnackbarDuration.Short,
        )
        viewModel.dismissMessage()
        if (result == SnackbarResult.ActionPerformed) viewModel.signOutNow()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.displayLarge.copy(color = MaterialTheme.colorScheme.primary),
                modifier = Modifier.semantics { heading() },
            )
            VerticalSpacer(5.dp)
            Text(stringResource(R.string.settings_subtitle), style = MaterialTheme.typography.bodyMedium)
            VerticalSpacer(10.dp)
            HorizontalDivider(color = Color.Gray.copy(alpha = .3f), thickness = 1.dp)
            if (state.integrity == DeviceIntegrity.COMPROMISED) {
                VerticalSpacer(10.dp)
                Text(
                    text = stringResource(R.string.settings_integrity_warning),
                    style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.error),
                )
            }
            state.settings?.let { settings ->
                BackupSection(
                    settings.cloudSyncEnabled,
                    enabled = state.task == null,
                    onToggle = viewModel::onBackupToggled,
                )
                RetentionSection(settings.retention, onSelect = viewModel::onRetention)
                LockSection(settings.appLockEnabled, state.lockAvailable, onToggle = viewModel::onAppLock)
            }
            DataSection(
                state,
                onExport = viewModel::export,
                onNotice = onOpenNotice,
                onDelete = viewModel::requestDelete,
            )
        }
        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (state.confirmingBackup) {
        ConfirmDialog(
            title = R.string.settings_backup_consent_title,
            body = R.string.settings_backup_consent_body,
            confirm = R.string.settings_backup_agree,
            onResult = viewModel::onBackupConsent,
        )
    }
    if (state.confirmingDelete) {
        ConfirmDialog(
            title = R.string.settings_delete_title,
            body = R.string.settings_delete_body,
            confirm = R.string.settings_delete_confirm,
            destructive = true,
            onResult = viewModel::onDeleteConfirmed,
        )
    }
}

@Composable
private fun BackupSection(checked: Boolean, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    SectionTitle(R.string.settings_backup_title)
    SwitchRow(
        checked = checked,
        text = stringResource(R.string.settings_backup_body),
        enabled = enabled,
        onToggle = onToggle,
    )
}

@Composable
private fun RetentionSection(current: RetentionPeriod, onSelect: (RetentionPeriod) -> Unit) {
    SectionTitle(R.string.settings_retention_title)
    Text(stringResource(R.string.settings_retention_body), style = MaterialTheme.typography.bodyMedium)
    VerticalSpacer(6.dp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RetentionPeriod.entries.forEach { period ->
            FilterChip(
                selected = current == period,
                onClick = { onSelect(period) },
                label = { Text(stringResource(period.labelRes)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

@Composable
private fun LockSection(enabled: Boolean, available: Boolean, onToggle: (Boolean) -> Unit) {
    val activity = LocalContext.current.findFragmentActivity()
    val title = stringResource(R.string.lock_prompt_title)
    SectionTitle(R.string.settings_lock_title)
    SwitchRow(
        checked = enabled,
        text = stringResource(if (available) R.string.settings_lock_body else R.string.settings_lock_unavailable),
        enabled = available && activity != null,
        // Turning the lock on requires authenticating once, so it is known to work before it is enforced.
        onToggle = { on ->
            if (on && activity != null) authenticate(activity, title) { onToggle(true) } else onToggle(false)
        },
    )
}

@Composable
private fun DataSection(state: SettingsUiState, onExport: () -> Unit, onNotice: () -> Unit, onDelete: () -> Unit) {
    SectionTitle(R.string.settings_data_title)
    Text(stringResource(R.string.settings_export_body), style = MaterialTheme.typography.bodyMedium)
    VerticalSpacer(8.dp)
    when (state.task) {
        SettingsTask.EXPORTING -> Busy(R.string.settings_exporting)
        SettingsTask.DELETING -> Busy(R.string.settings_deleting)
        null -> Unit
    }
    PrimaryButton(text = stringResource(R.string.settings_export), onClick = onExport, enabled = state.task == null)
    VerticalSpacer(8.dp)
    SecondaryButton(text = stringResource(R.string.settings_notice), onClick = onNotice)
    VerticalSpacer(8.dp)
    TextButton(onClick = onDelete, enabled = state.task == null, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.settings_delete), color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun SectionTitle(title: Int) {
    VerticalSpacer(18.dp)
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.primary),
        modifier = Modifier.semantics { heading() },
    )
    VerticalSpacer(4.dp)
}

@Composable
private fun SwitchRow(checked: Boolean, text: String, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onToggle),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun Busy(text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
    VerticalSpacer(8.dp)
}

@Composable
private fun ConfirmDialog(
    title: Int,
    body: Int,
    confirm: Int,
    onResult: (Boolean) -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = { onResult(false) },
        title = { Text(stringResource(title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            TextButton(onClick = { onResult(true) }) {
                Text(
                    stringResource(confirm),
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { onResult(false) }) { Text(stringResource(R.string.settings_cancel)) }
        },
    )
}

@Composable
private fun messageText(message: SettingsMessage): String = when (message) {
    is SettingsMessage.RetentionApplied -> stringResource(R.string.settings_retention_deleted, message.deleted)
    SettingsMessage.RecentLoginRequired -> stringResource(R.string.settings_msg_recent_login)
    is SettingsMessage.Failed -> stringResource(
        when (message.error) {
            AppError.Network -> R.string.settings_msg_network
            AppError.StorageFull -> R.string.settings_msg_storage
            else -> R.string.settings_msg_failed
        }
    )
}

private val RetentionPeriod.labelRes: Int
    get() = when (this) {
        RetentionPeriod.NEVER -> R.string.settings_retention_never
        RetentionPeriod.DAYS_30 -> R.string.settings_retention_30
        RetentionPeriod.DAYS_90 -> R.string.settings_retention_90
        RetentionPeriod.DAYS_365 -> R.string.settings_retention_365
    }
