package com.aod.pomodromo.ui.components

import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Monospace clock digits with burn-in mitigation:
 * every 60s the whole clock drifts to a new random offset within ±10dp,
 * so no pixel cluster stays statically lit across long sessions.
 */
@Composable
fun AodClock(
    text: String,
    contentDescription: String,
    dimmed: Boolean,
    modifier: Modifier = Modifier,
) {
    var shift by remember { mutableStateOf(IntOffset(0, 0)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            shift = IntOffset(
                x = Random.nextInt(-10, 11),
                y = Random.nextInt(-10, 11),
            )
        }
    }

    Text(
        text = text,
        style = MaterialTheme.typography.displayLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier
            .offset { shift }
            .alpha(if (dimmed) 0.35f else 1f)
            .semantics { this.contentDescription = contentDescription },
    )
}
