package com.rhyan57.rcst.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object AppColors {
    val Background     = Color(0xFF1E1F22)
    val Surface        = Color(0xFF2B2D31)
    val SurfaceVariant = Color(0xFF313338)
    val Primary        = Color(0xFF5865F2)
    val OnPrimary      = Color(0xFFFFFFFF)
    val TextPrimary    = Color(0xFFF2F3F5)
    val TextSecondary  = Color(0xFFB0B3B8)
    val TextMuted      = Color(0xFF72767D)
    val Divider        = Color(0xFF3F4147)
    val Error          = Color(0xFFED4245)
    val OnError        = Color(0xFFFFDFDE)
    val ErrorContainer = Color(0xFF3B1A1B)
    val Success        = Color(0xFF57F287)
}

object Radius {
    val Card   = RoundedCornerShape(16.dp)
    val Button = RoundedCornerShape(12.dp)
    val Small  = RoundedCornerShape(8.dp)
}

enum class ThemeMode {
    SYSTEM, DARK, AMOLED, LIGHT
}
