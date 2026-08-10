package com.katti.patcher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
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
import androidx.core.net.toUri

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

    fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open link: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Katti's Mod Patcher for Rocket League Sideswipe",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(0.dp, 50.dp)
            )

            // Full-width Start Patch button
            Button(
                onClick = {
                    isProcessing = true
                    appendLog("Starting patching process...")

                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val destinationFile = File(context.cacheDir, "libKattisMod.so")

                            // GitHub direct asset download URL format:
                            val githubReleaseUrl = "https://github.com/CKatti/KattisMod/releases/latest/download/libKattisMod.so"

                            withContext(Dispatchers.Main) {
                                appendLog("Downloading latest libKattisMod.so release...")
                            }

                            downloadFileFromUrl(githubReleaseUrl, destinationFile)

                            withContext(Dispatchers.Main) {
                                appendLog("Download complete: ${destinationFile.length()} bytes saved.")
                            }

                            // TODO: Add Zip4j, Dexlib2, and Apksig logic here
                            withContext(Dispatchers.Main) { appendLog("Extracting base.apk...") }
                            Thread.sleep(1000) // Simulated work

                            withContext(Dispatchers.Main) { appendLog("Injecting Lib...") }
                            Thread.sleep(1000) // Simulated work

                            withContext(Dispatchers.Main) { appendLog("Changing Manifest...") }
                            Thread.sleep(1000) // Simulated work

                            withContext(Dispatchers.Main) { appendLog("Modifying Smali code...") }
                            Thread.sleep(1000) // Simulated work

                            withContext(Dispatchers.Main) { appendLog("Repacking APK...") }
                            Thread.sleep(1000) // Simulated work

                            withContext(Dispatchers.Main) { appendLog("Signing modified APK...") }
                            Thread.sleep(1000) // Simulated work

                            withContext(Dispatchers.Main) { appendLog("Zip aligning apk...") }
                            Thread.sleep(1000) // Simulated work

                            withContext(Dispatchers.Main) {
                                appendLog("SUCCESS: Patch completed successfully!")
                                appendLog("Ready to install modded apk.")
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Spacer(modifier = Modifier.height(4.dp))
            }

            Text(
                text = "Console Output",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )

            val verticalScrollState = rememberScrollState()
            val horizontalScrollState = rememberScrollState()
            LaunchedEffect(logText) { verticalScrollState.animateScrollTo(verticalScrollState.maxValue) }

            val scrollBarColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)

            // Console Box
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.80f), // Fills up to 90% of the screen height below the top controls            shape = RoundedCornerShape(12.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    SelectionContainer(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp)
                                    .drawWithContent {
                                        drawContent()
                                        if (verticalScrollState.maxValue > 0) {
                                            val viewPortHeight = size.height
                                            val contentHeight = viewPortHeight + verticalScrollState.maxValue
                                            val scrollbarHeight = (viewPortHeight / contentHeight) * viewPortHeight
                                            val scrollbarTop = (verticalScrollState.value / contentHeight) * viewPortHeight

                                            drawRoundRect(
                                                color = scrollBarColor,
                                                topLeft = Offset(size.width - 4.dp.toPx(), scrollbarTop),
                                                size = Size(4.dp.toPx(), scrollbarHeight),
                                                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                                            )
                                        }
                                    }
                                    .verticalScroll(verticalScrollState)
                                    .horizontalScroll(horizontalScrollState)
                            ) {
                                Text(
                                    text = logText,
                                    color = Color(0xFF00FF66),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    softWrap = false
                                )
                            }
                        }
                    }

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

            // Spacer to keep card at bottom if weight isn't enough
            Spacer(modifier = Modifier.height(4.dp))
        }

        // The Bottom Tray
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // YouTube Link
                BottomLinkItem(
                    painter = painterResource(id = R.drawable.bootstrap_youtube),
                    label = "Tutorial",
                    onClick = { openUrl("https://youtube.com/") }
                )

                // GitHub Link - Fixed and improved
                BottomLinkItem(
                    painter = painterResource(id = R.drawable.bootstrap_github),
                    label = "GitHub",
                    onClick = { openUrl("https://github.com/CKatti/KattisModPatcher") }
                )

                // Website Link
                BottomLinkItem(
                    painter = rememberVectorPainter(Icons.Default.Language),
                    label = "Website",
                    onClick = { openUrl("https://yourwebsite.com") }
                )
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

// Reusable Component for Bottom Bar Items
@Composable
fun BottomLinkItem(
    painter: Painter, // Changed from ImageVector to Painter
    label: String,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painter, // Use painter here
                contentDescription = label,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = label, fontSize = 11.sp)
        }
    }
}
