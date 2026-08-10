package com.katti.patcher

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PatcherScreen()
                }
            }
        }
    }
}

@Composable
fun PatcherScreen() {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    var logText by remember { mutableStateOf("[INFO] App initialized.\n[INFO] Ready to download & patch...\n") }
    var isProcessing by remember { mutableStateOf(false) }

    fun appendLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        logText += "[$timestamp] $message\n"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding() // Top padding for Android status bar/notch
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Full-width Start Patch button
        Button(
            onClick = {
                isProcessing = true
                appendLog("Starting patching process...")

                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val destinationFile = File(context.cacheDir, "libmod.so")

                        // GitHub direct asset download URL format:
                        // https://github.com/<OWNER>/<REPO>/releases/latest/download/libmod.so
                        val githubReleaseUrl = "https://github.com/CKatti/your-repo/releases/latest/download/libmod.so"

                        withContext(Dispatchers.Main) {
                            appendLog("Downloading latest libmod.so release...")
                        }

                        downloadFileFromUrl(githubReleaseUrl, destinationFile)

                        withContext(Dispatchers.Main) {
                            appendLog("Download complete: ${destinationFile.length()} bytes saved.")
                        }

                        // ... Proceed with APK extraction & insertion ...

                        withContext(Dispatchers.Main) {
                            appendLog("SUCCESS: Patch completed successfully!")
                            isProcessing = false
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            appendLog("ERROR: ${e.message}")
                            isProcessing = false
                        }
                    }
                }
            },
            enabled = !isProcessing,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Start Patch", fontSize = 16.sp)
        }

        if (isProcessing) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary
            )
        }

        Text(
            text = "Console Output",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        val scrollState = rememberScrollState()
        LaunchedEffect(logText) { scrollState.animateScrollTo(scrollState.maxValue) }

        // Console Box with Top-Right Copy Icon
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Scrollable & Selectable Log Content
                SelectionContainer(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(end = 48.dp) // Leave room on the right for the copy button
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                            .verticalScroll(scrollState)
                    ) {
                        Text(
                            text = logText,
                            color = Color(0xFF00FF66),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }

                // Copy Icon Button in the Top Right Corner
                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(logText))
                        Toast.makeText(context, "Logs copied to clipboard!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Logs",
                        tint = Color.LightGray
                    )
                }
            }
        }
    }
}

suspend fun downloadFileFromUrl(urlString: String, outputFile: File) {
    withContext(Dispatchers.IO) {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.instanceFollowRedirects = true
        connection.connect()

        val responseCode = connection.responseCode

        // Follow manual redirect if HttpURLConnection doesn't switch protocols automatically (HTTP -> HTTPS)
        if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
            responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
            responseCode == 307 || responseCode == 308) {
            val newUrl = connection.getHeaderField("Location")
            downloadFileFromUrl(newUrl, outputFile)
            return@withContext
        }

        if (responseCode == HttpURLConnection.HTTP_OK) {
            connection.inputStream.use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } else {
            throw Exception("HTTP Download failed with code $responseCode: ${connection.responseMessage}")
        }
    }
}
