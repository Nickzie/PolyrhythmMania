package polyrhythmmania.editor.block

import paintbox.Paintbox
import paintbox.binding.ReadOnlyVar
import paintbox.binding.Var
import polyrhythmmania.Localization
import polyrhythmmania.editor.Editor
import polyrhythmmania.engine.Engine


/**
 * An [Instantiator] is effectively a [Block] factory with extra metadata for the UI.
 */
data class Instantiator<B : Block>(val id: String,
                                   val blockClass: Class<B>,
                                   val name: ReadOnlyVar<String>,
                                   val summary: ReadOnlyVar<String>,
                                   val desc: ReadOnlyVar<String>,
                                   val deprecatedIDs: Set<String> = emptySet(),
                                   val factory: Instantiator<B>.(Engine) -> B)

object Instantiators {

    val map: Map<String, Instantiator<*>>
    val list: List<Instantiator<*>>
    
    val endStateInstantiator: Instantiator<BlockEndState>

    init {
        val tempMap = mutableMapOf<String, Instantiator<*>>()
        val tempList = mutableListOf<Instantiator<*>>()

        fun add(instantiator: Instantiator<*>) {
            tempMap[instantiator.id] = instantiator
            instantiator.deprecatedIDs.forEach { tempMap[it] = instantiator }
            tempList += instantiator
        }

        endStateInstantiator = Instantiator("endState", BlockEndState::class.java, Localization.getVar("instantiator.endState.name"),
                Localization.getVar("instantiator.endState.summary"),
                Localization.getVar("instantiator.endState.desc")) { engine ->
            BlockEndState(engine)
        }
        add(endStateInstantiator)
        add(Instantiator("spawnPattern", BlockSpawnPattern::class.java, Localization.getVar("instantiator.spawnPattern.name"),
                Localization.getVar("instantiator.spawnPattern.summary"),
                Localization.getVar("instantiator.spawnPattern.desc")) { engine ->
            BlockSpawnPattern(engine)
        })
        add(Instantiator("deployRod", BlockDeployRod::class.java, Localization.getVar("instantiator.deployRod.name"),
                Localization.getVar("instantiator.deployRod.summary"),
                Localization.getVar("instantiator.deployRod.desc")) { engine ->
            BlockDeployRod(engine)
        })
        add(Instantiator("retractPistons", BlockRetractPistons::class.java, Localization.getVar("instantiator.retractPistons.name"),
                Localization.getVar("instantiator.retractPistons.summary"),
                Localization.getVar("instantiator.retractPistons.desc")) { engine ->
            BlockRetractPistons(engine)
        })
        add(Instantiator("despawnPattern", BlockDespawnPattern::class.java, Localization.getVar("instantiator.despawnPattern.name"),
                Localization.getVar("instantiator.despawnPattern.summary"),
                Localization.getVar("instantiator.despawnPattern.desc")) { engine ->
            BlockDespawnPattern(engine)
        })

//        add(Instantiator("baselineTest", Var("Baseline Test"), Var("Description baseline test."), Var("[font=prmania_icons]RspladAD[]")) { engine ->
//            BlockTest(engine)
//        })
//        (1..19).forEach { i ->
//            add(Instantiator("test$i", Var("Test$i"), Var(""), Var("[font=prmania_icons]RspladAD[]")) { engine ->
//                BlockTest(engine)
//            })
//        }

        map = tempMap
        list = tempList
    }
}