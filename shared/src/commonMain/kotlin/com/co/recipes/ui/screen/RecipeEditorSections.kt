package com.co.recipes.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.co.recipes.domain.viewmodel.RecipeEditorViewModel

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
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
internal fun IngredientEditorRow(
    row: RecipeEditorViewModel.IngredientRow,
    onChange: (RecipeEditorViewModel.IngredientRow) -> Unit,
    onRemove: () -> Unit,
) {
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
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = row.unit,
            onValueChange = { onChange(row.copy(unit = it)) },
            label = { Text("Unit") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = row.name,
            onValueChange = { onChange(row.copy(name = it)) },
            label = { Text("Ingredient") },
            singleLine = true,
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
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRemove) { Text("×") }
    }
}
