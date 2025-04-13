package com.androidlabs.lab_4

import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.webkit.MimeTypeMap
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var blockUploadFromInternet: LinearLayout
    private lateinit var blockChooseLocal: LinearLayout
    private lateinit var etUrl: EditText
    private lateinit var fileChooserLauncher: ActivityResultLauncher<Intent>
    private lateinit var loadingOverlay: View

    private var downloadId: Long = 0L
    private var currentDownloadName: String = ""

    private val pollHandler = Handler(Looper.getMainLooper())
    private val pollRunnable = object : Runnable {
        override fun run() {
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = dm.query(query)

            if (cursor != null && cursor.moveToFirst()) {
                val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        loadingOverlay.visibility = View.GONE

                        val downloadedUri: Uri? = dm.getUriForDownloadedFile(downloadId)
                        if (downloadedUri != null) {
                            val resolvedMime = contentResolver.getType(downloadedUri)
                            val ext = if (!resolvedMime.isNullOrEmpty()) {
                                val extFromMime = MimeTypeMap.getSingleton().getExtensionFromMimeType(resolvedMime)
                                if (!extFromMime.isNullOrEmpty()) ".$extFromMime" else ""
                            } else ""

                            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                            val originalFile = File(downloadsDir, currentDownloadName)
                            val newFile = if (ext.isNotEmpty() && !currentDownloadName.endsWith(ext, ignoreCase = true)) File(downloadsDir, currentDownloadName + ext)
                            else originalFile

                            if (originalFile.exists() && originalFile.name != newFile.name) {
                                val renamed = originalFile.renameTo(newFile)
                                if (!renamed) {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Failed to rename downloaded file",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                            val finalUri = Uri.fromFile(newFile)

                            val isVideo = resolvedMime?.startsWith("video") == true

                            val playIntent = Intent(this@MainActivity, PlayerActivity::class.java).apply {
                                putExtra("media_uri", finalUri)
                                putExtra("isVideo", isVideo)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            startActivity(playIntent)
                        } else {
                            Toast.makeText(
                                this@MainActivity,
                                "Failed to retrieve downloaded file",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        cursor.close()
                        return
                    }
                    DownloadManager.STATUS_FAILED -> {
                        loadingOverlay.visibility = View.GONE
                        Toast.makeText(this@MainActivity, "Download failed", Toast.LENGTH_SHORT)
                            .show()
                        cursor.close()
                        return
                    }
                }
                cursor.close()
            }
            pollHandler.postDelayed(this, 2000)
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_main)

        etUrl = findViewById(R.id.etUrl)
        blockUploadFromInternet = findViewById(R.id.blockUploadFromInternet)
        blockChooseLocal = findViewById(R.id.blockChooseLocal)
        loadingOverlay = findViewById(R.id.loading_overlay)
        loadingOverlay.visibility = View.GONE

        fileChooserLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    val fileUri: Uri? = result.data?.data
                    if (fileUri != null) {
                        val mimeType = contentResolver.getType(fileUri) ?: ""
                        val isVideo = mimeType.startsWith("video")
                        val intent = Intent(this, PlayerActivity::class.java).apply {
                            putExtra("media_uri", fileUri)
                            putExtra("isVideo", isVideo)
                        }
                        startActivity(intent)
                    }
                }
            }

        blockUploadFromInternet.setOnClickListener {
            val url = etUrl.text.toString().trim()
            if (url.isEmpty()) {
                showAlert("Error", "URL field is empty!")
                return@setOnClickListener
            }
            if (!url.startsWith("http")) {
                showAlert("Error", "Invalid URL!")
                return@setOnClickListener
            }
            try {
                val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val downloadUri: Uri = Uri.parse(url)
                currentDownloadName = System.currentTimeMillis().toString()
                val request = DownloadManager.Request(downloadUri)
                    .setAllowedNetworkTypes(
                        DownloadManager.Request.NETWORK_WIFI or
                                DownloadManager.Request.NETWORK_MOBILE
                    )
                    .setMimeType("")
                    .setTitle(currentDownloadName)
                    .setDescription("Downloading media file")
                    .setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                    )
                    .setAllowedOverRoaming(false)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, currentDownloadName)
                downloadId = dm.enqueue(request)
                loadingOverlay.visibility = View.VISIBLE
                Toast.makeText(this, "Download started...", Toast.LENGTH_SHORT).show()
                pollHandler.postDelayed(pollRunnable, 2000)
            } catch (e: Exception) {
                showAlert("Error", "Failed to start download: ${e.localizedMessage}")
            }
        }

        blockChooseLocal.setOnClickListener {
            val options = arrayOf("Gallery", "File Storage")
            AlertDialog.Builder(this)
                .setTitle("Select Source")
                .setItems(options) { dialog, which ->
                    when (which) {
                        0 -> {
                            val intent = Intent(Intent.ACTION_PICK).apply { type = "video/*" }
                            fileChooserLauncher.launch(intent)
                        }
                        1 -> {
                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "*/*"
                                val mimeTypes = arrayOf("audio/*", "video/*")
                                putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
                            }
                            fileChooserLauncher.launch(intent)
                        }
                    }
                }
                .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
                .show()
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        pollHandler.removeCallbacks(pollRunnable)
    }

    private fun showAlert(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }

}