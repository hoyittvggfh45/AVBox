package com.github.avbox.osc.ui.page

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.github.avbox.osc.R
import com.github.avbox.osc.miniapp.MiniAppSettings
import com.github.avbox.osc.ui.components.AppTopBarScaffold

@Composable
fun MiniAppSettingsScreen(
    initial: MiniAppSettings,
    onNavigateBack: () -> Unit,
    onSave: (Int?, Int, Boolean, Boolean, Boolean, String) -> Unit
) {
    var portText by remember(initial) { mutableStateOf(initial.basePort.toString()) }
    var marketUrlText by remember(initial) { mutableStateOf(initial.marketUrl) }
    var maxBackground by remember(initial) { mutableIntStateOf(initial.maxBackground) }
    var adFilter by remember(initial) { mutableStateOf(initial.adFilter) }
    var hybridComposition by remember(initial) { mutableStateOf(initial.hybridComposition) }
    var lanShare by remember(initial) { mutableStateOf(initial.lanShare) }

    AppTopBarScaffold(
        titleContent = {
            Text(
                text = stringResource(R.string.miniapp_settings),
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
        actions = {}
    ) { topPadding, bottomPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topPadding, bottom = bottomPadding)
                .padding(16.dp)
        ) {
            item {
                OutlinedTextField(
                    value = portText,
                    onValueChange = { value -> portText = value.filter { it.isDigit() } },
                    label = { Text(stringResource(R.string.miniapp_settings_port)) },
                    supportingText = { Text(stringResource(R.string.miniapp_settings_port_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.miniapp_settings_maxbg)) },
                    supportingContent = { Text(stringResource(R.string.miniapp_settings_maxbg_sub)) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { if (maxBackground > 1) maxBackground-- },
                                enabled = maxBackground > 1
                            ) {
                                Icon(imageVector = Icons.Filled.Remove, contentDescription = null)
                            }
                            Text(
                                text = maxBackground.toString(),
                                style = MaterialTheme.typography.titleMedium
                            )
                            IconButton(
                                onClick = { if (maxBackground < 10) maxBackground++ },
                                enabled = maxBackground < 10
                            ) {
                                Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                            }
                        }
                    }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.miniapp_settings_adfilter)) },
                    supportingContent = { Text(stringResource(R.string.miniapp_settings_adfilter_sub)) },
                    trailingContent = {
                        Switch(checked = adFilter, onCheckedChange = { adFilter = it })
                    }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.miniapp_settings_hybrid)) },
                    supportingContent = { Text(stringResource(R.string.miniapp_settings_hybrid_sub)) },
                    trailingContent = {
                        Switch(checked = hybridComposition, onCheckedChange = { hybridComposition = it })
                    }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.miniapp_settings_lan)) },
                    supportingContent = { Text(stringResource(R.string.miniapp_settings_lan_sub)) },
                    trailingContent = {
                        Switch(checked = lanShare, onCheckedChange = { lanShare = it })
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            item {
                OutlinedTextField(
                    value = marketUrlText,
                    onValueChange = { marketUrlText = it },
                    label = { Text(stringResource(R.string.miniapp_settings_market)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
            item {
                FilledButton(
                    onClick = {
                        onSave(
                            portText.trim().toIntOrNull(),
                            maxBackground,
                            adFilter,
                            hybridComposition,
                            lanShare,
                            marketUrlText.trim()
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.miniapp_settings_save))
                }
            }
            item {
                Column(modifier = Modifier.height(24.dp)) {}
            }
        }
    }
}
