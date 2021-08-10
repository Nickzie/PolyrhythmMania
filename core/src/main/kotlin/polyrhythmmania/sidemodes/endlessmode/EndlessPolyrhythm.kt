package polyrhythmmania.sidemodes.endlessmode

import net.beadsproject.beads.ugens.SamplePlayer
import paintbox.binding.FloatVar
import paintbox.binding.Var
import polyrhythmmania.PRManiaGame
import polyrhythmmania.editor.block.Block
import polyrhythmmania.editor.block.BlockType
import polyrhythmmania.editor.block.RowSetting
import polyrhythmmania.engine.Event
import polyrhythmmania.engine.EventChangePlaybackSpeed
import polyrhythmmania.engine.EventConditionalOnRods
import polyrhythmmania.engine.EventPlaySFX
import polyrhythmmania.engine.music.MusicVolume
import polyrhythmmania.engine.tempo.TempoChange
import polyrhythmmania.sidemodes.*
import polyrhythmmania.soundsystem.BeadsMusic
import polyrhythmmania.soundsystem.sample.LoopParams
import polyrhythmmania.util.RandomBagIterator
import polyrhythmmania.util.Semitones
import polyrhythmmania.world.EventDeployRod
import polyrhythmmania.world.EventRowBlockDespawn
import polyrhythmmania.world.WorldMode
import polyrhythmmania.world.tileset.StockTexturePacks
import polyrhythmmania.world.tileset.TilesetPalette
import java.time.LocalDate
import java.time.Year
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.*
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

