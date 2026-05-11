package com.codex.appraisalcamera

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ContentValues
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.codex.appraisalcamera.ui.CameraScreen
import com.codex.appraisalcamera.ui.theme.AppraisalCameraTheme
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.HashSet
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Phase D2 — Compose UI 도입.
 *
 * - Activity 는 비즈니스 로직(촬영/사진 관리/익스포트/공유/SharedPreferences) 보유.
 * - UI 는 [setContent] 로 [CameraScreen] Composable 이 그린다.
 * - UI 가 관찰해야 할 상태는 모두 [mutableStateOf] / [mutableStateListOf].
 * - 다이얼로그(설정/도움말/저장형식/공유형식/모드/물건지/사진목록 등)는 D2 에선
 *   기존 AlertDialog.Builder 그대로. D3 에서 Compose 로 전환.
 */
class MainActivity : ComponentActivity() {

    // ---- UI-driving observable state (Compose 가 읽음) ----
    var currentCategory by mutableStateOf(CATEGORY_LAND)
    var currentSymbol by mutableStateOf("")
    var currentBuildingSub by mutableStateOf("")
    var currentMemo by mutableStateOf("")
    var propertyAddress by mutableStateOf("")
    var appMode by mutableStateOf(MODE_SELF_APPRAISAL)
    var debtorName by mutableStateOf("")
    var fieldSurveyor by mutableStateOf("")
    val photos: SnapshotStateList<PhotoItem> = mutableStateListOf()

    // 설정/메일 — Settings 시트가 즉시 반영하도록 mutableStateOf.
    var emailRecipient by mutableStateOf("")
    var mailAppPref by mutableStateOf(MAIL_APP_OTHER)
    var guideAlphaPercent by mutableStateOf(82)
    var guideScalePercent by mutableStateOf(78)
    var floatingCaptureButton by mutableStateOf(true)

    /** 현재 열려 있는 시트/다이얼로그. CameraScreen 이 관찰. */
    var openSheet by mutableStateOf<AppSheet>(AppSheet.None)

    /**
     * 사진 자료 익스포트 시 카테고리 정렬 순서. SettingsSheet 에서 사용자가 조정.
     * CATEGORY_FIELD 는 항상 마지막. 초기값은 land/building/extra/custom 순.
     */
    var categoryOrder by mutableStateOf<List<String>>(DEFAULT_CATEGORY_ORDER)

    /** 사진자료 한 페이지에 들어갈 사진 수. 짝수 (2/4/6) 만 허용, 기본 4. */
    var photosPerPage by mutableStateOf(DEFAULT_PHOTOS_PER_PAGE)

    // ---- Non-UI state ----
    private var imageCapture: ImageCapture? = null
    private var pendingExportBytes: ByteArray? = null
    private var pendingExportLabel: String = ""

    // ActivityResult launchers
    private lateinit var pickImageLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var createOutputLauncher: ActivityResultLauncher<Intent>
    private lateinit var cameraPermissionLauncher: ActivityResultLauncher<String>

    // 백그라운드 익스포트
    private val exportExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var exportInProgress: Boolean = false

    // ---- Lifecycle ----

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        registerActivityResultLaunchers()
        loadPhotos()
        loadAppMode()
        loadPropertyAddress()
        loadFieldSurveyInfo()
        loadGuideSettings()
        loadEmailRecipient()
        loadMailApp()
        loadCategoryOrder()
        loadPhotosPerPage()
        restoreControlState(savedInstanceState)

        setContent {
            AppraisalCameraTheme {
                CameraScreen(activity = this)
            }
        }

