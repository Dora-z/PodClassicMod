package com.example.podclassic.media

enum class TubeAmpPreset(
    val displayName: String,
    val gain: Float,
    val saturation: Float,
    val harmonics: Float,
    val ratio: Float,
    val attackMs: Float,
    val releaseMs: Float,
    val warmth: Float
) {
    NONE("Off", 1.0f, 0.0f, 0.0f, 1.0f, 10.0f, 100.0f, 0.0f),
    WARM("Warm", 1.08f, 0.25f, 0.18f, 1.10f, 12.0f, 120.0f, 0.35f),
    CLASSIC("Classic", 1.15f, 0.38f, 0.28f, 1.25f, 15.0f, 140.0f, 0.50f),
    DRIVE("Drive", 1.22f, 0.50f, 0.40f, 1.40f, 18.0f, 180.0f, 0.65f),
    BRIGHT("Bright", 1.10f, 0.20f, 0.12f, 1.05f, 8.0f, 90.0f, 0.20f)
}
