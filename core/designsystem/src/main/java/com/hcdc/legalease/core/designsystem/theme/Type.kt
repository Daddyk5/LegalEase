package com.hcdc.legalease.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.hcdc.legalease.core.designsystem.R

val RobotoSlab = FontFamily(
    Font(R.font.roboto_slab_regular, FontWeight.Normal),
    Font(R.font.roboto_slab_medium, FontWeight.Medium),
    Font(R.font.roboto_slab_bold, FontWeight.Bold)
)

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_bold, FontWeight.Bold)
)

val LegalEaseTypography = Typography(
    // Titles — Roboto Slab
    displayLarge = TextStyle( // Title 1
        fontFamily = RobotoSlab,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp
    ),
    headlineMedium = TextStyle( // Title 2
        fontFamily = RobotoSlab,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    titleMedium = TextStyle( // Title 3 (buttons/labels)
        fontFamily = RobotoSlab,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),

    // Body — Inter
    bodyLarge = TextStyle( // Body 1
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 26.sp
    ),
    bodyMedium = TextStyle( // Body 2
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp
    ),
    bodySmall = TextStyle( // Body 3
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp
    )
)
