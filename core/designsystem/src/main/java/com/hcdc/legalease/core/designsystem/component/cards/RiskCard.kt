package com.hcdc.legalease.core.designsystem.component.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.hcdc.legalease.core.designsystem.component.spacers.VerticalSpacer

@Composable
fun RiskCard(
    riskLevel: String,
    riskColor: Color,
    points: List<String>,
    modifier: Modifier = Modifier,
    /** When set, points whose index it accepts are tappable (e.g. to open the cited page). */
    onPointClick: ((Int) -> Unit)? = null,
    isPointClickable: (Int) -> Boolean = { onPointClick != null },
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row with colored circle and label
            Row(verticalAlignment = Alignment.CenterVertically) {
                VerticalSpacer(8.dp)
                Text(
                    text = riskLevel,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = riskColor.copy(alpha = .7f)
                    )
                )
            }

            VerticalSpacer(16.dp)

            // Sub-cards for each point
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                points.forEachIndexed { index, point ->
                    val clickable = onPointClick != null && isPointClickable(index)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (clickable) {
                                    Modifier.clickable(role = Role.Button) { onPointClick(index) }
                                } else {
                                    Modifier
                                }
                            ),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.background
                        ),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Text(
                            text = point,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }
    }
}
