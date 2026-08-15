package com.katti.patcher

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
//import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.toClipEntry
import android.content.ClipData
import android.graphics.BitmapFactory
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.katti.patcher.ui.theme.KattisModPatcherTheme
import android.os.Build
import android.provider.Settings

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KattisModPatcherTheme{
                val density = LocalDensity.current

                // Main subtle gradient background requested
                val gradientBrush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.onBackground,
                        MaterialTheme.colorScheme.background,
                    ),
                    startY = 0.0f,
                    endY = with(density) { 160.dp.toPx() }
                )

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent
                ) {
                    Box(modifier = Modifier
                        .fillMaxSize()
                        .background(gradientBrush)) {
                        PatcherApp()
                    }
                }
            }
        }
    }
}

enum class AppScreen { Main, Settings }

@Composable
fun PatcherApp() {
    var currentScreen by remember { mutableStateOf(AppScreen.Main) }
    val textColor = MaterialTheme.colorScheme.tertiary
    var logText by remember {
        mutableStateOf(
            buildAnnotatedString {
                withStyle(SpanStyle(color = textColor)) { // Default terminal green
                    append("[INFO] App initialized.\n[INFO] Ready to download & patch...\n")
                }
            }
        )
    }
    var isProcessing by remember { mutableStateOf(false) }

    // Handle system back button for Settings screen
    BackHandler(enabled = currentScreen == AppScreen.Settings) {
        currentScreen = AppScreen.Main
    }

    if (currentScreen == AppScreen.Main) {
        MainScreen(
            onSettingsClick = { currentScreen = AppScreen.Settings },
            logText = logText,
            isProcessing = isProcessing,
            onAppendLog = { message, color ->
                val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                logText = buildAnnotatedString {
                    append(logText) // Keeps all existing lines and colors
                    withStyle(SpanStyle(color = color)) {
                        append("[$timestamp] $message\n")
                    }
                }
            },
            onProcessingUpdate = { isProcessing = it }
        )
    } else {
        SettingsScreen(onBack = { currentScreen = AppScreen.Main })
    }
}

