// Play Asset Delivery pack for the on-device language model (fast-follow: downloaded right after
// install, outside the base APK). Model files are not in git; see SETUP.md.
plugins {
    id("com.android.asset-pack")
}

assetPack {
    packName.set("llm_pack")
    dynamicDelivery {
        deliveryType.set("fast-follow")
    }
}
