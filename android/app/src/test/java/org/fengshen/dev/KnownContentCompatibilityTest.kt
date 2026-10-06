package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

/** Marker admission only; actual snapshot validation remains the loader gate. */
class KnownContentCompatibilityTest {
    @Test fun allAlreadyGeneratedSchemaOneIterationsRemainEligibleForFullSnapshotValidation(){
        val current="opening-segment-001-c60"
        for(version in 1..60)assertTrue("c$version",SaveSnapshot.compatibleContentVersion("opening-segment-001-c$version",current))
        assertTrue(SaveSnapshot.compatibleContentVersion("opening-segment-001-c51-r1",current))
        assertTrue(SaveSnapshot.compatibleContentVersion("opening-to-world-b1",current))
    }
    @Test fun futureUnknownOrMalformedMarkersCannotBecomeKnownLegacySaves(){
        val current="opening-segment-001-c60"
        for(marker in listOf("opening-segment-001-c61","opening-segment-001-c999","opening-segment-001-c0",
            "opening-segment-001-c58-random","opening-segment-001-c058","", "different-game"))
            assertFalse(marker,SaveSnapshot.compatibleContentVersion(marker,current))
        assertTrue(SaveSnapshot.compatibleContentVersion("fixture","fixture"))
    }
}
