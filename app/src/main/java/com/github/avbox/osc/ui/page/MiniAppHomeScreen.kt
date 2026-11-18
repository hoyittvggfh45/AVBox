package com.github.avbox.osc.ui.page

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.avbox.osc.R
import com.github.avbox.osc.miniapp.MiniAppManifest
import com.github.avbox.osc.miniapp.MiniAppMarketItem
import com.github.avbox.osc.ui.components.AppTopBarScaffold

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MiniAppHomeScreen(
    onNavigateBack: () -> Unit,
    onOpenApp: (MiniAppManifest) -> Unit,
    onOpenHaikuo: () -> Unit,
    onOpenSettings: () -> Unit,
    onFolderPicked: (Uri) -> Unit,
    vm: MiniAppHomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val installed by vm.installed.collectAsState()
    val market by vm.market.collectAsState()
    val marketState by vm.marketState.collectAsState()
    val marketError by vm.marketError.collectAsState()
    val installing by vm.installing.collectAsState()
    val busy by vm.busy.collectAsState()
    val loading by vm.loading.collectAsState()
    val gridMode by vm.gridMode.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var menuExpanded by remember { mutableStateOf(false) }
    var linkDialogOpen by remember { mutableStateOf(false) }
    var actionsTarget by remember { mutableStateOf<MiniAppManifest?>(null) }
    var infoTarget by remember { mutableStateOf<MiniAppManifest?>(null) }
    var marketInfoTarget by remember { mutableStateOf<MiniAppMarketItem?>(null) }
    var deleteTarget by remember { mutableStateOf<MiniAppManifest?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    LaunchedEffect(Unit) {
        vm.events.collect { message ->
            if (message.isNotEmpty()) {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(marketState) {
        if (marketState == "ready" || marketState == "empty" || marketState == "error") {
            refreshing = false
        }
    }

    val zipPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) vm.installFromZipBytes(bytes)
        } catch (e: Exception) {
            Toast.makeText(context, e.message ?: e.toString(), Toast.LENGTH_SHORT).show()
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) onFolderPicked(uri)
    }

    AppTopBarScaffold(
        titleContent = {
            Text(
                text = stringResource(R.string.miniapp_title),
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
                IconButton(onClick = { menuExpanded = true }, enabled = !busy) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.miniapp_install)
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.miniapp_install_zip)) },
                        onClick = {
                            menuExpanded = false
                            zipPicker.launch("application/zip")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.miniapp_install_folder)) },
                        onClick = {
                            menuExpanded = false
                            folderPicker.launch(null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.miniapp_install_link)) },
                        onClick = {
                            menuExpanded = false
                            linkDialogOpen = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.miniapp_haikuo)) },
                        onClick = {
                            menuExpanded = false
                            onOpenHaikuo()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.miniapp_refresh_market)) },
                        onClick = {
                            menuExpanded = false
                            vm.loadMarket()
                        }
                    )
                }
            }
            IconButton(onClick = { vm.setGridMode(!gridMode) }) {
                Icon(
                    imageVector = if (gridMode) Icons.Filled.ViewList else Icons.Filled.GridView,
                    contentDescription = stringResource(
                        if (gridMode) R.string.miniapp_view_list else R.string.miniapp_view_grid
                    )
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.miniapp_settings)
                )
            }
        }
    ) { topPadding, bottomPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topPadding, bottom = bottomPadding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.miniapp_tab_installed)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.miniapp_tab_market)) }
                )
            }
            if (loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (selectedTab == 0) {
                if (installed.isEmpty()) {
                    MiniAppEmptyState(
                        title = stringResource(R.string.miniapp_empty_title),
                        description = stringResource(R.string.miniapp_empty_desc),
                        actionLabel = stringResource(R.string.miniapp_empty_action),
                        onAction = { selectedTab = 1 }
                    )
                } else if (gridMode) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 28.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(installed, key = { it.appId }) { manifest ->
                            MiniAppTile(
                                name = manifest.name,
                                seed = manifest.appId,
                                badge = null,
                                onTap = { onOpenApp(manifest) },
                                onLongPress = { actionsTarget = manifest }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(installed, key = { it.appId }) { manifest ->
                            val subtitle = listOf(
                                manifest.appId,
                                manifest.version
                            ).filter { it.isNotEmpty() }.joinToString(" · ")
                            MiniAppListRow(
                                name = manifest.name,
                                subtitle = subtitle,
                                seed = manifest.appId,
                                trailing = null,
                                onTap = { onOpenApp(manifest) },
                                onLongPress = { actionsTarget = manifest }
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }
            } else {
                MiniAppMarketTab(
                    marketState = marketState,
                    marketError = marketError,
                    market = market,
                    installed = installed,
                    installing = installing,
                    gridMode = gridMode,
                    refreshing = refreshing,
                    onRefresh = {
                        refreshing = true
                        vm.loadMarket()
                    },
                    onRetry = { vm.loadMarket() },
                    onMarketTap = { item, isInstalled, isInstalling ->
                        if (isInstalled) {
                            val manifest = installed.firstOrNull { it.appId == item.appId }
                            if (manifest != null) onOpenApp(manifest)
                        } else if (!isInstalling) {
                            vm.installMarketItem(item)
                        }
                    },
                    onMarketLongPress = { marketInfoTarget = it }
                )
            }
        }
    }

    if (actionsTarget != null) {
        val target = actionsTarget as MiniAppManifest
        ModalBottomSheet(
            onDismissRequest = { actionsTarget = null },
            sheetState = sheetState
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    text = target.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp)
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.miniapp_open)) },
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            actionsTarget = null
                            onOpenApp(target)
                        },
                        onLongClick = {}
                    )
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.miniapp_uninstall)) },
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            actionsTarget = null
                            deleteTarget = target
                        },
                        onLongClick = {}
                    )
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.miniapp_info)) },
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            actionsTarget = null
                            infoTarget = target
                        },
                        onLongClick = {}
                    )
                )
            }
        }
    }

    if (infoTarget != null) {
        val target = infoTarget as MiniAppManifest
        AlertDialog(
            onDismissRequest = { infoTarget = null },
            title = { Text(target.name) },
            text = {
                Column {
                    Text(stringResource(R.string.miniapp_app_id, target.appId))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        stringResource(
                            R.string.miniapp_version,
                            if (target.version.isEmpty()) stringResource(R.string.miniapp_version_unknown) else target.version
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(stringResource(R.string.miniapp_renderer, target.renderer))
                    if (target.description.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(target.description)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { infoTarget = null }) {
                    Text(stringResource(R.string.miniapp_close))
                }
            }
        )
    }

    if (marketInfoTarget != null) {
        val item = marketInfoTarget as MiniAppMarketItem
        AlertDialog(
            onDismissRequest = { marketInfoTarget = null },
            title = { Text(item.name) },
            text = {
                Column {
                    Text(stringResource(R.string.miniapp_app_id, item.appId))
                    if (item.version.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(stringResource(R.string.miniapp_version, item.version))
                    }
                    if (item.author.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(stringResource(R.string.miniapp_author, item.author))
                    }
                    if (item.description.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(item.description)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { marketInfoTarget = null }) {
                    Text(stringResource(R.string.miniapp_close))
                }
            }
        )
    }

    if (deleteTarget != null) {
        val target = deleteTarget as MiniAppManifest
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.miniapp_delete_title)) },
            text = { Text(stringResource(R.string.miniapp_delete_message, target.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        vm.deleteApp(target)
                    }
                ) {
                    Text(stringResource(R.string.miniapp_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.miniapp_cancel))
                }
            }
        )
    }

    if (linkDialogOpen) {
        var urlText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { linkDialogOpen = false },
            title = { Text(stringResource(R.string.miniapp_install_link_title)) },
            text = {
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    placeholder = { Text(stringResource(R.string.miniapp_install_link_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val url = urlText.trim()
                        linkDialogOpen = false
                        if (url.isNotEmpty()) vm.installFromUrl(url)
                    }
                ) {
                    Text(stringResource(R.string.miniapp_install_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { linkDialogOpen = false }) {
                    Text(stringResource(R.string.miniapp_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MiniAppMarketTab(
    marketState: String,
    marketError: String,
    market: List<MiniAppMarketItem>,
    installed: List<MiniAppManifest>,
    installing: Set<String>,
    gridMode: Boolean,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onMarketTap: (MiniAppMarketItem, Boolean, Boolean) -> Unit,
    onMarketLongPress: (MiniAppMarketItem) -> Unit
) {
    if (marketState == "loading" && market.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (marketState == "error") {
        MiniAppEmptyState(
            title = stringResource(R.string.miniapp_market_error_title),
            description = marketError,
            actionLabel = stringResource(R.string.miniapp_market_retry),
            onAction = onRetry
        )
        return
    }
    if (marketState == "empty" || market.isEmpty()) {
        MiniAppEmptyState(
            title = stringResource(R.string.miniapp_market_empty_title),
            description = stringResource(R.string.miniapp_market_empty_desc),
            actionLabel = stringResource(R.string.miniapp_market_refetch),
            onAction = onRetry
        )
        return
    }
    val pullState = rememberPullToRefreshState()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullToRefresh(
                state = pullState,
                isRefreshing = refreshing,
                onRefresh = onRefresh
            )
    ) {
        if (gridMode) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 28.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(market, key = { it.appId }) { item ->
                    val isInstalled = installed.any { it.appId == item.appId }
                    val isInstalling = installing.contains(item.appId)
                    MiniAppTile(
                        name = item.name,
                        seed = item.appId,
                        badge = if (isInstalling) "…" else if (isInstalled) "✓" else null,
                        onTap = { onMarketTap(item, isInstalled, isInstalling) },
                        onLongPress = { onMarketLongPress(item) }
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(market, key = { it.appId }) { item ->
                    val isInstalled = installed.any { it.appId == item.appId }
                    val isInstalling = installing.contains(item.appId)
                    val subtitle = listOf(
                        item.version,
                        item.author,
                        item.description
                    ).filter { it.isNotEmpty() }.joinToString(" · ")
                    MiniAppListRow(
                        name = item.name,
                        subtitle = subtitle,
                        seed = item.appId,
                        trailing = {
                            Button(
                                onClick = { onMarketTap(item, isInstalled, isInstalling) },
                                enabled = !isInstalling
                            ) {
                                Text(
                                    stringResource(
                                        if (isInstalled) R.string.miniapp_open
                                        else if (isInstalling) R.string.miniapp_installing
                                        else R.string.miniapp_install_action
                                    )
                                )
                            }
                        },
                        onTap = { onMarketTap(item, isInstalled, isInstalling) },
                        onLongPress = { onMarketLongPress(item) }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
        HomePullRefreshIndicator(
            state = pullState,
            isRefreshing = refreshing,
            topPadding = 0.dp,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MiniAppTile(
    name: String,
    seed: String,
    badge: String?,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val color = miniAppIconColor(seed)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .combinedClickable(onClick = onTap, onLongClick = onLongPress)
            .padding(vertical = 9.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(color, Color(color.red * 0.72f, color.green * 0.72f, color.blue * 0.72f, 1f))
                        )
                    )
            ) {
                Text(
                    text = miniAppIconGlyph(name),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )
            }
            if (badge != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = color
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = name,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MiniAppListRow(
    name: String,
    subtitle: String,
    seed: String,
    trailing: @Composable (() -> Unit)?,
    onTap: () -> Unit,
    onLongPress: (() -> Unit)?
) {
    val color = miniAppIconColor(seed)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.35f))
            .combinedClickable(onClick = onTap, onLongClick = { onLongPress?.invoke() })
            .padding(start = 10.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(color)
        ) {
            Text(
                text = miniAppIconGlyph(name),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
private fun MiniAppEmptyState(
    title: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(28.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

private val MINI_APP_ICON_PALETTE = listOf(
    Color(0xFFFF8A3D),
    Color(0xFF3B82F6),
    Color(0xFFA855F7),
    Color(0xFF22C55E),
    Color(0xFFEF4444),
    Color(0xFF14B8A6),
    Color(0xFFF59E0B),
    Color(0xFF6366F1)
)

private fun miniAppIconColor(seed: String): Color {
    if (seed.isEmpty()) return MINI_APP_ICON_PALETTE.first()
    var hash = 0
    for (unit in seed.toCharArray()) {
        hash = (hash * 31 + unit.code) and 0x7fffffff
    }
    return MINI_APP_ICON_PALETTE[hash % MINI_APP_ICON_PALETTE.size]
}

private fun miniAppIconGlyph(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return "小"
    val first = trimmed.codePointAt(0)
    return String(Character.toChars(first))
}
