package com.hcdc.legalease.core.ml.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceCapabilityTest {

    @Test
    fun `the model tier follows total RAM, and low-memory devices get none`() {
        val gib = 1L shl 30
        assertEquals(ModelTier.PRIMARY, DeviceCapability.tierFor(12 * gib))
        assertNull(DeviceCapability.tierFor(2 * gib))
        assertEquals(ModelTier.LITE, DeviceCapability.tierFor(4 * gib))
        assertEquals(ModelTier.PRIMARY, DeviceCapability.tierFor(6 * gib))
    }
}
