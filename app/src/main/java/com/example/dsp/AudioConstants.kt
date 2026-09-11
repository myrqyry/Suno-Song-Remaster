package com.example.dsp

/** Shared constants for the Android audio processing pipeline. */
object AudioConstants {
    const val SAMPLE_RATE_44K = 44100
    const val SAMPLE_RATE_48K = 48000

    const val BIT_DEPTH_16 = 16
    const val BIT_DEPTH_24 = 24

    // Loudness normalization
    const val TARGET_LUFS_DEFAULT = -14
    const val TARGET_INTER_SAMPLE_PEAK_DEFAULT = -1.0
    const val TARGET_LRA = 11
    const val ABSOLUTE_THRESHOLD_LUFS = -70.0
    const val RELATIVE_THRESHOLD_LU = -10.0

    // Frequency bands (Hz)
    const val FREQ_LOW = 80.0
    const val FREQ_LOW_MID = 250.0
    const val FREQ_MID = 1000.0
    const val FREQ_HIGH_MID = 4000.0
    const val FREQ_HIGH = 12000.0

    // Filter frequencies
    const val HIGHPASS_FREQ = 30.0
    const val MUD_CUT_FREQ = 250.0
    const val BASS_MONO_FREQ = 120.0
    const val HARSHNESS_FREQ_1 = 4000.0
    const val HARSHNESS_FREQ_2 = 6000.0
    const val AIR_FREQ = 12000.0

    // Compression settings
    const val GLUE_THRESHOLD = -18.0
    const val GLUE_RATIO = 3.0
    const val GLUE_ATTACK = 0.02
    const val GLUE_RELEASE = 0.25

    // Limiter settings
    const val LIMITER_RATIO = 20.0
    const val LIMITER_ATTACK = 0.001
    const val LIMITER_RELEASE = 0.05

    // Harshness reduction
    const val HARSHNESS_Q_4K = 2.0
    const val HARSHNESS_GAIN_4K = -2.0
    const val HARSHNESS_Q_6K = 1.5
    const val HARSHNESS_GAIN_6K = -1.5
}
