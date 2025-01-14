package paintbox.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.utils.Align
import paintbox.binding.FloatVar
import paintbox.binding.Var
import kotlin.math.max
import kotlin.math.min


open class ImageNode(tex: TextureRegion? = null,
                     renderingMode: ImageRenderingMode = ImageRenderingMode.MAINTAIN_ASPECT_RATIO)
    : UIElement() {

    val textureRegion: Var<TextureRegion?> = Var(tex)
    val tint: Var<Color> = Var(Color(1f, 1f, 1f, 1f))
    val renderingMode: Var<ImageRenderingMode> = Var(renderingMode)
    val rotation: FloatVar = FloatVar(0f)
    val rotationPointX: FloatVar = FloatVar(0.5f)
    val rotationPointY: FloatVar = FloatVar(0.5f)
    val renderAlign: Var<Int> = Var(Align.center)

    override fun renderSelf(originX: Float, originY: Float, batch: SpriteBatch) {
        val tex = textureRegion.getOrCompute()
        if (tex != null) {
            val old = batch.packedColor

            val tmpColor = ColorStack.getAndPush()
            tmpColor.set(tint.getOrCompute())
            val opacity = apparentOpacity.getOrCompute()
            tmpColor.a *= opacity

            batch.color = tmpColor

            val renderBounds = this.contentZone
            val x = renderBounds.x.getOrCompute() + originX
            val y = originY - renderBounds.y.getOrCompute()
            val w = renderBounds.width.getOrCompute()
            val h = renderBounds.height.getOrCompute()

            when (val renderingMode = this.renderingMode.getOrCompute()) {
                ImageRenderingMode.FULL -> {
                    batch.draw(tex, x, y - h,
                            rotationPointX.getOrCompute() * w, rotationPointY.getOrCompute() * h,
                            w, h, 1f, 1f, rotation.getOrCompute())
                }
                ImageRenderingMode.MAINTAIN_ASPECT_RATIO, ImageRenderingMode.OVERSIZE -> {
                    val aspectWidth = w / tex.regionWidth
                    val aspectHeight = h / tex.regionHeight
                    val aspectRatio = if (renderingMode == ImageRenderingMode.MAINTAIN_ASPECT_RATIO)
                        min(aspectWidth, aspectHeight) else max(aspectWidth, aspectHeight)

                    val rw: Float = tex.regionWidth * aspectRatio
                    val rh: Float = tex.regionHeight * aspectRatio
                    val align = this.renderAlign.getOrCompute()
                    val xOffset: Float = when {
                        Align.isLeft(align) -> 0f
                        Align.isRight(align) -> w - rw
                        else -> w / 2 - (rw / 2)
                    }
                    val yOffset: Float = when {
                        Align.isTop(align) -> h - rh
                        Align.isBottom(align) -> 0f
                        else -> h / 2 - (rh / 2)
                    }
                    val rx: Float = xOffset
                    val ry: Float = yOffset


                    batch.draw(tex, x + rx, y + ry - h,
                            rotationPointX.getOrCompute() * rw, rotationPointY.getOrCompute() * rh,
                            rw, rh,
                            1f, 1f,
                            rotation.getOrCompute())
                }
            }

            ColorStack.pop()

            batch.packedColor = old
        }
    }

}

enum class ImageRenderingMode {

    /**
     * Draws the texture region at the full bounds of this element.
     */
    FULL,

    /**
     * Maintains the texture region's original aspect ratio, but doesn't oversize past the UI element bounds.
     */
    MAINTAIN_ASPECT_RATIO,

    /**
     * Maintains the texture region's original aspect ratio, but can oversize.
     */
    OVERSIZE,

}