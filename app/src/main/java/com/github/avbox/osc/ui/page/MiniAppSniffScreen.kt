package com.github.avbox.osc.ui.page

import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import com.github.avbox.osc.R
import com.github.avbox.osc.ui.components.AppTopBarScaffold

@Composable
fun MiniAppSniffScreen(
    title: String,
    startUrl: String?,
    startHeaders: Map<String, String>,
    onWebViewCreated: (WebView) -> Unit,
    onNavigateBack: () -> Unit
) {
    AppTopBarScaffold(
        titleContent = {
            Text(
                text = title,
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topPadding, bottom = bottomPadding)
        ) {
            var loadedUrl by remember { mutableStateOf<String?>(null) }
            AndroidView(
                factory = { context ->
                    WebView(context).also { onWebViewCreated(it) }
                },
                update = { view ->
                    if (startUrl != null && loadedUrl != startUrl) {
                        loadedUrl = startUrl
                        view.loadUrl(startUrl, startHeaders)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            if (startUrl == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}
