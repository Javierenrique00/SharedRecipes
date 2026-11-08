package com.co.recipes.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.co.recipes.domain.viewmodel.AdminRecipesViewModel

/**
 * Admin console: backup the whole collection to a file, restore some or all of it, and
 * clear it (soft by default) so a subset can be restored.
 *
 * Reached from the list screen's "Admin" action; every mutation behind it is checked by
 * `public.is_admin()` in Postgres, so hiding the button is convenience, not security.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminRecipesScreen(
    navController: NavHostController,
    viewModel: AdminRecipesViewModel,
) {
    LaunchedEffect(Unit) { viewModel.load() }

    val snackbarHostState = remember { SnackbarHostState() }
    var showClearDialog by remember { mutableStateOf(false) }
    var selectedDeletedIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    val status = viewModel.statusMessage
    LaunchedEffect(status) {
        if (status != null) {
            snackbarHostState.showSnackbar(status)
            viewModel.clearStatus()
        }
    }

    val error = viewModel.errorMessage
    LaunchedEffect(error) {
        if (error != null) {
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recipe admin") },
                navigationIcon = {
                    TextButton(onClick = { navController.popBackStack() }) { Text("Back") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                viewModel.isLoading && viewModel.recipes.isEmpty() ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        AdminSummary(
                            activeCount = viewModel.activeRecipes.size,
                            deletedCount = viewModel.deletedRecipes.size,
                        )
                    }

                    item {
                        BackupSection(
                            recipeCount = viewModel.recipes.size,
                            isBusy = viewModel.isBusy,
                            onExport = viewModel::exportAll,
                        )
                    }

                    item {
                        RestoreSection(
                            isBusy = viewModel.isBusy,
                            onPick = viewModel::pickBackupFile,
                        )
                    }

                    viewModel.importPreview?.let { preview ->
                        item {
                            ImportPreviewSection(
                                preview = preview,
                                isBusy = viewModel.isBusy,
                                onToggle = viewModel::toggleImportRecipe,
                                onSelectAll = viewModel::selectAllImportRecipes,
                                onClearSelection = viewModel::clearImportSelection,
                                onRestore = viewModel::restoreImported,
                                onCancel = viewModel::cancelImport,
                            )
                        }
                    }

                    item {
                        DeletedSection(
                            recipes = viewModel.deletedRecipes,
                            selectedIds = selectedDeletedIds,
                            isBusy = viewModel.isBusy,
                            onToggle = { id ->
                                selectedDeletedIds = selectedDeletedIds.toMutableSet().apply {
                                    if (!add(id)) remove(id)
                                }
                            },
                            onRestoreSelected = {
                                viewModel.restoreDeleted(
                                    viewModel.deletedRecipes.filter { it.id in selectedDeletedIds },
                                )
                                selectedDeletedIds = emptySet()
                            },
                            onRestoreAll = viewModel::restoreAllDeleted,
                        )
                    }

                    item {
                        ClearSection(
                            recipeCount = viewModel.recipes.size,
                            isBusy = viewModel.isBusy,
                            onClear = { showClearDialog = true },
                        )
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        ClearRecipesDialog(
            recipeCount = viewModel.recipes.size,
            isBusy = viewModel.isBusy,
            onDismiss = { showClearDialog = false },
            onConfirm = { mode ->
                showClearDialog = false
                selectedDeletedIds = emptySet()
                viewModel.clear(mode)
            },
        )
    }
}

/**
 * Two-step confirmation for the one destructive action in the app.
 *
 * Soft is preselected because it is fully recoverable; the permanent option needs the
 * word DELETE typed out, which is the only cheap way to stop a mis-tap on a phone.
 */
@Composable
private fun ClearRecipesDialog(
    recipeCount: Int,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (AdminRecipesViewModel.ClearMode) -> Unit,
) {
    var mode by remember { mutableStateOf(AdminRecipesViewModel.ClearMode.SOFT) }
    var confirmation by remember { mutableStateOf("") }

    val hardSelected = mode == AdminRecipesViewModel.ClearMode.HARD
    val confirmed = !hardSelected || confirmation.trim().equals(HARD_CONFIRMATION, ignoreCase = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clear $recipeCount recipes?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ClearModeOption(
                    selected = mode == AdminRecipesViewModel.ClearMode.SOFT,
                    title = "Soft clear (recoverable)",
                    description = "They disappear from the list but can be restored from " +
                        "this screen or from a backup file.",
                    onSelect = { mode = AdminRecipesViewModel.ClearMode.SOFT },
                )
                ClearModeOption(
                    selected = hardSelected,
                    title = "Delete permanently",
                    description = "Removes the recipes, their ingredients, steps and " +
                        "comments. This cannot be undone.",
                    onSelect = { mode = AdminRecipesViewModel.ClearMode.HARD },
                )

                if (hardSelected) {
                    Text(
                        text = "Type $HARD_CONFIRMATION to confirm:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(mode) },
                enabled = !isBusy && confirmed,
            ) {
                Text(if (hardSelected) "Delete permanently" else "Clear")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ClearModeOption(
    selected: Boolean,
    title: String,
    description: String,
    onSelect: () -> Unit,
) {
    Row(verticalAlignment = Alignment.Top) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(Modifier.padding(top = 10.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val HARD_CONFIRMATION = "DELETE"