class EndlessPolyrhythm(main: PRManiaGame, prevHighScore: EndlessModeScore,
                        /** A 48-bit seed. */ val seed: Long,
                        val dailyChallenge: LocalDate?)
    : AbstractEndlessMode(main, prevHighScore) {
    
    companion object {
        fun getCurrentDailyChallengeDate(): LocalDate {
            return ZonedDateTime.now(ZoneOffset.UTC).toLocalDate()
        }
        
        fun getSeedFromHexString(hex: String): Long {
            if (hex.isEmpty()) return 0L
            return hex.takeLast(8).toUInt(16).toLong() and 0x0_FFFF_FFFFL
        }
        
        fun getSeedFromLocalDate(date: LocalDate): Long {
            return (date.dayOfYear and 0b111111111 /* 9 bits */).toLong() or (((date.year.toLong() % Year.MAX_VALUE) and 0xFFFF_FFFF) shl 9) or (1L shl 42) 
        }
    }
    
    val random: Random = Random(seed)
    val difficultyBags: Map<Difficulty, RandomBagIterator<Pattern>> = EndlessPatterns.patternsByDifficulty.entries.associate { 
        it.key to RandomBagIterator(it.value, random, RandomBagIterator.ExhaustionBehaviour.SHUFFLE_EXCLUDE_LAST)
    }
    val difficultyFactor: FloatVar = FloatVar(0f)
    val speedIncreaseSemitones: Var<Int> = Var(0)

    init {
        container.texturePack.set(StockTexturePacks.hd)
        TilesetPalette.createGBA1TilesetPalette().applyTo(container.renderer.tileset)
        container.world.tilesetPalette.copyFrom(container.renderer.tileset)
        
        container.world.worldMode = WorldMode.POLYRHYTHM_ENDLESS
        container.renderer.dailyChallengeDate.set(dailyChallenge)
        container.engine.inputter.endlessScore.maxLives.set(3)
    }

    override fun initialize() {
        engine.tempos.addTempoChange(TempoChange(0f, 129f))

        val music: BeadsMusic = SidemodeAssets.polyrhythmTheme // Music loop (Polyrhythm) is 88 beats long
        val musicData = engine.musicData
        musicData.musicSyncPointBeat = 0f
        musicData.loopParams = LoopParams(SamplePlayer.LoopType.LOOP_FORWARDS, 0.0, music.musicSample.lengthMs)
        musicData.rate = 1f
        musicData.firstBeatSec = 0f
        musicData.beadsMusic = music
        musicData.volumeMap.addMusicVolume(MusicVolume(0f, 0f, 100))
        musicData.update()
        
        addInitialBlocks()
    }
    
    private fun addInitialBlocks() {
        val blocks = mutableListOf<Block>()
        blocks += ResetMusicVolumeBlock(engine).apply {
            this.beat = 0f
        }
        blocks += InitializationBlock().apply {
            this.beat = 0f
        }

        container.addBlocks(blocks)
    }
    
    fun nextGaussianAbs(stdDev: Float, mean: Float): Float {
        return (random.nextGaussian().absoluteValue * stdDev + mean).toFloat()
    }
    
    fun getStdDevFromDifficulty(): Float = 1 / 2f
    fun getMeanFromDifficulty(): Float = (difficultyFactor.get() / 2f).coerceIn(0f, 2.25f)

    override fun getDebugString(): String {
        return """seed: ${if (dailyChallenge != null) "daily challenge" else seed.toString(16).uppercase()}
difficultyFactor: ${difficultyFactor.get()}
distribution: mean = ${getMeanFromDifficulty()}, stddev = ${getStdDevFromDifficulty()}
""".dropLast(1)
    }

    /**
     * Generates a pattern when started.
     */
    inner class PatternGeneratorEvent(startBeat: Float, val delay: Float) : Event(engine) {
        
        init {
            this.beat = startBeat
        }
        
        override fun onStart(currentBeat: Float) {
            super.onStart(currentBeat)

            val gaussian = nextGaussianAbs(getStdDevFromDifficulty(), getMeanFromDifficulty()).coerceIn(0f, (Difficulty.VALUES.size - 1).toFloat())
            val difficultyInt = gaussian.roundToInt()
            val diff = Difficulty.VALUES[difficultyInt % Difficulty.VALUES.size]
            
            var pattern = difficultyBags.getValue(diff).next()
            if (pattern.flippable) {
                if (random.nextBoolean()) {
                    pattern = pattern.flip()
                }
            }
            
            val patternDuration: Int = 4 + 4 /* 4 beats setup, 4 beats pattern and teardown in one */
            val patternStart: Float = this.beat + delay
            
            engine.addEvents(pattern.toEvents(engine, patternStart))
            val anyA = pattern.rowA.row.isNotEmpty()
            val anyDpad = pattern.rowDpad.row.isNotEmpty()
            if (anyA) {
                engine.addEvent(EventDeployRod(engine, world.rowA, patternStart))
                engine.addEvent(EventRowBlockDespawn(engine, world.rowA, 0, patternStart + patternDuration - 0.25f, affectThisIndexAndForward = true))
            }
            if (anyDpad) {
                engine.addEvent(EventDeployRod(engine, world.rowDpad, patternStart))
                engine.addEvent(EventRowBlockDespawn(engine, world.rowDpad, 0, patternStart + patternDuration - 0.25f, affectThisIndexAndForward = true))
            }
            
            if (anyA || anyDpad) {
                val awardScoreBeat = patternStart + patternDuration + 0.01f
                engine.addEvent(EventConditionalOnRods(engine, awardScoreBeat,
                        if (anyA && anyDpad) RowSetting.BOTH else if (anyA) RowSetting.ONLY_A else RowSetting.ONLY_DPAD) {
                    engine.addEvent(EventIncrementEndlessScore(engine).also {
                        it.beat = awardScoreBeat
                    })
                    engine.addEvent(EventPlaySFX(engine, awardScoreBeat, "sfx_practice_moretimes_1"))
                })
            }
            
            // Loop
            engine.addEvent(PatternGeneratorEvent(patternStart + 4, 4f))
        }

        override fun onEnd(currentBeat: Float) {
            super.onEnd(currentBeat)
            difficultyFactor.set(difficultyFactor.get() + 0.1f)
        }
    }
    
    inner class InitializationBlock : Block(engine, EnumSet.allOf(BlockType::class.java)) {
        override fun compileIntoEvents(): List<Event> {
            return listOf(
                    object : Event(engine) {
                        override fun onStart(currentBeat: Float) {
                            random.setSeed(this@EndlessPolyrhythm.seed)
                            difficultyBags.values.forEach { it.shuffle() }
                            difficultyFactor.set(0f)
                            speedIncreaseSemitones.set(0)
                            engine.playbackSpeed = 1f
                        }
                    }.also { e ->
                        e.beat = this.beat
                    },
                    LoopingEvent(engine, 88f, { true }) { engine, startBeat ->
                        val currentSpeedIncrease = speedIncreaseSemitones.getOrCompute()
                        val newSpeed = (currentSpeedIncrease + 1).coerceAtMost(12)
                        speedIncreaseSemitones.set(newSpeed)
                        engine.playbackSpeed = Semitones.getALPitch(newSpeed)
                    }.also { e ->
                        e.beat = 88f
                    },
                    PatternGeneratorEvent(this.beat + 1, delay = 8f - 1),
            )
        }

        override fun copy(): Block = throw NotImplementedError()
    }

}