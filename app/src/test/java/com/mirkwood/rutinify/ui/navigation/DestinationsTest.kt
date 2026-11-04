package com.mirkwood.rutinify.ui.navigation

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class DestinationsTest {

    @Test
    fun `OrderDetailDestination sin alarma resaltada usa null por defecto`() {
        val decoded = Json.decodeFromString(OrderDetailDestination.serializer(), """{"orderId":7}""")
        assertThat(decoded).isEqualTo(OrderDetailDestination(orderId = 7, highlightAlarmId = null))
    }

    @Test
    fun `ReviewDestination conserva el draftId al serializar`() {
        val original = ReviewDestination(draftId = "abc-123")
        val json = Json.encodeToString(ReviewDestination.serializer(), original)
        assertThat(Json.decodeFromString(ReviewDestination.serializer(), json)).isEqualTo(original)
    }
}
