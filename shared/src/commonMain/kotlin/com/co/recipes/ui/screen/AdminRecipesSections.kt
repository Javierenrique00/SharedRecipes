package com.co.recipes.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.co.recipes.domain.maintenance.OrphanReport
import com.co.recipes.domain.viewmodel.AdminRecipesViewModel
import com.co.recipes.infraestructure.dataclass.RecipeBackupDto

/**
 * The cards of the admin console, split from the screen for the same reason
 * `RecipeDetailSections.kt` exists: the screen keeps the scaffolding and the dialogs, the
 * sections stay presentational and easy to test in previews.
 */

@Composable
internal fun AdminSummary(activeCount: Int, deletedCount: Int) {
    Text(
        text = buildString {
            append("$activeCount recipes")
            if (deletedCount > 0) append("  ·  $deletedCount deleted")
        },
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun BackupSection(
    recipeCount: Int,
    isBusy: Boolean,
    onExport: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Backup", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Writes all $recipeCount recipes, their ingredients, steps and comments " +
                    "— deleted ones included — to a file. Recipes with photos are saved as a " +
                    ".zip (the JSON plus an images folder); without photos it stays a plain " +
                    ".json. It is the file “Restore” reads back, so keep it somewhere safe.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onExport, enabled = !isBusy, modifier = Modifier.fillMaxWidth()) {
                Text("Export all recipes to a file")
            }
        }
    }
}

@Composable
internal fun RestoreSection(isBusy: Boolean, onPick: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Restore", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Pick a backup file (.json or .zip), choose which recipes to bring back, " +
                    "then confirm. Recipes that still exist keep their comments; deleted ones " +
                    "reappear with their original id, photos included.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = onPick,
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Choose a backup file…")
            }
        }
    }
}

/** Checkable list of the recipes inside the picked file. */
@Composable
internal fun ImportPreviewSection(
    preview: AdminRecipesViewModel.ImportPreview,
    isBusy: Boolean,
    onToggle: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onRestore: () -> Unit,
    onCancel: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(preview.fileName, style = MaterialTheme.typography.titleSmall)
            Text(
                text = "${preview.recipes.size} recipes in the file · " +
                    "${preview.selectedCount} selected",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onSelectAll, enabled = !isBusy) { Text("Select all") }
                TextButton(onClick = onClearSelection, enabled = !isBusy) { Text("Select none") }
            }

            HorizontalDivider()

            preview.recipes.forEachIndexed { index, recipe ->
                RecipeBackupRow(
                    recipe = recipe,
                    checked = index in preview.selectedIndexes,
                    trailing = if (preview.isExisting(index)) "update" else "new",
                    onToggle = { onToggle(index) },
                )
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCancel, enabled = !isBusy) { Text("Cancel") }
                Button(
                    onClick = onRestore,
                    enabled = !isBusy && preview.selectedCount > 0,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Restore ${preview.selectedCount}")
                }
            }
        }
    }
}

/** Soft-deleted recipes, with a checkbox per row. */
@Composable
internal fun DeletedSection(
    recipes: List<RecipeBackupDto>,
    selectedIds: Set<String>,
    isBusy: Boolean,
    onToggle: (String) -> Unit,
    onRestoreSelected: () -> Unit,
    onRestoreAll: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Deleted recipes", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Clearing and deleting are soft, so these are still here. Restore the " +
                    "ones you want, or all of them.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (recipes.isEmpty()) {
                Text(
                    text = "Nothing is deleted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            recipes.forEach { recipe ->
                RecipeBackupRow(
                    recipe = recipe,
                    checked = recipe.id != null && recipe.id in selectedIds,
                    trailing = null,
                    onToggle = { recipe.id?.let(onToggle) },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onRestoreAll, enabled = !isBusy) { Text("Restore all") }
                Button(
                    onClick = onRestoreSelected,
                    enabled = !isBusy && selectedIds.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Restore selected")
                }
            }
        }
    }
}

/** Danger zone: empty the collection. */
@Composable
internal fun ClearSection(recipeCount: Int, isBusy: Boolean, onClear: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Clear recipes",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                text = "Empties the collection so you can restore only what you want. The " +
                    "default is a soft clear: everything stays recoverable from this screen or " +
                    "from a backup.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = onClear,
                enabled = !isBusy && recipeCount > 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Clear $recipeCount recipes…")
            }
        }
    }
}

/** One selectable recipe row, shared by the import preview and the deleted list. */
@Composable
internal fun RecipeBackupRow(
    recipe: RecipeBackupDto,
    checked: Boolean,
    trailing: String?,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f)) {
            Text(
                text = recipe.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val detail = buildString {
                if (recipe.ingredients.isNotEmpty()) append("${recipe.ingredients.size} ingredients")
                if (recipe.steps.isNotEmpty()) {
                    if (isNotEmpty()) append(" · ")
                    append("${recipe.steps.size} steps")
                }
                if (recipe.isDeleted && isNotEmpty()) append(" · deleted")
            }
            if (detail.isNotEmpty()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * Housekeeping between the two extremes of "restore everything" and "delete everything".
 *
 * Two independent actions, each confirmed on its own:
 *
 *  * purge the recipes already sitting in the trash (rows + their photos);
 *  * sweep photos in the bucket that no recipe points at any more.
 *
 * The orphan side is scan-then-delete on purpose: the admin sees how many objects and how
 * much space before anything is removed.
 */
@Composable
internal fun MaintenanceSection(
    deletedCount: Int,
    orphanScan: OrphanReport?,
    isScanning: Boolean,
    isBusy: Boolean,
    onPurgeDeleted: () -> Unit,
    onScanOrphans: () -> Unit,
    onDeleteOrphans: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Maintenance", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Keeps the database and the photo bucket in sync. Both actions are " +
                    "permanent, so each one asks for confirmation.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()

            Text(
                text = if (deletedCount == 0) {
                    "The trash is empty."
                } else {
                    "$deletedCount deleted ${if (deletedCount == 1) "recipe" else "recipes"} " +
                        "can be removed for good."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(
                onClick = onPurgeDeleted,
                enabled = !isBusy && deletedCount > 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (deletedCount > 0) {
                        "Delete $deletedCount deleted ${if (deletedCount == 1) "recipe" else "recipes"}"
                    } else {
                        "Delete deleted recipes"
                    },
                )
            }

            HorizontalDivider()

            Text("Orphaned photos", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = when {
                    orphanScan == null ->
                        "Photos in the bucket that no recipe uses. Scan to see if there are any."
                    orphanScan.isEmpty ->
                        "None — every photo in the bucket belongs to a recipe."
                    else ->
                        "${orphanScan.count} not used by any recipe · ${formatBytes(orphanScan.totalBytes)}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onScanOrphans,
                    enabled = !isBusy && !isScanning,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (isScanning) "Scanning…" else "Scan storage")
                }
                if (orphanScan != null && !orphanScan.isEmpty) {
                    Button(
                        onClick = onDeleteOrphans,
                        enabled = !isBusy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Delete ${orphanScan.count} photos")
                    }
                }
            }
            Text(
                text = "Photos uploaded in the last hour are never swept: an upload is " +
                    "written before the recipe that uses it.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** `430 KB`, `1.4 MB` — enough precision to judge whether a sweep is worth running. */
private fun formatBytes(bytes: Long): String {
    val kb = bytes / 1024
    return when {
        kb < 1 -> "$bytes B"
        kb < 1024 -> "$kb KB"
        else -> "${kb / 1024}.${(kb % 1024) * 10 / 1024} MB"
    }
}
