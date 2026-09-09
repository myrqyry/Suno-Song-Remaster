package com.example.dsp

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

object SampleAudioGenerator {

    fun generateSample(type: String = "synthwave", sampleRate: Int = 44100): AudioBuffer {
        val durationSec = 12.0
        val numSamples = (durationSec * sampleRate).toInt()
        val buffer = AudioBuffer(2, numSamples, sampleRate)
        val left = buffer.getChannel(0)
        val right = buffer.getChannel(1)

        val tempoBpm = 118.0
        val beatSec = 60.0 / tempoBpm

        // Chord frequencies (F minor -> Ab major -> Eb major -> Bb minor)
        val chords = arrayOf(
            doubleArrayOf(174.61, 207.65, 261.63, 349.23), // Fm
            doubleArrayOf(207.65, 261.63, 311.13, 415.30), // Ab
            doubleArrayOf(155.56, 196.00, 233.08, 311.13), // Eb
            doubleArrayOf(116.54, 138.59, 174.61, 233.08)  // Bbm
        )
        val bassFreqs = doubleArrayOf(43.65, 51.91, 38.89, 29.14) // F1, Ab1, Eb1, Bb0

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val beatTime = t % (4 * beatSec)
            val barIdx = ((t / (4 * beatSec)).toInt()) % chords.size

            val currentChord = chords[barIdx]
            val currentBass = bassFreqs[barIdx]

            // 1. Bassline (Punchy sub + harmonics)
            val bassEnv = exp(-((t % beatSec) * 3.5))
            val bassSub = sin(2.0 * PI * currentBass * t) * 0.35 * bassEnv
            val bassHarmonic = sin(2.0 * PI * (currentBass * 2.0) * t) * 0.15 * bassEnv
            val bassTotal = (bassSub + bassHarmonic).toFloat()

            // 2. Chords / Pad (Stereo spread)
            var padL = 0.0
            var padR = 0.0
            for ((idx, freq) in currentChord.withIndex()) {
                val detuneL = freq * 1.002
                val detuneR = freq * 0.998
                val panPhase = idx * (PI / 4.0)
                padL += (sin(2.0 * PI * detuneL * t + panPhase) * 0.06)
                padR += (sin(2.0 * PI * detuneR * t - panPhase) * 0.06)
            }

            // 3. Arpeggiator / Pluck
            val arp16th = (t / (beatSec / 4.0)).toInt()
            val arpNoteIdx = arp16th % currentChord.size
            val arpFreq = currentChord[arpNoteIdx] * 2.0
            val arpTime = t % (beatSec / 4.0)
            val arpEnv = exp(-arpTime * 12.0)
            val arpSig = sin(2.0 * PI * arpFreq * t) * arpEnv * 0.18
            val arpPan = sin(2.0 * PI * 0.5 * t) // Panning back and forth
            val arpL = (arpSig * (0.5 + 0.4 * arpPan)).toFloat()
            val arpR = (arpSig * (0.5 - 0.4 * arpPan)).toFloat()

            // 4. Drum groove: Kick on every beat, snare on 2 & 4
            val beatPhase = (t / beatSec) % 1.0
            val isSnareBeat = ((t / beatSec).toInt() % 2) == 1

            // Kick drum
            var kick = 0f
            if (beatPhase < 0.2) {
                val kickT = beatPhase * beatSec
                val kickFreq = 120.0 * exp(-kickT * 35.0) + 45.0
                val kickEnv = exp(-kickT * 18.0)
                kick = (sin(2.0 * PI * kickFreq * kickT) * kickEnv * 0.45).toFloat()
            }

            // Snare / Clap
            var snare = 0f
            if (isSnareBeat && beatPhase < 0.25) {
                val snareT = beatPhase * beatSec
                val noise = ((i * 1103515245 + 12345) and 0x7FFFFFFF).toFloat() / 0x7FFFFFFF * 2f - 1f
                val snareBody = (sin(2.0 * PI * 180.0 * snareT) * exp(-snareT * 25.0) * 0.25).toFloat()
                val snareNoise = (noise * exp(-snareT * 18.0) * 0.2).toFloat()
                snare = snareBody + snareNoise
            }

            val mixL = (bassTotal * 0.6f) + (padL.toFloat()) + arpL + kick + (snare * 0.85f)
            val mixR = (bassTotal * 0.6f) + (padR.toFloat()) + arpR + kick + (snare * 0.85f)

            left[i] = mixL.coerceIn(-0.95f, 0.95f)
            right[i] = mixR.coerceIn(-0.95f, 0.95f)
        }

        return buffer
    }
}
