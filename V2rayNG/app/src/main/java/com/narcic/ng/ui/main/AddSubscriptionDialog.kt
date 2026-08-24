package com.narcic.ng.ui.main

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.narcic.ng.AppConfig
import com.narcic.ng.R
import com.narcic.ng.extension.toast
import com.narcic.ng.ui.ScannerActivity
import com.narcic.ng.util.Utils

/**
 * «افزودن سابسکریپشن»
 *
 * مرحله ۱ – کاربر یکی از دو گزینه را انتخاب می‌کند:
 *   • «دستی» → فرم ورود نام + محتوا (رفتار قبلی)
 *   • «مخزن موجود» → لیست DEFAULT_SUBSCRIPTIONS از AppConfig
 *
 * مخازن موجود داخل یک بلوک جمع‌وجور نشان داده می‌شوند؛
 * کاربر روی هر کدام کلیک می‌کند و بلافاصله اضافه می‌شود.
 */
@Composable
fun AddSubscriptionDialog(
    onAdd: (name: String, content: String) -> Unit,
    onDismiss: () -> Unit,
) {
    // null = step 1 (choose mode), "manual" or "preset" = step 2
    var mode by remember { mutableStateOf<String?>(null) }

    when (mode) {
        null -> ModePickerDialog(
            onManual = { mode = "manual" },
            onPreset = { mode = "preset" },
            onDismiss = onDismiss,
        )
        "manual" -> ManualInputDialog(
            onAdd = onAdd,
            onDismiss = onDismiss,
        )
        "preset" -> PresetDialog(
            onAdd = onAdd,
            onDismiss = onDismiss,
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Step 1 – انتخاب روش افزودن
// ─────────────────────────────────────────────────────────────
@Composable
private fun ModePickerDialog(
    onManual: () -> Unit,
    onPreset: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("افزودن سابسکریپشن") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "چگونه می‌خواهید سابسکریپشن اضافه کنید؟",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onPreset,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("مخزن موجود")
                }
                OutlinedButton(
                    onClick = onManual,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("وارد کردن دستی")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("لغو") }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

// ─────────────────────────────────────────────────────────────
// Step 2a – مخزن موجود: لیست DEFAULT_SUBSCRIPTIONS
// هر آیتم در یک ردیف جداگانه؛ با کلیک فوری اضافه می‌شود
// ─────────────────────────────────────────────────────────────
@Composable
private fun PresetDialog(
    onAdd: (name: String, content: String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مخزن موجود") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    "یک مخزن را برای افزودن انتخاب کنید:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                AppConfig.DEFAULT_SUBSCRIPTIONS.forEachIndexed { index, (remarks, url) ->
                    if (index > 0) Divider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAdd(remarks, url) }
                            .padding(vertical = 14.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = remarks,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = url.take(48) + if (url.length > 48) "…" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = "+ افزودن",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("لغو") }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

// ─────────────────────────────────────────────────────────────
// Step 2b – وارد کردن دستی (همان رفتار قبلی)
// ─────────────────────────────────────────────────────────────
@Composable
private fun ManualInputDialog(
    onAdd: (name: String, content: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    val context = LocalContext.current

    val qrScanLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = result.data?.getStringExtra("SCAN_RESULT")
            if (!scanResult.isNullOrBlank()) {
                content = scanResult
            } else {
                context.toast(R.string.toast_decoding_failed)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("افزودن دستی") },
        text = {
            Column {
                Text(
                    text = "URL سابسکریپشن یا لینک‌های پروکسی را وارد کنید.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("نام سابسکریپشن") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipboardText = Utils.getClipboard(context)
                            if (clipboardText.isBlank()) {
                                context.toast(R.string.toast_none_data_clipboard)
                            } else {
                                content = clipboardText
                            }
                        },
                    ) {
                        Text("چسباندن از کلیپ‌بورد")
                    }
                    OutlinedButton(
                        onClick = {
                            qrScanLauncher.launch(Intent(context, ScannerActivity::class.java))
                        },
                    ) {
                        Text("اسکن QR")
                    }
                }
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    placeholder = { Text("URL، YAML، JSON یا لینک‌های پروکسی…") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .padding(top = 8.dp),
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
            TextButton(onClick = onDismiss) { Text("لغو") }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}
