package app.smartlife.patches.smartlife.misc.telemetry

import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.resourcePatch
import app.smartlife.patches.smartlife.shared.Constants.COMPATIBILITY_SMARTLIFE
import app.smartlife.patches.smartlife.shared.removeAttributionPipeline
import app.smartlife.patches.smartlife.shared.removeModuleAppPipelines
import app.smartlife.patches.smartlife.shared.setBoolResources
import app.smartlife.patches.smartlife.shared.warnIfMissing

@Suppress("unused")
val disableTelemetryPatch = resourcePatch(
    name = "Disable telemetry",
    description = "Disables Firebase Analytics/Crashlytics and automatic page tracking. " +
        "Works by turning off the app's own analytics feature flags.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_SMARTLIFE)

    val disableFirebase = booleanOption(
        key = "disableFirebase",
        default = true,
        title = "Disable Firebase",
        description = "Turns off Firebase Analytics and Crashlytics.",
    )

    val disablePageTracking = booleanOption(
        key = "disablePageTracking",
        default = true,
        title = "Disable page tracking",
        description = "Turns off automatic page/event tracking.",
    )

    execute {
        val flags = linkedMapOf<String, Boolean>()

        if (disableFirebase.value ?: true) {
            flags["firebase_analytics_enable"] = false
            flags["firebase_crash_enable"] = false
        }
        if (disablePageTracking.value ?: true) {
            flags["is_open_page_auto_track"] = false
        }

        // Stop the analytics / log-upload subsystems from starting.
        val pipelines = listOf(
            "com.thingclips.smart.stat.StatPipeLine",
            "com.thingclips.smart.stat.StatUploadInitPipeline",
            "com.thingclips.smart.logupload.LogUploadPipeLine",
            "com.thingclips.smart.logupload.AppStartPipeLine",
            "com.thingclips.smart.apievent.ApiEventPipleLine",
            "com.thingclips.smart.auto.page.track.AutoPageTrackerPipeLine",
            "com.thing.trackcontrol.TrackFilterInitPipeline",
            "com.thingclips.smart.message.stat.NotifyPermissionStatPipeLine",
        )
        warnIfMissing(
            "Disable telemetry", "module_app.json pipelines",
            pipelines, removeModuleAppPipelines(pipelines),
        )
        // Attribution is tracking too, so it stays disabled here even when ads are kept.
        removeAttributionPipeline()

        warnIfMissing("Disable telemetry", "bools.xml flags", flags.keys, setBoolResources(flags))
    }
}
