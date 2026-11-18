package com.github.avbox.osc.ui.page

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImage
import com.github.avbox.osc.R
import com.github.avbox.osc.ui.components.AppTopBarScaffold

@Composable
fun HaikuoGalleryScreen(
    urls: List<String>,
    title: String,
    onNavigateBack: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { urls.size })
    AppTopBarScaffold(
        titleContent = {
            Text(
                text = if (title.isEmpty()) {
                    stringResource(R.string.haikuo_title)
                } else if (urls.size > 1) {
                    title + "  " + (pagerState.currentPage + 1) + "/" + urls.size
                } else {
                    title
                },
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
        }
    ) { topPadding, bottomPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topPadding, bottom = bottomPadding)
        ) {
            if (urls.isEmpty()) {
                Text(
                    text = stringResource(R.string.haikuo_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    AsyncImage(
                        model = urls[page],
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
