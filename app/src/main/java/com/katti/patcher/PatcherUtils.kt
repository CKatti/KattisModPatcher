package com.katti.patcher

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.content.Context
import android.graphics.Rect
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.graphics.createBitmap

import com.android.apksig.ApkSigner
import com.android.apksig.KeyConfig
import com.reandroid.archive.ZipAlign
import com.reandroid.archive.writer.ZipAligner
import com.reandroid.arsc.chunk.TableBlock
import com.reandroid.arsc.chunk.xml.ResXmlDocument
import com.reandroid.arsc.chunk.xml.ResXmlElement

import java.net.HttpURLConnection
import java.io.File
import java.io.FileOutputStream
import java.net.URL

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.ZipFile

import org.jf.dexlib2.DexFileFactory
import org.jf.dexlib2.iface.DexFile
import org.jf.dexlib2.iface.Method
import org.jf.dexlib2.iface.instruction.Instruction
import org.jf.dexlib2.immutable.ImmutableMethod
import org.jf.dexlib2.immutable.ImmutableMethodImplementation
import org.jf.dexlib2.immutable.ImmutableTryBlock
import org.jf.dexlib2.immutable.ImmutableExceptionHandler
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction21c
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction35c
import org.jf.dexlib2.immutable.reference.ImmutableMethodReference
import org.jf.dexlib2.immutable.reference.ImmutableStringReference
import org.jf.dexlib2.Opcode
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.rewriter.DexRewriter
import org.jf.dexlib2.rewriter.Rewriter
import org.jf.dexlib2.rewriter.RewriterModule
import org.jf.dexlib2.rewriter.Rewriters
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate

import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import ru.solrudev.ackpine.installer.PackageInstaller
import ru.solrudev.ackpine.session.Session
import ru.solrudev.ackpine.session.await
import ru.solrudev.ackpine.installer.parameters.InstallParameters
import ru.solrudev.ackpine.session.parameters.Confirmation

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

suspend fun getCachedOrExtractBaseApk(
    context: Context,
    targetPackage: String,
    onLog: (String, String) -> Unit // We pass the logger in so we can log cache hits/misses
): File {
    return withContext(Dispatchers.IO) {
        val packageManager = context.packageManager

        // Get the current version code of the installed game
        val packageInfo = packageManager.getPackageInfo(targetPackage, 0)
        val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)

        // Define our target cache file name based on that version
        val cachedApkName = "base_${targetPackage}_v${versionCode}.apk"
        val cachedApkFile = File(context.cacheDir, cachedApkName)

        // CACHE HIT: If it already exists, return it
        if (cachedApkFile.exists() && cachedApkFile.length() > 0) {
            onLog("Found cached APK (Version: $versionCode)", "warning")
            return@withContext cachedApkFile
        }

        // CACHE MISS: Delete any old versions to save space
        onLog("No matching cache found for v$versionCode", "warning")
        context.cacheDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("base_${targetPackage}_") && file.name.endsWith(".apk")) {
                file.delete()
                onLog("Deleted old cached APK: ${file.name}", "success")
            }
        }

        // Extract the new APK
        onLog("Extracting base.apk... This might take a while.", "success")
        val appInfo = packageManager.getApplicationInfo(targetPackage, 0)
        val sourceApkFile = File(appInfo.sourceDir)

        if (!sourceApkFile.exists()) {
            onLog("Extracting failed.", "error")
            throw Exception("Source APK not found at ${appInfo.sourceDir}")
        }

        cachedApkFile.parentFile?.mkdirs()
        sourceApkFile.copyTo(cachedApkFile, overwrite = true)

        cachedApkFile // Return the newly extracted file
    }
}

suspend fun extractApkContents(
    apkFile: File,
    outputDir: File,
    onLog: (String, String) -> Unit
) {
    withContext(Dispatchers.IO) {
        // Return if APK is already extracted
        if (outputDir.exists()) {
            onLog("APK already unzipped", "warning")
            return@withContext
        }

        // Unzip using Zip4j
        outputDir.mkdirs()
        onLog("Unzipping APK contents...", "success")
        try {
            val zipFile = ZipFile(apkFile)
            zipFile.extractAll(outputDir.absolutePath)
            onLog("Unzipped APK contents to: ${outputDir.name}/", "success")
        } catch (e: Exception) {
            onLog("Unzipping failed", "error")
            throw Exception("Zip4j failed to extract APK: ${e.message}")
        }
    }
}

