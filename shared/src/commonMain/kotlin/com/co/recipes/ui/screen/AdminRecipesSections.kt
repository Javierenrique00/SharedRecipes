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
                text = "Writes a JSON file with all $recipeCount recipes, their ingredients, " +
                    "steps and comments — deleted ones included. It is the file “Restore” reads " +
                    "back, so keep it somewhere safe.",
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
                text = "Pick a backup file, choose which recipes to bring back, then confirm. " +
                    "Recipes that still exist keep their comments; deleted ones reappear with " +
                    "their original id.",
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
