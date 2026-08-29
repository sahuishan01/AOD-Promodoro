package com.aod.pomodromo.data.background

/** A bundled background: one color = solid, two+ = vertical gradient. */
data class BundledBackground(
    val id: String,
    val displayName: String,
    val colors: List<Long>,
    /** High-contrast foreground tint that stays legible on this background. */
    val accentColor: Long,
) {
    val isGradient: Boolean get() = colors.size > 1
}

/**
 * Code-defined bundled backgrounds. Palettes favor dark, low-luminance surfaces
 * with tinted (never pure-white) accents for burn-in safety.
 */
object BackgroundCatalog {
    val all: List<BundledBackground> = listOf(
        BundledBackground("midnight", "Midnight", listOf(0xFF0B0E14), 0xFFF5F1E3),
        BundledBackground("ember", "Ember", listOf(0xFF1A0E0A), 0xFFE4572E),
        BundledBackground("forest", "Forest", listOf(0xFF0C1410), 0xFF7FB069),
        BundledBackground("dusk", "Dusk", listOf(0xFF14101F, 0xFF241A38), 0xFFCDB4DB),
        BundledBackground("abyss", "Abyss", listOf(0xFF04121A, 0xFF0A2A3A), 0xFF8ECAE6),
        BundledBackground("wine", "Wine", listOf(0xFF1C0A12, 0xFF33101F), 0xFFE5989B),
    )

    val default: BundledBackground = all.first()

    fun byId(id: String): BundledBackground? = all.find { it.id == id }
}
