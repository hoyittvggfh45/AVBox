package com.github.avbox.osc.ui.page

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.github.avbox.osc.R
import com.github.avbox.osc.haikuo.HaikuoDuplicateDecision
import com.github.avbox.osc.haikuo.HaikuoProgram
import com.github.avbox.osc.haikuo.haikuoDataIconBytes
import com.github.avbox.osc.haikuo.haikuoFaviconUrl
import com.github.avbox.osc.ui.HaikuoRunnerActivity
import com.github.avbox.osc.ui.components.AppTopBarScaffold
import com.github.avbox.osc.ui.components.SearchField

@Composable
fun HaikuoHomeScreen(
    onNavigateBack: () -> Unit,
    vm: HaikuoHomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val programs by vm.programs.collectAsState()
    val loading by vm.loading.collectAsState()
    val query by vm.query.collectAsState()
    val duplicatePrompt by vm.duplicatePrompt.collectAsState()
    var menuExpanded by remember { mutableStateOf(false) }
    var textImportOpen by remember { mutableStateOf(false) }
    var urlImportOpen by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<HaikuoProgram?>(null) }

    LaunchedEffect(Unit) {
        vm.events.collect { message ->
            if (message.isNotEmpty()) {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) {
                vm.importBytes(bytes, uri.lastPathSegment ?: "")
            }
        } catch (e: Exception) {
            Toast.makeText(context, e.message ?: e.toString(), Toast.LENGTH_SHORT).show()
        }
    }

    val filtered = remember(programs, query) {
        if (query.isBlank()) {
            programs
        } else {
            programs.filter { it.name.contains(query, ignoreCase = true) }
        }
    }

    AppTopBarScaffold(
        titleContent = {
            Text(
                text = stringResource(R.string.haikuo_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_left),
                    contentDescription = null
                )
            }
        },
        actions = {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_file_choose),
                        contentDescription = stringResource(R.string.haikuo_import)
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.haikuo_import_text)) },
                        onClick = {
                            menuExpanded = false
                            textImportOpen = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.haikuo_import_url)) },
                        onClick = {
                            menuExpanded = false
                            urlImportOpen = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.haikuo_import_file)) },
                        onClick = {
                            menuExpanded = false
                            filePicker.launch("*/*")
                        }
                    )
                }
            }
        }
    ) { topPad, _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topPad)
        ) {
            SearchField(
                query = query,
                onQueryChange = vm::setQuery,
                onSearch = {},
                hint = stringResource(R.string.haikuo_search_hint),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(8.dp))
            if (loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.haikuo_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    items(filtered, key = { it.id }) { program ->
                        HaikuoProgramRow(
                            program = program,
                            onClick = {
                                context.startActivity(
                                    Intent(context, HaikuoRunnerActivity::class.java).apply {
                                        putExtra(
                                            HaikuoRunnerActivity.EXTRA_PROGRAM_ID,
                                            program.id
                                        )
                                    }
                                )
                            },
                            onLongClick = { deleteTarget = program }
                        )
                    }
                }
            }
        }
    }

    if (textImportOpen) {
        HaikuoTextImportDialog(
            onDismiss = { textImportOpen = false },
            onConfirm = { text ->
                textImportOpen = false
                vm.importText(text)
            }
        )
    }

    if (urlImportOpen) {
        HaikuoUrlImportDialog(
            onDismiss = { urlImportOpen = false },
            onConfirm = { url ->
                urlImportOpen = false
                vm.importUrl(url)
            }
        )
    }

    val prompt = duplicatePrompt
    if (prompt != null) {
        AlertDialog(
            onDismissRequest = { vm.resolveDuplicates(HaikuoDuplicateDecision.cancel) },
            title = { Text(stringResource(R.string.haikuo_duplicate_title)) },
            text = {
                Text(
                    prompt.duplicates.joinToString("、") { it.incoming.name }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.resolveDuplicates(HaikuoDuplicateDecision.replace) }
                ) {
                    Text(stringResource(R.string.haikuo_duplicate_replace))
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = { vm.resolveDuplicates(HaikuoDuplicateDecision.cancel) }
                    ) {
                        Text(stringResource(R.string.common_cancel))
                    }
                    TextButton(
                        onClick = { vm.resolveDuplicates(HaikuoDuplicateDecision.keepBoth) }
                    ) {
                        Text(stringResource(R.string.haikuo_duplicate_keep_both))
                    }
                }
            }
        )
    }

    val target = deleteTarget
    if (target != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.haikuo_delete_title)) },
            text = {
                Text(
                    stringResource(R.string.haikuo_delete_message, target.name)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.remove(target.id)
                        deleteTarget = null
                    }
                ) {
                    Text(stringResource(R.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HaikuoProgramRow(
    program: HaikuoProgram,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val iconBytes = remember(program.icon) { haikuoDataIconBytes(program.icon) }
        val favicon = remember(program) { haikuoFaviconUrl(program) }
        val model: Any? = iconBytes ?: favicon
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = program.name.firstOrNull()?.toString() ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = program.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = program.subtitle
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun HaikuoTextImportDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.haikuo_import_text)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.haikuo_import_text_hint)) },
                minLines = 5,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                onClick = { onConfirm(text) }
            ) {
                Text(stringResource(R.string.common_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
private fun HaikuoUrlImportDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.haikuo_import_url)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.haikuo_import_url_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                onClick = { onConfirm(text.trim()) }
            ) {
                Text(stringResource(R.string.common_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}
