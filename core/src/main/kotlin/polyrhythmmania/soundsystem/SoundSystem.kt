package polyrhythmmania.soundsystem

import com.badlogic.gdx.utils.Disposable
import io.github.chrislo27.paintbox.Paintbox
import io.github.chrislo27.paintbox.util.Sync
import net.beadsproject.beads.core.AudioContext
import net.beadsproject.beads.core.IOAudioFormat
import javax.sound.sampled.*
import polyrhythmmania.soundsystem.beads.*
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlin.math.abs


/**
 * A wrapper for an [net.beadsproject.beads.core.AudioContext] and associated audio system utilities, using
 * [DaemonJavaSoundAudioIO] as the audio IO interface.
 */
class SoundSystem(private val mixer: Mixer,
                  val ioAudioFormat: IOAudioFormat = DEFAULT_AUDIO_FORMAT,
                  bufferSize: Int = AudioContext.DEFAULT_BUFFER_SIZE)
    : Disposable, TimingProvider {

    companion object {
        val AUDIO_FORMAT_44100: IOAudioFormat = IOAudioFormat(44_100f, 16, 2, 2, true, true)
        val AUDIO_FORMAT_48000: IOAudioFormat = IOAudioFormat(48_000f, 16, 2, 2, true, true)
        val DEFAULT_AUDIO_FORMAT: IOAudioFormat = AUDIO_FORMAT_44100

        fun createDefaultSoundSystem(ioAudioFormat: IOAudioFormat = DEFAULT_AUDIO_FORMAT,
                                     bufferSize: Int = AudioContext.DEFAULT_BUFFER_SIZE): SoundSystem {
            val audioFormat: AudioFormat = ioAudioFormat.toJavaAudioFormat()
            val datalineInfo: DataLine.Info = DataLine.Info(SourceDataLine::class.java, audioFormat)
            val supportedMixers: List<Mixer> = AudioSystem.getMixerInfo().map { AudioSystem.getMixer(it) }.filter { mixer ->
                try {
                    // Attempt to get the line. If it is not supported it will throw an exception.
                    mixer.getLine(datalineInfo)
//                    Paintbox.LOGGER.debug("Mixer ${mixer.mixerInfo} is compatible for outputting.")
                    true
                } catch (e: Exception) {
//                    Paintbox.LOGGER.debug("Mixer ${mixer.mixerInfo} is NOT compatible for outputting!")
                    false
                }
            }

            fun getDefaultMixer(): Mixer {
                val first = supportedMixers.firstOrNull {
                    val name = it.mixerInfo.name
                    !name.startsWith("Port ") || name.contains("Primary Sound Driver")
                }
                return first ?: supportedMixers.first()
            }

            return SoundSystem(getDefaultMixer(), ioAudioFormat, bufferSize)
        }
    }

    val audioContext: AudioContext =
            AudioContext(DaemonJavaSoundAudioIO(mixer), bufferSize, ioAudioFormat)

    @Volatile
    private var currentlyRealTime: Boolean = true

    @Volatile
    private var isDisposed: Boolean = false
    private val timingProvider: AdaptiveTimingProvider = this.AdaptiveTimingProvider()

    override var seconds: Float
        get() = timingProvider.seconds
        set(value) {
            timingProvider.seconds = value
        }
    override val listeners: MutableList<TimingListener> by timingProvider::listeners

    val isPaused: Boolean
        get() = audioContext.out.isPaused

    init {
        audioContext.out.addDependent(timingProvider.timingBead)
    }

    fun setPaused(paused: Boolean) {
        audioContext.out.pause(paused)
        if (paused) {
            timingProvider.forceSyncWithBead()
        }
    }

    fun startRealtime() {
        currentlyRealTime = true
        audioContext.start()
    }

    fun stopRealtime() {
        audioContext.stop()
        currentlyRealTime = false
    }

    fun startNonrealtimeTimed(msToRun: Double) {
        currentlyRealTime = false
        audioContext.runForNMillisecondsNonRealTime(msToRun)
    }

    override fun dispose() {
        isDisposed = true
        stopRealtime()
        audioContext.out.clearInputConnections()
        audioContext.out.clearDependents()
    }

    private inner class AdaptiveTimingProvider : TimingProvider {
        override var seconds: Float = 0f
        override val listeners: MutableList<TimingListener> = CopyOnWriteArrayList()

        val timingBead: TimingBead = TimingBead(audioContext)
        val realtimeThread: Thread

        init {
            timingBead.listeners += TimingListener { oldSeconds, newSeconds ->
                if (!currentlyRealTime && !isPaused) {
                    this.seconds = newSeconds
                    this.onUpdate(oldSeconds, newSeconds)
                }
            }
            realtimeThread = thread(start = true, isDaemon = true, name = "AdaptiveTimingProvider", priority = Thread.MAX_PRIORITY) {
                try {
                    val pollRateHz = 1.0 / (512.0 / 44100.0)//100
                    val forceSyncThreshold = 0.025f
                    val sync = Sync()

                    var nano = System.nanoTime()
                    while (!this@SoundSystem.isDisposed) {
                        sync.sync(pollRateHz)

                        // Action
                        if (currentlyRealTime && !isPaused) {
                            val secondsDiff = (1.0 / pollRateHz).toFloat()
                            val oldSeconds = this.seconds
                            this.seconds += secondsDiff
                            
                            if (abs(this.seconds - timingBead.seconds) >= forceSyncThreshold) {
                                println("AdaptiveTimingProvider Force sync: this was ${this.seconds} and TimingBead was ${timingBead.seconds} (delta ${abs(this.seconds - timingBead.seconds)}, force sync threshold ${forceSyncThreshold})")
                                // Force sync with timing bead if off by too much
                                this.seconds = timingBead.seconds
                            }
                            
                            this.onUpdate(oldSeconds, this.seconds)

//                            val ns = System.nanoTime() - nano
//                            println("Expected ${1000.0 / pollRateHz}, actual ${ns / 1_000_000.0}  delta ${(1000.0 / pollRateHz) - (ns / 1_000_000.0)}")
//                            nano = System.nanoTime()
                        }
                    }
                } catch (e: Exception) {
                    Paintbox.LOGGER.debug("AdaptiveTimingProvider thread encountered an exception")
                    e.printStackTrace()
                }
            }
        }
        
        fun forceSyncWithBead() {
            val oldSeconds = this.seconds
            this.seconds = timingProvider.timingBead.seconds
            this.onUpdate(oldSeconds, this.seconds)
        }
    }
}