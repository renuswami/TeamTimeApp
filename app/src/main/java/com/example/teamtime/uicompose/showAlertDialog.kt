package com.example.teamtime.uicompose

import androidx.compose.runtime.Composable

@Composable
fun ShowAlertDialog(title: String, message: String, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text(text = title) },
        text = { androidx.compose.material3.Text(text = message) },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                androidx.compose.material3.Text("OK")
            }
        }
    )
}

