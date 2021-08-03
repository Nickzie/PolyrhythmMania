package polyrhythmmania.editor.pane.dialog

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.utils.Align
import com.eclipsesource.json.Json
import com.eclipsesource.json.WriterConfig
import paintbox.binding.FloatVar
import paintbox.binding.Var
import paintbox.font.TextAlign
import paintbox.packing.PackedSheet
import paintbox.registry.AssetRegistry
import paintbox.ui.Anchor
import paintbox.ui.ImageNode
import paintbox.ui.Pane
import paintbox.ui.UIElement
import paintbox.ui.area.Insets
import paintbox.ui.control.*
import paintbox.ui.layout.HBox
import paintbox.ui.layout.VBox
import paintbox.util.Matrix4Stack
import polyrhythmmania.Localization
import polyrhythmmania.editor.pane.EditorPane
import polyrhythmmania.ui.ColourPicker
import polyrhythmmania.ui.PRManiaSkins
import polyrhythmmania.world.*
import polyrhythmmania.world.entity.*
import polyrhythmmania.world.render.WorldRenderer
import polyrhythmmania.world.tileset.*
import kotlin.math.sign


class PaletteEditDialog(editorPane: EditorPane, val tilesetPalette: TilesetPalette,
                        val baseTileset: TilesetPalette?,
                        val titleLocalization: String = "editor.dialog.tilesetPalette.title",
) : EditorDialog(editorPane) {

    companion object {
        private val PR1_CONFIG: TilesetPalette = TilesetPalette.createGBA1TilesetPalette()
        private val PR2_CONFIG: TilesetPalette = TilesetPalette.createGBA2TilesetPalette()
    }
    
    data class ResetDefault(val baseConfig: TilesetPalette)
    
    private val availableResetDefaults: List<ResetDefault> = listOfNotNull(
            ResetDefault(PR1_CONFIG),
            ResetDefault(PR2_CONFIG),
            baseTileset?.let { ResetDefault(it) }
    )
    private var resetDefault: ResetDefault = availableResetDefaults.first()
    private val tempTileset: Tileset = Tileset(StockTexturePacks.gba /* This isn't used for rendering so any stock texture pack is fine */).apply {
        tilesetPalette.applyTo(this)
    }

    val groupFaceYMapping: ColorMappingGroupedCubeFaceY = ColorMappingGroupedCubeFaceY("groupCubeFaceYMapping")
    val groupPistonFaceZMapping: ColorMappingGroupedPistonFaceZ = ColorMappingGroupedPistonFaceZ("groupPistonFaceZMapping")
    private val groupMappings: List<ColorMapping> = listOf(groupFaceYMapping, groupPistonFaceZMapping)
    val allMappings: List<ColorMapping> = groupMappings + tilesetPalette.allMappings
    val allMappingsByID: Map<String, ColorMapping> = allMappings.associateBy { it.id }
    val currentMapping: Var<ColorMapping> = Var(allMappings[0])
    
    val objPreview: ObjectPreview = ObjectPreview()
    val colourPicker: ColourPicker = ColourPicker(false, font = editorPane.palette.musicDialogFont).apply { 
        this.setColor(currentMapping.getOrCompute().color.getOrCompute(), true)
    }

    /**
     * When false, updating the color in [ColourPicker] will NOT apply that colour to the tileset.
     * Used when switching between colour properties since there's no need for it to be applied
     */
    private var shouldColorPickerUpdateUpdateTileset: Boolean = true
    
    private val rodRotation: FloatVar = FloatVar(0f)

    init {
        resetGroupMappingsToTileset()
        
        this.titleLabel.text.bind { Localization.getVar(titleLocalization).use() }

        bottomPane.addChild(Button("").apply {
            Anchor.BottomRight.configure(this)
            this.bindWidthToSelfHeight()
            this.applyDialogStyleBottom()
            this.setOnAction {
                attemptClose()
            }
            this += ImageNode(TextureRegion(AssetRegistry.get<PackedSheet>("ui_icon_editor_linear")["x"])).apply {
                this.tint.bind { editorPane.palette.toolbarIconToolNeutralTint.use() }
            }
            this.tooltipElement.set(editorPane.createDefaultTooltip(Localization.getVar("common.close")))
        })

        val scrollPane: ScrollPane = ScrollPane().apply {
            this.vBarPolicy.set(ScrollPane.ScrollBarPolicy.ALWAYS)
            this.hBarPolicy.set(ScrollPane.ScrollBarPolicy.NEVER)
            (this.skin.getOrCompute() as ScrollPaneSkin).bgColor.set(Color(0f, 0f, 0f, 0f))
            this.bindWidthToParent(multiplier = 0.4f)
            this.vBar.blockIncrement.set(64f)
            this.vBar.skinID.set(PRManiaSkins.SCROLLBAR_SKIN)
        }
        contentPane.addChild(scrollPane)

        val listVbox = VBox().apply {
            this.spacing.set(1f)
        }

        listVbox.temporarilyDisableLayouts {
            val toggleGroup = ToggleGroup()
            allMappings.forEachIndexed { index, mapping ->
                listVbox += RadioButton(binding = { Localization.getVar("editor.dialog.tilesetPalette.object.${mapping.id}").use() },
                        font = editorPane.palette.musicDialogFont).apply {
                    this.textLabel.textColor.set(Color.WHITE.cpy())
                    this.textLabel.margin.set(Insets(0f, 0f, 8f, 8f))
                    this.textLabel.markup.set(editorPane.palette.markup)
                    this.imageNode.tint.set(Color.WHITE.cpy())
                    this.imageNode.padding.set(Insets(4f))
                    toggleGroup.addToggle(this)
                    this.bounds.height.set(48f)
                    this.onSelected = {
                        currentMapping.set(mapping)
                        shouldColorPickerUpdateUpdateTileset = false
                        updateColourPickerToMapping(mapping)
                        shouldColorPickerUpdateUpdateTileset = true
                    }
                    if (index == 0) selectedState.set(true)
                }
            }
        }
        listVbox.sizeHeightToChildren(300f)
        scrollPane.setContent(listVbox)

        val previewVbox = VBox().apply {
            Anchor.TopRight.configure(this)
            this.bindWidthToParent(multiplier = 0.6f, adjust = -8f)
            this.spacing.set(12f)
        }
        contentPane.addChild(previewVbox)
        previewVbox.temporarilyDisableLayouts {
            previewVbox += HBox().apply {
                this.spacing.set(8f)
                this.margin.set(Insets(4f))
                this.bounds.height.set(200f)
                this.temporarilyDisableLayouts {
                    this += objPreview.apply {
                        this.bounds.width.bind { 
                            bounds.height.useF() * (16f / 9f)
                        }
                    }
                    this += VBox().also { v -> 
                        v.bindWidthToParent(adjustBinding = { objPreview.bounds.width.useF() * -1 })
                        v.spacing.set(4f)
                        v.temporarilyDisableLayouts { 
                            v += Button(binding = { Localization.getVar("editor.dialog.tilesetPalette.reset").use() },
                                    font = editorPane.palette.musicDialogFont).apply {
                                this.applyDialogStyleContent()
                                this.bounds.height.set(40f)
                                this.setOnAction {
                                    val currentMapping = currentMapping.getOrCompute()
                                    val baseConfig = resetDefault.baseConfig
                                    baseConfig.allMappings.forEach { baseMapping ->
                                        val m = tilesetPalette.allMappingsByID.getValue(baseMapping.id)
                                        val baseColor = baseMapping.color.getOrCompute()
                                        m.color.set(baseColor.cpy())
                                    }
                                    tilesetPalette.applyTo(tempTileset)
                                    currentMapping.color.set(currentMapping.tilesetGetter(tempTileset).getOrCompute().cpy())
                                    updateColourPickerToMapping()
                                }
                            }
                            v += HBox().apply {
                                this.spacing.set(8f)
                                this.bounds.height.set(64f)
                                this.temporarilyDisableLayouts { 
                                    this += ImageNode(AssetRegistry.get<PackedSheet>("tileset_gba")["xyz"]).apply { 
                                        this.bounds.width.set(64f)
                                    }
                                    this += TextLabel("[b][color=#FF0000]X-[] [color=#00D815]Y+[] [color=#0000FF]Z+[][]").apply { 
                                        this.markup.set(editorPane.palette.markup)
                                        this.bindWidthToParent(adjust = -64f)
                                        this.textColor.set(Color.WHITE)
                                        this.renderBackground.set(true)
                                        this.bgPadding.set(Insets(8f))
                                        (this.skin.getOrCompute() as TextLabelSkin).defaultBgColor.set(Color(1f, 1f, 1f, 01f))
                                    }
                                }
                            }
                            v += HBox().apply { 
                                this.bounds.height.set(32f)
                                this.spacing.set(8f)
                                this += TextLabel(binding = { Localization.getVar("editor.dialog.tilesetPalette.rotateRod").use() }).apply {
                                    this.markup.set(editorPane.palette.markup)
                                    this.textColor.set(Color.WHITE)
                                    this.bounds.width.set(100f)
                                    this.renderAlign.set(Align.right)
                                }
                                this += Pane().apply {
                                    this.padding.set(Insets(4f))
                                    this += Slider().apply slider@{
                                        this.bindWidthToParent(adjust = -100f)
                                        this.minimum.set(0f)
                                        this.maximum.set(1f)
                                        this.tickUnit.set(0f)
                                        this.setValue(0f)
                                        rodRotation.bind { this@slider.value.useF() * 2f }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            previewVbox += colourPicker.apply {
                this.bindWidthToParent()
                this.bounds.height.set(220f)
            }
        }

        
        
        val bottomHbox = HBox().apply {
            this.spacing.set(8f)
            this.bindWidthToParent(adjustBinding = { -(bounds.height.useF() + 4f) })
        }
        bottomHbox.temporarilyDisableLayouts {
            bottomHbox += TextLabel(binding = { Localization.getVar("editor.dialog.tilesetPalette.resetLabel").use() },
                    font = editorPane.palette.musicDialogFont).apply {
                this.markup.set(editorPane.palette.markup)
                this.textColor.set(Color.WHITE.cpy())
                this.renderAlign.set(Align.right)
                this.textAlign.set(TextAlign.RIGHT)
                this.doLineWrapping.set(true)
                this.bounds.width.set(250f)
            }
            val toggleGroup = ToggleGroup()
            bottomHbox += VBox().apply { 
                this.spacing.set(2f)
                this.bounds.width.set(185f)
                this += RadioButton(binding = { Localization.getVar("editor.dialog.tilesetPalette.reset.pr1").use() },
                        font = editorPane.palette.musicDialogFont).apply {
                    this.bindHeightToParent(multiplier = 0.5f, adjust = -1f)
                    this.textLabel.textColor.set(Color.WHITE.cpy())
                    this.textLabel.markup.set(editorPane.palette.markup)
                    this.imageNode.tint.set(Color.WHITE.cpy())
                    this.imageNode.padding.set(Insets(1f))
                    toggleGroup.addToggle(this)
                    this.onSelected = {
                        resetDefault = availableResetDefaults[0]
                    }
                    this.selectedState.set(true)
                }
                this += RadioButton(binding = { Localization.getVar("editor.dialog.tilesetPalette.reset.pr2").use() },
                        font = editorPane.palette.musicDialogFont).apply {
                    this.bindHeightToParent(multiplier = 0.5f, adjust = -1f)
                    this.textLabel.textColor.set(Color.WHITE.cpy())
                    this.textLabel.markup.set(editorPane.palette.markup)
                    this.imageNode.tint.set(Color.WHITE.cpy())
                    this.imageNode.padding.set(Insets(1f))
                    toggleGroup.addToggle(this)
                    this.onSelected = {
                        resetDefault = availableResetDefaults[1]
                    }
                }
            }
            bottomHbox += VBox().apply {
                this.spacing.set(2f)
                this.bounds.width.set(215f)
                if (baseTileset != null) {
                    this += RadioButton(binding = { Localization.getVar("editor.dialog.tilesetPalette.reset.base").use() },
                            font = editorPane.palette.musicDialogFont).apply {
                        this.bindHeightToParent(multiplier = 0.5f, adjust = -1f)
                        this.textLabel.textColor.set(Color.WHITE.cpy())
                        this.imageNode.tint.set(Color.WHITE.cpy())
                        this.imageNode.padding.set(Insets(1f))
                        toggleGroup.addToggle(this)
                        this.onSelected = {
                            resetDefault = availableResetDefaults[2]
                        }
                    }
                }
            }
            bottomHbox += Button(binding = { Localization.getVar("editor.dialog.tilesetPalette.resetAll").use() },
                    font = editorPane.palette.musicDialogFont).apply {
                this.applyDialogStyleBottom()
                this.bounds.width.set(325f)
                this.setOnAction {
                    val baseConfig = resetDefault.baseConfig
                    baseConfig.allMappings.forEach { baseMapping ->
                        val m = tilesetPalette.allMappingsByID.getValue(baseMapping.id)
                        val baseColor = baseMapping.color.getOrCompute()
                        m.color.set(baseColor.cpy())
                    }
                    tilesetPalette.applyTo(objPreview.worldRenderer.tileset)
                    resetGroupMappingsToTileset()
                    updateColourPickerToMapping()
                }
            }
            bottomHbox += Button("").apply {
                this.applyDialogStyleBottom()
                this.bindWidthToSelfHeight()
                this.padding.set(Insets(8f))
                this += ImageNode(TextureRegion(AssetRegistry.get<Texture>("ui_colour_picker_copy")))
                this.tooltipElement.set(editorPane.createDefaultTooltip(Localization.getVar("editor.dialog.tilesetPalette.copyAll")))
                this.setOnAction { 
                    Gdx.app.clipboard.contents = tilesetPalette.toJson().toString(WriterConfig.MINIMAL)
                }
            }
            bottomHbox += Button("").apply {
                this.applyDialogStyleBottom()
                this.bindWidthToSelfHeight()
                this.padding.set(Insets(8f))
                this += ImageNode(TextureRegion(AssetRegistry.get<Texture>("ui_colour_picker_paste")))
                this.tooltipElement.set(editorPane.createDefaultTooltip(Localization.getVar("editor.dialog.tilesetPalette.pasteAll")))
                this.setOnAction { 
                    val clipboard = Gdx.app.clipboard
                    if (clipboard.hasContents()) {
                        try {
                            val jsonValue = Json.parse(clipboard.contents)
                            if (jsonValue.isObject) {
                                tilesetPalette.fromJson(jsonValue.asObject())
                                tilesetPalette.applyTo(objPreview.worldRenderer.tileset)
//                                applyCurrentMappingToPreview(currentMapping.getOrCompute().color.getOrCompute())
                                resetGroupMappingsToTileset()
                                shouldColorPickerUpdateUpdateTileset = false
                                updateColourPickerToMapping()
                                shouldColorPickerUpdateUpdateTileset = true
                            }
                        } catch (ignored: Exception) {}
                    }
                }
            }
        }
        bottomPane.addChild(bottomHbox)
    }
    
    init {
        colourPicker.currentColor.addListener { c ->
            if (shouldColorPickerUpdateUpdateTileset) {
                applyCurrentMappingToPreview(c.getOrCompute().cpy())
            }
        }
    }
    
    private fun resetGroupMappingsToTileset() {
        groupMappings.forEach { m ->
            m.color.set(m.tilesetGetter(objPreview.worldRenderer.tileset).getOrCompute().cpy())
        }
    }
    
    private fun applyCurrentMappingToPreview(newColor: Color) {
        val m = currentMapping.getOrCompute()
        m.color.set(newColor.cpy())
        m.applyTo(objPreview.worldRenderer.tileset)
    }
    
    private fun updateColourPickerToMapping(mapping: ColorMapping = currentMapping.getOrCompute()) {
        colourPicker.setColor(mapping.color.getOrCompute(), true)
    }
    
    fun prepareShow(): PaletteEditDialog {
        tilesetPalette.applyTo(objPreview.worldRenderer.tileset)
        resetGroupMappingsToTileset()
        updateColourPickerToMapping()
        return this
    }

    override fun canCloseDialog(): Boolean {
        return true
    }

    override fun onCloseDialog() {
        super.onCloseDialog()
        editor.updatePaletteChangesState()
    }
    
    inner class ObjectPreview : UIElement() {
        
        val world: World = World()
        // TODO the tileset (not the palette config) should be copied from the global settings for the level
        val worldRenderer: WorldRenderer = WorldRenderer(world, Tileset(editor.container.renderer.tileset.texturePack).apply { 
            tilesetPalette.applyTo(this)
        })
        
        val rodEntity: EntityRodDecor
        
        init {
            this += ImageNode(editor.previewTextureRegion)
            rodEntity = object : EntityRodDecor(world) {
                override fun getAnimationAlpha(): Float {
                    return (rodRotation.get() % 1f).coerceIn(0f, 1f)
                }
            }
        }
        
        init {
            world.clearEntities()
            for (x in 2..12) {
                for (z in -5..4) {
                    val ent = if (z == 0) EntityPlatform(world, withLine = x == 4) else EntityCube(world, withLine = x == 4, withBorder = z == 1)
                    world.addEntity(ent.apply { 
                        this.position.set(x.toFloat(), -1f, z.toFloat())
                    })
                    if (z == 0 && x <= 4) {
                        world.addEntity(EntityPlatform(world, withLine = x == 4).apply {
                            this.position.set(x.toFloat(), 0f, z.toFloat())
                        })
                    }
                }
            }
            
            world.addEntity(EntityPiston(world).apply { 
                this.position.set(6f, 0f, 0f)
                this.type = EntityPiston.Type.PISTON_A
                this.pistonState = EntityPiston.PistonState.FULLY_EXTENDED
            })
            world.addEntity(EntityPiston(world).apply { 
                this.position.set(9f, 0f, 0f)
                this.type = EntityPiston.Type.PISTON_DPAD
                this.pistonState = EntityPiston.PistonState.FULLY_EXTENDED
            })
            world.addEntity(EntityCube(world).apply { 
                this.position.set(7f, 0f, 2f)
            })
            world.addEntity(rodEntity.apply {
                this.position.set(4f, 1f, 0f)
            })

            // Button signs
            val signs = mutableListOf<EntitySign>()
            signs += EntitySign(world, EntitySign.Type.A).apply {
                this.position.set(5f, 2f, -3f)
            }
            signs += EntitySign(world, EntitySign.Type.DPAD).apply {
                this.position.set(6f, 2f, -3f)
            }
            signs += EntitySign(world, EntitySign.Type.BO).apply {
                this.position.set(4f, 2f, -2f)
            }
            signs += EntitySign(world, EntitySign.Type.TA).apply {
                this.position.set(5f, 2f, -2f)
            }
            signs += EntitySign(world, EntitySign.Type.N).apply {
                this.position.set(6f, 2f, -2f)
            }
            signs.forEach { sign ->
                sign.position.x += (12 / 32f)
                sign.position.z += (8 / 32f)
                world.addEntity(sign)
            }
        }

        override fun renderSelf(originX: Float, originY: Float, batch: SpriteBatch) {
            val renderBounds = this.paddingZone
            val x = renderBounds.x.get() + originX
            val y = originY - renderBounds.y.get()
            val w = renderBounds.width.get()
            val h = renderBounds.height.get()
            val lastPackedColor = batch.packedColor


            val cam = worldRenderer.camera
            cam.zoom = 1f / 2f
            cam.position.x = 3.5f
            cam.position.y = 1f
            cam.update()

            batch.end()
            val prevMatrix = Matrix4Stack.getAndPush().set(batch.projectionMatrix)
            batch.projectionMatrix = cam.combined
            val frameBuffer = editor.previewFrameBuffer
            frameBuffer.begin()
            Gdx.gl.glClearColor(0f, 0f, 0f, 0f)
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
            worldRenderer.render(batch, editor.engine)
            frameBuffer.end()
            batch.projectionMatrix = prevMatrix
            batch.begin()

            Matrix4Stack.pop()
            
            batch.packedColor = lastPackedColor
        }
    }

    inner class ColorMappingGroupedCubeFaceY(id: String)
        : ColorMapping(id, { it.cubeFaceY.color }) {
        
        private val hsv: FloatArray = FloatArray(3) { 0f }

        override fun applyTo(tileset: Tileset) {
            val varr = tilesetGetter(tileset)
            val thisColor = this.color.getOrCompute()
            varr.set(thisColor.cpy())
            allMappingsByID.getValue("cubeFaceY").color.set(thisColor.cpy())

            thisColor.toHsv(hsv)
            hsv[1] = (hsv[1] + 0.18f * hsv[1].sign)
            hsv[2] = (hsv[2] - 0.17f)
            val borderColor = Color(1f, 1f, 1f, thisColor.a).fromHsv(hsv)
            tileset.cubeBorder.color.set(borderColor.cpy())
            allMappingsByID.getValue("cubeBorder").color.set(borderColor.cpy())
            tileset.signShadowColor.set(borderColor.cpy())
            allMappingsByID.getValue("signShadow").color.set(borderColor.cpy())

            hsv[1] = (hsv[1] + 0.03f * hsv[1].sign)
            hsv[2] = (hsv[2] - 0.13f)
            val cubeBorderZColor = Color(1f, 1f, 1f, thisColor.a).fromHsv(hsv)
            tileset.cubeBorderZ.color.set(cubeBorderZColor)
            allMappingsByID.getValue("cubeBorderZ").color.set(cubeBorderZColor.cpy())
            
            // Face
            thisColor.toHsv(hsv)
            hsv[1] = (hsv[1] + 0.08f * hsv[1].sign)
            hsv[2] = (hsv[2] - 0.10f)
            val faceZColor = Color(1f, 1f, 1f, 1f).fromHsv(hsv)
            tileset.cubeFaceZ.color.set(faceZColor.cpy())
            allMappingsByID.getValue("cubeFaceZ").color.set(faceZColor.cpy())
            thisColor.toHsv(hsv)
            hsv[1] = (hsv[1] + 0.11f * hsv[1].sign)
            hsv[2] = (hsv[2] - 0.13f)
            val faceXColor = Color(1f, 1f, 1f, 1f).fromHsv(hsv)
            tileset.cubeFaceX.color.set(faceXColor.cpy())
            allMappingsByID.getValue("cubeFaceX").color.set(faceXColor.cpy())
        }
    }
    
    inner class ColorMappingGroupedPistonFaceZ(id: String)
        : ColorMapping(id, { it.pistonFaceZColor }) {

        private val hsv: FloatArray = FloatArray(3) { 0f }

        override fun applyTo(tileset: Tileset) {
            val varr = tilesetGetter(tileset)
            val thisColor = this.color.getOrCompute()
            varr.set(thisColor.cpy())
            allMappingsByID.getValue("pistonFaceZ").color.set(thisColor.cpy())

            thisColor.toHsv(hsv)
            hsv[2] = (hsv[2] - 0.20f)
            val pistonFaceX = Color(1f, 1f, 1f, thisColor.a).fromHsv(hsv)
            tileset.pistonFaceXColor.set(pistonFaceX.cpy())
            allMappingsByID.getValue("pistonFaceX").color.set(pistonFaceX.cpy())
        }
    }

}