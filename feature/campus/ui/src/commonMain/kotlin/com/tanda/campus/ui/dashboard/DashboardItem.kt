package com.tanda.campus.ui.dashboard

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanda.core.ui.design.DesignText
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import tanda.feature.campus.ui.generated.resources.Res
import tanda.feature.campus.ui.generated.resources.biometric_capture
import tanda.feature.campus.ui.generated.resources.ic_fingerprint
import tanda.feature.campus.ui.generated.resources.ic_right_arrow

@Composable
fun DashboardCard(
    onItemClick: () -> Unit = {},
    title: StringResource,
    icon: DrawableResource,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFEFF5F4)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        ),
        onClick = onItemClick
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 20.dp,
                    vertical = 22.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = Color(0xFF19BA93)
            )

            Spacer(Modifier.width(16.dp))

            DesignText(
                text = stringResource(title),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.weight(1f),
            )

            Icon(
                painterResource(Res.drawable.ic_right_arrow),
                contentDescription = null,
            )
        }
    }
}

@Composable
@Preview
fun DashboardCardPreview() {
    DashboardCard(
            title = Res.string.biometric_capture,
            icon = Res.drawable.ic_fingerprint,
            onItemClick = {}
    )
}
