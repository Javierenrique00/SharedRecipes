package com.co.recipes.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.co.recipes.domain.viewmodel.RecipeEditorViewModel

/**
 * A step field stops growing after this many lines and scrolls instead.
 *
 * Growth is the reason the rows below it moved on every line wrap while typing: an unbounded
 * `maxLines` makes the whole `LazyColumn` re-lay-out as the text wraps. Four lines keep a normal
 * step readable while bounding the movement.
 */
private const val StepMaxLines = 4

/**
 * "Next" behaviour shared by every single-line cell in the editor: move to the next cell and keep
 * the keyboard open. The default action closes the IME, and each close/reopen re-runs the IME
 * animation, which reads as the form jumping between cells.
 */
@Composable
internal fun nextCellActions(): KeyboardActions {
    val focusManager = LocalFocusManager.current
    return KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) })
}

@Composable
internal fun EditorNumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> if (input.all { it.isDigit() }) onValueChange(input) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Next,
        ),
        keyboardActions = nextCellActions(),
        modifier = modifier,
    )
}

@Composable
internal fun IngredientEditorRow(
    row: RecipeEditorViewModel.IngredientRow,
    onChange: (RecipeEditorViewModel.IngredientRow) -> Unit,
    onRemove: () -> Unit,
) {
    // Decimal (not Number) so the "," / "." separator the ViewModel already accepts in `quantity`
    // is actually typeable on the ingredient cell.
    val nextActions = nextCellActions()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = row.quantity,
            onValueChange = { onChange(row.copy(quantity = it)) },
            label = { Text("Qty") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = nextActions,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = row.unit,
            onValueChange = { onChange(row.copy(unit = it)) },
            label = { Text("Unit") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = nextActions,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = row.name,
            onValueChange = { onChange(row.copy(name = it)) },
            label = { Text("Ingredient") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = nextActions,
            modifier = Modifier.weight(2f),
        )
        TextButton(onClick = onRemove) { Text("×") }
    }
}

@Composable
internal fun StepEditorRow(
    row: RecipeEditorViewModel.StepRow,
    onChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = row.instruction,
            onValueChange = onChange,
            label = { Text("Step") },
            minLines = 1,
            maxLines = StepMaxLines,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRemove) { Text("×") }
    }
}