/**
 * Decodes a classes.dex file into memory using dexlib2.
 *
 * @param dexFile The physical classes.dex file extracted from the APK.
 * @param onLog Callback to print updates to the UI console.
 * @return The decoded DexFile object, or null if it fails.
 */
suspend fun decodeDexFile(
    dexFile: File,
    onLog: (String, String) -> Unit
): DexFile? {
    return withContext(Dispatchers.IO) {
        try {
            if (!dexFile.exists()) {
                throw Exception("The file ${dexFile.name} does not exist.")
            }

            onLog("Loading ${dexFile.name} with dexlib2...", "success")

            // Define the Opcodes API level.
            // Opcodes.getDefault() works for most modern apps, but you can target a specific API
            // like Opcodes.forApi(29) if the game requires it.
            val opcodes = Opcodes.getDefault()

            // Decode the .dex file into an in-memory object
            val loadedDex: DexFile = DexFileFactory.loadDexFile(dexFile, opcodes)

            onLog("Successfully decoded ${dexFile.name}!", "success")
            onLog("Found ${loadedDex.classes.size} classes inside the dex.", "success")

            loadedDex // Return the in-memory representation
        } catch (e: Exception) {
            onLog("Failed to decode dex: ${e.message}", "error")
            null
        }
    }
}

/**
 * Injects System.loadLibrary("<libName>") into SplashActivity.onCreate()
 */
suspend fun injectLibIntoDex(
    inputDex: File,
    outputDex: File,
    libName: String,
    onLog: (String, String) -> Unit
): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            onLog("Initializing DexRewriter for SplashActivity...", "success")
            val opcodes = Opcodes.getDefault()
            val originalDex = DexFileFactory.loadDexFile(inputDex, opcodes)

            // Create a custom RewriterModule to intercept the specific method
            val module = object : RewriterModule() {
                override fun getMethodRewriter(rewriters: Rewriters): Rewriter<Method> {
                    val defaultRewriter = super.getMethodRewriter(rewriters)

                    return Rewriter { method ->
                        // Let the default rewriter process it first
                        val rewrittenMethod = defaultRewriter.rewrite(method)

                        // Identify SplashActivity -> onCreate(Bundle)
                        if (rewrittenMethod.definingClass == "Lcom/epicgames/ue4/SplashActivity;" &&
                            rewrittenMethod.name == "onCreate" &&
                            rewrittenMethod.parameters.size == 1 &&
                            rewrittenMethod.parameters[0].type == "Landroid/os/Bundle;"
                        ) {
                            val originalImpl = rewrittenMethod.implementation
                            if (originalImpl != null) {
                                onLog("Found onCreate in SplashActivity! Injecting Smali (this might take a minute)...", "success")

                                val newInstructions = mutableListOf<Instruction>()

                                // Smali: const-string v0, "<libName>"
                                newInstructions.add(
                                    ImmutableInstruction21c(
                                        Opcode.CONST_STRING,
                                        0, // Register v0
                                        ImmutableStringReference(libName)
                                    )
                                )

                                // Smali: invoke-static {v0}, Ljava/lang/System;->loadLibrary(Ljava/lang/String;)V
                                newInstructions.add(
                                    ImmutableInstruction35c(
                                        Opcode.INVOKE_STATIC,
                                        1, 0, 0, 0, 0, 0,
                                        ImmutableMethodReference(
                                            "Ljava/lang/System;",
                                            "loadLibrary",
                                            listOf("Ljava/lang/String;"),
                                            "V"
                                        )
                                    )
                                )

                                // Append all the original instructions after our injected ones
                                originalImpl.instructions.forEach { newInstructions.add(it) }

                                val shiftAmount = 5
                                val shiftedTryBlocks = originalImpl.tryBlocks.map { tryBlock ->
                                    ImmutableTryBlock(
                                        tryBlock.startCodeAddress + shiftAmount, // Shift the start
                                        tryBlock.codeUnitCount, // Length stays the same
                                        tryBlock.exceptionHandlers.map { handler ->
                                            ImmutableExceptionHandler(
                                                handler.exceptionType,
                                                handler.handlerCodeAddress + shiftAmount // Shift the catch block
                                            )
                                        }
                                    )
                                }

                                // Package it back into an ImmutableMethodImplementation
                                val newImpl = ImmutableMethodImplementation(
                                    originalImpl.registerCount,
                                    newInstructions,
                                    shiftedTryBlocks, // Use our newly shifted try blocks,
                                    emptyList() // Drop debug items (line numbers) to prevent offset crashes
                                )

                                // Return the modified method
                                return@Rewriter ImmutableMethod(
                                    rewrittenMethod.definingClass,
                                    rewrittenMethod.name,
                                    rewrittenMethod.parameters,
                                    rewrittenMethod.returnType,
                                    rewrittenMethod.accessFlags,
                                    rewrittenMethod.annotations,
                                    rewrittenMethod.hiddenApiRestrictions,
                                    newImpl
                                )
                            }
                        }

                        // If it's not the target method, return it completely unchanged
                        rewrittenMethod
                    }
                }
            }

            // Run the rewriter across the entire classes.dex
            val rewriter = DexRewriter(module)
            val rewrittenDex = rewriter.dexFileRewriter.rewrite(originalDex)

            // Save the modified DEX, overwriting the original in the unpacked folder
            DexFileFactory.writeDexFile(outputDex.absolutePath, rewrittenDex)

            onLog("Smali injection complete. DEX rewritten successfully.", "success")
            true
        } catch (e: Exception) {
            onLog("Failed to rewrite DEX: ${e.message}", "error")
            false
        }
    }
}

