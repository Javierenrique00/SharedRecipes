package com.co.recipes.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.co.recipes.domain.viewmodel.RecipeEditorViewModel
import com.co.recipes.infraestructure.files.PickedImage
import com.co.recipes.infraestructure.storage.RecipeImages

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
    onPickImage: () -> Unit,
    onRemoveImage: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
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

        StepImageEditor(
            pick = row.newImage,
            path = row.imagePath,
            onPick = onPickImage,
            onRemove = onRemoveImage,
        )
    }
}

/**
 * The photo controls under a method row: an empty state with one button, or the preview
 * plus Replace/Remove. The preview is the **normalized** JPEG for a new pick and the public
 * URL for one already stored, so what the admin sees is what gets uploaded/printed.
 */
@Composable
private fun StepImageEditor(
    pick: PickedImage?,
    path: String?,
    onPick: () -> Unit,
    onRemove: () -> Unit,
) {
    val model = imageModel(pick, path)

    if (model == null) {
        TextButton(onClick = onPick) { Text("+ Photo for this step") }
        return
    }

    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(6.dp))
        AsyncImage(
            model = model,
            contentDescription = "Photo for this step",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        Row {
            TextButton(onClick = onPick) { Text("Replace photo") }
            TextButton(onClick = onRemove) { Text("Remove photo") }
        }
    }
}

/** The recipe-level cover photo, edited the same way as a step photo. */
@Composable
internal fun CoverImageEditor(
    pick: PickedImage?,
    path: String?,
    isBusy: Boolean,
    onPick: () -> Unit,
    onRemove: () -> Unit,
) {
    val model = imageModel(pick, path)

    Column(Modifier.fillMaxWidth()) {
        if (model == null) {
            TextButton(onClick = onPick, enabled = !isBusy) { Text("+ Add cover photo") }
            return@Column
        }

        AsyncImage(
            model = model,
            contentDescription = "Cover photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp)),
        )
        Row {
            TextButton(onClick = onPick, enabled = !isBusy) { Text("Replace photo") }
            TextButton(onClick = onRemove) { Text("Remove photo") }
        }
    }
}

/**
 * Coil model for an image in the editor: the picked bytes when there is a local pick (so the
 * preview needs no upload), otherwise the stored object's public URL.
 */
internal fun imageModel(pick: PickedImage?, path: String?): Any? =
    pick?.bytes ?: RecipeImages.publicUrl(path)

