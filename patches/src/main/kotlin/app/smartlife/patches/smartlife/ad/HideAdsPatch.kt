package app.smartlife.patches.smartlife.ad

import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.resourcePatch
import app.smartlife.patches.smartlife.shared.Constants.COMPATIBILITY_SMARTLIFE
import app.smartlife.patches.smartlife.shared.removeAttributionPipeline
import app.smartlife.patches.smartlife.shared.removeJsonStringEntries
import app.smartlife.patches.smartlife.shared.removeModuleAppPipelines
import app.smartlife.patches.smartlife.shared.removeModuleAppServices
import app.smartlife.patches.smartlife.shared.setBoolResources
import app.smartlife.patches.smartlife.shared.warnIfMissing

private const val PATCH_NAME = "Hide ads"

private val AD_PIPELINES = listOf(
    "com.thingclips.smart.advertisement.AdvertisementManagerPipeline",
    "com.thingclips.smart.advertisement.AdvertisementManagerLoginPipeline",
    "com.thingclips.smart.advertisement.AdvertisementManagerLogoutPipeline",
    "com.thingclips.smart.advertisement.pid.pipeline.PidAdvertisePipeline",
    "com.thingclips.smart.advertisement.pid.pipeline.PidAdvertiseLoginPipeline",
    "com.thingclips.smart.advertisement.pid.pipeline.PidAdvertiseLogoutPipeline",
    "com.thingclips.smart.advertisement.admob.lifecycle.AdMobManagerLogoutPipeline",
    "com.thingclips.smart.activitypush.api.ActivityAdPushManagerPipeline",
)

private val AD_SERVICES = listOf(
    "com.thingclips.smart.advertisement.service.AbsAdvertisementService",
    "com.thingclips.smart.advertisement.pid.service.AbsMarketingBoothService",
    "com.thingclips.smart.activitypush.api.AbsActivityAdPushService",
    "com.thingclips.smart.attribution.common.AppsFlyerService",
)

private val HOME_AD_ENTRIES = listOf(
    "ThingMainPageMarketing",
    "ThingMainPagePidBanner",
    "ThingShoppingMall",
)

private val PROFILE_AD_ENTRIES = listOf(
    "personal_center_advertising",
    "personal_center_welfare_banner",
    "personal_center_mall",
)

@Suppress("unused")
val hideAdsPatch = resourcePatch(
    name = PATCH_NAME,
    description = "Disables in-app ads, promotional banners, ad push, and ad-related attribution. " +
        "Stops the corresponding advertising subsystems and removes Home/Profile ad entries.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_SMARTLIFE)

    val removePipelines = booleanOption(
        key = "removeAdPipelines",
        default = true,
        title = "Stop ad subsystems",
        description = "Removes the ad, ad-push and attribution startup pipelines so they never start.",
    )

    val removeEntries = booleanOption(
        key = "removeAdEntries",
        default = true,
        title = "Remove Home/Profile ad entries",
        description = "Removes the marketing, PID banner and shopping mall cards from Home " +
            "and the advertising, welfare banner and mall rows from Profile.",
    )

    val removeServices = booleanOption(
        key = "removeAdServices",
        default = false,
        title = "Remove ad services (experimental)",
        description = "Also unregisters the ad, marketing booth, ad push and AppsFlyer services. " +
            "A screen that still looks one of them up may crash, so this is off by default.",
    )

    execute {
        val flags = linkedMapOf(
            "is_admob_support" to false,
            "is_admob_banner_badge_enabled" to false,
            "is_marketing_advertising_support" to false,
        )
        warnIfMissing(PATCH_NAME, "bools.xml flags", flags.keys, setBoolResources(flags))

        if (removePipelines.value ?: true) {
            warnIfMissing(
                PATCH_NAME, "module_app.json pipelines",
                AD_PIPELINES, removeModuleAppPipelines(AD_PIPELINES),
            )
            removeAttributionPipeline()
        }

        if (removeServices.value ?: false) {
            warnIfMissing(
                PATCH_NAME, "module_app.json services",
                AD_SERVICES, removeModuleAppServices(AD_SERVICES),
            )
        }

        if (removeEntries.value ?: true) {
            warnIfMissing(
                PATCH_NAME, "home_fragment_new.json entries",
                HOME_AD_ENTRIES, removeJsonStringEntries("assets/home_fragment_new.json", HOME_AD_ENTRIES),
            )
            warnIfMissing(
                PATCH_NAME, "personal_center_config_list.json entries",
                PROFILE_AD_ENTRIES,
                removeJsonStringEntries("assets/personal_center_config_list.json", PROFILE_AD_ENTRIES),
            )
        }
    }
}
