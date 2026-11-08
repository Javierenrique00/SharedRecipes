package com.co.recipes.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.co.recipes.domain.dataclass.Comment
import com.co.recipes.domain.dataclass.Recipe

@Composable
internal fun VideoHeader(recipe: Recipe) {
    val thumbnail = recipe.youtubeThumbnailUrl ?: return
    val watchUrl = recipe.youtubeWatchUrl ?: return
    val uriHandler = LocalUriHandler.current

    Column {
        Card(modifier = Modifier.fillMaxWidth().clickable { uriHandler.openUri(watchUrl) }) {
            AsyncImage(
                model = thumbnail,
                contentDescription = "Video thumbnail for ${recipe.title}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            )
        }
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = { uriHandler.openUri(watchUrl) }) {
            Text("Watch on YouTube")
        }
    }
}

@Composable
internal fun MetaRow(recipe: Recipe) {
    val parts = listOfNotNull(
        recipe.servings?.let { "$it servings" },
        recipe.prepMinutes?.let { "prep $it min" },
        recipe.cookMinutes?.let { "cook $it min" },
    )
    if (parts.isEmpty()) return

    Text(
        text = parts.joinToString("  ·  "),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/**
 * One ingredient as "name ........ quantity unit".
 *
 * The name carries the weight, not the amount: Compose measures unweighted children first, so
 * the amount keeps its intrinsic width (one line, flush right) and the name absorbs whatever is
 * left, wrapping downwards. Without the weight the name grabbed the whole row and the amount was
 * squeezed into a one-character column, which wrapped vertically one glyph per line.
 */
@Composable
internal fun IngredientRow(name: String, display: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = display.removeSuffix(name).trim(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
internal fun CommentRow(
    comment: Comment,
    canDelete: Boolean,
    onDelete: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = comment.authorId.take(8),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (canDelete) {
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
        Text(comment.body, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun CommentComposer(
    draft: String,
    onDraftChange: (String) -> Unit,
    isPosting: Boolean,
    onPost: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            label = { Text("Add a comment") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = onPost,
            enabled = draft.isNotBlank() && !isPosting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isPosting) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Text("Post comment")
            }
        }
    }
}
