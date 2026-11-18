package com.github.avbox.osc.ui.page

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.github.avbox.osc.haikuo.haikuoX5RowLayout
import com.github.avbox.osc.ui.HaikuoX5State
import com.github.avbox.osc.ui.player.PlayContainer

@Composable
fun HaikuoRunnerScreen(
    startUrl: String?,
    bootError: String?,
    dialog: HaikuoDialogUi?,
    bridgeReady: Boolean,
    inlineContainer: PlayContainer?,
    x5Session: HaikuoX5State?,
    x5UiTick: Int,
    x5WebView: WebView?,
    floatVideoUrl: String,
    floatVideoContainer: PlayContainer?,
    onWebViewCreated: (WebView) -> Unit,
    onDialogResult: (Map<String, Any?>) -> Unit,
    onCloseFloatVideo: () -> Unit,
    onNavigateBack: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
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
                        attachSingleChild(holder, inlineContainer)
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
                            text = bootError,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onNavigateBack) {
                            Text(stringResource(R.string.common_cancel))
                        }
                    }
                } else {
                    var loadedUrl by remember { mutableStateOf<String?>(null) }
                    AndroidView(
                        factory = { context ->
                            WebView(context).also { onWebViewCreated(it) }
                        },
                        update = { view ->
                            if (startUrl != null && bridgeReady && loadedUrl != startUrl) {
                                loadedUrl = startUrl
                                view.loadUrl(startUrl)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    if (startUrl == null || !bridgeReady) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                }
            }
        }
        val x5 = x5Session
        if (x5 != null && x5.visible && x5UiTick >= 0) {
            val headerHeight = if (x5.topOffset > 0) x5.topOffset else 48.0
            val layout = haikuoX5RowLayout(
                x5.desc,
                maxHeight.value.toDouble(),
                maxWidth.value.toDouble(),
                headerHeight
            )
            val showProgress = x5.showProgress && !x5.hideLoadingRequested && x5.progress < 100
            Box(
                modifier = Modifier
                    .align(if (layout.floatMode) Alignment.BottomCenter else Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(layout.height.dp)
                    .padding(top = if (layout.topMode) headerHeight.dp else 0.dp)
                    .background(Color.White)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (showProgress) {
                        LinearProgressIndicator(
                            progress = { x5.progress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                        )
                    }
                    AndroidView(
                        factory = { context -> FrameLayout(context) },
                        update = { holder ->
                            attachSingleChild(holder, x5WebView)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            }
        }
        if (floatVideoUrl.isNotEmpty() && floatVideoContainer != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 8.dp, end = 8.dp, bottom = 16.dp)
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
            ) {
                AndroidView(
                    factory = { context -> FrameLayout(context) },
                    update = { holder ->
                        attachSingleChild(holder, floatVideoContainer)
                    },
                    modifier = Modifier.fillMaxSize()
                )
                IconButton(
                    onClick = onCloseFloatVideo,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.54f), CircleShape)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.player_ic_close),
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            }
        }
        if (dialog != null) {
            HaikuoRunnerDialog(state = dialog, onResult = onDialogResult)
        }
    }
}

private fun attachSingleChild(holder: FrameLayout, child: View?) {
    if (child != null) {
        if (child.parent !== holder) {
            (child.parent as? ViewGroup)?.removeView(child)
            holder.addView(
                child,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    } else {
        holder.removeAllViews()
    }
}
