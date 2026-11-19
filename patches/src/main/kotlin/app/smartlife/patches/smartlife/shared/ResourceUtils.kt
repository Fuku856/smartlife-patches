package app.smartlife.patches.smartlife.shared

import app.morphe.patcher.patch.ResourcePatchContext
import org.w3c.dom.Element
import java.util.logging.Logger

/**
 * SmartLife gates most optional features (AI, ads, analytics) behind
 * <bool name="..."> entries in res/values/bools.xml. At runtime the app reads
 * the local bool first (obfuscated L.getBoolean via hhl/ihl) before any remote
 * override, so flipping these is the most robust, lowest-risk way to disable a
 * feature. Only the boolean names present in [values] are changed; unknown names
 * on a different app version are ignored.
 *
 * @return the set of boolean names that were actually found and changed.
 */
internal fun ResourcePatchContext.setBoolResources(values: Map<String, Boolean>): Set<String> {
    val changed = mutableSetOf<String>()
    val boolsFile = get("res/values/bools.xml")
    if (!boolsFile.exists()) return changed

    document("res/values/bools.xml").use { document ->
        val nodes = document.getElementsByTagName("bool")
        for (index in 0 until nodes.length) {
            val element = nodes.item(index) as? Element ?: continue
            val name = element.getAttribute("name")
            val newValue = values[name] ?: continue
            element.textContent = newValue.toString()
            changed += name
        }
    }
    return changed
}

/**
 * Parse an asset JSON file, let [edit] change the tree, and write it back only if
 * [edit] reported a change. Returns whatever [edit] returns (empty if the file is
 * missing, so an app version without the file is simply skipped).
 */
private fun ResourcePatchContext.editAssetJson(
    path: String,
    edit: (JsonNode) -> Set<String>,
): Set<String> {
    val file = get(path)
    if (!file.exists()) return emptySet()

    val root = JsonNode.parse(file.readText())
    val changed = edit(root)
    if (changed.isNotEmpty()) file.writeText(root.write())
    return changed
}

/**
 * Remove string elements equal to one of [values] from every JSON array in the asset
 * file at [path]. Only array elements are touched: object keys and property values
 * that happen to contain the same text are left alone. Unknown values (different app
 * version) are skipped.
 *
 * @return the set of values that were found and removed.
 */
internal fun ResourcePatchContext.removeJsonStringEntries(
    path: String,
    values: Collection<String>,
): Set<String> {
    val targets = values.toSet()
    return editAssetJson(path) { it.removeArrayStrings(targets) }
}

/**
 * Remove class-name entries from the startup pipeline arrays in assets/module_app.json,
 * which stops that subsystem from starting. The service map is not touched.
 *
 * @return the set of class names that were found and removed.
 */
internal fun ResourcePatchContext.removeModuleAppPipelines(classNames: Collection<String>): Set<String> =
    removeJsonStringEntries("assets/module_app.json", classNames)

/**
 * Remove service-map entries from assets/module_app.json. The service map is
 * `"interface":"impl"`, so an entry is removed when either its key (the service
 * interface) or its string value (the implementation) is one of [classNames];
 * the whole key/value pair goes, so no interface is left pointing at nothing.
 * Only string-valued object members are considered.
 *
 * @return the set of class names that matched at least one entry.
 */
internal fun ResourcePatchContext.removeModuleAppServices(
    classNames: Collection<String>,
): Set<String> {
    val targets = classNames.toSet()
    return editAssetJson("assets/module_app.json") { it.removeStringMembers(targets) }
}

/**
 * Log a warning for entries a patch expected but did not find, which usually means
 * the app version differs and the patch had no effect for them.
 */
internal fun warnIfMissing(patchName: String, what: String, expected: Collection<String>, found: Set<String>) {
    val missing = expected.toSet() - found
    if (missing.isEmpty()) return
    Logger.getLogger(patchName).warning(
        "$patchName: these $what were not found (app version may differ): $missing"
    )
}

/**
 * AppsFlyer attribution (install/campaign tracking). It is both ad-related and
 * telemetry, so both "Hide ads" and "Disable telemetry" remove it; whichever runs
 * second finds it already gone, which is why this is not reported as missing.
 */
internal fun ResourcePatchContext.removeAttributionPipeline() {
    removeModuleAppPipelines(listOf("com.thingclips.smart.attribution.common.AttributionConfigPipeline"))
}
