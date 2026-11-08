package com.bolkhata.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * The app's own dialog, used everywhere instead of the system one: the theme's page colour, rounded corners, a bold
 * title, the text, then the buttons on the right in the app's orange (a red Delete keeps its own colour).
 */
@Composable
fun AppAlert(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(20.dp)
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Column(modifier.fillMaxWidth().shadow(12.dp, shape).clip(shape).background(LocalPage.current).padding(16.dp)) {
            val ink = MaterialTheme.colorScheme.onSurface
            title?.let { ProvideTextStyle(TextStyle(fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = ink)) { it() } }
            text?.let {
                Box(Modifier.padding(top = 10.dp)) { ProvideTextStyle(TextStyle(fontSize = 13.sp, lineHeight = 18.sp, color = ink)) { it() } }
            }
            MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primary = EmberTop)) {
                ProvideTextStyle(TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold)) {
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically) {
                        dismissButton?.invoke()
                        confirmButton()
                    }
                }
            }
        }
    }
}
