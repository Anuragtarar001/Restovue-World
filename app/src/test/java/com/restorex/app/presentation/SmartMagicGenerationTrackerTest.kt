package com.restorex.app.presentation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartMagicGenerationTrackerTest {
    @Test fun `latest render wins rapid mode switching`() {
        val tracker = SmartMagicGenerationTracker()
        val source = tracker.newSource()
        val natural = tracker.newRender()
        val extreme = tracker.newRender()
        val enhanced = tracker.newRender()
        assertFalse(tracker.isCurrent(source, natural))
        assertFalse(tracker.isCurrent(source, extreme))
        assertTrue(tracker.isCurrent(source, enhanced))
    }

    @Test fun `new source invalidates every older render`() {
        val tracker = SmartMagicGenerationTracker()
        val oldSource = tracker.newSource()
        val oldRender = tracker.newRender()
        tracker.newSource()
        assertFalse(tracker.isCurrent(oldSource, oldRender))
    }
}
