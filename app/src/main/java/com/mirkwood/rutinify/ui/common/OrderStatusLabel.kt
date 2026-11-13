package com.mirkwood.rutinify.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mirkwood.rutinify.R
import com.mirkwood.rutinify.domain.model.OrderDisplayStatus
import com.mirkwood.rutinify.ui.preview.ThemePreviews
import com.mirkwood.rutinify.ui.theme.RutinifyTheme

/** Order status chip: "Activa", "Pausada", "Cancelada" or "Terminada" (R-8.2, D-45). */
@Composable
fun OrderStatusLabel(status: OrderDisplayStatus, modifier: Modifier = Modifier) {
    val (textRes, container) = statusStyle(status)
    Surface(color = container, shape = MaterialTheme.shapes.small, modifier = modifier) {
        Text(
            text = stringResource(textRes),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun statusStyle(status: OrderDisplayStatus): Pair<Int, Color> {
    val colors = MaterialTheme.colorScheme
    return when (status) {
        OrderDisplayStatus.ACTIVE -> R.string.order_status_active to colors.primaryContainer
        OrderDisplayStatus.PAUSED -> R.string.order_status_paused to colors.tertiaryContainer
        OrderDisplayStatus.CANCELLED -> R.string.order_status_cancelled to colors.errorContainer
        OrderDisplayStatus.FINISHED -> R.string.order_status_finished to colors.surfaceVariant
    }
}

@ThemePreviews
@Composable
private fun OrderStatusLabelPreview() {
    RutinifyTheme { OrderStatusLabel(OrderDisplayStatus.PAUSED) }
}
