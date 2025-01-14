package polyrhythmmania.screen.mainmenu

import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import polyrhythmmania.engine.Engine
import polyrhythmmania.world.SimpleRenderedEntity
import polyrhythmmania.world.World
import polyrhythmmania.world.render.Tileset
import polyrhythmmania.world.render.WorldRenderer


class EntityRowBlockDecor(world: World)
    : SimpleRenderedEntity(world) {

    companion object {
        val FULL_EXTENSION_TIME_BEATS: Float = 0.05f
    }

    enum class Type(val renderHeight: Float) {
        PLATFORM(1f),
        PISTON_A(1.25f),
        PISTON_DPAD(1.25f)
    }

    enum class PistonState {
        FULLY_EXTENDED,
        PARTIALLY_EXTENDED,
        RETRACTED
    }

    var pistonState: PistonState = PistonState.RETRACTED
    var type: Type = Type.PLATFORM
        set(value) {
            field = value
            pistonState = PistonState.RETRACTED
        }
    var active: Boolean = true

    val collisionHeight: Float
        get() = if (type == Type.PLATFORM || pistonState == PistonState.RETRACTED) 1f else 1.15f

    private var shouldPartiallyExtend: Boolean = false
    private var fullyExtendedAtBeat: Float = 0f


    fun fullyExtend(engine: Engine, beat: Float) {
        pistonState = PistonState.FULLY_EXTENDED
        shouldPartiallyExtend = true
        fullyExtendedAtBeat = beat
    }

    fun retract() {
        pistonState = PistonState.RETRACTED
    }

    override fun engineUpdate(engine: Engine, beat: Float, seconds: Float) {
        super.engineUpdate(engine, beat, seconds)
        if (shouldPartiallyExtend) {
            if (beat - fullyExtendedAtBeat >= FULL_EXTENSION_TIME_BEATS) {
                shouldPartiallyExtend = false
                pistonState = PistonState.PARTIALLY_EXTENDED
            }
        }
    }

    override fun getRenderWidth(): Float = 1f
    override fun getRenderHeight(): Float = this.type.renderHeight
    override fun getTextureRegionFromTileset(tileset: Tileset): TextureRegion {
        return when (type) {
            Type.PLATFORM -> tileset.platform
            Type.PISTON_A -> when (pistonState) {
                PistonState.FULLY_EXTENDED -> tileset.padAExtended
                PistonState.PARTIALLY_EXTENDED -> tileset.padAPartial
                PistonState.RETRACTED -> tileset.padARetracted
            }
            Type.PISTON_DPAD -> when (pistonState) {
                PistonState.FULLY_EXTENDED -> tileset.padDExtended
                PistonState.PARTIALLY_EXTENDED -> tileset.padDPartial
                PistonState.RETRACTED -> tileset.padDRetracted
            }
        }
    }

    override fun render(renderer: WorldRenderer, batch: SpriteBatch, tileset: Tileset, engine: Engine) {
        if (active) {
            super.render(renderer, batch, tileset, engine)
        }
    }
}