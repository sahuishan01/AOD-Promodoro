package com.aod.pomodromo.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.aod.pomodromo.data.background.BundledBackground

/** Sealed background model resolved from the persisted settings string. */
sealed interface BackgroundModel {
    data class Bundled(val background: BundledBackground) : BackgroundModel
    data class UserImage(val uri: Uri) : BackgroundModel
}

/**
 * Full-bleed background surface for the timer screen.
 * User images are decoded through Coil with bounded size (media gate §4.4);
 * no network fetchers are registered, so only local content URIs resolve.
 */
@Composable
fun BackgroundSurface(
    model: BackgroundModel,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (model) {
            is BackgroundModel.Bundled -> {
                val colors = model.background.colors.map { Color(it) }
                val brush = if (colors.size == 1) {
                    Brush.verticalGradient(listOf(colors[0], colors[0]))
                } else {
                    Brush.verticalGradient(colors)
                }
                Box(Modifier.fillMaxSize().background(brush))
            }
            is BackgroundModel.UserImage -> {
                val context = LocalContext.current
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(model.uri)
                        .size(1080) // bounded decode: prevents pixel-bomb OOM
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                // Scrim keeps the clock legible over arbitrary images.
                Box(Modifier.fillMaxSize().background(Color(0x66000000)))
            }
        }
        content()
    }
}
