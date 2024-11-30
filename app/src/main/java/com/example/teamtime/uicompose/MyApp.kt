package com.example.teamtime.uicompose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun MyApp(
    showDialog: Boolean,
    dialogTitle: String,
    dialogMessage: String,
    onDismissDialog: () -> Unit,
    onRequestLocation: () -> Unit
) {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            LocationButton(onRequestLocation)
            if (showDialog) {
                AlertDialog(
                    onDismissRequest = { onDismissDialog() },
                    title = { Text(text = dialogTitle) },
                    text = { Text(text = dialogMessage) },
                    confirmButton = {
                        Button(onClick = { onDismissDialog() }) {
                            Text("OK")
                        }
                    }
                )
            }
            if (showDialog)
            {
                ShowAlertDialog(
                    title = dialogTitle,
                    message = dialogMessage,
                    onDismiss = onDismissDialog
                )
            }
        }
    }
}
