package com.notasapp.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit

/**
 * Dimensiones responsivas basadas en el ancho de pantalla.
 *
 * Proporciona tamaños adaptables para que la UI se vea bien
 * en teléfonos pequeños (≤360dp), normales, tablets y landscape.
 *
 * Uso:
 * ```kotlin
 * val dimens = rememberResponsiveDimens()
 * Text(fontSize = dimens.headlineSize)
 * Modifier.padding(dimens.screenPadding)
 * ```
 */
data class ResponsiveDimens(
    val screenPadding: Dp,
    val cardPadding: Dp,
    val cardElevation: Dp,
    val cardCornerRadius: Dp,
    val itemSpacing: Dp,
    val sectionSpacing: Dp,
    val iconSizeSmall: Dp,
    val iconSizeMedium: Dp,
    val iconSizeLarge: Dp,
    val headlineSize: TextUnit,
    val titleSize: TextUnit,
    val bodySize: TextUnit,
    val labelSize: TextUnit,
    val gaugeSize: Dp,
    val gaugeStroke: Dp,
    val fabExtended: Boolean,
    val columnsGrid: Int
)

/**
 * Calcula las dimensiones responsivas según el tamaño de pantalla actual.
 *
 * Categorías:
 * - Compacto: ≤ 360dp de ancho (teléfonos pequeños)
 * - Normal: 361–599dp (teléfonos normales)
 * - Expandido: ≥ 600dp (tablets, landscape, foldables)
 */
@Composable
fun rememberResponsiveDimens(): ResponsiveDimens {
    val config = LocalConfiguration.current
    val screenWidthDp = config.screenWidthDp

    return when {
        screenWidthDp <= 360 -> ResponsiveDimens(
            screenPadding = 12.dp,
            cardPadding = 12.dp,
            cardElevation = 1.dp,
            cardCornerRadius = 12.dp,
            itemSpacing = 8.dp,
            sectionSpacing = 12.dp,
            iconSizeSmall = 18.dp,
            iconSizeMedium = 22.dp,
            iconSizeLarge = 60.dp,
            headlineSize = 20.sp,
            titleSize = 15.sp,
            bodySize = 13.sp,
            labelSize = 10.sp,
            gaugeSize = 90.dp,
            gaugeStroke = 8.dp,
            fabExtended = false,
            columnsGrid = 1
        )
        screenWidthDp < 600 -> ResponsiveDimens(
            screenPadding = 16.dp,
            cardPadding = 16.dp,
            cardElevation = 2.dp,
            cardCornerRadius = 16.dp,
            itemSpacing = 12.dp,
            sectionSpacing = 16.dp,
            iconSizeSmall = 20.dp,
            iconSizeMedium = 24.dp,
            iconSizeLarge = 80.dp,
            headlineSize = 22.sp,
            titleSize = 16.sp,
            bodySize = 14.sp,
            labelSize = 11.sp,
            gaugeSize = 120.dp,
            gaugeStroke = 10.dp,
            fabExtended = true,
            columnsGrid = 1
        )
        else -> ResponsiveDimens(
            screenPadding = 24.dp,
            cardPadding = 20.dp,
            cardElevation = 3.dp,
            cardCornerRadius = 20.dp,
            itemSpacing = 16.dp,
            sectionSpacing = 24.dp,
            iconSizeSmall = 24.dp,
            iconSizeMedium = 28.dp,
            iconSizeLarge = 100.dp,
            headlineSize = 26.sp,
            titleSize = 18.sp,
            bodySize = 16.sp,
            labelSize = 12.sp,
            gaugeSize = 150.dp,
            gaugeStroke = 12.dp,
            fabExtended = true,
            columnsGrid = 2
        )
    }
}