suspend fun modifyResourcesArsc(
    arscFile: File,
    newAppName: String,
    overlayBitmap: Bitmap,
    onLog: (String, String) -> Unit
) {
    return withContext(Dispatchers.IO) {
        try {
            onLog("Loading resources.arsc with ARSCLib...", "success")

            // Load the binary ARSC file into memory
            val tableBlock = TableBlock()
            tableBlock.readBytes(arscFile)

            // Find and modify the "app_name" string
            val appNameEntry = tableBlock.getEntry(null, "string", "app_name")
            if (appNameEntry != null) {
                appNameEntry.resValue.valueAsString = newAppName
                onLog("Successfully modified app_name to: $newAppName", "success")
            } else {
                onLog("Could not find 'app_name' resource.", "warning")
            }

            // Find the file path for "icon_bg" (or equivalent mipmap/drawable)
            val iconFilePath: String?
            val iconEntry = tableBlock.getEntry(null, "mipmap", "icon_bg")
            if (iconEntry != null) {
                iconFilePath = iconEntry.resValue.valueAsString
                onLog("Found icon path in ARSC: $iconFilePath", "success")
            } else {
                onLog("Warning: Could not find 'icon_bg' resource.","warning")
                // Fallback hardcoded path if you already know it
                iconFilePath = "res/drawable/5M.png"
            }

            // Save the modified resources.arsc back to disk
            tableBlock.refresh() // Ensure headers and offsets are recalculated
            tableBlock.writeBytes(arscFile)
            onLog("Saved modified resources.arsc.", "success")

            iconFilePath?.let { path ->
                overlayCustomIcon(
                    targetIconFile = File(arscFile.parentFile, path),
                    overlayBitmap = overlayBitmap,
                    onLog = { msg, status -> onLog(msg, status) }
                )
            }

        } catch (e: Exception) {
            onLog("Failed to modify resources.arsc: ${e.message}", "error")
        }
    }
}

