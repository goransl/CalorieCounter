package com.example.floating.caloriecounter

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.floating.caloriecounter.Model.FoodRepository
import com.example.floating.caloriecounter.backup.BackupArchive
import com.example.floating.caloriecounter.utils.exportBackupZip
import com.example.floating.caloriecounter.utils.importBackupZip
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

private const val BACKUP_REMINDER_PREFERENCES = "backup_reminder"
private const val BACKUP_REMINDER_STARTED_AT = "started_at"
private const val LAST_SUCCESSFUL_BACKUP_AT = "last_successful_backup_at"
private const val BACKUP_REMINDER_SNOOZED_UNTIL = "snoozed_until"
private const val ONE_DAY_MILLIS = 24L * 60L * 60L * 1_000L
private const val BACKUP_REMINDER_INTERVAL_MILLIS = 7L * ONE_DAY_MILLIS

data class BackupActions(
    val export: (suggestedFileName: String) -> Unit,
    val restore: () -> Unit
)

/** Owns document launchers, validation, confirmation, and restore progress UI. */
@Composable
fun rememberBackupActions(
    repository: FoodRepository,
    onRestored: () -> Unit
): BackupActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reminderPreferences = remember(context) {
        context.getSharedPreferences(BACKUP_REMINDER_PREFERENCES, Context.MODE_PRIVATE)
    }
    var pendingBackup by remember { mutableStateOf<BackupArchive?>(null) }
    var restoreInProgress by remember { mutableStateOf(false) }
    var showBackupReminder by remember { mutableStateOf(false) }

    LaunchedEffect(reminderPreferences) {
        val now = System.currentTimeMillis()
        var reminderStartedAt = reminderPreferences.getLong(BACKUP_REMINDER_STARTED_AT, 0L)
        if (reminderStartedAt == 0L) {
            reminderStartedAt = now
            reminderPreferences.edit()
                .putLong(BACKUP_REMINDER_STARTED_AT, reminderStartedAt)
                .apply()
        }

        val lastSuccessfulBackupAt = reminderPreferences.getLong(
            LAST_SUCCESSFUL_BACKUP_AT,
            0L
        )
        val lastBackupOrStart = lastSuccessfulBackupAt.takeIf { it > 0L }
            ?: reminderStartedAt
        val snoozedUntil = reminderPreferences.getLong(BACKUP_REMINDER_SNOOZED_UNTIL, 0L)
        showBackupReminder = now - lastBackupOrStart >= BACKUP_REMINDER_INTERVAL_MILLIS &&
            now >= snoozedUntil
    }

    val saveZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val output = context.contentResolver.openOutputStream(uri, "w")
                        ?: throw IOException("The selected document could not be opened for writing.")
                    exportBackupZip(output, repository, BuildConfig.VERSION_NAME)
                }
                reminderPreferences.edit()
                    .putLong(LAST_SUCCESSFUL_BACKUP_AT, System.currentTimeMillis())
                    .remove(BACKUP_REMINDER_SNOOZED_UNTIL)
                    .apply()
                showBackupReminder = false
                Toast.makeText(context, "Backup saved.", Toast.LENGTH_SHORT).show()
            } catch (error: CancellationException) {
                withContext(NonCancellable + Dispatchers.IO) {
                    runCatching { context.contentResolver.delete(uri, null, null) }
                }
                throw error
            } catch (error: Exception) {
                withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.delete(uri, null, null) }
                }
                Toast.makeText(
                    context,
                    "Backup failed: ${error.message ?: "unknown error"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val openZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                pendingBackup = withContext(Dispatchers.IO) {
                    val input = context.contentResolver.openInputStream(uri)
                        ?: throw IOException("The selected document could not be opened for reading.")
                    importBackupZip(input)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Toast.makeText(
                    context,
                    "Backup could not be imported: ${error.message ?: "unknown error"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    pendingBackup?.let { archive ->
        AlertDialog(
            onDismissRequest = {
                if (!restoreInProgress) pendingBackup = null
            },
            title = { Text("Restore backup?") },
            text = {
                val source = archive.manifest?.let {
                    "format ${it.formatVersion}, app ${it.appVersion}"
                } ?: "legacy format"
                Text(
                    "${archive.data.recordCount} records were validated ($source). " +
                        "Restoring replaces all current foods, totals, weights, plans, and workouts."
                )
            },
            confirmButton = {
                Button(
                    enabled = !restoreInProgress,
                    onClick = {
                        restoreInProgress = true
                        scope.launch {
                            try {
                                repository.restoreBackup(archive.data)
                                pendingBackup = null
                                onRestored()
                                Toast.makeText(context, "Backup restored.", Toast.LENGTH_SHORT).show()
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                Toast.makeText(
                                    context,
                                    "Restore failed: ${error.message ?: "unknown error"}",
                                    Toast.LENGTH_LONG
                                ).show()
                            } finally {
                                restoreInProgress = false
                            }
                        }
                    }
                ) {
                    Text(if (restoreInProgress) "Restoring…" else "Replace and restore")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !restoreInProgress,
                    onClick = { pendingBackup = null }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showBackupReminder && pendingBackup == null) {
        val snoozeReminder = {
            reminderPreferences.edit()
                .putLong(
                    BACKUP_REMINDER_SNOOZED_UNTIL,
                    System.currentTimeMillis() + ONE_DAY_MILLIS
                )
                .apply()
            showBackupReminder = false
        }

        AlertDialog(
            onDismissRequest = snoozeReminder,
            title = { Text("Back up your data?") },
            text = {
                Text(
                    "No successful backup has been saved in the last 7 days. " +
                        "Create one now to protect your foods, calorie history, weight and workouts."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        snoozeReminder()
                        val stamp = java.time.LocalDateTime.now().format(
                            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm")
                        )
                        saveZipLauncher.launch("CalorieCounter_Backup_$stamp.zip")
                    }
                ) {
                    Text("Back up now")
                }
            },
            dismissButton = {
                TextButton(onClick = snoozeReminder) {
                    Text("Later")
                }
            }
        )
    }

    return BackupActions(
        export = { fileName -> saveZipLauncher.launch(fileName) },
        restore = {
            openZipLauncher.launch(
                arrayOf(
                    "application/zip",
                    "application/octet-stream",
                    "application/x-zip-compressed"
                )
            )
        }
    )
}
