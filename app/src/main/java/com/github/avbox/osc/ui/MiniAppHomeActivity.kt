package com.github.avbox.osc.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.ViewModelProvider
import com.github.avbox.osc.R
import com.github.avbox.osc.miniapp.MiniAppManifest
import com.github.avbox.osc.ui.base.BaseActivity
import com.github.avbox.osc.ui.components.SheetHostScaffold
import com.github.avbox.osc.ui.page.MiniAppHomeScreen
import com.github.avbox.osc.ui.page.MiniAppHomeViewModel
import com.github.avbox.osc.ui.theme.AVBoxTheme
import com.github.avbox.osc.ui.theme.enableTransparentEdgeToEdge
import java.io.File
import kotlin.concurrent.thread

class MiniAppHomeActivity : BaseActivity() {

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, MiniAppHomeActivity::class.java))
        }
    }

    private lateinit var vm: MiniAppHomeViewModel

    override fun shouldRefreshAutoSize(): Boolean = true

    override fun hideSysBar() {
    }

    override fun init() {
        vm = ViewModelProvider(this)[MiniAppHomeViewModel::class.java]
        enableTransparentEdgeToEdge()
        findViewById<ComposeView>(R.id.compose_view).setContent {
            AVBoxTheme {
                SheetHostScaffold {
                    MiniAppHomeScreen(
                        onNavigateBack = { finish() },
                        onOpenApp = { manifest -> openApp(manifest) },
                        onOpenHaikuo = { HaikuoHomeActivity.start(this) },
                        onOpenSettings = { MiniAppSettingsActivity.start(this) },
                        onFolderPicked = { uri -> installFromFolderUri(uri) }
                    )
                }
            }
        }
    }

    private fun openApp(manifest: MiniAppManifest) {
        MiniAppHostActivity.start(this, manifest.appId)
    }

    private fun installFromFolderUri(treeUri: Uri) {
        thread {
            try {
                val tempDir = File(cacheDir, "miniapp_folder_import")
                if (tempDir.exists()) tempDir.deleteRecursively()
                tempDir.mkdirs()
                copyTreeToDir(treeUri, tempDir)
                runOnUiThread { vm.installFromDirectory(tempDir) }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, e.message ?: e.toString(), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun copyTreeToDir(treeUri: Uri, target: File) {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        copyDocumentChildren(treeUri, rootId, target)
    }

    private fun copyDocumentChildren(treeUri: Uri, parentDocId: String, target: File) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val cursor = contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            ),
            null,
            null,
            null
        ) ?: return
        cursor.use {
            val idIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (it.moveToNext()) {
                val docId = it.getString(idIndex)
                val name = it.getString(nameIndex)
                val mime = it.getString(mimeIndex)
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    val subDir = File(target, name)
                    subDir.mkdirs()
                    copyDocumentChildren(treeUri, docId, subDir)
                } else {
                    val outFile = File(target, name)
                    contentResolver.openInputStream(docUri)?.use { input ->
                        outFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
        }
    }
}
