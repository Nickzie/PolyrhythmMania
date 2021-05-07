package polyrhythmmania.editor

import com.badlogic.gdx.graphics.Color
import io.github.chrislo27.paintbox.binding.Var
import io.github.chrislo27.paintbox.font.Markup
import io.github.chrislo27.paintbox.font.PaintboxFont
import io.github.chrislo27.paintbox.font.TextRun
import io.github.chrislo27.paintbox.util.gdxutils.grey
import polyrhythmmania.PRManiaGame


open class Palette(val main: PRManiaGame) {
    protected val background: Var<Color> = Var(Color().grey(0f, 0.75f))
    protected val borderTrim: Var<Color> = Var(Color().grey(1f, 1f))
    protected val separator: Var<Color> = Var(Color().grey(0.75f, 0.5f))
    
    val bgColor: Var<Color> = Var(Color().grey(0.094f))
    val toolbarBg: Var<Color> = Var(Color().grey(0.3f))
    
    val menubarIconTint: Var<Color> = Var(Color().grey(0.1f))
    
    val statusBg: Var<Color> = Var.bind { background.use() }
    val statusBorder: Var<Color> = Var.bind { borderTrim.use() }
    val statusTextColor: Var<Color> = Var(Color().grey(1f, 1f))
    
    val upperPaneBorder: Var<Color> = Var.bind { borderTrim.use() }
    val instantiatorPaneBorder: Var<Color> = Var.bind { upperPaneBorder.use() }
    val instantiatorSummaryText: Var<Color> = Var.bind { Color().grey(1f, 1f) }
    val instantiatorDescText: Var<Color> = Var.bind { instantiatorSummaryText.use() }
    val previewPaneBorder: Var<Color> = Var.bind { borderTrim.use() }
    val previewPaneSeparator: Var<Color> = Var.bind { separator.use() }
    val toolbarIconTint: Var<Color> = Var.bind { menubarIconTint.use() }
    val toolbarIconToolNeutralTint: Var<Color> = Var.bind { toolbarIconTint.use() }
    val toolbarIconToolActiveTint: Var<Color> = Var(Color.valueOf("00A070"))
    val toolbarIconToolActiveBorderTint: Var<Color> = Var(Color.valueOf("00F2AD"))
    
    val trackPaneBorder: Var<Color> = Var.bind { borderTrim.use() }
    val trackPaneTimeBg: Var<Color> = Var(Color().grey(0f, 1f))
    val trackPaneTimeText: Var<Color> = Var(Color().grey(1f, 1f))
    val trackPaneTextColor: Var<Color> = Var(Color().grey(1f, 1f))
    val trackPaneTempoBg: Var<Color> = Var(Color(0.4f, 0.4f, 0.9f, 1f))
    val trackVerticalBeatLineColor: Var<Color> = Var(Color().grey(1f, 0.4f))
    
    // Fonts
    
    val beatTimeFont: PaintboxFont = main.fontEditorBeatTime
    val beatTrackFont: PaintboxFont = main.fontEditorBeatTrack
    val instantiatorFont: PaintboxFont = main.fontEditorInstantiator
    val instantiatorSummaryFont: PaintboxFont = main.fontEditorInstantiatorSummary
    val instantiatorDescFont: PaintboxFont = main.fontEditorInstantiatorSummary
    
    // Markup
    val markup: Markup = Markup(mapOf("bold" to main.mainFontBold, "italic" to main.mainFontItalic,
            "bolditalic" to main.mainFontBoldItalic),
            TextRun(main.mainFont, ""),
            Markup.FontStyles("bold", "italic", "bolditalic"))
}