        requestCameraPreview()
    }

    override fun onPause() {
        super.onPause()
        saveControlState()
    }

    override fun onDestroy() {
        super.onDestroy()
        exportExecutor.shutdownNow()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_CURRENT_CATEGORY, currentCategory)
        outState.putString(STATE_CURRENT_SYMBOL, currentSymbol)
        outState.putString(STATE_CURRENT_BUILDING_SUB, currentBuildingSub)
        outState.putString(STATE_CURRENT_MEMO, currentMemo)
        super.onSaveInstanceState(outState)
    }

    // ---- Activity result launchers ----

    private fun registerActivityResultLaunchers() {
        pickImageLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri == null) return@registerForActivityResult
            try {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // 일부 갤러리 앱은 임시 접근만 부여.
            }
            addPhoto(uri.toString())
        }

        createOutputLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode != RESULT_OK) {
                pendingExportBytes = null
                pendingExportLabel = ""
                return@registerForActivityResult
            }
            val data = result.data
            data?.data?.let { writePendingExport(it) }
        }

        cameraPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                startCameraPreview()
            } else {
                Toast.makeText(this, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ---- Camera ----

    /**
     * Compose 의 AndroidView 안에서 PreviewView 가 만들어지면 호출되도록.
     * Activity 가 PreviewView 를 잡아두고 binding 한다.
     */
    fun bindCameraPreview(previewView: PreviewView) {
        cameraPreviewView = previewView
        requestCameraPreview()
    }

    private var cameraPreviewView: PreviewView? = null

    private fun requestCameraPreview() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }
        startCameraPreview()
    }

    private fun startCameraPreview() {
        val previewView = cameraPreviewView ?: return
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build()
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    // 일반 화질 — 사진자료용으로 충분하고 파일도 작아 PPTX/PDF 가
                    // 더 가벼워진다. 후처리 압축이 디바이스마다 실패하던 이슈도 해결.
                    .setJpegQuality(60)
                    .build()
                preview.surfaceProvider = previewView.surfaceProvider

                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                imageCapture = capture
            } catch (_: Exception) {
                Toast.makeText(this, "카메라 시작에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    fun pickImage() {
        pickImageLauncher.launch(arrayOf("image/*"))
    }

    fun capturePhoto() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }

        val capture = imageCapture
        if (capture == null) {
            Toast.makeText(this, "카메라 준비 중입니다. 잠시 후 다시 촬영하세요.", Toast.LENGTH_SHORT).show()
            startCameraPreview()
            return
        }

        val capturedAt = System.currentTimeMillis()
        val outputFile = try {
            createAppImageFile(capturedAt)
        } catch (e: IOException) {
            Toast.makeText(this, "앱 내부 사진 저장 폴더를 만들 수 없습니다.", Toast.LENGTH_LONG).show()
            return
        }

        val options = ImageCapture.OutputFileOptions.Builder(outputFile).build()
        capture.takePicture(options, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                val savedUri = outputFileResults.savedUri ?: Uri.fromFile(outputFile)
                // 후처리 압축 제거 — JpegQuality 60 으로 직접 저장. 디바이스마다
                // 실패하던 "사진 용량 줄이기 실패" 토스트도 사라진다.
                addPhoto(savedUri.toString(), capturedAt, false)
                // 갤러리(MediaStore)에도 사본 저장 — 백그라운드, 실패해도 무시.
                exportExecutor.submit { savePhotoToGallery(outputFile, capturedAt) }
            }

            override fun onError(exception: ImageCaptureException) {
                Toast.makeText(this@MainActivity, "촬영 저장에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
        })
    }

    /**
     * 촬영 직후 사진을 갤러리(MediaStore)에 사본 저장.
     * 사진자료 익스포트와 똑같이 "촬영: yyyy.MM.dd HH:mm" 워터마크를 우측 하단에 굽는다.
     * 앱 내부 원본은 그대로 두고(익스포트 시 워터마크는 재계산), 갤러리 사본만 별도 워터마크 베이크.
     * minSdk 29 이므로 MediaStore Q+ API 사용 — WRITE_EXTERNAL_STORAGE 권한 불필요.
     * 실패해도 촬영 자체에는 영향 없음 (Toast 등 표시하지 않음).
     */
    private fun savePhotoToGallery(sourceFile: File, capturedAt: Long) {
        try {
            val displayName = "appraisal_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.KOREA).format(Date(capturedAt))}.jpg"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/AppraisalCamera")
                put(MediaStore.Images.Media.DATE_TAKEN, capturedAt)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val galleryUri = contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
            ) ?: return

            var bitmap: Bitmap? = null
            var oriented: Bitmap? = null
            var stamped: Bitmap? = null
            try {
                // 워터마크용 적당한 크기로 디코드 (메모리 절약).
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                FileInputStream(sourceFile).use { BitmapFactory.decodeStream(it, null, bounds) }
                val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
                var sample = 1
                while (maxSide / sample > 2400) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                bitmap = FileInputStream(sourceFile).use {
                    BitmapFactory.decodeStream(it, null, opts)
                } ?: throw IOException("decode failed")

                oriented = rotateBitmap(bitmap, readExifOrientation(Uri.fromFile(sourceFile)))
                stamped = drawTimestampWatermark(oriented, "촬영: ${formatDate(capturedAt)}")

                contentResolver.openOutputStream(galleryUri)?.use { output ->
                    stamped.compress(Bitmap.CompressFormat.JPEG, 85, output)
                } ?: throw IOException("Cannot open gallery output stream")

                val ready = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                contentResolver.update(galleryUri, ready, null, null)
            } catch (e: Exception) {
                contentResolver.delete(galleryUri, null, null)
                throw e
            } finally {
                if (stamped != null && stamped !== oriented) stamped.recycle()
                if (oriented != null && oriented !== bitmap) oriented.recycle()
                bitmap?.recycle()
            }
        } catch (_: Exception) {
            // 촬영은 이미 성공했으므로 갤러리 저장 실패는 조용히 무시.
        }
    }

    /**
     * 사진 우측 하단에 반투명 검은 박스 + 흰 글씨로 촬영 시각을 굽는다.
     * 익스포트의 drawOutputStamp / PptxExporter.stampBox 와 시각적 일관성 유지.
     */
    private fun drawTimestampWatermark(source: Bitmap, text: String): Bitmap {
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)

        val textSize = maxOf(36f, output.width * 0.025f)
        val padX = textSize * 0.55f
        val padY = textSize * 0.32f
        val margin = textSize * 0.55f

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
        textPaint.color = Color.WHITE
        textPaint.textSize = textSize
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        val bounds = Rect()
        textPaint.getTextBounds(text, 0, text.length, bounds)
        val metrics = textPaint.fontMetrics

        val boxW = bounds.width() + padX * 2f
        val boxH = (metrics.descent - metrics.ascent) + padY * 2f
        val left = output.width - boxW - margin
        val top = output.height - boxH - margin

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        bgPaint.color = Color.argb(175, 0, 0, 0)
        canvas.drawRect(RectF(left, top, left + boxW, top + boxH), bgPaint)
        canvas.drawText(text, left + padX, top + padY - metrics.ascent, textPaint)
        return output
    }

    @Throws(IOException::class)
    private fun createAppImageFile(capturedAt: Long): File {
        var picturesRoot = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        if (picturesRoot == null) {
            picturesRoot = File(filesDir, "Pictures")
        }
        val addressDir = File(File(picturesRoot, "AppraisalCamera"), propertyFolderName())
        if (!addressDir.exists() && !addressDir.mkdirs()) {
            throw IOException("Cannot create app photo directory")
        }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.KOREA).format(Date(capturedAt))
        return File(addressDir, "appraisal_$timestamp.jpg")
    }

    private fun propertyFolderName(): String {
        var name = documentHeaderText()
        if (modeDefaultTitle() == name) name = "미지정_물건지"
        name = name.replace(Regex("[\\\\/:*?\"<>|\\r\\n]+"), "_").trim()
        if (name.isEmpty()) return "미지정_물건지"
        return if (name.length > 80) name.substring(0, 80) else name
    }

    // ---- Image processing ----

    private fun compressSavedImage(uri: Uri, capturedAt: Long) {
        var bitmap: Bitmap? = null
        var oriented: Bitmap? = null
        try {
            bitmap = decodeBitmap(uri, 1800)
            if (bitmap == null) throw IOException("Cannot decode captured image")
            oriented = rotateBitmap(bitmap, readExifOrientation(uri))
            openImageOutputStream(uri)?.use { output ->
                oriented.compress(Bitmap.CompressFormat.JPEG, 78, output)
            } ?: throw IOException("Cannot open captured image for writing")
            preserveExifDateTime(uri, capturedAt)
        } catch (_: IOException) {
            Toast.makeText(this, "사진 용량 줄이기에 실패했습니다.", Toast.LENGTH_SHORT).show()
        } finally {
            if (oriented != null && oriented !== bitmap) oriented.recycle()
            bitmap?.recycle()
        }
    }

    private fun preserveExifDateTime(uri: Uri, capturedAt: Long) {
        if (uri.scheme != "file") return
        val file = fileFromUri(uri) ?: return
        try {
            val exif = ExifInterface(file.absolutePath)
            val exifTime = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(Date(capturedAt))
            exif.setAttribute(ExifInterface.TAG_DATETIME, exifTime)
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, exifTime)
            exif.saveAttributes()
        } catch (_: IOException) {
            // EXIF 보존 실패는 사진 사용에 영향 없으므로 조용히 무시.
        }
    }

    @Throws(IOException::class)
    private fun openImageInputStream(uri: Uri): InputStream? {
        if (uri.scheme == "file") {
            val file = fileFromUri(uri) ?: return null
            return FileInputStream(file)
        }
        return contentResolver.openInputStream(uri)
    }

    @Throws(IOException::class)
    private fun openImageOutputStream(uri: Uri): OutputStream? {
        if (uri.scheme == "file") {
            val file = fileFromUri(uri) ?: return null
            return FileOutputStream(file)
        }
        return contentResolver.openOutputStream(uri, "w")
    }

    private fun fileFromUri(uri: Uri): File? {
        if (uri.scheme != "file") return null
        val candidates = arrayListOf<String>()
        uri.path?.let { candidates.add(it) }
        uri.encodedPath?.let { candidates.add(Uri.decode(it)) }
        for (path in candidates.distinct()) {
            val file = File(path)
            if (file.exists()) return file
        }
        return candidates.firstOrNull()?.let { File(it) }
    }

    @Throws(IOException::class)
    private fun decodeBitmap(uri: Uri, maxSideLimit: Int): Bitmap? {
        if (uri.scheme == "file") {
            val file = fileFromUri(uri)
            if (file != null) {
                decodeFileBitmap(file, maxSideLimit)?.let { return it }
            }
        }

        return decodeStreamBitmap(uri, maxSideLimit)
    }

    private fun decodeFileBitmap(file: File, maxSideLimit: Int): Bitmap? {
        if (!file.exists() || file.length() <= 0L) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            FileInputStream(file).use { input ->
                BitmapFactory.decodeFileDescriptor(input.fd, null, bounds)
            }
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (maxSide / sample > maxSideLimit) sample *= 2

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.absolutePath, options) ?: FileInputStream(file).use { input ->
            BitmapFactory.decodeFileDescriptor(input.fd, null, options)
        }
    }

    @Throws(IOException::class)
    private fun decodeStreamBitmap(uri: Uri, maxSideLimit: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openImageInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (maxSide / sample > maxSideLimit) sample *= 2

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        openImageInputStream(uri)?.use { input ->
            return BitmapFactory.decodeStream(input, null, options)
        }
        return null
    }

    private fun readExifOrientation(uri: Uri): Int {
        try {
            if (uri.scheme == "file") {
                val file = fileFromUri(uri) ?: return ExifInterface.ORIENTATION_NORMAL
                val exif = ExifInterface(file.absolutePath)
                return exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
            openImageInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                return exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        } catch (_: IOException) {
        }
        return ExifInterface.ORIENTATION_NORMAL
    }

    private fun rotateBitmap(source: Bitmap, orientation: Int): Bitmap {
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> return source
        }
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    // ---- Photo lifecycle ----

    @JvmOverloads
    fun addPhoto(uri: String, createdAt: Long = System.currentTimeMillis(), stamped: Boolean = false) {
        val symbol = selectedSymbol()
        val item = PhotoItem(
            id = UUID.randomUUID().toString(),
            category = if (isFieldSurveyMode()) CATEGORY_FIELD else currentCategory,
            symbol = symbol,
            memo = currentMemo.trim(),
            uri = uri,
            createdAt = createdAt,
            stamped = stamped,
            debtorName = if (isFieldSurveyMode()) debtorName.trim() else "",
            fieldSurveyor = if (isFieldSurveyMode()) fieldSurveyor.trim() else ""
        )
        photos.add(item)

        currentMemo = ""
        if (currentCategory != CATEGORY_CUSTOM && !isFieldSurveyMode()) {
            currentSymbol = ""
        }
        currentBuildingSub = ""
        savePhotos()
        saveControlState()
        Toast.makeText(this, "${photoTitle(item)} 등록", Toast.LENGTH_SHORT).show()
    }

    fun deletePhoto(item: PhotoItem) {
        photos.remove(item)
        deleteAppPhotoFile(item)
        savePhotos()
    }

    fun confirmClear() {
        if (photos.isEmpty()) return
        openSheet = AppSheet.ConfirmClear
    }

    /** ConfirmClear 시트의 확인 콜백. */
    fun applyClearAll() {
        photos.toList().forEach { deleteAppPhotoFile(it) }
        photos.clear()
        savePhotos()
    }

    /** PhotoList 시트에서 개별 사진 삭제 확인 인텐트. */
    fun confirmDeletePhoto(item: PhotoItem) {
        openSheet = AppSheet.ConfirmDelete(item)
    }

    fun sortedPhotos(): List<PhotoItem> {
        return photos.sortedWith(compareBy({ categoryRank(it.category) }, { symbolRank(it) }, { it.createdAt }))
    }

    private fun categoryRank(category: String): Int {
        // 사용자 설정 categoryOrder 를 우선 적용. CATEGORY_FIELD 는 항상 마지막.
        val idx = categoryOrder.indexOf(category)
        if (idx >= 0) return idx
        if (category == CATEGORY_FIELD) return categoryOrder.size
        return 99
    }

    // ---- Category order (사진 자료 정렬 순서) ----

    private fun loadCategoryOrder() {
        val csv = prefs().getString(PREF_CATEGORY_ORDER, null) ?: return
        val parts = csv.split(",").filter { it in DEFAULT_CATEGORY_ORDER }
        // 파싱이 정확히 4개여야 인정 — 누락된 카테고리가 있으면 기본값 유지.
        if (parts.size == DEFAULT_CATEGORY_ORDER.size && parts.toSet() == DEFAULT_CATEGORY_ORDER.toSet()) {
            categoryOrder = parts
        }
    }

    private fun saveCategoryOrder() {
        prefs().edit().putString(PREF_CATEGORY_ORDER, categoryOrder.joinToString(",")).apply()
    }

    fun moveCategoryUp(category: String) {
        val idx = categoryOrder.indexOf(category)
        if (idx <= 0) return
        val list = categoryOrder.toMutableList()
        list.removeAt(idx)
        list.add(idx - 1, category)
        categoryOrder = list
        saveCategoryOrder()
    }

    fun moveCategoryDown(category: String) {
        val idx = categoryOrder.indexOf(category)
        if (idx < 0 || idx >= categoryOrder.size - 1) return
        val list = categoryOrder.toMutableList()
        list.removeAt(idx)
        list.add(idx + 1, category)
        categoryOrder = list
        saveCategoryOrder()
    }

    fun resetCategoryOrder() {
        categoryOrder = DEFAULT_CATEGORY_ORDER
        saveCategoryOrder()
    }

    // ---- Photos per page ----

    private fun loadPhotosPerPage() {
        val saved = prefs().getInt(PREF_PHOTOS_PER_PAGE, DEFAULT_PHOTOS_PER_PAGE)
        photosPerPage = if (saved in ALLOWED_PHOTOS_PER_PAGE) saved else DEFAULT_PHOTOS_PER_PAGE
    }

    private fun savePhotosPerPage() {
        prefs().edit().putInt(PREF_PHOTOS_PER_PAGE, photosPerPage).apply()
    }

    fun applyPhotosPerPage(value: Int) {
        if (value !in ALLOWED_PHOTOS_PER_PAGE) return
        photosPerPage = value
        savePhotosPerPage()
    }

    private fun symbolRank(photo: PhotoItem): Int {
        if (photo.category == CATEGORY_FIELD || photo.category == CATEGORY_LAND) {
            return photo.symbol.toIntOrNull() ?: 9999
        }
        if (photo.category == CATEGORY_BUILDING) {
            val parts = photo.symbol.split("-")
            val baseRank = indexOf(BUILDING_SYMBOLS, parts[0])
            val subRank = if (parts.size > 1) parts[1].toIntOrNull() ?: 99 else 0
            return (if (baseRank < 0) 99 else baseRank) * 100 + subRank
        }
        if (photo.category == CATEGORY_EXTRA) {
            return indexOfOrDefault(EXTRA_SYMBOLS, photo.symbol, 9999)
        }
        return 0
    }

    private fun deleteAppPhotoFile(photo: PhotoItem) {
        try {
            val uri = Uri.parse(photo.uri)
            val file = fileFromUri(uri) ?: return
            val filePath = file.canonicalPath
            val externalRoot = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            val internalRoot = File(filesDir, "Pictures")
            val appOwned = startsWithRoot(filePath, externalRoot) || startsWithRoot(filePath, internalRoot)
            if (appOwned && file.exists()) file.delete()
        } catch (_: IOException) {
        }
    }

    @Throws(IOException::class)
    private fun startsWithRoot(filePath: String, root: File?): Boolean {
        if (root == null) return false
        val rootPath = root.canonicalPath
        return filePath == rootPath || filePath.startsWith(rootPath + File.separator)
    }

    // ---- Symbol selection logic ----

    fun selectedSymbol(): String {
        if (isFieldSurveyMode()) return nextFieldPhotoNumber().toString()
        if (currentCategory == CATEGORY_CUSTOM) {
            return if (currentSymbol.isEmpty()) "기타사항" else currentSymbol
        }
        val base = if (currentSymbol.isEmpty()) nextSymbol(currentCategory).base else currentSymbol
        if (currentCategory != CATEGORY_BUILDING) return base
        return if (currentBuildingSub.isEmpty()) base else base + currentBuildingSub
    }

    private fun nextFieldPhotoNumber(): Int {
        var max = 0
        for (photo in photos) {
            if (photo.category == CATEGORY_FIELD) {
                max = maxOf(max, photo.symbol.toIntOrNull() ?: (max + 1))
            }
        }
        return max + 1
    }

    fun nextSymbol(category: String): NextSymbol {
        val used = HashSet<String>()
        for (photo in photos) if (photo.category == category) used.add(photo.symbol)

        if (category == CATEGORY_BUILDING) {
            for (s in BUILDING_SYMBOLS) if (!used.contains(s)) return NextSymbol(s, "")
            return NextSymbol(BUILDING_SYMBOLS.last(), "1")
        }
        for (s in symbolsForCategory(category)) if (!used.contains(s)) return NextSymbol(s, "")
        return NextSymbol(symbolsForCategory(category).last(), "")
    }

    fun symbolsForCategory(category: String): Array<String> = when (category) {
        CATEGORY_LAND -> LAND_SYMBOLS
        CATEGORY_BUILDING -> BUILDING_SYMBOLS
        else -> EXTRA_SYMBOLS
    }

    // ---- Mode / display helpers ----

    fun isFieldSurveyMode(): Boolean = appMode == MODE_FIELD_SURVEY

    fun modeLabel(): String = if (isFieldSurveyMode()) "현지답사" else "자체감정"

    fun modeDefaultTitle(): String = "${modeLabel()} 사진"

    fun documentHeaderText(): String {
        return if (propertyAddress.trim().isEmpty()) modeDefaultTitle() else propertyAddress.trim()
    }

    fun categoryLabel(category: String): String = when (category) {
        CATEGORY_LAND -> "토지"
        CATEGORY_BUILDING -> "건물"
        CATEGORY_CUSTOM -> "기타"
        CATEGORY_FIELD -> "현지답사"
        else -> "제시외건물"
    }

    private fun formatDate(time: Long): String =
        SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA).format(Date(time))

    fun displayPhotoStamp(photo: PhotoItem): String = "촬영: ${formatDate(photo.createdAt)}"

    fun photoMetaText(photo: PhotoItem): String {
        val details = fieldPhotoDetails(photo)
        if (photo.memo.isEmpty()) return if (details.isEmpty()) formatDate(photo.createdAt) else details
        return if (details.isEmpty()) photo.memo else "${photo.memo} / $details"
    }

    fun photoCaption(photo: PhotoItem): String {
        if (photo.category == CATEGORY_FIELD) {
            val details = fieldPhotoDetails(photo)
            if (photo.memo.isNotEmpty() && details.isNotEmpty()) return "${photo.memo} / $details"
            if (photo.memo.isNotEmpty()) return photo.memo
            if (details.isNotEmpty()) return details
        }
        if (photo.memo.isNotEmpty()) return photo.memo
        return photoTitle(photo)
    }

    fun photoTitle(photo: PhotoItem): String {
        if (photo.category == CATEGORY_FIELD) return "현지답사 사진 ${photo.symbol}"
        if (photo.category == CATEGORY_CUSTOM) return "기타사항: ${photo.symbol}"
        return "${categoryLabel(photo.category)} 기호 ${photo.symbol}"
    }

    fun fieldPhotoDetails(photo: PhotoItem): String {
        if (photo.category != CATEGORY_FIELD) return ""
        val parts = mutableListOf<String>()
        if (photo.debtorName.isNotBlank()) parts.add("채무자: ${photo.debtorName.trim()}")
        if (photo.fieldSurveyor.isNotBlank()) parts.add("답사자: ${photo.fieldSurveyor.trim()}")
        return parts.joinToString(" / ")
    }

    // ---- 시트 / 다이얼로그 인텐트 (실제 UI 는 Compose AppSheets) ----

    fun showModeDialog() {
        openSheet = AppSheet.Mode
    }

    fun showAddressDialog() {
        openSheet = AppSheet.Address
    }

    fun showEmailDialog() {
        if (photos.isEmpty()) {
            Toast.makeText(this, "공유할 사진이 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        val shareMode = mailAppPref
        val savedRecipient = emailRecipient
        if (shareMode == MAIL_APP_OTHER || savedRecipient.isNotEmpty()) {
            openSheet = AppSheet.ShareFormat(savedRecipient)
            return
        }
        openSheet = AppSheet.EmailRecipient
    }

    fun showEmailAddressSheet() {
        openSheet = AppSheet.EmailRecipient
    }

    fun showMailAppSheet() {
        openSheet = AppSheet.MailApp
    }

    fun showGuideSettingsDialog() {
        openSheet = AppSheet.Settings
    }

    fun showHelpDialog() {
        openSheet = AppSheet.Help
    }

    fun showPhotoListDialog() {
        if (photos.isEmpty()) {
            Toast.makeText(this, "등록된 사진이 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        openSheet = AppSheet.PhotoList
    }

    fun showExportFormatDialog() {
        if (photos.isEmpty()) {
            Toast.makeText(this, "저장할 사진이 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        openSheet = AppSheet.ExportFormat
    }

    fun closeSheet() {
        openSheet = AppSheet.None
    }

    /** 시트 onConfirm 콜백에서 호출. */
    fun applyAddress(newAddress: String) {
        propertyAddress = newAddress.trim()
        savePropertyAddress()
        Toast.makeText(this, "물건지 주소가 저장되었습니다.", Toast.LENGTH_SHORT).show()
    }

    fun applyMode(newMode: String) {
        if (appMode == newMode) return
        appMode = newMode
        saveAppMode()
        val label = if (newMode == MODE_FIELD_SURVEY) "현지답사" else "자체감정"
        Toast.makeText(this, "$label 모드로 변경했습니다.", Toast.LENGTH_SHORT).show()
    }

    fun applyEmailRecipient(recipient: String): Boolean {
        if (!recipient.contains("@")) {
            Toast.makeText(this, "메일주소를 확인해주세요.", Toast.LENGTH_LONG).show()
            return false
        }
        saveEmailRecipient(recipient.trim())
        Toast.makeText(this, "기본 메일주소가 저장되었습니다.", Toast.LENGTH_SHORT).show()
        return true
    }

    fun applyMailApp(value: String) {
        saveMailApp(value)
        val label = if (value == MAIL_APP_GMAIL) "Gmail" else "Other"
        Toast.makeText(this, "${label}로 설정했습니다.", Toast.LENGTH_SHORT).show()
    }

    fun applyGuideAlpha(percent: Int) {
        guideAlphaPercent = percent.coerceIn(35, 100)
        saveGuideSettings()
    }

    fun applyGuideScale(percent: Int) {
        guideScalePercent = percent.coerceIn(60, 100)
        saveGuideSettings()
    }

    fun resetGuideDefaults() {
        guideAlphaPercent = 82
        guideScalePercent = 78
        floatingCaptureButton = true
        saveGuideSettings()
    }

    fun toggleFloatingCaptureButton() {
        floatingCaptureButton = !floatingCaptureButton
        saveGuideSettings()
    }

    fun startExport(format: String) {
        exportOutput(format)
    }

    fun startShare(recipient: String, format: String) {
        shareOutput(recipient, format)
    }

    // ---- Export (background) ----

    private fun exportOutput(format: String) {
        if (exportInProgress) {
            Toast.makeText(this, "이미 사진자료를 만드는 중입니다.", Toast.LENGTH_SHORT).show()
            return
        }
        val totalPhotos = photos.size
        exportInProgress = true
        // 진행 시트를 즉시 띄움. JPG/PDF 는 페이지마다 갱신, PPTX 는 indeterminate(0/0).
        openSheet = AppSheet.ExportProgress(format = format, current = 0, total = totalPhotos)

        exportExecutor.submit {
            try {
                if (format == FORMAT_JPG) {
                    val jpgFiles = createJpgFiles { done, total ->
                        runOnUiSafely { openSheet = AppSheet.ExportProgress(format, done, total) }
                    }
                    writePublicFiles(jpgFiles)
                    runOnUiSafely {
                        exportInProgress = false
                        closeSheet()
                        Toast.makeText(this, "JPG 사진자료를 저장했습니다.", Toast.LENGTH_LONG).show()
                    }
                    return@submit
                }
                val bytes = if (format == FORMAT_PDF) {
                    createPdfBytes { done, total ->
                        runOnUiSafely { openSheet = AppSheet.ExportProgress(format, done, total) }
                    }
                } else {
                    createPptxBytes()
                }
                runOnUiSafely {
                    exportInProgress = false
                    closeSheet()
                    pendingExportBytes = bytes
                    pendingExportLabel = formatLabel(format)
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = mimeForFormat(format)
                        putExtra(Intent.EXTRA_TITLE, outputFileName(format))
                    }
                    createOutputLauncher.launch(intent)
                }
            } catch (e: Exception) {
                runOnUiSafely {
                    exportInProgress = false
                    closeSheet()
                    Toast.makeText(
                        this,
                        "${formatLabel(format)} 파일을 만들 수 없습니다: ${e.message ?: "알 수 없는 오류"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun runOnUiSafely(block: () -> Unit) {
        mainHandler.post {
            if (isFinishing || isDestroyed) return@post
            block()
        }
    }

    /**
     * 사진 URI 가 최소한 열릴 수 있는지 가벼운 체크.
     * 너무 엄격하면 디바이스마다 거짓 양성으로 사진이 모두 누락될 수 있어
     * file:// 는 파일 존재 + 크기만, content:// 는 InputStream open 만 검사.
     */
    private fun canReadPhoto(uri: String): Boolean {
        return try {
            val parsed = Uri.parse(uri)
            if (parsed.scheme == "file") {
                val file = fileFromUri(parsed) ?: return false
                return file.exists() && file.length() > 0L
            }
            // content:// : 스트림이 한 번 열리면 OK.
            openImageInputStream(parsed)?.use { it.read() }
            true
        } catch (_: Exception) {
            false
        }
    }

    @Throws(IOException::class)
    private fun createPptxBytes(): ByteArray {
        val exportPhotos = ArrayList<PptxExporter.PhotoData>()
        for (photo in sortedPhotos()) {
            // canReadPhoto 는 가벼운 1차 필터 — 명백히 깨진 것만 거른다.
            // PptxExporter 내부에서 디코드 실패한 사진도 자동 skip.
            if (!canReadPhoto(photo.uri)) continue
            exportPhotos.add(
                PptxExporter.PhotoData(Uri.parse(photo.uri), photoCaption(photo), displayPhotoStamp(photo))
            )
        }
        if (exportPhotos.isEmpty()) {
            throw IOException("저장할 수 있는 사진이 없습니다")
        }
        val result = PptxExporter.createWithStats(this, exportPhotos, documentHeaderText(), photosPerPage)
        if (result.skipped > 0) {
            runOnUiSafely {
                Toast.makeText(this, "${result.skipped}장 누락 (디코드 실패)", Toast.LENGTH_LONG).show()
            }
        }
        return result.bytes
    }

    @Throws(IOException::class)
    private fun createPdfBytes(onProgress: (Int, Int) -> Unit = { _, _ -> }): ByteArray {
        val sorted = sortedPhotos().filter { canReadPhoto(it.uri) }
        if (sorted.isEmpty()) throw IOException("저장할 수 있는 사진이 없습니다")
        val perPage = photosPerPage
        val totalPages = (sorted.size + perPage - 1) / perPage
        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        var totalDrawn = 0
        try {
            var i = 0
            while (i < sorted.size) {
                val pageNumber = (i / perPage) + 1
                onProgress(pageNumber, totalPages)
                val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                val page = document.startPage(info)
                totalDrawn += drawOutputPage(page.canvas, pageWidth, pageHeight, sorted, i, pageNumber, perPage)
                document.finishPage(page)
                i += perPage
            }
            val output = ByteArrayOutputStream()
            document.writeTo(output)
            val skipped = sorted.size - totalDrawn
            if (skipped > 0) {
                runOnUiSafely {
                    Toast.makeText(this, "PDF: ${skipped}장이 디코드 실패로 누락됨", Toast.LENGTH_LONG).show()
                }
            }
            return output.toByteArray()
        } finally {
            document.close()
        }
    }

    @Throws(IOException::class)
    private fun createJpgFiles(onProgress: (Int, Int) -> Unit = { _, _ -> }): ArrayList<ExportFile> {
        val sorted = sortedPhotos().filter { canReadPhoto(it.uri) }
        if (sorted.isEmpty()) throw IOException("저장할 수 있는 사진이 없습니다")
        val perPage = photosPerPage
        val totalPages = (sorted.size + perPage - 1) / perPage
        val files = ArrayList<ExportFile>()
        val pageWidth = 1240
        val pageHeight = 1754
        var totalDrawn = 0
        var i = 0
        while (i < sorted.size) {
            val pageNumber = (i / perPage) + 1
            onProgress(pageNumber, totalPages)
            val bitmap = Bitmap.createBitmap(pageWidth, pageHeight, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bitmap)
                totalDrawn += drawOutputPage(canvas, pageWidth, pageHeight, sorted, i, pageNumber, perPage)
                val output = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)
                files.add(ExportFile(pageJpgFileName(pageNumber), JPG_MIME, output.toByteArray()))
            } finally {
                bitmap.recycle()
            }
            i += perPage
        }
        val skipped = sorted.size - totalDrawn
        if (skipped > 0) {
            runOnUiSafely {
                Toast.makeText(this, "JPG: ${skipped}장이 디코드 실패로 누락됨", Toast.LENGTH_LONG).show()
            }
        }
        return files
    }

    /** 페이지 내 사진 슬롯 (frame + caption baseline). */
    private data class PageSlot(val frame: RectF, val captionBaselineY: Float)

    /**
     * 595x842 (A4 portrait) 비율로 페이지의 사진 슬롯을 계산.
     * count 는 2/4/6 만 지원 (짝수). 사진 수가 부족한 페이지에서도 동일 그리드 사용,
     * 빈 슬롯은 호출 측에서 그리지 않음.
     */
    private fun computeSlots(pageWidth: Int, pageHeight: Int, count: Int): List<PageSlot> {
        val sx = pageWidth / 595f
        val sy = pageHeight / 842f
        val sideMargin = 50f * sx
        val topMargin = 120f * sy
        val bottomMargin = 30f * sy
        val gap = 12f * sy
        val captionHeight = 28f * sy
        val availWidth = pageWidth - sideMargin * 2
        val availHeight = pageHeight - topMargin - bottomMargin

        val (cols, rows) = when (count) {
            2 -> 1 to 2
            6 -> 2 to 3
            else -> 2 to 2 // 4 (default)
        }
        val photoWidth = (availWidth - gap * (cols - 1)) / cols
        val rowHeight = availHeight / rows
        val photoHeight = rowHeight - captionHeight - gap

        val slots = ArrayList<PageSlot>(cols * rows)
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val left = sideMargin + c * (photoWidth + gap)
                val top = topMargin + r * rowHeight
                val frame = RectF(left, top, left + photoWidth, top + photoHeight)
                val baseline = top + photoHeight + captionHeight - 8f
                slots.add(PageSlot(frame, baseline))
            }
        }
        return slots
    }

    /** @return 이 페이지에서 정상적으로 그려진 사진 수. */
    private fun drawOutputPage(
        canvas: Canvas,
        pageWidth: Int,
        pageHeight: Int,
        sorted: List<PhotoItem>,
        startIndex: Int,
        pageNumber: Int,
        perPage: Int
    ): Int {
        val sx = pageWidth / 595f
        val sy = pageHeight / 842f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
        canvas.drawColor(Color.WHITE)
        paint.color = Color.rgb(20, 20, 20)
        paint.typeface = Typeface.DEFAULT
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 12f * sx
        canvas.drawText(documentHeaderText(), 44f * sx, 34f * sy, paint)

        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = 22f * sx
        canvas.drawText("사 진 용 지", pageWidth / 2f, 72f * sy, paint)

        paint.textAlign = Paint.Align.RIGHT
        paint.typeface = Typeface.DEFAULT
        paint.textSize = 10f * sx
        canvas.drawText("Page : $pageNumber", 530f * sx, 105f * sy, paint)

        val slots = computeSlots(pageWidth, pageHeight, perPage)
        // 캡션 폰트 크기는 슬롯 너비에 비례 — 4/6장 그리드에서는 자동으로 작아짐.
        val captionScale = (slots[0].frame.width() / (455f * sx)).coerceIn(0.5f, 1.0f) * sx
        var drawnCount = 0
        for (slotIndex in slots.indices) {
            val photoIndex = startIndex + slotIndex
            if (photoIndex >= sorted.size) break
            val slot = slots[slotIndex]
            if (drawOutputPhoto(canvas, sorted[photoIndex], slot.frame, slot.captionBaselineY, captionScale)) {
                drawnCount++
            }
        }

        paint.color = Color.rgb(20, 20, 20)
        paint.textAlign = Paint.Align.RIGHT
        paint.typeface = Typeface.DEFAULT
        paint.textSize = 10f * sx
        canvas.drawText("Page : $pageNumber", 530f * sx, 815f * sy, paint)
        return drawnCount
    }

    /**
     * @return true 이면 사진을 정상적으로 그렸음, false 면 디코드 실패하여 회색 박스만 그림.
     */
    private fun drawOutputPhoto(canvas: Canvas, photo: PhotoItem, frame: RectF, captionBaseline: Float, scale: Float): Boolean {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(245, 245, 245)
        canvas.drawRect(frame, paint)

        var bitmap: Bitmap? = null
        var oriented: Bitmap? = null
        var drewPhoto = false
        try {
            val uri = Uri.parse(photo.uri)
            // 1차 시도: 1400px (PDF/JPG 출력 프레임이 ~1200x510px 정도여서 충분).
            bitmap = decodeBitmap(uri, 1400)
            // 2차 시도: 메모리 부족 등으로 1차 실패 시 더 작게.
            if (bitmap == null) {
                bitmap = decodeBitmap(uri, 800)
            }
            if (bitmap != null) {
                oriented = rotateBitmap(bitmap, readExifOrientation(uri))
                drawBitmapCenterCrop(canvas, oriented, frame, paint)
                drewPhoto = true
            }
        } catch (_: Throwable) {
            // OOM 등 어떤 오류든 이 사진만 skip — 회색 박스는 이미 그려져 있음.
        } finally {
            if (oriented != null && oriented !== bitmap) oriented.recycle()
            bitmap?.recycle()
        }

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = maxOf(1f, scale)
        paint.color = Color.rgb(35, 35, 35)
        canvas.drawRect(frame, paint)

        drawOutputStamp(canvas, displayPhotoStamp(photo), frame, scale)

        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(20, 20, 20)
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT
        paint.textSize = 12f * scale
        canvas.drawText(photoCaption(photo), frame.centerX(), captionBaseline, paint)
        return drewPhoto
    }

    private fun drawBitmapCenterCrop(canvas: Canvas, bitmap: Bitmap, dst: RectF, paint: Paint) {
        val s = maxOf(dst.width() / bitmap.width, dst.height() / bitmap.height)
        val srcW = dst.width() / s
        val srcH = dst.height() / s
        val left = (bitmap.width - srcW) / 2f
        val top = (bitmap.height - srcH) / 2f
        val src = Rect(
            maxOf(0, left.roundToIntSafe()),
            maxOf(0, top.roundToIntSafe()),
            minOf(bitmap.width, (left + srcW).roundToIntSafe()),
            minOf(bitmap.height, (top + srcH).roundToIntSafe())
        )
        canvas.drawBitmap(bitmap, src, dst, paint)
    }

    private fun Float.roundToIntSafe(): Int = Math.round(this)

    private fun drawOutputStamp(canvas: Canvas, text: String, frame: RectF, scale: Float) {
        if (text.isEmpty()) return
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = 9f * scale
        textPaint.color = Color.WHITE
        val bounds = Rect()
        textPaint.getTextBounds(text, 0, text.length, bounds)
        val padX = 5f * scale
        val padY = 4f * scale
        val boxW = bounds.width() + padX * 2f
        val boxH = bounds.height() + padY * 2f
        val left = frame.right - boxW - 8f * scale
        val top = frame.bottom - boxH - 8f * scale
        val bg = Paint(Paint.ANTI_ALIAS_FLAG)
        bg.style = Paint.Style.FILL
        bg.color = Color.argb(175, 0, 0, 0)
        canvas.drawRect(left, top, left + boxW, top + boxH, bg)
        canvas.drawText(text, left + padX, top + padY + bounds.height(), textPaint)
    }

    private fun outputFileName(format: String): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.KOREA).format(Date())
        return "${modeLabel()}_사진자료_$stamp.${extensionForFormat(format)}"
    }

    private fun pageJpgFileName(pageNumber: Int): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.KOREA).format(Date())
        return "${modeLabel()}_사진자료_${stamp}_p$pageNumber.jpg"
    }

    private fun extensionForFormat(format: String): String = when (format) {
        FORMAT_PDF -> "pdf"
        FORMAT_JPG -> "jpg"
        else -> "pptx"
    }

    private fun mimeForFormat(format: String): String = when (format) {
        FORMAT_PDF -> PDF_MIME
        FORMAT_JPG -> JPG_MIME
        else -> PPTX_MIME
    }

    private fun formatLabel(format: String): String = when (format) {
        FORMAT_PDF -> "PDF"
        FORMAT_JPG -> "JPG"
        else -> "PPTX"
    }

    // ---- Share ----

    private fun shareOutput(recipient: String, format: String) {
        if (exportInProgress) {
            Toast.makeText(this, "이미 사진자료를 만드는 중입니다.", Toast.LENGTH_SHORT).show()
            return
        }
        exportInProgress = true
        openSheet = AppSheet.ExportProgress(format = format, current = 0, total = photos.size)

        val otherShare = mailAppPref == MAIL_APP_OTHER

        exportExecutor.submit {
            val attachmentUris: ArrayList<Uri>
            try {
                attachmentUris = if (format == FORMAT_JPG) {
                    val jpgFiles = createJpgFiles { d, t ->
                        runOnUiSafely { openSheet = AppSheet.ExportProgress(format, d, t) }
                    }
                    if (otherShare) writePublicFiles(jpgFiles) else writeCachedFiles(jpgFiles)
                } else {
                    val bytes = if (format == FORMAT_PDF) {
                        createPdfBytes { d, t ->
                            runOnUiSafely { openSheet = AppSheet.ExportProgress(format, d, t) }
                        }
                    } else {
                        createPptxBytes()
                    }
                    val file = ExportFile(outputFileName(format), mimeForFormat(format), bytes)
                    val files = arrayListOf(file)
                    if (otherShare) writePublicFiles(files) else writeCachedFiles(files)
                }
            } catch (e: Exception) {
                runOnUiSafely {
                    exportInProgress = false
                    closeSheet()
                    Toast.makeText(
                        this,
                        "공유 파일 생성 실패: ${e.message ?: "알 수 없는 오류"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return@submit
            }
            runOnUiSafely {
                exportInProgress = false
                closeSheet()
                launchShareIntent(recipient, format, otherShare, attachmentUris)
            }
        }
    }

    private fun launchShareIntent(recipient: String, format: String, otherShare: Boolean, attachmentUris: ArrayList<Uri>) {
        try {
            val emailIntent = if (otherShare) createShareIntent(attachmentUris, format)
            else createEmailIntent(recipient, attachmentUris, format)
            val mailPackage = selectedMailPackage()
            if (mailPackage.isNotEmpty()) {
                emailIntent.setPackage(mailPackage)
                for (uri in attachmentUris) {
                    grantUriPermission(mailPackage, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(emailIntent)
            } else {
                grantUriPermissionsToMatchingApps(emailIntent, attachmentUris)
                startActivity(Intent.createChooser(emailIntent, "공유 앱 선택"))
            }
        } catch (_: ActivityNotFoundException) {
            openEmailChooserFallback(recipient, attachmentUris, format)
        } catch (_: Exception) {
            Toast.makeText(this, "공유 화면을 열 수 없습니다.", Toast.LENGTH_LONG).show()
        }
    }

    @Throws(IOException::class)
    private fun writeCachedFiles(files: ArrayList<ExportFile>): ArrayList<Uri> {
        val exportDir = File(cacheDir, "mail_exports")
        if (!exportDir.exists() && !exportDir.mkdirs()) {
            throw IOException("Cannot create mail export directory")
        }
        exportDir.listFiles()?.forEach { it.delete() }

        val uris = ArrayList<Uri>()
        for (file in files) {
            val exportFile = File(exportDir, file.name)
            FileOutputStream(exportFile).use { it.write(file.bytes) }
            uris.add(FileProvider.getUriForFile(this, "$packageName.fileprovider", exportFile))
        }
        return uris
    }

    @Throws(IOException::class)
    private fun writePublicFiles(files: ArrayList<ExportFile>): ArrayList<Uri> {
        val uris = ArrayList<Uri>()
        for (file in files) uris.add(writePublicFile(file))
        return uris
    }

    @Throws(IOException::class)
    private fun writePublicFile(file: ExportFile): Uri {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, file.mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/AppraisalCamera")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Cannot create shared file")
            contentResolver.openOutputStream(uri)?.use { it.write(file.bytes) }
                ?: throw IOException("No output stream")
            val readyValues = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            contentResolver.update(uri, readyValues, null, null)
            return uri
        }
        return writeCachedFiles(arrayListOf(file))[0]
    }

    private fun createEmailIntent(recipient: String, uris: ArrayList<Uri>, format: String): Intent {
        val intent = Intent(if (uris.size > 1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND)
        intent.type = mimeForFormat(format)
        if (recipient.isNotBlank()) intent.putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient.trim()))
        intent.putExtra(Intent.EXTRA_SUBJECT, "${documentHeaderText()} 사진자료")
        intent.putExtra(Intent.EXTRA_TEXT, "사진자료 ${formatLabel(format)} 파일을 첨부합니다.")
        putStreams(intent, uris)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return intent
    }

    private fun createShareIntent(uris: ArrayList<Uri>, format: String): Intent {
        val intent = Intent(if (uris.size > 1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND)
        intent.type = mimeForFormat(format)
        intent.putExtra(Intent.EXTRA_TITLE, "${documentHeaderText()} 사진자료")
        putStreams(intent, uris)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return intent
    }

    private fun putStreams(intent: Intent, uris: ArrayList<Uri>) {
        if (uris.size == 1) intent.putExtra(Intent.EXTRA_STREAM, uris[0])
        else intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        var clipData: ClipData? = null
        for (uri in uris) {
            if (clipData == null) clipData = ClipData.newUri(contentResolver, "사진자료", uri)
            else clipData.addItem(ClipData.Item(uri))
        }
        if (clipData != null) intent.clipData = clipData
    }

    private fun grantUriPermissionsToMatchingApps(intent: Intent, uris: ArrayList<Uri>) {
        for (info in packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)) {
            val pkg = info.activityInfo?.packageName ?: continue
            for (uri in uris) grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun openEmailChooserFallback(recipient: String, uris: ArrayList<Uri>, format: String) {
        if (uris.isEmpty()) {
            Toast.makeText(this, "공유 화면을 열 수 없습니다.", Toast.LENGTH_LONG).show()
            return
        }
        try {
            val fallback = createShareIntent(uris, format)
            grantUriPermissionsToMatchingApps(fallback, uris)
            startActivity(Intent.createChooser(fallback, "공유 앱 선택"))
        } catch (_: Exception) {
            Toast.makeText(this, "공유 화면을 열 수 없습니다.", Toast.LENGTH_LONG).show()
        }
    }

    private fun writePendingExport(uri: Uri) {
        val bytes = pendingExportBytes ?: return
        try {
            contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw IOException("No output stream")
            Toast.makeText(this, "$pendingExportLabel 사진자료를 저장했습니다.", Toast.LENGTH_LONG).show()
        } catch (_: IOException) {
            Toast.makeText(this, "사진자료 저장에 실패했습니다.", Toast.LENGTH_LONG).show()
        } finally {
            pendingExportBytes = null
            pendingExportLabel = ""
        }
    }

    // ---- SharedPreferences ----

    private fun prefs(): SharedPreferences = getSharedPreferences(PREFS, MODE_PRIVATE)

    private fun loadPhotos() {
        photos.clear()
        val saved = prefs().getString(PREF_PHOTOS, "[]") ?: "[]"
        try {
            val array = JSONArray(saved)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val savedId = obj.optString("id")
                val item = PhotoItem(
                    id = if (savedId.isEmpty()) UUID.randomUUID().toString() else savedId,
                    category = obj.optString("category", CATEGORY_LAND),
                    symbol = obj.optString("symbol", "1"),
                    memo = obj.optString("memo"),
                    uri = obj.optString("uri"),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    stamped = obj.optBoolean("stamped", true),
                    debtorName = obj.optString("debtorName"),
                    fieldSurveyor = obj.optString("fieldSurveyor")
                )
                if (item.uri.isNotEmpty()) photos.add(item)
            }
        } catch (_: JSONException) {
            photos.clear()
        }
    }

    private fun savePhotos() {
        val array = JSONArray()
        for (item in photos) {
            try {
                val obj = JSONObject()
                obj.put("id", item.id)
                obj.put("category", item.category)
                obj.put("symbol", item.symbol)
                obj.put("memo", item.memo)
                obj.put("uri", item.uri)
                obj.put("createdAt", item.createdAt)
                obj.put("stamped", item.stamped)
                obj.put("debtorName", item.debtorName)
                obj.put("fieldSurveyor", item.fieldSurveyor)
                array.put(obj)
            } catch (_: JSONException) {
            }
        }
        prefs().edit().putString(PREF_PHOTOS, array.toString()).apply()
    }

    private fun loadPropertyAddress() {
        propertyAddress = prefs().getString(PREF_ADDRESS, "") ?: ""
    }

    private fun savePropertyAddress() {
        prefs().edit().putString(PREF_ADDRESS, propertyAddress).apply()
    }

    private fun loadAppMode() {
        val savedMode = prefs().getString(PREF_APP_MODE, MODE_SELF_APPRAISAL) ?: MODE_SELF_APPRAISAL
        appMode = if (savedMode == MODE_FIELD_SURVEY) MODE_FIELD_SURVEY else MODE_SELF_APPRAISAL
    }

    fun saveAppMode() {
        prefs().edit().putString(PREF_APP_MODE, appMode).apply()
    }

    private fun loadFieldSurveyInfo() {
        val p = prefs()
        debtorName = p.getString(PREF_DEBTOR_NAME, "") ?: ""
        fieldSurveyor = p.getString(PREF_FIELD_SURVEYOR, "") ?: ""
    }

    fun saveFieldSurveyInfo() {
        prefs().edit()
            .putString(PREF_DEBTOR_NAME, debtorName)
            .putString(PREF_FIELD_SURVEYOR, fieldSurveyor)
            .apply()
    }

    private fun loadGuideSettings() {
        val p = prefs()
        guideAlphaPercent = clamp(p.getInt(PREF_GUIDE_ALPHA, 82), 35, 100)
        guideScalePercent = clamp(p.getInt(PREF_GUIDE_SCALE, 78), 60, 100)
        floatingCaptureButton = p.getBoolean(PREF_FLOATING_CAPTURE, true)
    }

    private fun saveGuideSettings() {
        prefs().edit()
            .putInt(PREF_GUIDE_ALPHA, guideAlphaPercent)
            .putInt(PREF_GUIDE_SCALE, guideScalePercent)
            .putBoolean(PREF_FLOATING_CAPTURE, floatingCaptureButton)
            .apply()
    }

    private fun restoreControlState(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            currentCategory = normalizeCategory(savedInstanceState.getString(STATE_CURRENT_CATEGORY, CATEGORY_LAND))
            currentSymbol = savedInstanceState.getString(STATE_CURRENT_SYMBOL, "") ?: ""
            currentBuildingSub = savedInstanceState.getString(STATE_CURRENT_BUILDING_SUB, "") ?: ""
            currentMemo = savedInstanceState.getString(STATE_CURRENT_MEMO, "") ?: ""
            return
        }
        val p = prefs()
        currentCategory = normalizeCategory(p.getString(PREF_CURRENT_CATEGORY, CATEGORY_LAND))
        currentSymbol = p.getString(PREF_CURRENT_SYMBOL, "") ?: ""
        currentBuildingSub = p.getString(PREF_CURRENT_BUILDING_SUB, "") ?: ""
        currentMemo = p.getString(PREF_CURRENT_MEMO, "") ?: ""
    }

    fun saveControlState() {
        prefs().edit()
            .putString(PREF_CURRENT_CATEGORY, currentCategory)
            .putString(PREF_CURRENT_SYMBOL, currentSymbol)
            .putString(PREF_CURRENT_BUILDING_SUB, currentBuildingSub)
            .putString(PREF_CURRENT_MEMO, currentMemo)
            .apply()
    }

    private fun normalizeCategory(category: String?): String {
        if (category == CATEGORY_FIELD) return CATEGORY_LAND
        for (v in CATEGORY_ORDER) if (v == category) return v
        return CATEGORY_LAND
    }

    private fun loadEmailRecipient() {
        emailRecipient = (prefs().getString(PREF_EMAIL, "") ?: "").trim()
    }

    private fun saveEmailRecipient(recipient: String) {
        emailRecipient = recipient.trim()
        prefs().edit().putString(PREF_EMAIL, emailRecipient).apply()
    }

    private fun loadMailApp() {
        val v = prefs().getString(PREF_MAIL_APP, MAIL_APP_OTHER) ?: MAIL_APP_OTHER
        mailAppPref = if (v == "chooser" || v == "naver") MAIL_APP_OTHER else v
    }

    private fun saveMailApp(mailApp: String) {
        mailAppPref = mailApp
        prefs().edit().putString(PREF_MAIL_APP, mailApp).apply()
    }

    private fun selectedMailPackage(): String =
        if (mailAppPref == MAIL_APP_GMAIL) GMAIL_PACKAGE else ""

    fun mailAppLabel(mailApp: String): String =
        if (mailApp == MAIL_APP_GMAIL) "Gmail" else "Other"

    // ---- helpers ----

    private fun dpPx(value: Int): Int =
        Math.round(value * resources.displayMetrics.density)

    private fun clamp(v: Int, min: Int, max: Int): Int = maxOf(min, minOf(max, v))

    private fun indexOf(values: Array<String>, target: String): Int {
        for (i in values.indices) if (values[i] == target) return i
        return -1
    }

    private fun indexOfOrDefault(values: Array<String>, target: String, fallback: Int): Int {
        for (i in values.indices) if (values[i] == target) return i
        return fallback
    }

    // ---- Data classes ----

    data class PhotoItem(
        val id: String,
        val category: String,
        val symbol: String,
        val memo: String,
        val uri: String,
        val createdAt: Long,
        val stamped: Boolean,
        val debtorName: String,
        val fieldSurveyor: String
    )

    data class ExportFile(val name: String, val mimeType: String, val bytes: ByteArray)

    data class NextSymbol(val base: String, val sub: String)

    companion object {
        const val PREFS = "appraisal_photos"
        const val PREF_PHOTOS = "photos"
        const val PREF_ADDRESS = "property_address"
        const val PREF_APP_MODE = "app_mode"
        const val PREF_DEBTOR_NAME = "debtor_name"
        const val PREF_FIELD_SURVEYOR = "field_surveyor"
        const val PREF_EMAIL = "email_recipient"
        const val PREF_MAIL_APP = "mail_app"
        const val PREF_CURRENT_CATEGORY = "current_category"
        const val PREF_CURRENT_SYMBOL = "current_symbol"
        const val PREF_CURRENT_BUILDING_SUB = "current_building_sub"
        const val PREF_CURRENT_MEMO = "current_memo"
        const val PREF_GUIDE_ALPHA = "guide_alpha_percent"
        const val PREF_GUIDE_SCALE = "guide_scale_percent"
        const val PREF_FLOATING_CAPTURE = "floating_capture_button"
        const val STATE_CURRENT_CATEGORY = "state_current_category"
        const val STATE_CURRENT_SYMBOL = "state_current_symbol"
        const val STATE_CURRENT_BUILDING_SUB = "state_current_building_sub"
        const val STATE_CURRENT_MEMO = "state_current_memo"
        const val MAIL_APP_OTHER = "other"
        const val MAIL_APP_GMAIL = "gmail"
        const val GMAIL_PACKAGE = "com.google.android.gm"
        const val PPTX_MIME = "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        const val PDF_MIME = "application/pdf"
        const val JPG_MIME = "image/jpeg"
        const val FORMAT_PPTX = "pptx"
        const val FORMAT_PDF = "pdf"
        const val FORMAT_JPG = "jpg"
        const val MODE_SELF_APPRAISAL = "self_appraisal"
        const val MODE_FIELD_SURVEY = "field_survey"

        const val CATEGORY_LAND = "land"
        const val CATEGORY_BUILDING = "building"
        const val CATEGORY_EXTRA = "extra"
        const val CATEGORY_CUSTOM = "custom"
        const val CATEGORY_FIELD = "field"
        const val PREF_CATEGORY_ORDER = "category_order"
        const val PREF_PHOTOS_PER_PAGE = "photos_per_page"
        const val DEFAULT_PHOTOS_PER_PAGE = 4
        val ALLOWED_PHOTOS_PER_PAGE = listOf(2, 4, 6)
        val CATEGORY_ORDER = arrayOf(CATEGORY_LAND, CATEGORY_BUILDING, CATEGORY_EXTRA, CATEGORY_CUSTOM, CATEGORY_FIELD)
        val DEFAULT_CATEGORY_ORDER = listOf(CATEGORY_LAND, CATEGORY_BUILDING, CATEGORY_EXTRA, CATEGORY_CUSTOM)
        val LAND_SYMBOLS = Array(99) { (it + 1).toString() }
        val BUILDING_SYMBOLS = arrayOf("가","나","다","라","마","바","사","아","자","차","카","타","파","하")
        val EXTRA_SYMBOLS = arrayOf("ㄱ","ㄴ","ㄷ","ㄹ","ㅁ","ㅂ","ㅅ","ㅇ","ㅈ","ㅊ","ㅋ","ㅌ","ㅍ","ㅎ")
        val BUILDING_SUB_SYMBOLS = arrayOf("없음","-1","-2","-3","-4","-5","-6","-7","-8","-9")

        // 기타 카테고리 프리셋 — SymbolPicker 가 4개 버튼 + 개별입력 텍스트필드 렌더.
        val CUSTOM_PRESETS = arrayOf("인근도로", "물건전경", "기계기구")
    }
}
