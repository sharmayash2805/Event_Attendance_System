package com.cu.attendance

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.view.MotionEvent
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.widget.Toast
import android.content.ContentValues
import android.provider.MediaStore
import android.os.Build
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.ImageFormat
import android.graphics.YuvImage
import java.io.ByteArrayOutputStream
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import android.graphics.Rect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.cu.attendance.AttendanceMarkedDialog
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class BarcodeScannerActivity : ComponentActivity() {

    private val selectedEvent: SelectedEvent? by lazy {
        val id = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
        val name = intent.getStringExtra(EXTRA_EVENT_NAME).orEmpty()
        if (id <= 0L || name.isBlank()) null else SelectedEvent(eventId = id, eventName = name)
    }

    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var cameraExecutor: ExecutorService
    
	private val attendanceViewModel: AttendanceViewModel by lazy {
		androidx.lifecycle.ViewModelProvider(this)[AttendanceViewModel::class.java]
	}

    private var barcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .build()
    )

    private var camera: Camera? = null
    private var lastCapturedImageUri: android.net.Uri? = null
    private var isScanning = true
    // Holds the captured image bytes for the currently-detected scan until user confirms
    private var pendingCapturedBytes: ByteArray? = null
    private var pendingCapturedUid: String? = null
    private var onScanListener: ((String) -> Unit)? = null
    private var lastScanTimestamp = 0L
    private var lastStableValue: String? = null
    private var stableDecodeCount = 0
    private var lastHintTimestamp = 0L
    private var lastFocusReset = 0L
    private var consecutiveFailures = 0
    private var lastFailureHint = 0L
    private var lastStableValueResetAt = 0L

    private val cameraPermissionGranted = mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraPermissionGranted.value = granted
        if (!granted) {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ServerConfig.load(this)
        val event = selectedEvent
        if (event == null) {
            Toast.makeText(this, "Select an event first", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        attendanceViewModel.setSelectedEvent(event.eventId, event.eventName)
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            cameraPermissionGranted.value = true
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }

        setContent {
            val lifecycleOwner = LocalLifecycleOwner.current
            val previewView = remember { PreviewView(this) }

            var torchOn by remember { mutableStateOf(true) }
            var serverOnline by remember { mutableStateOf(false) }
            val hasCameraPermission by cameraPermissionGranted

            var showInvalidDialog by remember { mutableStateOf(false) }
            var showAddStudentDialog by remember { mutableStateOf(false) }
            var showAlreadyMarkedDialog by remember { mutableStateOf(false) }
            var showConfirmDialog by remember { mutableStateOf(false) }
            var showMarkedDialog by remember { mutableStateOf(false) }
            var scannedUid by remember { mutableStateOf("") }
            var scannedStudent by remember { mutableStateOf<StudentEntity?>(null) }
            var alreadyMarkedResult by remember { mutableStateOf<AlreadyMarkedResult?>(null) }
            var markedResult by remember { mutableStateOf<AlreadyMarkedResult?>(null) }
			var markedSyncStatus by remember { mutableStateOf("Synced") }

            LaunchedEffect(Unit) {
                while (true) {
                    serverOnline = ScannerHelper.checkServer(this@BarcodeScannerActivity, event.eventId)
                    kotlinx.coroutines.delay(5000)
                }
            }

            LaunchedEffect(torchOn, hasCameraPermission) {
                if (hasCameraPermission && camera?.cameraInfo?.hasFlashUnit() == true) {
                    camera?.cameraControl?.enableTorch(torchOn)
                }
            }

            DisposableEffect(Unit) {
                onScanListener = { uid ->
                    scannedUid = uid

                    ScannerHelper.handleScannedUid(this@BarcodeScannerActivity, event.eventId, uid, object : ScannedCallback {
                        override fun onResult(status: String, student: StudentEntity?) {
                            when (status) {
                                "INVALID" -> {
                                    // clear any pending captured image for this uid
                                    pendingCapturedBytes = null
                                    pendingCapturedUid = null
                                    showInvalidDialog = true
                                }
                                "ALREADY" -> {
                                    alreadyMarkedResult = AlreadyMarkedResult(
                                        uid = student?.uid ?: uid,
                                        name = student?.name ?: "Unknown",
                                        time = student?.timestamp ?: ""
                                    )
                                    // don't keep a pending photo for already marked UID
                                    pendingCapturedBytes = null
                                    pendingCapturedUid = null
                                    showAlreadyMarkedDialog = true
                                }
                                "SUCCESS" -> {
                                    // Show confirmation dialog instead of immediately marking
                                    scannedStudent = student
                                    showConfirmDialog = true
                                }
							"QUEUED" -> {
								// Show confirmation dialog instead of immediately marking
								scannedStudent = student
								showConfirmDialog = true
							}
                                else -> {
                                    Toast.makeText(this@BarcodeScannerActivity, "Error processing scan", Toast.LENGTH_SHORT).show()
                                    resetScanState()
                                }
                            }
                        }
                    })
                }
                onDispose { onScanListener = null }
            }

            DisposableEffect(hasCameraPermission) {
                if (hasCameraPermission) {
                    startCamera(previewView, lifecycleOwner)
                }
                onDispose {
                    try {
                        val provider = ProcessCameraProvider.getInstance(this@BarcodeScannerActivity).get()
                        provider.unbindAll()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error unbinding camera", e)
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )

                ScannerScreen(
                    serverOnline = serverOnline,
                    torchOn = torchOn,
                    onToggleTorch = {
                        val newState = !torchOn
                        torchOn = newState
                        if (camera?.cameraInfo?.hasFlashUnit() == true) {
                            camera?.cameraControl?.enableTorch(newState)
                        }
                    },
                    onClose = { finish() }
                )
            }

            if (showInvalidDialog) {
                InvalidUidDialog(
                    uid = scannedUid,
                    onAddStudent = {
                        showInvalidDialog = false
                        showAddStudentDialog = true
                    },
                    onDismiss = {
                        showInvalidDialog = false
                        isScanning = true
                    }
                )
            }

            if (showAddStudentDialog) {
                AddStudentDialog(
                    uid = scannedUid,
                    onUidChange = { newUid -> scannedUid = newUid },
                    onSubmit = { name, branch, year ->
                        addStudentAndMarkAttendance(scannedUid, name, branch, year)
                        showAddStudentDialog = false
                    },
                    onDismiss = {
                        showAddStudentDialog = false
                        isScanning = true
                    }
                )
            }

            if (showAlreadyMarkedDialog && alreadyMarkedResult != null) {
                AlreadyMarkedDialog(
                    result = alreadyMarkedResult!!,
                    onDismiss = {
                        showAlreadyMarkedDialog = false
                        alreadyMarkedResult = null
                        isScanning = true
                    }
                )
            }

            if (showConfirmDialog && scannedStudent != null) {
                ConfirmAttendanceDialog(
                    uid = scannedStudent!!.uid,
                    name = scannedStudent!!.name,
                    branch = scannedStudent!!.branch ?: "",
                    year = scannedStudent!!.year ?: "",
                    onConfirm = {
                        showConfirmDialog = false
                        // Save pending captured image (if any) then mark attendance
                        savePendingCaptureAndMark(scannedStudent!!)
                    },
                    onCancel = {
                        showConfirmDialog = false
                        scannedStudent = null
                        // clear any pending capture because user cancelled
                        pendingCapturedBytes = null
                        pendingCapturedUid = null
                        isScanning = true
                    }
                )
            }

            if (showMarkedDialog && markedResult != null) {
                AttendanceMarkedDialog(
                    uid = markedResult!!.uid,
                    name = markedResult!!.name,
                    time = markedResult!!.time,
					syncStatus = markedSyncStatus,
                    onDismiss = {
                        showMarkedDialog = false
                        markedResult = null
                        resetScanState()
                    }
                )
            }
        }
    }

    private fun startCamera(previewView: PreviewView, lifecycleOwner: androidx.lifecycle.LifecycleOwner) {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases(cameraProvider, previewView, lifecycleOwner)
            } catch (e: ExecutionException) {
                Log.e(TAG, "Error starting camera", e)
                Toast.makeText(this, "Error starting camera", Toast.LENGTH_SHORT).show()
            } catch (e: InterruptedException) {
                Log.e(TAG, "Error starting camera", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases(
        cameraProvider: ProcessCameraProvider,
        previewView: PreviewView,
        lifecycleOwner: androidx.lifecycle.LifecycleOwner
    ) {
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(1280, 720))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()

        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
            processImageProxy(imageProxy)
        }

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )

            previewView.setOnTouchListener { _, event ->
                if (event.action != MotionEvent.ACTION_UP) return@setOnTouchListener true
                val factory = previewView.meteringPointFactory
                val point = factory.createPoint(event.x, event.y)
                val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                    .setAutoCancelDuration(3, TimeUnit.SECONDS)
                    .build()
                camera?.cameraControl?.startFocusAndMetering(action)
                true
            }

            enableContinuousAfAe()
        } catch (e: Exception) {
            Log.e(TAG, "Use case binding failed", e)
        }
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy) {
        val now = SystemClock.elapsedRealtime()

        if (!isScanning || (now - lastScanTimestamp < SCAN_COOLDOWN_MS)) {
            imageProxy.close()
            return
        }

        maybeRefreshFocus(now)

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        barcodeScanner.process(image)
            .addOnSuccessListener { barcodes ->
                val listener = onScanListener
                if (listener != null) {
                    if (barcodes.isEmpty()) {
                        consecutiveFailures += 1
                        maybeHintHoldSteady(now)
                    }
                    for (barcode in barcodes) {
                        val rawValue = barcode.rawValue
                        val box = barcode.boundingBox
                        if (rawValue.isNullOrEmpty() || box == null) continue

                        if (isBoxTooSmall(box, imageProxy)) {
                            maybeHintMoveCloser(now)
                            continue
                        }

                        // Check if barcode is within the scanning region
                        if (!isBarcodeInScanningRegion(box, imageProxy)) {
                            continue
                        }

                        if (isStableDecode(rawValue)) {
                            // Capture one image bytes for this stable scan and cache it until confirmation
                            try {
                                if (pendingCapturedBytes == null) {
                                    pendingCapturedBytes = imageProxyToJpegByteArray(imageProxy)
                                    pendingCapturedUid = rawValue
                                    Log.i(TAG, "Captured pending proof image for uid=$rawValue, bytes=${pendingCapturedBytes?.size}")
                                } else {
                                    Log.i(TAG, "Pending capture already exists for uid=${pendingCapturedUid}")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error capturing pending image", e)
                                pendingCapturedBytes = null
                                pendingCapturedUid = null
                            }

                            isScanning = false
                            lastScanTimestamp = now
                            consecutiveFailures = 0
                            runOnUiThread { listener(rawValue) }
                            break
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                consecutiveFailures += 1
                maybeHintHoldSteady(SystemClock.elapsedRealtime())
                Log.e(TAG, "Barcode scanning failed", e)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun addStudentAndMarkAttendance(uid: String, name: String, branch: String, year: String) {
		attendanceViewModel.addStudentAndMarkPresent(
            uid = uid,
            name = name,
            branch = branch,
            year = year,
            onSuccess = { student ->
                val msg = "Added & Marked: ${student.name}"
                showResult(msg, true)
            },
            onError = { error ->
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                isScanning = true
            }
        )
    }

    private fun markAttendanceForScannedStudent(student: StudentEntity) {
        attendanceViewModel.markStudentPresent(
            uid = student.uid,
            onSuccess = { markedStudent ->
                val displayName = markedStudent.name.ifBlank { student.uid }
                val msg = "Marked: $displayName"
                runOnUiThread {
                    Toast.makeText(this@BarcodeScannerActivity, msg, Toast.LENGTH_SHORT).show()
                }
                resetScanState()
            },
            onAlreadyMarked = { _ ->
                runOnUiThread {
                    Toast.makeText(this@BarcodeScannerActivity, "Already marked present", Toast.LENGTH_SHORT).show()
                }
                resetScanState()
            },
            onInvalid = {
                runOnUiThread {
                    Toast.makeText(this@BarcodeScannerActivity, "Invalid UID", Toast.LENGTH_SHORT).show()
                }
                isScanning = true
            }
        )
    }

    private fun savePendingCaptureAndMark(student: StudentEntity) {
        val uid = student.uid
        val bytes = pendingCapturedBytes
        val pendingUid = pendingCapturedUid

        // Clear pending capture immediately to avoid duplicate saves
        pendingCapturedBytes = null
        pendingCapturedUid = null

        if (bytes != null && pendingUid == uid) {
            // Save on background executor
            cameraExecutor.execute {
                val uri = saveJpegBytesToMediaStore(bytes, uid)
                if (uri != null) {
                    lastCapturedImageUri = uri
                    Log.i(TAG, "Saved proof image after confirm: $uri")
                } else {
                    Log.w(TAG, "Failed to save proof image after confirm for uid=$uid")
                }

                // Continue with marking on main thread
                runOnUiThread { markAttendanceForScannedStudent(student) }
            }
        } else {
            // No pending image: just mark
            markAttendanceForScannedStudent(student)
        }
    }

    private fun showResult(message: String, success: Boolean) {
        val resultIntent = Intent().apply {
            putExtra("result_message", message)
        }
        setResult(if (success) RESULT_OK else RESULT_CANCELED, resultIntent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        barcodeScanner.close()
    }

    companion object {
        private const val TAG = "BarcodeScannerActivity"
        private const val SCAN_COOLDOWN_MS = 450L
        private const val MIN_BOX_FRACTION = 0.09f
        private const val STABLE_REQUIRED = 1
        private const val HINT_COOLDOWN_MS = 1200L
        private const val FOCUS_RESET_MS = 4000L
        private const val FAILURE_HINT_COOLDOWN_MS = 1500L
		const val EXTRA_EVENT_ID = "extra_event_id"
		const val EXTRA_EVENT_NAME = "extra_event_name"
    }

    private fun enableContinuousAfAe() {
        val factory: MeteringPointFactory = androidx.camera.core.SurfaceOrientedMeteringPointFactory(1f, 1f)
        val center = factory.createPoint(0.5f, 0.5f)
        val action = FocusMeteringAction.Builder(center, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(5, TimeUnit.SECONDS)
            .build()
        camera?.cameraControl?.startFocusAndMetering(action)
        lastFocusReset = SystemClock.elapsedRealtime()
    }

    private fun maybeRefreshFocus(now: Long) {
        if (now - lastFocusReset < FOCUS_RESET_MS) return
        enableContinuousAfAe()
    }

    private fun maybeHintHoldSteady(now: Long) {
        if (now - lastFailureHint < FAILURE_HINT_COOLDOWN_MS) return
        lastFailureHint = now
        runOnUiThread {
            Toast.makeText(this, "Hold steady on the code", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isBoxTooSmall(box: android.graphics.Rect, proxy: ImageProxy): Boolean {
        val frameMin = minOf(proxy.width, proxy.height).toFloat()
        val boxMin = minOf(box.width(), box.height()).toFloat()
        return boxMin < frameMin * MIN_BOX_FRACTION
    }

    private fun isBarcodeInScanningRegion(box: android.graphics.Rect, proxy: ImageProxy): Boolean {
        // Define scanning region: center 340x280 dp region
        // Convert dp to pixels based on screen density
        val density = this.resources.displayMetrics.density
        val regionWidth = (340 * density).toInt()
        val regionHeight = (280 * density).toInt()

        // Calculate center-based scanning region
        val centerX = proxy.width / 2
        val centerY = proxy.height / 2
        val regionLeft = centerX - (regionWidth / 2)
        val regionTop = centerY - (regionHeight / 2)
        val regionRight = centerX + (regionWidth / 2)
        val regionBottom = centerY + (regionHeight / 2)

        // Check if barcode center is within the scanning region
        val barcodeCenter = box.centerX()
        val barcodeCenterY = box.centerY()

        return barcodeCenter >= regionLeft && barcodeCenter <= regionRight &&
               barcodeCenterY >= regionTop && barcodeCenterY <= regionBottom
    }

    private fun isStableDecode(value: String): Boolean {
        return if (value == lastStableValue) {
            stableDecodeCount += 1
            stableDecodeCount >= STABLE_REQUIRED
        } else {
            lastStableValue = value
            stableDecodeCount = 1
            false
        }
    }

    // Convert ImageProxy (YUV_420_888) to JPEG byte array synchronously.
    private fun imageProxyToJpegByteArray(imageProxy: ImageProxy): ByteArray {
        val yBuffer = imageProxy.planes[0].buffer
        val uBuffer = imageProxy.planes[1].buffer
        val vBuffer = imageProxy.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)

        // Try a reasonable interleaving fallback for U and V
        // Many devices have V and U plane ordering; we interleave V then U as NV21 expects VU
        var pos = ySize
        val vBytes = ByteArray(vSize)
        val uBytes = ByteArray(uSize)
        vBuffer.get(vBytes)
        uBuffer.get(uBytes)
        var i = 0
        while (i < vBytes.size && pos < nv21.size) {
            nv21[pos++] = vBytes[i]
            if (pos < nv21.size) {
                nv21[pos++] = if (i < uBytes.size) uBytes[i] else 0
            }
            i++
        }

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 90, out)
        var jpegBytes = out.toByteArray()

        // Rotate if needed
        val rotation = imageProxy.imageInfo.rotationDegrees
        if (rotation != 0) {
            try {
                var bmp = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
                val rotatedOut = ByteArrayOutputStream()
                rotated.compress(Bitmap.CompressFormat.JPEG, 90, rotatedOut)
                jpegBytes = rotatedOut.toByteArray()
                bmp.recycle()
                rotated.recycle()
            } catch (e: Exception) {
                Log.w(TAG, "Rotation failed, saving original jpeg", e)
            }
        }

        return jpegBytes
    }

    // Save JPEG bytes to MediaStore (Pictures/AttendanceProofs). Returns saved Uri or null.
    private fun saveJpegBytesToMediaStore(jpegBytes: ByteArray, uid: String): android.net.Uri? {
        try {
            val fileName = "attendance_${uid}_${System.currentTimeMillis()}.jpg"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AttendanceProofs")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val resolver = contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri == null) return null

            resolver.openOutputStream(uri).use { out ->
                out?.write(jpegBytes)
                out?.flush()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }

            return uri
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied saving image", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving image", e)
        }
        return null
    }

    // Capture and save the current ImageProxy synchronously (call before proxy is closed).
    private fun saveImageProxyToGallerySync(imageProxy: ImageProxy, uid: String): android.net.Uri? {
        return try {
            val jpeg = imageProxyToJpegByteArray(imageProxy)
            saveJpegBytesToMediaStore(jpeg, uid)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to capture/save image", e)
            null
        }
    }

    private fun resetScanState() {
        isScanning = true
        lastStableValue = null
        stableDecodeCount = 0
        lastStableValueResetAt = SystemClock.elapsedRealtime()
    }

    private fun nowString(): String {
        return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
    }

    private fun maybeHintMoveCloser(now: Long) {
        if (now - lastHintTimestamp < HINT_COOLDOWN_MS) return
        lastHintTimestamp = now
        runOnUiThread {
            Toast.makeText(this, "Move closer / steady the code", Toast.LENGTH_SHORT).show()
        }
    }
}
