package polyrhythmmania.editor.pane.track

import com.badlogic.gdx.graphics.g2d.SpriteBatch
import paintbox.binding.FloatVar
import paintbox.ui.ColorStack
import paintbox.ui.Pane
import paintbox.util.gdxutils.fillRect
import polyrhythmmania.editor.Click
import polyrhythmmania.editor.Editor
import polyrhythmmania.editor.PlayState
import polyrhythmmania.editor.pane.EditorPane
import kotlin.math.ceil
import kotlin.math.floor


class VerticalBeatLinesPane(val editorPane: EditorPane) : Pane() {

    val lineWidth: FloatVar = FloatVar(2f)

    init {
        this.doClipping.set(true)
    }

    override fun renderSelf(originX: Float, originY: Float, batch: SpriteBatch) {
        val renderBounds = this.contentZone
        val x = renderBounds.x.getOrCompute() + originX
        val y = originY - renderBounds.y.getOrCompute()
        val w = renderBounds.width.getOrCompute()
        val h = renderBounds.height.getOrCompute()
        val lastPackedColor = batch.packedColor

        val tmpColor = ColorStack.getAndPush()
        val editor = editorPane.editor
        val trackView = editor.trackView
        val trackViewBeat = trackView.beat.getOrCompute()
        val leftBeat = floor(trackViewBeat)
        val rightBeat = ceil(trackViewBeat + (w / trackView.pxPerBeat.getOrCompute()))
        val lineWidth = this.lineWidth.getOrCompute()

        val beatLines: Editor.BeatLines = editor.beatLines

        // Render vertical beat lines
        tmpColor.set(editorPane.palette.trackVerticalBeatLineColor.getOrCompute())
        batch.color = tmpColor
        for (b in leftBeat.toInt()..rightBeat.toInt()) {
            batch.fillRect(x + trackView.translateBeatToX(b.toFloat()) - lineWidth / 2f, y - h, lineWidth, h)
        }

        if (beatLines.active) {
            val snap = editor.snapping.getOrCompute()
            if (snap > 0f) {
                val snapCount = (1f / snap).toInt()
                tmpColor.a *= 0.5f
                batch.color = tmpColor
                for (b in beatLines.fromBeat.coerceAtLeast(leftBeat.toInt()) until beatLines.toBeat.coerceAtMost(rightBeat.toInt())) {
                    for (i in 1 until snapCount) {
                        batch.fillRect(x + trackView.translateBeatToX(b.toFloat() + (i * snap)) - lineWidth / 2f, y - h, lineWidth, h)
                    }
                }
            }
        }


        val currentClick = editor.click.getOrCompute()
        if (currentClick is Click.MoveMarker) {
            tmpColor.set(currentClick.type.color)
            batch.setColor(tmpColor.r, tmpColor.g, tmpColor.b, tmpColor.a * 0.25f)
            batch.fillRect(x + trackView.translateBeatToX(currentClick.originalPosition), y - h, lineWidth, h)
        }
        
        editor.markerMap.values.forEach { marker ->
            tmpColor.set(marker.type.color)
            batch.color = tmpColor
            val markerPos = marker.beat.getOrCompute()
            batch.fillRect(x + trackView.translateBeatToX(markerPos), y - h, lineWidth, h)
        }
        
        if (editor.playState.getOrCompute() != PlayState.STOPPED) {
            val tmpColor2 = ColorStack.getAndPush().set(editorPane.palette.trackPlayback.getOrCompute())
            val pos = editor.engineBeat.getOrCompute()
            batch.color = tmpColor2
            batch.fillRect(x + trackView.translateBeatToX(pos), y - h, lineWidth, h)
            ColorStack.pop()
        }

        ColorStack.pop()
        batch.packedColor = lastPackedColor
    }
}