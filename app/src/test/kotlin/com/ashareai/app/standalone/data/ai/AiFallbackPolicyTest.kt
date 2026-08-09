package com.ashareai.app.standalone.data.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiFallbackPolicyTest {
    @Test
    fun onlyExplicitUnsupportedEndpointStatusesMayFallback() {
        assertTrue(AiFallbackPolicy.mayFallbackFromResponses(404))
        assertTrue(AiFallbackPolicy.mayFallbackFromResponses(405))
        assertTrue(AiFallbackPolicy.mayFallbackFromResponses(501))
        assertFalse(AiFallbackPolicy.mayFallbackFromResponses(400))
        assertFalse(AiFallbackPolicy.mayFallbackFromResponses(401))
        assertFalse(AiFallbackPolicy.mayFallbackFromResponses(429))
        assertFalse(AiFallbackPolicy.mayFallbackFromResponses(500))
    }
}
