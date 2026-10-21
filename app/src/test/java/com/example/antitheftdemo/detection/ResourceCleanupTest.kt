package com.example.antitheftdemo.detection

import com.example.antitheftdemo.util.releaseResources
import org.junit.Assert.*
import org.junit.Test

class ResourceCleanupTest {
    @Test fun failingResourceCannotSkipOtherOwnersOrSessionReset() {
        val controller = DetectionController()
        val session = controller.request(com.example.antitheftdemo.home.HomeItemType.MOTION_DETECTION)!!
        controller.started(session)
        val released = mutableListOf<String>()
        val failures = mutableListOf<String>()
        releaseResources({ name, _ -> failures += name },
            "sensor" to { error("Unregister failed") },
            "audio" to { released += "audio" },
            "torch" to { released += "torch" },
            "wake lock" to { released += "wake lock" },
            "notification" to { released += "notification" },
            "session" to { controller.ended(session) })
        assertEquals(listOf("sensor"), failures)
        assertEquals(listOf("audio", "torch", "wake lock", "notification"), released)
        assertNull(controller.state.value.session)
    }
}
