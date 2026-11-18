package com.github.avbox.osc.ui.page

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.github.avbox.osc.R
import com.github.avbox.osc.haikuo.HaikuoJson

sealed class HaikuoDialogUi {
    data class Alert(val title: String, val content: String) : HaikuoDialogUi()
    data class Confirm(val title: String, val content: String) : HaikuoDialogUi()
    data class Input(val title: String, val value: String, val hint: String) : HaikuoDialogUi()
    data class Select(val title: String, val options: List<String>, val columns: Int) : HaikuoDialogUi()
}

fun haikuoDialogUiFrom(params: Map<String, Any?>): HaikuoDialogUi {
    val type = HaikuoJson.str(params["type"])
    val title = HaikuoJson.str(params["title"])
    val content = HaikuoJson.str(params["content"])
    return when (type) {
        "confirm" -> HaikuoDialogUi.Confirm(title, content)
        "input" -> HaikuoDialogUi.Input(
            title,
            HaikuoJson.str(params["value"]),
            HaikuoJson.str(params["hint"])
        )
        "select" -> {
            val options = ArrayList<String>()
            val raw = params["options"]
            if (raw is String && raw.isNotEmpty()) {
                val decoded = HaikuoJson.parse(raw)
                if (decoded is List<*>) {
                    for (item in decoded) {
                        if (item != null) options.add(item.toString())
                    }
                }
            } else if (raw is List<*>) {
                for (item in raw) {
                    if (item != null) options.add(item.toString())
                }
            }
            val columns = (HaikuoJson.num(params["col"])?.toInt() ?: 3).coerceIn(1, 6)
            HaikuoDialogUi.Select(title, options, columns)
        }
        else -> HaikuoDialogUi.Alert(title, content)
    }
}

@Composable
fun HaikuoRunnerDialog(
    state: HaikuoDialogUi,
    onResult: (Map<String, Any?>) -> Unit
) {
    when (state) {
        is HaikuoDialogUi.Alert -> {
            AlertDialog(
                onDismissRequest = { onResult(mapOf("result" to true)) },
                title = { Text(state.title) },
                text = { Text(state.content) },
                confirmButton = {
                    TextButton(onClick = { onResult(mapOf("result" to true)) }) {
                        Text(stringResource(R.string.common_confirm))
                    }
                }
            )
        }
        is HaikuoDialogUi.Confirm -> {
            AlertDialog(
                onDismissRequest = { onResult(mapOf("result" to false)) },
                title = { Text(state.title) },
                text = { Text(state.content) },
                confirmButton = {
                    TextButton(onClick = { onResult(mapOf("result" to true)) }) {
                        Text(stringResource(R.string.common_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { onResult(mapOf("result" to false)) }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            )
        }
        is HaikuoDialogUi.Input -> {
            var text by remember(state) { mutableStateOf(state.value) }
            AlertDialog(
                onDismissRequest = { onResult(mapOf("result" to null)) },
                title = { Text(state.title) },
                text = {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = { Text(state.hint) },
                        singleLine = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = { onResult(mapOf("result" to text)) }) {
                        Text(stringResource(R.string.common_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { onResult(mapOf("result" to null)) }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            )
        }
        is HaikuoDialogUi.Select -> {
            AlertDialog(
                onDismissRequest = { onResult(mapOf("result" to null)) },
                title = { Text(state.title) },
                text = {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(state.columns),
                        modifier = Modifier.heightIn(max = 320.dp)
                    ) {
                        itemsIndexed(state.options) { index, option ->
                            OutlinedButton(
                                onClick = { onResult(mapOf("result" to index)) }
                            ) {
                                Text(option)
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { onResult(mapOf("result" to null)) }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            )
        }
    }
}
