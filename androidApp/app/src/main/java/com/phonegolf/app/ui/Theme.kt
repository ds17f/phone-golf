package com.phonegolf.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF1F5A36),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7EBDD),
    onPrimaryContainer = Color(0xFF0E2A19),
    background = Color(0xFFEEF1E8),
    surface = Color(0xFFFFFFFF),
    error = Color(0xFFB4443A),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF6CC58F),
    onPrimary = Color(0xFF0D1410),
    primaryContainer = Color(0xFF1F4A30),
    onPrimaryContainer = Color(0xFFD7EBDD),
    background = Color(0xFF121614),
    surface = Color(0xFF1B211E),
    error = Color(0xFFEC7F74),
)

/** Golf convention: red is under par. */
val UnderPar @Composable get() = Golf.Red
val OverPar @Composable get() = MaterialTheme.colorScheme.onSurface

/** Fixed colors for the leaderboard and the paper scorecard (same in light and dark). */
object Golf {
    val Board = Color(0xFF17432A)
    val BoardLine = Color(0xFF2B5F40)
    val BoardText = Color(0xFFCFE3D6)
    val Plaque = Color(0xFFFBFAF4)
    val PlaqueInk = Color(0xFF142019)
    val Paper = Color(0xFFFBF6E6)
    val PaperLine = Color(0xFFC9BF9F)
    val PaperInk = Color(0xFF2A2A22)
    val PaperMuted = Color(0xFF6B6550)
    val Green = Color(0xFF1F5A36)
    val GreenDark = Color(0xFF173F27)
    val Gold = Color(0xFFE8C547)
    val Red = Color(0xFFC0282D)
}

@Composable
fun PhoneGolfTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
