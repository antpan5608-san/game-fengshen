package org.fengshen.dev
import org.junit.Assert.*
import org.junit.Test

class ContentSaveVersionTest {
    @Test fun alreadyReviewedIterationsAreCompatibleWithoutAdmittingUnknownVersions(){
        for(i in 1..33)assertTrue(SaveSnapshot.compatibleContentVersion("opening-segment-001-c$i","opening-segment-001-c34"))
        assertTrue(SaveSnapshot.compatibleContentVersion("opening-to-world-b1","opening-segment-001-c34"))
        assertTrue(SaveSnapshot.compatibleContentVersion("opening-segment-001-c34","opening-segment-001-c34"))
        for(id in listOf("opening-segment-001-c0","opening-segment-001-c99","opening-segment-001-c025","unknown"))
            assertFalse(SaveSnapshot.compatibleContentVersion(id,"opening-segment-001-c34"))
    }
}
