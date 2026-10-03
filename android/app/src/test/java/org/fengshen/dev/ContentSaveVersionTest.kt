package org.fengshen.dev
import org.junit.Assert.*
import org.junit.Test

class ContentSaveVersionTest {
    @Test fun alreadyReviewedIterationsAreCompatibleWithoutAdmittingUnknownVersions(){
        for(i in 1..28)assertTrue(SaveSnapshot.compatibleContentVersion("opening-segment-001-c$i","opening-segment-001-c29"))
        assertTrue(SaveSnapshot.compatibleContentVersion("opening-to-world-b1","opening-segment-001-c29"))
        assertTrue(SaveSnapshot.compatibleContentVersion("opening-segment-001-c29","opening-segment-001-c29"))
        for(id in listOf("opening-segment-001-c0","opening-segment-001-c99","opening-segment-001-c025","unknown"))
            assertFalse(SaveSnapshot.compatibleContentVersion(id,"opening-segment-001-c29"))
    }
}
