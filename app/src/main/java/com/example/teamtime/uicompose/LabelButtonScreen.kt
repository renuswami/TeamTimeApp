package com.example.teamtime.uicompose

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun LocationButton(onClick: () -> Unit) {
    var isClicked by remember { mutableStateOf(false) } // Track the click state

    val buttonSize by animateDpAsState(
        targetValue = if (isClicked) 150.dp else 200.dp, // Shrink when clicked, expand back afterward
        animationSpec = tween(durationMillis = 300) // Smooth animation duration
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = {
                isClicked = true // Set the button to "clicked" state
                onClick() // Perform the original onClick action
            },
            modifier = Modifier
                .padding(24.dp)
                .width(buttonSize) // Dynamically change width
                .height(60.dp) // Keep height constant
                .shadow(8.dp, shape = RoundedCornerShape(12.dp)),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = "Submit",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(8.dp)
            )
        }
    }

    // Reset the button size back to normal after a short delay
    LaunchedEffect(isClicked) {
        if (isClicked) {
            delay(300) // Wait for animation to complete
            isClicked = false // Reset to normal size
        }
    }


}


