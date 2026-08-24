package org.openmw.ui.page.mod

/**
 * The default plugin load order applied when a load order is first SEEDED — never to one the player
 * has arranged.
 *
 * Both seeding paths discover plugins by listing a folder, and neither order is meaningful:
 * `ModAssistantViewModel.modPathSelection` (Add Mods) takes raw `listFiles()` order, and the
 * simplified launcher's auto-registration takes plain alphabetical. For most mods that genuinely
 * does not matter — OpenMW loads content in the order given, and an unrelated pair can go either
 * way round. For a handful it matters a great deal, because a plugin that depends on another must
 * come after it, so the arbitrary default could put a large mod in an order that does not work.
 *
 * This fixes the entries where the correct order is known, and leaves everything else deterministic
 * but unopinionated. It is a DEFAULT in the full sense: the load-order panel's drag handle still
 * rewrites the file however the player likes, and [DEFAULT_LOAD_ORDER_HEAD] carries no special
 * status once they have.
 */

/**
 * Pinned to the FRONT, in exactly this order, whenever present.
 *
 * The base game's three masters first, as they already were. Then Tamriel Rebuilt, whose five files
 * have a required internal order: `Tamriel_Data.esm` is a master of `TR_Mainland.esm`, `TR_Factions
 * .esp` builds on that in turn, and the two `.omwscripts` files act on the plugins already loaded so
 * they close the block. Absent entries are simply skipped, so a plain Morrowind install is
 * unaffected and a Tribunal-only install still gets 1-2.
 *
 * Note `TR_Factions.esp` is pinned even though it is an ESP: this list is about known dependency
 * order, not file type, and the type-based grouping in [middleRank] only decides between plugins
 * NOT named here. Left unpinned it sorted alphabetically into the middle, which happened to land it
 * after the TR masters by luck rather than by rule.
 */
val DEFAULT_LOAD_ORDER_HEAD: List<String> = listOf(
    "Morrowind.esm",
    "Tribunal.esm",
    "Bloodmoon.esm",
    "Tamriel_Data.esm",
    "TR_Mainland.esm",
    "TR_Factions.esp",
    "Tamriel_Data.omwscripts",
    "TamrielRebuilt.omwscripts",
)

/**
 * Pinned to the BACK, in exactly this order, whenever present.
 *
 * ReAnimation patches animation behaviour across whatever else is loaded, so it wants the last word;
 * the API half must precede the module that consumes it.
 */
val DEFAULT_LOAD_ORDER_TAIL: List<String> = listOf(
    "ReAnimation_API.omwscripts",
    "ReAnimation_v2_Rogue.omwscripts",
)

/** Everything not named above sorts between the two, by [middleRank] then case-insensitive name. */
private const val MIDDLE_RANK = 1_000
private const val TAIL_BASE = 1_000_000

/**
 * Where a name sits in the overall order. Matching is case-insensitive because these names are
 * typed by hand in this file and read from a case-preserving filesystem; `tamrielrebuilt.omwscripts`
 * and `TamrielRebuilt.omwscripts` are the same plugin to the engine and must be to us.
 */
private fun primaryRank(name: String): Int {
    val key = name.trim().lowercase()
    DEFAULT_LOAD_ORDER_HEAD.forEachIndexed { i, head ->
        if (key == head.lowercase()) return i
    }
    DEFAULT_LOAD_ORDER_TAIL.forEachIndexed { i, tail ->
        if (key == tail.lowercase()) return TAIL_BASE + i
    }
    return MIDDLE_RANK
}

/**
 * Order WITHIN the unpinned middle: masters, then addons, then scripts.
 *
 * Not arbitrary — it is the ordinary Morrowind convention, and it is the safe direction: a master
 * can be depended on by an addon but never the reverse, so masters-first cannot break a pair that
 * would otherwise have worked, while the alphabetical default could. Scripts last for the same
 * reason ReAnimation is pinned last: they act on what is already loaded.
 *
 * This only ever decides between plugins whose relative order was arbitrary anyway.
 */
private fun middleRank(name: String): Int = when (name.substringAfterLast('.', "").lowercase()) {
    "esm", "omwgame" -> 0
    "esp", "esl", "omwaddon" -> 1
    "omwscripts" -> 2
    else -> 3
}

/**
 * Sort [items] into the default load order.
 *
 * Stable, so entries that tie keep their incoming relative order, and total, so the same folder
 * always produces the same order — which the previous `listFiles()` default did not guarantee.
 */
fun <T> List<T>.sortedByDefaultLoadOrder(nameOf: (T) -> String): List<T> =
    sortedWith(
        compareBy<T> { primaryRank(nameOf(it)) }
            .thenBy { middleRank(nameOf(it)) }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { nameOf(it) }
    )

/** [sortedByDefaultLoadOrder] for a plain list of file names. */
fun List<String>.sortedByDefaultLoadOrder(): List<String> = sortedByDefaultLoadOrder { it }
