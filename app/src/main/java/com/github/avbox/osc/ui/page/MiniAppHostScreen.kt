package com.github.avbox.osc.ui.page

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.avbox.osc.R
import com.github.avbox.osc.haikuo.haikuoSlotHeight
import com.github.avbox.osc.ui.components.AppTopBarScaffold
import com.github.avbox.osc.ui.player.PlayContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiniAppHostScreen(
    appName: String,
    startUrl: String?,
    bootError: String?,
    pageLoading: Boolean,
    loadingOverlay: String?,
    confirmTitle: String?,
    confirmContent: String?,
    sheetItems: List<String>?,
    inlineContainer: PlayContainer?,
    onWebViewCreated: (WebView) -> Unit,
    onConfirmResult: (Boolean) -> Unit,
    onSheetResult: (Int) -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    AppTopBarScaffold(
        titleContent = {
            Text(
                text = appName,
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
            IconButton(onClick = onRefresh, enabled = startUrl != null) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.miniapp_refresh)
                )
            }
        }
    ) { topPadding, bottomPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topPadding, bottom = bottomPadding)
        ) {
            val slotHeight = if (inlineContainer != null) {
                haikuoSlotHeight(
                    16.0 / 9.0,
                    maxWidth.value.toDouble(),
                    maxHeight.value.toDouble()
                ).dp
            } else {
                0.dp
            }
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(slotHeight)
                ) {
                    AndroidView(
                        factory = { context -> FrameLayout(context) },
                        update = { holder ->
                            miniAppAttachSingleChild(holder, inlineContainer)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    if (bootError != null) {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.miniapp_start_failed),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = bootError,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = onRetry) {
                                Text(stringResource(R.string.miniapp_retry))
                            }
                        }
                    } else {
                        var loadedUrl by remember { mutableStateOf<String?>(null) }
                        AndroidView(
                            factory = { context ->
                                WebView(context).also { onWebViewCreated(it) }
                            },
                            update = { view ->
                                if (startUrl != null && loadedUrl != startUrl) {
                                    loadedUrl = startUrl
                                    view.loadUrl(startUrl)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        if (startUrl == null) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                        if (pageLoading && startUrl != null) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .fillMaxWidth()
                                    .height(2.dp)
                            )
                        }
                        if (loadingOverlay != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.45f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = Color.White)
                                    if (loadingOverlay.isNotEmpty()) {
                                        Spacer(Modifier.height(12.dp))
                                        Text(
                                            text = loadingOverlay,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmTitle != null) {
        AlertDialog(
            onDismissRequest = { onConfirmResult(false) },
            title = { Text(confirmTitle) },
            text = { Text(confirmContent ?: "") },
            confirmButton = {
                TextButton(onClick = { onConfirmResult(true) }) {
                    Text(stringResource(R.string.miniapp_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { onConfirmResult(false) }) {
                    Text(stringResource(R.string.miniapp_cancel))
                }
            }
        )
    }

    if (sheetItems != null) {
        ModalBottomSheet(
            onDismissRequest = { onSheetResult(-1) },
            sheetState = sheetState
        ) {
            LazyColumn(modifier = Modifier.padding(bottom = 24.dp)) {
                itemsIndexed(sheetItems) { index, item ->
                    ListItem(
                        headlineContent = { Text(item) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSheetResult(index) }
                    )
                }
            }
        }
    }
}

private fun miniAppAttachSingleChild(holder: FrameLayout, child: View?) {
    if (child == null) {
        if (holder.childCount > 0) holder.removeAllViews()
        return
    }
    if (holder.childCount == 1 && holder.getChildAt(0) === child) return
    val parent = child.parent as? ViewGroup
    parent?.removeView(child)
    if (holder.childCount > 0) holder.removeAllViews()
    holder.addView(
        child,
        ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    )
}
