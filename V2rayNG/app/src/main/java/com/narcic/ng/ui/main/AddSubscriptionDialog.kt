package com.narcic.ng.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * "افزودن سابسکریپشن" (image 9). [content] is handed to
 * MainAction.AddSubscriptionFromText as-is — the ViewModel/repository layer
 * decides whether it's one/more subscription links or raw share links.
 */
@Composable
fun AddSubscriptionDialog(
    onAdd: (name: String, content: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("افزودن سابسکریپشن") },
        text = {
            Column {
                Text(
                    text = "منبع را انتخاب کنید، اتصال‌هایش را تست کنید یا گزینه‌ها را باز کنید.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("نام سابسکریپشن") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    placeholder = { Text("یا لینک‌های پرو... URL، YAML، JSON") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .padding(top = 12.dp),
                )
                Text(
                    text = "پشتیبانی از Clash/Xray JSON و vless، vmess، trojan، ss",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(name.trim(), content.trim()) },
                enabled = content.isNotBlank(),
            ) {
                Text("+ افزودن")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("لغو")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}