@Composable
fun MainScreen(
    onSettingsClick: () -> Unit,
    logText: AnnotatedString,
    isProcessing: Boolean,
    onAppendLog: (message: String, color: Color) -> Unit,
    onProcessingUpdate: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 2 }) // Pager State (Index 0 = Patch, Index 1 = Info)

    var releaseNotes by remember { mutableStateOf("Fetching patch notes...") }

    val successColor = MaterialTheme.colorScheme.tertiary
    val warningColor = MaterialTheme.colorScheme.scrim
    val errorColor = MaterialTheme.colorScheme.error
    val releaseNotesApi = stringResource(R.string.github_release_notes_api)
    val githubReleaseUrl = stringResource(R.string.github_release_url)
    val libNameRes = stringResource(R.string.libName)

    // Fetch Release notes when switching to the Info tab (Page 1)
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage == 1 && releaseNotes == "Fetching patch notes...") {
            withContext(Dispatchers.IO) {
                try {
                    val url = URL(releaseNotesApi)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("Accept", "application/vnd.github.v3+json")

                    if (connection.responseCode == 200) {
                        val response = connection.inputStream.bufferedReader().use { it.readText() }
                        val json = JSONObject(response)
                        releaseNotes = json.optString("body", "No release notes provided.")
                    } else {
                        releaseNotes = "Failed to fetch release notes (Code: ${connection.responseCode})"
                    }
                } catch (e: Exception) {
                    releaseNotes = "Error fetching notes: ${e.message}"
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Row: App Logo, Name, Settings
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Image(
                painter = painterResource(id = R.drawable.kattismod_transparent),
                contentDescription = "Description of image",
                modifier = Modifier.size(30.dp),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.app_name_long),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSecondary
                )
            }
        }

        // 2. Dynamic Segmented Control for Tabs
        val bgColor = MaterialTheme.colorScheme.secondary
        val selectedColor = MaterialTheme.colorScheme.primary

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(bgColor, RoundedCornerShape(24.dp))
                .padding(4.dp)
        ) {
            // Background Sliding Pill synchronized with the Pager swipe
            BoxWithConstraints(modifier = Modifier.matchParentSize()) {
                val tabWidth = maxWidth / 2

                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = ((pagerState.currentPage + pagerState.currentPageOffsetFraction) * tabWidth.toPx()).toInt(),
                                y = 0
                            )
                        }
                        .width(tabWidth)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .background(selectedColor)
                )
            }

            // Foreground Tab Content (Buttons)
            Row(modifier = Modifier.fillMaxWidth()) {
                // Patch Tab
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { coroutineScope.launch { pagerState.animateScrollToPage(0) } }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Build, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Patch", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Medium)
                }

                // Info Tab
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { coroutineScope.launch { pagerState.animateScrollToPage(1) } }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Info", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Medium)
                }
            }
        }

        // 3. Swipeable Content Area
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            pageSpacing = 10.dp
        ) { page ->
            if (page == 0) {
                PatchContent(
                    isProcessing = isProcessing,
                    logText = logText,
                    onStartPatch = {
                        onProcessingUpdate(true)
                        onAppendLog("Starting patching process...", successColor)

                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                                    withContext(Dispatchers.Main) {
                                        onAppendLog("Please allow installation permissions in Settings, then try again.", warningColor)
                                        onProcessingUpdate(false)
                                    }

                                    // Launch the specific Android settings page for your app
                                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                        data = "package:${context.packageName}".toUri()
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK // Required if launching from outside an Activity context
                                    }
                                    context.startActivity(intent)

                                    // Abort the patch process so they can grant it and click "Patch" again
                                    return@launch
                                }
                                val originalPackage = "com.Psyonix.RL2D"
                                val modPackage = "com.Psyonix.RL2D.KattisMod"

                                val isTargetInstalled = try {
                                    context.packageManager.getPackageInfo(originalPackage, 0)
                                    true
                                } catch (_: PackageManager.NameNotFoundException) {
                                    false
                                }
                                val isModInstalled = try {
                                    context.packageManager.getPackageInfo(modPackage, 0)
                                    true
                                } catch (_: PackageManager.NameNotFoundException) {
                                    false
                                }

                                if (!isTargetInstalled) {
                                    withContext(Dispatchers.Main) {
                                        onAppendLog("($originalPackage) missing. Install RLSS first through official sources.", errorColor)
                                        onProcessingUpdate(false)
                                    }
                                    return@launch
                                }
                                if (isModInstalled) {
                                    withContext(Dispatchers.Main) {
                                        onAppendLog("Mod already installed. Updating.", warningColor)
                                    }
                                }

                                // Fetch latest release info to get the tag
                                withContext(Dispatchers.Main) { onAppendLog("Checking for updates...", successColor) }
                                val releaseJson = URL(releaseNotesApi).openConnection().run {
                                    this as HttpURLConnection
                                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                                    inputStream.bufferedReader().use { it.readText() }
                                }
                                val latestTag = JSONObject(releaseJson).getString("tag_name")

                                // Check cache for this specific tag
                                val libName = libNameRes
                                val downloadedFile = File(context.cacheDir, "lib${libName}_$latestTag.so")

                                if (downloadedFile.exists() && downloadedFile.length() > 0) {
                                    withContext(Dispatchers.Main) {
                                        onAppendLog("Using cached version ($latestTag).", warningColor)
                                    }
                                } else {
                                    // Clean up old cached .so files
                                    context.cacheDir.listFiles { _, name -> name.startsWith("lib") && name.endsWith(".so") }
                                        ?.forEach { it.delete() }

                                    withContext(Dispatchers.Main) { onAppendLog("Downloading version $latestTag...", successColor) }
                                    downloadFileFromUrl(githubReleaseUrl, downloadedFile)
                                    withContext(Dispatchers.Main) { onAppendLog("Download complete: ${downloadedFile.length()} bytes saved.", successColor) }
                                }

                                // Extract the APK file
                                withContext(Dispatchers.Main) {
                                    onAppendLog("Extracting APK from installed game...", successColor)
                                }
                                val extractedApkFile = getCachedOrExtractBaseApk(
                                    context = context,
                                    targetPackage = originalPackage,
                                    onLog = { msg, status ->
                                        // Route the utility logs back to the main UI thread safely
                                        coroutineScope.launch(Dispatchers.Main) {
                                            onAppendLog(msg, when (status) {
                                                "success" -> successColor
                                                "warning" -> warningColor
                                                else -> errorColor
                                            })
                                        }
                                    }
                                )
                                withContext(Dispatchers.Main) {
                                    val sizeMb = extractedApkFile.length() / (1024 * 1024)
                                    onAppendLog("APK size: $sizeMb MB", successColor)
                                }

                                // Unzip the APK file
                                val unzippedDir = File(context.cacheDir, extractedApkFile.name.replace(".apk", ""))
                                extractApkContents(
                                    apkFile = extractedApkFile,
                                    outputDir = unzippedDir,
                                    onLog = { msg, status ->
                                        coroutineScope.launch(Dispatchers.Main) {
                                            onAppendLog(msg, when (status) {
                                                "success" -> successColor
                                                "warning" -> warningColor
                                                else -> errorColor
                                            })
                                        }
                                    }
                                )

                                // Modify the resource file to change app name
                                val arscFile = File(unzippedDir, "resources.arsc")
                                val transparentLabelBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.icon_overlay)
                                modifyResourcesArsc(
                                    arscFile = arscFile,
                                    newAppName = "RLSS Katti's Mod",
                                    overlayBitmap = transparentLabelBitmap,
                                    onLog = { msg, status ->
                                        coroutineScope.launch(Dispatchers.Main) {
                                            onAppendLog(msg, when (status) {
                                                "success" -> successColor
                                                "warning" -> warningColor
                                                else -> errorColor
                                            })
                                        }
                                    }
                                )

                                // Modify the Manifest to change package name
                                val manifestFile = File(unzippedDir, "AndroidManifest.xml")
                                val manifestSuccess = modifyAndroidManifest(
                                    manifestFile = manifestFile,
                                    originalPackage = originalPackage,
                                    newPackage = modPackage,
                                    onLog = { msg, status ->
                                        coroutineScope.launch(Dispatchers.Main) {
                                            onAppendLog(msg, when (status) {
                                                "success" -> successColor
                                                "warning" -> warningColor
                                                else -> errorColor
                                            })
                                        }
                                    }
                                )
                                if (!manifestSuccess) {
                                    withContext(Dispatchers.Main) { onAppendLog("Manifest cloning may have failed.", warningColor) }
                                }

                                // Inject Load Lib into .dex file
                                val classesDexFile = File(unzippedDir, "classes.dex")
                                val success = injectLibIntoDex(
                                    inputDex = classesDexFile,
                                    outputDex = classesDexFile, // Overwriting the extracted file directly!
                                    libName = libNameRes,
                                    onLog = { msg, status ->
                                        coroutineScope.launch(Dispatchers.Main) {
                                            onAppendLog(msg, when (status) {
                                                "success" -> successColor
                                                "warning" -> warningColor
                                                else -> errorColor
                                            })
                                        }
                                    }
                                )
                                if (!success) {
                                    throw Exception("Smali injection failed. Aborting patch.")
                                }

                                // Copy downloaded lib to lib/arm64-v8a
                                withContext(Dispatchers.Main) { onAppendLog("Copying native library to APK structure...", successColor) }
                                val libDir = File(unzippedDir, "lib/arm64-v8a")
                                if (!libDir.exists()) {
                                    libDir.mkdirs()
                                }
                                val libFile = File(libDir, "lib${libNameRes}.so")
                                try {
                                    downloadedFile.inputStream().use { input ->
                                        libFile.outputStream().use { output ->
                                            input.copyTo(output)
                                        }
                                    }
                                    withContext(Dispatchers.Main) {
                                        onAppendLog("Library copied to: ${libFile.absolutePath}", successColor)
                                    }
                                } catch (e: Exception) {
                                    throw Exception("Failed to copy native library: ${e.message}")
                                }

                                // Repackage the APK
                                val patchedApkFile = File(context.cacheDir, "RL2D_KattisMod.apk")
                                withContext(Dispatchers.Main) { onAppendLog("Repackaging modified APK...", successColor) }
                                repackApk(
                                    unzippedDir = unzippedDir,
                                    outputApk = patchedApkFile,
                                    onLog = { msg, status ->
                                        coroutineScope.launch(Dispatchers.Main) {
                                            onAppendLog(msg, when (status) {
                                                "success" -> successColor
                                                "warning" -> warningColor
                                                else -> errorColor
                                            })
                                        }
                                    }
                                )

                                // Align the APK
                                val alignedApkFile = File(context.cacheDir, "RL2D_KattisMod_Aligned.apk")
                                val alignSuccess = zipalignApk(
                                    inputApk = patchedApkFile,
                                    outputApk = alignedApkFile,
                                    onLog = { msg, status ->
                                        coroutineScope.launch(Dispatchers.Main) {
                                            onAppendLog(msg, when (status) {
                                                "success" -> successColor
                                                "warning" -> warningColor
                                                else -> errorColor
                                            })
                                        }
                                    }
                                )
                                if (!alignSuccess) {
                                    throw Exception("Zipalign failed.")
                                }

                                val signedApkFile = File(context.cacheDir, "RL2D_KattisMod_Signed.apk")
                                val signSuccess = signApk(
                                    context = context,
                                    inputApk = alignedApkFile,
                                    outputApk = signedApkFile,
                                    onLog = { msg, status ->
                                        coroutineScope.launch(Dispatchers.Main) {
                                            onAppendLog(msg, when (status) {
                                                "success" -> successColor
                                                "warning" -> warningColor
                                                else -> errorColor
                                            })
                                        }
                                    }
                                )
                                if (!signSuccess) {
                                    throw Exception("Failed to sign the APK.")
                                }


                                withContext(Dispatchers.Main) {
                                    onAppendLog("SUCCESS: Patch completed successfully!", successColor)
                                    onAppendLog("Ready to install modded apk.", successColor)
                                    onProcessingUpdate(false)
                                }

                                promptInstallApk(
                                    context = context,
                                    apkFile = signedApkFile,
                                    onLog = { msg, status ->
                                        coroutineScope.launch(Dispatchers.Main) {
                                            onAppendLog(msg, when (status) {
                                                "success" -> successColor
                                                "warning" -> warningColor
                                                else -> errorColor
                                            })
                                        }
                                    }
                                )

                                // Clean up the cache
                                downloadedFile.delete()
                                extractedApkFile.delete()
                                unzippedDir.deleteRecursively()
                                patchedApkFile.delete()
                                alignedApkFile.delete()
                                signedApkFile.delete()
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    onAppendLog("ERROR: ${e.message}", errorColor)
                                    onProcessingUpdate(false)
                                }
                            }
                        }
                    }
                )
            } else {
                InfoContent(releaseNotes = releaseNotes)
            }
        }

        // Bottom Icon Tray
        BottomIconTray()
    }
}