suspend fun overlayCustomIcon(
    targetIconFile: File, // e.g., "res/drawable/i1.png"
    overlayBitmap: Bitmap,   // Your transparent label
    onLog: suspend (String, String) -> Unit
) {
    withContext(Dispatchers.IO) {
        try {
            if (!targetIconFile.exists()) {
                onLog("Target icon not found at: ${targetIconFile.absolutePath}", "warning")
                return@withContext
            }

            onLog("Overlaying custom label onto ${targetIconFile.absolutePath}...", "success")

            // 1. Decode the original background PNG with scaling disabled
            val options = BitmapFactory.Options().apply { inScaled = false }
            val originalBitmap = BitmapFactory.decodeFile(targetIconFile.absolutePath, options)
                ?: throw Exception("Could not decode target icon.")

            // 2. Create a fresh, mutable Bitmap of the exact same size
            val compositeBitmap = createBitmap(
                originalBitmap.width,
                originalBitmap.height,
                originalBitmap.config ?: Bitmap.Config.ARGB_8888
            )

            // 3. Use a Canvas to draw the original first, then your transparent overlay on top
            val canvas = Canvas(compositeBitmap)
            val rect = Rect(0, 0, compositeBitmap.width, compositeBitmap.height)
            
            // Drawing with a destination rect ensures pixel-perfect fit regardless of density
            canvas.drawBitmap(originalBitmap, null, rect, null)
            canvas.drawBitmap(overlayBitmap, null, rect, null)

            // Overwrite the original extracted file
            FileOutputStream(targetIconFile).use { out ->
                compositeBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            onLog("Successfully overlaid custom icon!", "success")
        } catch (e: Exception) {
            onLog("Failed to overlay image: ${e.message}", "error")
        }
    }
}

suspend fun modifyAndroidManifest(
    manifestFile: File,
    originalPackage: String,
    newPackage: String,
    onLog: (String, String) -> Unit
): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            if (!manifestFile.exists()) {
                throw Exception("AndroidManifest.xml not found!")
            }

            onLog("Loading binary AndroidManifest.xml...", "success")

            // Load the binary AXML file into memory
            val xmlDoc = ResXmlDocument()
            xmlDoc.readBytes(manifestFile)

            var replacementCount = 0
            val manifestElement = xmlDoc.documentElement

            // Change the root <manifest package="..."> attribute
            var packageAttr = manifestElement.searchAttribute("", "package")
                ?: manifestElement.searchAttributeByName("package")

            if (packageAttr != null) {
                packageAttr.valueAsString = newPackage
                replacementCount++
            } else {
                // Fallback: iterate through all attributes to find "package"
                val attributes = manifestElement.attributes
                while (attributes.hasNext()) {
                    val attr = attributes.next()
                    if (attr.name == "package") {
                        attr.valueAsString = newPackage
                        replacementCount++
                        packageAttr = attr
                        break
                    }
                }
            }

            // Recursively search all elements (providers, activities, intent-filters)
            // to replace authorities, actions, and custom permissions to prevent conflicts
            val queue = ArrayDeque<ResXmlElement>()
            val initialElements = manifestElement.elements
            while (initialElements.hasNext()) {
                queue.add(initialElements.next())
            }

            while (queue.isNotEmpty()) {
                val element = queue.removeFirst()

                // Skip specific activity that must keep its original name
                if (element.name == "activity") {
                    val nameAttr = element.searchAttribute("http://schemas.android.com/apk/res/android", "name")
                        ?: element.searchAttribute("", "name")
                    if (nameAttr != null && nameAttr.valueAsString == "com.Psyonix.RL2D.DownloaderActivity") {
                        // Skip adding children to queue and skip modifying this element's attributes
                        continue
                    }
                }

                // Iterate through all attributes of the current XML tag
                val attributes = element.attributes
                while (attributes.hasNext()) {
                    val attribute = attributes.next()
                    val value = attribute.valueAsString

                    // If the attribute contains the original package (e.g., android:authorities="com.Psyonix.RL2D.provider")
                    if (value != null && value.contains(originalPackage)) {
                        attribute.valueAsString = value.replace(originalPackage, newPackage)
                        replacementCount++
                    }
                }

                // Add child elements to the queue for checking
                val childElements = element.elements
                while (childElements.hasNext()) {
                    queue.add(childElements.next())
                }
            }

            if (replacementCount > 0) {
                // Save the modified binary XML back to disk
                xmlDoc.refreshFull()
                xmlDoc.writeBytes(manifestFile)
                onLog("Manifest updated! Replaced $replacementCount package instances.", "success")
                true
            } else {
                onLog("Warning: No instances of $originalPackage found in Manifest.", "warning")
                false
            }
        } catch (e: Exception) {
            onLog("Failed to modify AndroidManifest: ${e.message}", "error")
            false
        }
    }
}

