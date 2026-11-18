package com.shadowbooks.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.shadowbooks.R

enum class LabSoundType {
    SELECT,
    FIZZ,
    FLAME,
    POUR,
    POP,
}

class LabSoundManager(context: Context) {
    private val soundPool: SoundPool
    private val soundIds = mutableMapOf<LabSoundType, Int>()

    var isMuted: Boolean = false

    init {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(attributes)
            .build()

        soundIds[LabSoundType.SELECT] = soundPool.load(context, R.raw.lab_select_element, 1)
        soundIds[LabSoundType.FIZZ] = soundPool.load(context, R.raw.lab_reaction_fizz, 1)
        soundIds[LabSoundType.FLAME] = soundPool.load(context, R.raw.lab_reaction_flame, 1)
        soundIds[LabSoundType.POUR] = soundPool.load(context, R.raw.lab_pour_liquid, 1)
        soundIds[LabSoundType.POP] = soundPool.load(context, R.raw.lab_pop_hydrogen, 1)
    }

    fun play(type: LabSoundType, volume: Float = 1f) {
        if (isMuted) return
        val soundId = soundIds[type] ?: return
        soundPool.play(soundId, volume, volume, 1, 0, 1f)
    }

    fun release() {
        soundPool.release()
    }
}