@Composable
fun PatchContent(
    isProcessing: Boolean,
    logText: AnnotatedString,
    onStartPatch: () -> Unit
) {
    val clipboard= LocalClipboard.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        // Main Action Button
        Button(
            onClick = onStartPatch,
            enabled = !isProcessing,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                disabledContainerColor = MaterialTheme.colorScheme.secondary
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(if (isProcessing) "Patching in progress..." else "Start Patch", color = MaterialTheme.colorScheme.onPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }

        if (isProcessing) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.secondary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Console Output
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary) // Deep dark for terminal
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val verticalScrollState = rememberScrollState()
//                val horizontalScrollState = rememberScrollState()

                LaunchedEffect(logText) { verticalScrollState.animateScrollTo(verticalScrollState.maxValue) }

                SelectionContainer(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                            .drawWithContent {
                                drawContent()
                                if (verticalScrollState.maxValue > 0) {
                                    val viewPortHeight = size.height
                                    val contentHeight =
                                        viewPortHeight + verticalScrollState.maxValue
                                    val scrollbarHeight =
                                        (viewPortHeight / contentHeight) * viewPortHeight
                                    val scrollbarTop =
                                        (verticalScrollState.value.toFloat() / contentHeight) * viewPortHeight

                                    drawRoundRect(
                                        color = Color.LightGray.copy(alpha = 0.5f),
                                        topLeft = Offset(size.width - 4.dp.toPx(), scrollbarTop),
                                        size = Size(4.dp.toPx(), scrollbarHeight),
                                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                                    )
                                }
                            }
                            .verticalScroll(verticalScrollState)
//                            .horizontalScroll(horizontalScrollState)
                    ) {
                        Text(
                            text = logText,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
//                            softWrap = true
                        )
                    }
                }

                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            // 4. Create standard Android ClipData, convert to ClipEntry, and set it
                            val clipData = ClipData.newPlainText("Console Logs", logText.text)
                            clipboard.setClipEntry(clipData.toClipEntry())
                            Toast.makeText(context, "Logs copied to clipboard!", Toast.LENGTH_SHORT).show()
                        }
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

