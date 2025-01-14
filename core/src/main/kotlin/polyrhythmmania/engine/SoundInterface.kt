package polyrhythmmania.engine

import polyrhythmmania.soundsystem.BeadsAudio
import polyrhythmmania.soundsystem.BeadsMusic
import polyrhythmmania.soundsystem.SoundSystem
import polyrhythmmania.soundsystem.sample.MusicSamplePlayer
import polyrhythmmania.soundsystem.sample.PlayerLike


sealed class SoundInterface {
    companion object {
        fun createFromSoundSystem(soundSystem: SoundSystem?): SoundInterface =
                if (soundSystem == null) NoOp else Impl(soundSystem)
    }
    
    class Impl(val soundSystem: SoundSystem) : SoundInterface() {
        private var currentAudio: BeadsMusic? = null
        private var currentMusicPlayer: MusicSamplePlayer? = null

        @Synchronized override fun getCurrentMusicPlayer(audio: BeadsMusic?): MusicSamplePlayer? {
            if (audio == currentAudio) return currentMusicPlayer
            
            // Dispose current player
            val currentPlayer = this.currentMusicPlayer
            if (currentPlayer != null) {
                currentPlayer.pause(true)
                val out = soundSystem.audioContext.out
                for (i in 0 until out.ins) {
                    out.removeConnection(i, currentPlayer, i % currentPlayer.outs)
                }
                this.currentMusicPlayer = null
            }
            
            this.currentAudio = audio
            if (audio != null) {
                val out = soundSystem.audioContext.out
                val newPlayer = audio.createPlayer(soundSystem.audioContext)
                newPlayer.pause(true)
                out.addInput(newPlayer)
                this.currentMusicPlayer = newPlayer
            }
            
            return this.currentMusicPlayer
        }

        override fun playAudio(audio: BeadsAudio, callback: (player: PlayerLike) -> Unit): Long {
            return soundSystem.playAudio(audio, callback)
        }
    }

    object NoOp : SoundInterface() {
        override fun getCurrentMusicPlayer(audio: BeadsMusic?): MusicSamplePlayer? {
            return null
        }
        
        override fun playAudio(audio: BeadsAudio, callback: (player: PlayerLike) -> Unit): Long {
            return -1L
        }
    }

    /**
     * Gets the current [MusicSamplePlayer] for the given music [audio]. It is up to each implementation
     * to handle adding/removing/disposing of internal players.
     * 
     * A null [audio] should force an implementor to stop and remove the current music player, if any.
     * 
     * A newly created [MusicSamplePlayer] will always be paused at the start.
     */
    abstract fun getCurrentMusicPlayer(audio: BeadsMusic?): MusicSamplePlayer?
    
    abstract fun playAudio(audio: BeadsAudio, callback: (player: PlayerLike) -> Unit = {}): Long
    
    
}