suspend fun repackApk(
    unzippedDir: File,
    outputApk: File,
    onLog: (String, String) -> Unit
): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            if (!unzippedDir.exists() || !unzippedDir.isDirectory) {
                throw Exception("Working directory does not exist.")
            }

            // Clean up any previously failed packing attempts
            if (outputApk.exists()) {
                outputApk.delete()
            }

            onLog("Repacking modified files into APK...", "success")

            val zipFile = ZipFile(outputApk)
            val parameters = ZipParameters().apply {
                isIncludeRootFolder = false
                compressionMethod = CompressionMethod.STORE
            }

            // Compress all files and subdirectories
            zipFile.addFolder(unzippedDir, parameters)

            val sizeMb = outputApk.length() / (1024 * 1024)
            onLog("Successfully repacked APK! Size: $sizeMb MB", "success")

            true
        } catch (e: Exception) {
            onLog("Failed to repack APK: ${e.message}", "error")
            false
        }
    }
}

suspend fun zipalignApk(
    inputApk: File,
    outputApk: File,
    onLog: (String, String) -> Unit
): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            // Clean up any previous output
            if (outputApk.exists()) {
                outputApk.delete()
            }

            onLog("Aligning APK using ARSCLib...", "success")

            // val aligner = ZipAligner()
            // aligner.setDefaultAlignment(4)
            // aligner.setFileAlignment(ZipAligner.PREDICATE_NATIVE_LIBS, 16384) // 16KiB Aligned
            // ZipAlign.align(inputApk, outputApk, aligner)
            ZipAlign.alignApk(inputApk, outputApk) // Default 4KiB aligned

            val sizeMb = outputApk.length() / (1024 * 1024)
            onLog("Zipalign completed natively! Optimized size: $sizeMb MB", "success")

            true
        } catch (e: Exception) {
            onLog("ARSCLib Zipalign failed: ${e.message}", "error")
            false
        }
    }
}

suspend fun signApk(
    context: Context,
    inputApk: File,
    outputApk: File,
    onLog: (String, String) -> Unit
): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            if (outputApk.exists()) {
                outputApk.delete()
            }

            onLog("Loading keystore from assets...", "success")

            // Load the Keystore from the assets folder
            val keyStore = KeyStore.getInstance("PKCS12")
            val password = "kattismod".toCharArray()

            context.assets.open("keystore.p12").use { inputStream ->
                keyStore.load(inputStream, password)
            }

            // Extract the Private Key and Certificate
            val alias = keyStore.aliases().nextElement()
            val privateKey = keyStore.getKey(alias, password) as PrivateKey
            val certificate = keyStore.getCertificate(alias) as X509Certificate

            // Configure the Signer
            val signerConfig = ApkSigner.SignerConfig.Builder(
                "km_signer",
                KeyConfig.Jca(privateKey),
                listOf(certificate)
            ).build()

            onLog("Signing APK with V2/V3 signatures...", "success")

            // Run the apksig process
            val apkSigner = ApkSigner.Builder(listOf(signerConfig))
                .setInputApk(inputApk)
                .setOutputApk(outputApk)
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(true)
                .setV4SigningEnabled(false) // V4 requires an external .idsig file, skip it
                .build()

            apkSigner.sign()

            val sizeMb = outputApk.length() / (1024 * 1024)
            onLog("Successfully signed APK! Final size: $sizeMb MB", "success")

            true
        } catch (e: Exception) {
            onLog("Failed to sign APK: ${e.message}", "error")
            false
        }
    }
}

suspend fun promptInstallApk(
    context: Context,
    apkFile: File,
    onLog: (String, String) -> Unit
) {
    // Switch to the Main thread as package installers often bind to the UI lifecycle
    withContext(Dispatchers.Main) {
        try {
            if (!apkFile.exists()) {
                onLog("Error: Could not find the final APK to install.", "error")
                return@withContext
            }

            onLog("Launching Android Package Installer...", "success")

            // Get the PackageInstaller singleton
            val packageInstaller = PackageInstaller.getInstance(context)

            // Build the InstallParameters using your APK's Uri
            val apkUri = apkFile.toUri()
            val parameters = InstallParameters.Builder(apkUri)
                .setConfirmation(Confirmation.IMMEDIATE)
                .build()

            // Create the installation session with the parameters and await the result
            when (val result = packageInstaller.createSession(parameters).await()) {
                Session.State.Succeeded -> {
                    onLog("Mod installed successfully!", "success")
                }
                is Session.State.Failed -> {
                    onLog("Installation failed or was rejected: ${result.failure.message}", "error")
                }
            }
        } catch (_: CancellationException) {
            onLog("Installation was cancelled.", "warning")
        } catch (e: Exception) {
            onLog("Failed to launch installer: ${e.message}", "error")
        }
    }
}