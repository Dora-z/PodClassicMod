package com.example.podclassic.media

enum class DCPhasePreset(
    val displayName: String,
    val strength: Float,
    val lowDelay: Float,
    val midDelay: Float,
    val highDelay: Float,
    val crossover: Float,
    val highCrossover: Float
) {
    NONE("Off", 0.0f, 0.0f, 0.0f, 0.0f, 500.0f, 2000.0f),
    NATURAL("Natural", 0.20f, 0.10f, 0.05f, 0.02f, 400.0f, 1800.0f),
    VOCAL("Vocal", 0.35f, 0.08f, 0.12f, 0.03f, 500.0f, 2200.0f),
    DEEP("Deep", 0.50f, 0.18f, 0.10f, 0.02f, 320.0f, 1600.0f),
    CRISP("Crisp", 0.42f, 0.06f, 0.08f, 0.05f, 650.0f, 2600.0f)
}
