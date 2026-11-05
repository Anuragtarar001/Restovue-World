package com.restorex.app.data

import com.restorex.app.domain.engine.FilterCatalog
import com.restorex.app.domain.model.Adjustments
import com.restorex.app.domain.model.ChannelBalance
import com.restorex.app.domain.model.ColorBalance
import com.restorex.app.domain.model.FilmEffects
import com.restorex.app.domain.model.FilterCategory
import com.restorex.app.domain.model.FilterPreset
import com.restorex.app.domain.model.SplitToning

class DefaultFilterCatalog : FilterCatalog {
    override val presets = listOf(
        p(FilterPreset.ORIGINAL_ID, "Original", FilterCategory.NATURAL, 1f),
        p("clean", "Clean", FilterCategory.NATURAL, .85f, Adjustments(brightness = .025f, contrast = 1.04f, whites = .08f, shadows = .04f)),
        p("vivid", "Vivid", FilterCategory.NATURAL, .8f, Adjustments(contrast = 1.12f, saturation = 1.20f, vibrance = .16f, sharpening = .12f)),
        p("soft", "Soft", FilterCategory.NATURAL, .8f, Adjustments(brightness = .045f, contrast = .92f, highlights = -.08f, clarity = -.12f)),
        p("warm", "Warm", FilterCategory.NATURAL, .75f, Adjustments(temperature = .28f, tint = .03f, vibrance = .07f)),
        p("cool", "Cool", FilterCategory.NATURAL, .75f, Adjustments(temperature = -.25f, tint = -.025f, contrast = 1.03f)),
        p("fresh", "Fresh", FilterCategory.NATURAL, .78f, Adjustments(exposure = .08f, temperature = -.08f, shadows = .10f, vibrance = .14f)),
        p("balanced", "Balanced", FilterCategory.NATURAL, .85f, Adjustments(highlights = -.13f, shadows = .16f, whites = .05f, blacks = -.04f, vibrance = .08f)),

        p("portrait_natural", "Portrait Natural", FilterCategory.PORTRAIT, .72f, Adjustments(exposure = .08f, highlights = -.12f, shadows = .12f, saturation = .96f, clarity = -.08f)),
        p("skin_warm", "Skin Warm", FilterCategory.PORTRAIT, .68f, Adjustments(temperature = .20f, tint = .05f, saturation = .95f, highlights = -.10f)),
        p("soft_portrait", "Soft Portrait", FilterCategory.PORTRAIT, .75f, Adjustments(brightness = .05f, contrast = .88f, clarity = -.22f, texture = -.10f, vignette = .08f)),
        p("golden_portrait", "Golden Portrait", FilterCategory.PORTRAIT, .7f, Adjustments(temperature = .34f, exposure = .10f, highlights = -.17f, splitToning = SplitToning(highlightHue = 42f, highlightSaturation = .18f))),
        p("matte_portrait", "Matte Portrait", FilterCategory.PORTRAIT, .78f, Adjustments(contrast = .92f, blacks = .18f, saturation = .9f, film = FilmEffects(fade = .18f))),
        p("bright_portrait", "Bright Portrait", FilterCategory.PORTRAIT, .7f, Adjustments(exposure = .22f, whites = .10f, highlights = -.19f, shadows = .12f, saturation = .94f)),
        p("neutral_portrait", "Neutral Portrait", FilterCategory.PORTRAIT, .8f, Adjustments(contrast = 1.02f, highlights = -.15f, shadows = .11f, temperature = .04f, saturation = .93f)),

        p("cinematic_teal", "Cinematic Teal", FilterCategory.CINEMATIC, .72f, Adjustments(contrast = 1.15f, saturation = .86f, colorBalance = ColorBalance(shadows = ChannelBalance(green = .14f, blue = .22f), highlights = ChannelBalance(red = .12f)))),
        p("cinematic_orange", "Cinematic Orange", FilterCategory.CINEMATIC, .7f, Adjustments(contrast = 1.12f, temperature = .24f, colorBalance = ColorBalance(shadows = ChannelBalance(blue = .10f), highlights = ChannelBalance(red = .22f, green = .08f)))),
        p("moody_blue", "Moody Blue", FilterCategory.CINEMATIC, .76f, Adjustments(exposure = -.12f, contrast = 1.18f, temperature = -.28f, saturation = .78f, shadows = -.10f)),
        p("warm_cinema", "Warm Cinema", FilterCategory.CINEMATIC, .74f, Adjustments(temperature = .30f, contrast = 1.10f, highlights = -.17f, vignette = .14f, film = FilmEffects(highlightRolloff = .25f))),
        p("dark_cinema", "Dark Cinema", FilterCategory.CINEMATIC, .68f, Adjustments(exposure = -.28f, contrast = 1.22f, blacks = -.16f, saturation = .80f, vignette = .22f)),
        p("vintage_cinema", "Vintage Cinema", FilterCategory.CINEMATIC, .78f, Adjustments(temperature = .14f, saturation = .68f, contrast = .94f, film = FilmEffects(fade = .24f, grain = .12f, colorFade = .20f))),
        p("desaturated_cinema", "Desaturated Cinema", FilterCategory.CINEMATIC, .75f, Adjustments(saturation = .52f, contrast = 1.18f, shadows = -.08f, highlights = -.12f, toneCurve = .15f)),

        p("classic_film", "Classic Film", FilterCategory.FILM, .82f, Adjustments(contrast = 1.06f, saturation = .92f, film = FilmEffects(grain = .16f, fade = .10f, highlightRolloff = .20f, seed = 101))),
        p("warm_film", "Warm Film", FilterCategory.FILM, .78f, Adjustments(temperature = .22f, saturation = .88f, film = FilmEffects(grain = .14f, fade = .12f, tone = .20f, seed = 102))),
        p("cool_film", "Cool Film", FilterCategory.FILM, .76f, Adjustments(temperature = -.20f, tint = -.04f, film = FilmEffects(grain = .15f, fade = .09f, tone = -.18f, seed = 103))),
        p("faded_film", "Faded Film", FilterCategory.FILM, .85f, Adjustments(contrast = .88f, saturation = .72f, film = FilmEffects(grain = .12f, fade = .34f, colorFade = .28f, seed = 104))),
        p("retro_film", "Retro Film", FilterCategory.FILM, .8f, Adjustments(temperature = .18f, tint = .10f, saturation = .80f, film = FilmEffects(grain = .20f, fade = .22f, lightLeak = .10f, seed = 105))),
        p("analog_film", "Analog Film", FilterCategory.FILM, .82f, Adjustments(contrast = .96f, vibrance = -.10f, film = FilmEffects(grain = .25f, fade = .14f, chromaticAberration = .10f, seed = 106))),
        p("film_grain", "Film Grain", FilterCategory.FILM, .65f, Adjustments(film = FilmEffects(grain = .42f, highlightRolloff = .10f, seed = 107))),
        p("cross_process", "Cross Process", FilterCategory.FILM, .7f, Adjustments(contrast = 1.20f, hue = 8f, saturation = 1.08f, colorBalance = ColorBalance(shadows = ChannelBalance(green = .22f, blue = .05f), highlights = ChannelBalance(red = .18f, blue = -.12f)), film = FilmEffects(grain = .10f, seed = 108))),

        p("classic_mono", "Classic Mono", FilterCategory.MONO, .9f, Adjustments(saturation = 0f, contrast = 1.08f, sharpening = .08f)),
        p("high_contrast_mono", "High Contrast Mono", FilterCategory.MONO, .82f, Adjustments(saturation = 0f, contrast = 1.35f, blacks = -.16f, whites = .10f)),
        p("soft_mono", "Soft Mono", FilterCategory.MONO, .8f, Adjustments(saturation = 0f, contrast = .86f, highlights = -.15f, clarity = -.12f)),
        p("silver", "Silver", FilterCategory.MONO, .82f, Adjustments(saturation = 0f, exposure = .10f, contrast = 1.16f, highlights = .08f, shadows = .12f)),
        p("matte_black", "Matte Black", FilterCategory.MONO, .84f, Adjustments(saturation = 0f, contrast = 1.10f, blacks = .20f, film = FilmEffects(fade = .22f, grain = .10f, seed = 201))),
        p("deep_black", "Deep Black", FilterCategory.MONO, .78f, Adjustments(saturation = 0f, contrast = 1.28f, blacks = -.28f, vignette = .15f)),
        p("vintage_mono", "Vintage Mono", FilterCategory.MONO, .82f, Adjustments(saturation = .08f, temperature = .16f, contrast = .94f, film = FilmEffects(fade = .24f, grain = .18f, seed = 202))),

        p("landscape_vivid", "Landscape Vivid", FilterCategory.LANDSCAPE, .78f, Adjustments(contrast = 1.16f, saturation = 1.16f, vibrance = .24f, clarity = .14f)),
        p("mountain", "Mountain", FilterCategory.LANDSCAPE, .78f, Adjustments(temperature = -.10f, contrast = 1.15f, shadows = .12f, clarity = .22f, sharpening = .14f)),
        p("forest", "Forest", FilterCategory.LANDSCAPE, .75f, Adjustments(exposure = -.04f, saturation = 1.06f, colorBalance = ColorBalance(midtones = ChannelBalance(green = .18f), shadows = ChannelBalance(blue = .08f)), clarity = .16f)),
        p("ocean", "Ocean", FilterCategory.LANDSCAPE, .72f, Adjustments(temperature = -.18f, tint = -.06f, vibrance = .22f, colorBalance = ColorBalance(midtones = ChannelBalance(blue = .16f, green = .08f)))),
        p("sunset", "Sunset", FilterCategory.LANDSCAPE, .7f, Adjustments(temperature = .38f, tint = .10f, saturation = 1.08f, highlights = -.15f, splitToning = SplitToning(highlightHue = 28f, highlightSaturation = .28f))),
        p("autumn", "Autumn", FilterCategory.LANDSCAPE, .72f, Adjustments(temperature = .25f, hue = -5f, saturation = 1.04f, colorBalance = ColorBalance(midtones = ChannelBalance(red = .15f, green = .05f)))),
        p("nature", "Nature", FilterCategory.LANDSCAPE, .8f, Adjustments(vibrance = .20f, shadows = .10f, highlights = -.12f, clarity = .10f)),
        p("deep_sky", "Deep Sky", FilterCategory.LANDSCAPE, .68f, Adjustments(contrast = 1.13f, temperature = -.10f, saturation = 1.08f, colorBalance = ColorBalance(highlights = ChannelBalance(blue = .18f)), vignette = .08f)),

        p("dreamy", "Dreamy", FilterCategory.CREATIVE, .68f, Adjustments(exposure = .10f, contrast = .84f, clarity = -.26f, splitToning = SplitToning(shadowHue = 275f, shadowSaturation = .12f, highlightHue = 35f, highlightSaturation = .16f), film = FilmEffects(lightLeak = .08f))),
        p("pastel", "Pastel", FilterCategory.CREATIVE, .72f, Adjustments(brightness = .07f, contrast = .78f, saturation = .72f, shadows = .18f, film = FilmEffects(fade = .18f, colorFade = .25f))),
        p("sepia", "Sepia", FilterCategory.CREATIVE, .82f, Adjustments(saturation = .22f, temperature = .40f, colorBalance = ColorBalance(shadows = ChannelBalance(red = .12f), midtones = ChannelBalance(red = .24f, green = .10f), highlights = ChannelBalance(red = .18f, green = .12f)))),
        p("bleach_bypass", "Bleach Bypass", FilterCategory.CREATIVE, .72f, Adjustments(saturation = .36f, contrast = 1.34f, highlights = -.08f, blacks = -.12f, clarity = .20f)),
        p("dramatic", "Dramatic", FilterCategory.CREATIVE, .7f, Adjustments(contrast = 1.32f, saturation = .78f, highlights = -.24f, shadows = -.18f, clarity = .28f, vignette = .18f)),
    ).also { items ->
        require(items.size == MAX_PRESETS)
        require(items.map { it.id }.distinct().size == items.size)
        require(items.first().id == FilterPreset.ORIGINAL_ID)
    }

    override fun find(id: String) = presets.firstOrNull { it.id == id }

    private fun p(
        id: String,
        name: String,
        category: FilterCategory,
        defaultIntensity: Float,
        adjustments: Adjustments = Adjustments(),
    ) = FilterPreset(id, name, adjustments, category, defaultIntensity)

    companion object { const val MAX_PRESETS = 50 }
}
