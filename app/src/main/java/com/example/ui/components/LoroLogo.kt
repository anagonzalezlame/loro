package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ParrotDarkBlue
import com.example.ui.theme.ParrotOrange
import com.example.ui.theme.ParrotPrimaryBlue
import com.example.ui.theme.ParrotRed
import com.example.ui.theme.ParrotYellow
import com.example.ui.theme.RoundedFontFamily

@Composable
fun LoroLogo(modifier: Modifier = Modifier, sizePoints: Dp = 48.dp) {
    val textSize = sizePoints.value.sp
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // L
        Text(
            text = "L",
            color = ParrotPrimaryBlue,
            fontSize = textSize,
            fontWeight = FontWeight.Black,
            fontFamily = RoundedFontFamily
        )
        
        Spacer(modifier = Modifier.width(4.dp))
        
        // Custom O with wifi and smile
        val oSize = sizePoints * 0.7f
        Canvas(modifier = Modifier.size(oSize).padding(top = sizePoints * 0.1f)) {
            val strokeW = size.width * 0.25f
            val rad = size.width / 2 - strokeW / 2
            // Draw O
            drawCircle(
                color = ParrotPrimaryBlue,
                radius = rad,
                center = center,
                style = Stroke(width = strokeW)
            )
            // Wifi waves above
            drawArc(
                color = ParrotPrimaryBlue,
                startAngle = 210f,
                sweepAngle = 120f,
                useCenter = false,
                topLeft = Offset(-size.width * 0.2f, -size.height * 0.6f),
                size = Size(size.width * 1.4f, size.height * 1.4f),
                style = Stroke(width = strokeW * 0.7f, cap = StrokeCap.Round)
            )
            drawArc(
                color = ParrotPrimaryBlue,
                startAngle = 225f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(size.width * 0.1f, -size.height * 0.3f),
                size = Size(size.width * 0.8f, size.height * 0.8f),
                style = Stroke(width = strokeW * 0.6f, cap = StrokeCap.Round)
            )
            // Smile below
            drawArc(
                color = ParrotOrange,
                startAngle = 30f,
                sweepAngle = 120f,
                useCenter = false,
                topLeft = Offset(size.width * 0.15f, size.height * 0.3f),
                size = Size(size.width * 0.7f, size.height * 0.9f),
                style = Stroke(width = strokeW * 0.6f, cap = StrokeCap.Round)
            )
        }
        
        Spacer(modifier = Modifier.width(4.dp))
        
        // R
        Text(
            text = "R",
            color = ParrotPrimaryBlue,
            fontSize = textSize,
            fontWeight = FontWeight.Black,
            fontFamily = RoundedFontFamily
        )
        
        Spacer(modifier = Modifier.width(4.dp))
        
        // Custom O with 4 colors
        Canvas(modifier = Modifier.size(oSize)) {
            val strokeW = size.width * 0.3f
            val rectSize = Size(size.width - strokeW, size.height - strokeW)
            val topLeft = Offset(strokeW / 2, strokeW / 2)
            
            // Top Right: Yellow
            drawArc(
                color = ParrotYellow,
                startAngle = 270f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = topLeft,
                size = rectSize,
                style = Stroke(width = strokeW)
            )
            // Bottom Right: Dark Blue
            drawArc(
                color = ParrotDarkBlue,
                startAngle = 0f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = topLeft,
                size = rectSize,
                style = Stroke(width = strokeW)
            )
            // Bottom Left: Primary Blue
            drawArc(
                color = ParrotPrimaryBlue,
                startAngle = 90f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = topLeft,
                size = rectSize,
                style = Stroke(width = strokeW)
            )
            // Top Left: Red
            drawArc(
                color = ParrotRed,
                startAngle = 180f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = topLeft,
                size = rectSize,
                style = Stroke(width = strokeW)
            )
        }
    }
}
