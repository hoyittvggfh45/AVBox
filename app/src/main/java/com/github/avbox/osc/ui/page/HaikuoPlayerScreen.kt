package com.github.avbox.osc.ui.page

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.github.avbox.osc.ui.player.CastSheet
import com.github.avbox.osc.ui.player.PlayContainer

@Composable
fun HaikuoPlayerScreen(container: PlayContainer) {
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { container },
            modifier = Modifier.fillMaxSize()
        )
        val uiState = container.uiState()
        uiState.castSheet?.let { sheet ->
            CastSheet(sheet) { uiState.dismissCastSheet() }
        }
    }
}
