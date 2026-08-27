package com.tanda.biometrics.ui.subject

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanda.core.ui.design.DesignText
import com.tanda.core.ui.theme.DesignTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun SubjectSection(
    selectedTab: Int = 0,
    onSelected: (Int) -> Unit ={},
    modifier: Modifier = Modifier,
    sections: List<String> = listOf("Capture", "Listing")
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(87.dp)
            .background(Color(0xFFEFF5F4))
            .padding(
                horizontal = 8.dp,
                vertical = 14.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        sections.forEachIndexed { index, title ->
            val selected = selectedTab == index
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.background
                        } else {
                            Color.Transparent
                        }
                    )
                    .clickable {
                        onSelected(index)
                    },
                contentAlignment = Alignment.Center
            ) {
                DesignText(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Black
                    }
                )
            }
        }
    }
}

@Preview
@Composable
private fun PreviewSubjectSection(){
    DesignTheme(darkTheme = false){
        SubjectSection()
    }
}