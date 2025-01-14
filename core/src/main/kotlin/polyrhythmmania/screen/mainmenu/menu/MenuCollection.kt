package polyrhythmmania.screen.mainmenu.menu

import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.math.Vector2
import paintbox.binding.ReadOnlyVar
import paintbox.binding.Var
import paintbox.registry.AssetRegistry
import paintbox.ui.*
import paintbox.util.gdxutils.maxX
import paintbox.util.gdxutils.maxY
import polyrhythmmania.PRManiaGame
import polyrhythmmania.Settings
import polyrhythmmania.screen.mainmenu.MainMenuScreen
import java.lang.Float.max
import java.lang.Float.min
import java.util.*
import kotlin.math.ceil
import kotlin.math.floor


class MenuCollection(val mainMenu: MainMenuScreen, val sceneRoot: SceneRoot, val menuPane: Pane) {
    
    val main: PRManiaGame = mainMenu.main
    val settings: Settings = main.settings
    
    val menus: List<MMMenu> = mutableListOf()
    val activeMenu: ReadOnlyVar<MMMenu?> = Var(null)
    
    private val menuStack: Deque<MMMenu> = ArrayDeque()
    
    val uppermostMenu: UppermostMenu = UppermostMenu(this)
    val quitMenu: QuitMenu = QuitMenu(this)
    val creditsMenu: CreditsMenu = CreditsMenu(this)
    val playMenu: PlayMenu = PlayMenu(this)
    val settingsMenu: SettingsMenu = SettingsMenu(this)
    val audioSettingsMenu: AudioSettingsMenu = AudioSettingsMenu(this)
    
    init {
        addMenu(uppermostMenu)
        addMenu(quitMenu)
        addMenu(creditsMenu)
        addMenu(playMenu)
        addMenu(settingsMenu)
        addMenu(audioSettingsMenu)
        
        changeActiveMenu(uppermostMenu, false, instant = true)
        menuStack.push(uppermostMenu)
    }
    
    fun addMenu(menu: MMMenu) {
        menus as MutableList
        menus.add(menu)
        
        menu.visible.set(false)
        menuPane.addChild(menu)
        Anchor.BottomLeft.configure(menu)
    }
    
    fun removeMenu(menu: MMMenu) {
        menus as MutableList
        menus.remove(menu)
        menuPane.removeChild(menu)
    }
    
    fun resetMenuStack() {
        menuStack.clear()
        menuStack.push(uppermostMenu)
    }
    
    fun changeActiveMenu(menu: MMMenu, backOut: Boolean, instant: Boolean = false, playSound: Boolean = true) {
        if (!instant) {
            if (playSound) {
                AssetRegistry.get<Sound>("sfx_menu_${if (backOut) "deselect" else "select"}").play(settings.menuSfxVolume.getOrCompute() / 100f)
            }
            
            val changedBounds = RectangleStack.getAndPush().apply {
                val currentBounds = menu.bounds
                val relToRoot = menu.getPosRelativeToRoot(Vector2())
                this.set(relToRoot.x, relToRoot.y,
                        currentBounds.width.getOrCompute(), currentBounds.height.getOrCompute())
            }
            
            val currentActive = activeMenu.getOrCompute()
            if (currentActive != null) {
                val secondBounds = RectangleStack.getAndPush()
                val curActiveBounds = currentActive.bounds
                val relToRoot = currentActive.getPosRelativeToRoot(Vector2())
                secondBounds.set(relToRoot.x, relToRoot.y,
                        curActiveBounds.width.getOrCompute(), curActiveBounds.height.getOrCompute())
                
                // Merge the two rectangles to be maximal.
                changedBounds.x = min(changedBounds.x, secondBounds.x)
                changedBounds.y = min(changedBounds.y, secondBounds.y)
                changedBounds.width = max(changedBounds.maxX, secondBounds.maxX) - changedBounds.x
                changedBounds.height = max(changedBounds.maxY, secondBounds.maxY) - changedBounds.y
                
                RectangleStack.pop()
            }
            
            val rootWidth = 1280f
            val rootHeight = 720f
            val tileX = floor(changedBounds.x / rootWidth * mainMenu.tilesWidth).toInt()
            val tileY = floor(changedBounds.y / rootHeight * mainMenu.tilesHeight).toInt()
            val tileW = (ceil(changedBounds.width / rootWidth * mainMenu.tilesWidth).toInt()).coerceAtLeast(1)
            val tileH = (ceil(changedBounds.height / rootHeight * mainMenu.tilesHeight).toInt() + 1).coerceAtLeast(1)
            mainMenu.flipAnimation = MainMenuScreen.TileFlip(tileX, tileY, tileW, tileH,
                    if (backOut) Corner.TOP_RIGHT else Corner.TOP_LEFT)
            
            RectangleStack.pop()
        }
        menus.forEach { it.visible.set(false) }
        menu.visible.set(true)
        (activeMenu as Var).set(menu)
    }
    
    fun pushNextMenu(menu: MMMenu, instant: Boolean = false, playSound: Boolean = true) {
        changeActiveMenu(menu, false, instant, playSound)
        menuStack.push(menu)
    }

    fun popLastMenu(instant: Boolean = false, playSound: Boolean = true): MMMenu {
        if (menuStack.size <= 1) return menuStack.peek()
        val popped = menuStack.pop()
        val menu = menuStack.peek()
        changeActiveMenu(menu, true, instant, playSound)
        return popped
    }

    fun playBlipSound() {
        val sound = AssetRegistry.get<Sound>("sfx_menu_blip")
        sound.stop()
        sound.play(this.settings.menuSfxVolume.getOrCompute() / 100f)
    }
}