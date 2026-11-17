package com.podgir.app

import org.junit.Assert.*
import org.junit.Test

class UpdateLogicTest {
    @Test fun comparesNumericVersionsAndRejectsPrereleases() {
        assertTrue(isNewerVersion("v1.10.0", "1.9.9")); assertTrue(isNewerVersion("2.0.0", "1.99.99"))
        assertFalse(isNewerVersion("v1.0.0", "1.0.0")); assertFalse(isNewerVersion("v1.2.0-beta", "1.0.0")); assertFalse(isNewerVersion("broken", "1.0.0"))
    }
    @Test fun acceptsOnlyExactReleaseAssetHostAndPath() {
        val good = "https://github.com/Banafshex/Podgir/releases/download/v1.2.0/Podgir-1.2.0.apk"
        assertTrue(isTrustedUpdateUrl(good, "v1.2.0", "Podgir-1.2.0.apk"))
        assertFalse(isTrustedUpdateUrl(good.replace("github.com", "evil.example"), "v1.2.0", "Podgir-1.2.0.apk"))
        assertFalse(isTrustedUpdateUrl("https://github.com.evil.example/Banafshex/Podgir/releases/download/v1.2.0/Podgir-1.2.0.apk", "v1.2.0", "Podgir-1.2.0.apk"))
        assertFalse(isTrustedUpdateUrl(good + "?redirect=evil", "v1.2.0", "Podgir-1.2.0.apk"))
    }
}