@Composable
fun InfoContent(releaseNotes: String) {
    Card(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Latest Patch Notes",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = releaseNotes,
                color = Color.LightGray,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun BottomIconTray() {
    val context = LocalContext.current
    val youtubeUrl = stringResource(R.string.youtube_url)
    val githubUrl = stringResource(R.string.github_url)
    val websiteUrl = stringResource(R.string.website_url)

    fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show()
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomLinkItem(
                painter = painterResource(id = R.drawable.bootstrap_youtube),
                label = "Tutorial",
                onClick = { openUrl(youtubeUrl) }
            )
            BottomLinkItem(
                painter = painterResource(id = R.drawable.bootstrap_github),
                label = "GitHub",
                onClick = { openUrl(githubUrl) }
            )
            BottomLinkItem(
                painter = rememberVectorPainter(Icons.Default.Language),
                label = "Website",
                onClick = { openUrl(websiteUrl) }
            )
        }
    }
}

// Settings screen matching image_1260c9.png
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Settings",
                fontSize = 22.sp,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Settings Options
            SettingsItemCard(
                icon = Icons.Default.Settings,
                title = "General",
                subtitle = "Language, theme, dynamic color"
            )
            SettingsItemCard(
                icon = Icons.Default.Refresh,
                title = "Updates",
                subtitle = "Check for updates"
            )
            SettingsItemCard(
                icon = Icons.Default.Tune,
                title = "Advanced",
                subtitle = "URL, Debugging"
            )

            Spacer(modifier = Modifier.weight(1f))

            // About Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.kattismod_transparent),
                        contentDescription = "Description of image",
                        modifier = Modifier.size(30.dp),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(stringResource(R.string.app_name_long), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text(stringResource(R.string.app_version), color = Color.Gray, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsItemCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { /* TBD */ },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1b20))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = Color(0xFF8B94A5),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subtitle, color = Color.Gray, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun BottomLinkItem(painter: Painter, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painter,
                contentDescription = label,
                modifier = Modifier.size(24.dp),
                tint = Color.LightGray
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = label, fontSize = 11.sp, color = Color.LightGray)
        }
    }
}