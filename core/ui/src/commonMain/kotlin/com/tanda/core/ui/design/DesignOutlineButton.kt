package com.tanda.core.ui.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanda.core.ui.theme.DesignTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun DesignOutlineButton(
    text: String = "Biometric Capture",
    leading: @Composable () -> Unit = {},
    trailing: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.primary,
    borderColor: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color = MaterialTheme.colorScheme.onPrimary,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp),
    onClick: () -> Unit
) {
    val updatedLeading by rememberUpdatedState(leading)
    val updatedTrailing by rememberUpdatedState(trailing)
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(86.dp)
            .background(MaterialTheme.colorScheme.background),
        shape = RoundedCornerShape(5.dp),
        border = BorderStroke(
            width = 1.dp,
            color = borderColor
        ),
        contentPadding = contentPadding,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = textColor,
            containerColor = containerColor
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            updatedLeading()
            Spacer(modifier = Modifier.width(12.dp))
            Box(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                DesignText(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
            updatedTrailing()
        }
    }
}

@Preview
@Composable
private fun PreviewOutlineButton() {
    DesignTheme(darkTheme = false) {
        Box(
            modifier = Modifier.background(color = MaterialTheme.colorScheme.background)
                .fillMaxSize()
        ) {
            DesignOutlineButton(onClick = {}, modifier = Modifier.height(60.dp).background(
                MaterialTheme.colorScheme.background))
        }

    }
}