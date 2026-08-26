package com.example.floating.caloriecounter

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
    var pendingBackup by remember { mutableStateOf<BackupArchive?>(null) }
    var restoreInProgress by remember { mutableStateOf(false) }

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
