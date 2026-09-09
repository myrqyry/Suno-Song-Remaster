package com.example.model

data class MasteringSettings(
    val normalizeLoudness: Boolean = true,
    val truePeakLimit: Boolean = true,
    val truePeakCeiling: Float = -1.0f,
    val targetLufs: Int = -14,
    val inputGain: Float = 0.0f,
    val stereoWidth: Int = 100,
    val cleanLowEnd: Boolean = true,
    val glueCompression: Boolean = false,
    val centerBass: Boolean = false,
    val cutMud: Boolean = false,
    val addAir: Boolean = false,
    val tameHarsh: Boolean = false,
    val sampleRate: Int = 44100,
    val bitDepth: Int = 16,
    val eqLow: Float = 0.0f,
    val eqLowMid: Float = 0.0f,
    val eqMid: Float = 0.0f,
    val eqHighMid: Float = 0.0f,
    val eqHigh: Float = 0.0f,
    val activePreset: String = "flat"
) {
    fun clamped(): MasteringSettings {
        return copy(
            truePeakCeiling = truePeakCeiling.coerceIn(-3.0f, 0.0f),
            targetLufs = targetLufs.coerceIn(-24, -6),
            inputGain = inputGain.coerceIn(-12.0f, 12.0f),
            stereoWidth = stereoWidth.coerceIn(0, 200),
            eqLow = eqLow.coerceIn(-12.0f, 12.0f),
            eqLowMid = eqLowMid.coerceIn(-12.0f, 12.0f),
            eqMid = eqMid.coerceIn(-12.0f, 12.0f),
            eqHighMid = eqHighMid.coerceIn(-12.0f, 12.0f),
            eqHigh = eqHigh.coerceIn(-12.0f, 12.0f),
            sampleRate = if (sampleRate == 48000) 48000 else 44100,
            bitDepth = if (bitDepth == 24) 24 else 16
        )
    }

    companion object {
        val PRESETS = mapOf(
            "flat" to EQPreset("Flat", 0f, 0f, 0f, 0f, 0f),
            "vocal" to EQPreset("Vocal", -2f, -1f, 2f, 3f, 1f),
            "bass" to EQPreset("Bass", 6f, 3f, 0f, -1f, -2f),
            "bright" to EQPreset("Bright", -1f, 0f, 1f, 3f, 5f),
            "warm" to EQPreset("Warm", 3f, 2f, 0f, -2f, -3f),
            "suno" to EQPreset("Suno AI", 1f, -2f, 1f, -1f, 2f)
        )
    }
}

data class EQPreset(
    val name: String,
    val low: Float,
    val lowMid: Float,
    val mid: Float,
    val highMid: Float,
    val high: Float
)
