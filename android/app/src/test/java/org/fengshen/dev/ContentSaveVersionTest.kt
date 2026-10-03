package org.fengshen.dev
import org.junit.Assert.*
import org.junit.Test

class ContentSaveVersionTest {
    @Test fun alreadyReviewedIterationsAreCompatibleWithoutAdmittingUnknownVersions(){
        for(i in 1..25)assertTrue(SaveSnapshot.compatibleContentVersion("opening-segment-001-c$i","opening-segment-001-c26"))
        assertTrue(SaveSnapshot.compatibleContentVersion("opening-to-world-b1","opening-segment-001-c26"))
        assertTrue(SaveSnapshot.compatibleContentVersion("opening-segment-001-c26","opening-segment-001-c26"))
        for(id in listOf("opening-segment-001-c0","opening-segment-001-c99","opening-segment-001-c025","unknown"))
            assertFalse(SaveSnapshot.compatibleContentVersion(id,"opening-segment-001-c26"))
    }
}
