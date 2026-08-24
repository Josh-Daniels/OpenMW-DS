package org.openmw.ui.page.mod

import java.io.File

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

/**
 * Bethesda's own official plugins, which every GOTY copy ships (GOG and Steam alike) and which
 * VANILLA LEAVES SWITCHED OFF.
 *
 * They are not mods and not a sign of a tampered install, but they are also not part of the base
 * game: the original launcher lists them on its Data Files tab unticked, and a stock `Morrowind.ini`
 * names only the three masters under `[Game Files]`. Registering the folder used to switch all eight
 * ON, because auto-registration marks everything it discovers as enabled, so a first-time setup
 * silently played a slightly different game from vanilla: extra items, an extra quest, the Bitter
 * Coast ambience, and Master Index's map markers. A couple of them (Siege at Firemoth, Master Index)
 * are also the ones players most often leave off.
 *
 * So they are still REGISTERED, keeping them one tick away in the load-order panel, but registered
 * DISABLED (`;content=`) — which is what the vanilla launcher shows on a fresh install.
 *
 * Lowercase, and compared lowercase: the shipped casing is inconsistent (`adamantiumarmor.esp` vs
 * `AreaEffectArrows.esp` vs `Siege at Firemoth.esp`) and differs again between releases.
 */
val BETHESDA_OFFICIAL_PLUGINS: Set<String> = setOf(
    "adamantiumarmor.esp",      // Adamantium Armor
    "areaeffectarrows.esp",     // Area Effect Arrows
    "bcsounds.esp",             // Bitter Coast Sounds
    "ebq_artifact.esp",         // Helm of Tohan
    "entertainers.esp",         // Entertainers
    "lefemmarmor.esp",          // LeFemm Armor
    "master_index.esp",         // Master Index
    "siege at firemoth.esp",    // Siege at Firemoth
)

/**
 * Should this plugin be ENABLED when it is first registered?
 *
 * True for everything except [BETHESDA_OFFICIAL_PLUGINS]. Deliberately a question about the plugin
 * and not about the folder: the same eight names ship in every copy of the game, so this holds for a
 * mod folder that happens to contain them as much as for the base Data Files.
 */
fun defaultEnabledFor(name: String): Boolean =
    name.trim().lowercase() !in BETHESDA_OFFICIAL_PLUGINS

/**
 * `Tamriel_Data.esm`, the shared asset master every Tamriel Rebuilt install loads.
 *
 * Used as the "is Tamriel Rebuilt on?" test rather than `TR_Mainland.esm`, because this one is also
 * the master for the other Project Tamriel landmasses, so it catches a setup running those without
 * TR itself. Both take the same toll on launch time, which is what the notice is about.
 */
private const val TAMRIEL_DATA = "tamriel_data.esm"

/** Is this plugin the Tamriel Rebuilt asset master? Case-insensitive, as everything here is. */
fun isTamrielData(name: String): Boolean = name.trim().equals(TAMRIEL_DATA, ignoreCase = true)

/**
 * Does `openmw.cfg` name an ENABLED [TAMRIEL_DATA]?
 *
 * For callers with no parsed load order to hand — the companion splash runs in the game process and
 * has never read that file. Callers that already hold `ModValue`s should test those instead of
 * re-reading the file.
 *
 * Reads the file directly, so call it off the main thread. A missing or unreadable file answers
 * false, which is the right way to fail: no notice rather than a wrong one.
 */
fun tamrielDataEnabledInConfig(cfg: File): Boolean = runCatching {
    cfg.takeIf { it.isFile }?.readLines().orEmpty().any { line ->
        val trimmed = line.trim()
        // A DISABLED entry is `;content=…`, so the `;` must be rejected rather than trimmed off —
        // this asks whether TR is switched ON, not whether it is registered.
        trimmed.startsWith("content=") && isTamrielData(trimmed.removePrefix("content="))
    }
}.getOrDefault(false)

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
