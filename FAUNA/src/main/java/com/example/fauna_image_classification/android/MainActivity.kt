package com.example.fauna_image_classification.android

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.fauna_image_classification.android.databinding.ActivityMainBinding
import com.example.fauna_image_classification.android.ml.DangerousPlants
import com.example.fauna_image_classification.android.ml.Model
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


typealias LumaListener = (luma: Double) -> Unit
typealias ModelListener = (message: String) -> Unit

class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding
    private lateinit var imageProcessor: ImageProcessor
    private lateinit var resultsList: List<String>
    private lateinit var rvHorizontalPicker: RecyclerView
    private lateinit var tvSelectedItem: TextView

    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    private lateinit var cameraExecutor: ExecutorService
    private var textView : TextView? = null
    private var imageView : ImageView? = null
    private var imageSize = 150

    private val data = arrayListOf("Snakes", "Spiders", "Dangerous Plants")

    // 0 = snakes
    // 1 = commons plants
    //
    private var modelSelected = 1

    private val activityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions())
        { permissions ->
            // Handle Permission granted/rejected
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && it.value == false)
                    permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(baseContext,
                    "Permission request denied",
                    Toast.LENGTH_SHORT).show()
            } else {
                startCamera()
            }
        }

    // When an instance of this class is created, this function will run.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        textView = findViewById<TextView>(R.id.model_result_text) as TextView
        imageView = findViewById<ImageView>(R.id.imageView) as ImageView

        imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(150, 150, ResizeOp.ResizeMethod.BILINEAR))
            .build()

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }

        // Set up the listeners for take photo and video capture buttons
        viewBinding.imageCaptureButton.setOnClickListener { takePhoto() }
        viewBinding.selectImageButton.setOnClickListener { openGallery() }
        cameraExecutor = Executors.newSingleThreadExecutor()

        setTvSelectedItem()
        setHorizontalPicker()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.nav_menu, menu)
        return true
    }

    private fun setTvSelectedItem() {
        tvSelectedItem = findViewById(R.id.tv_selected_item)
    }

    private fun setHorizontalPicker() {
        rvHorizontalPicker = findViewById(R.id.rv_horizontal_picker)

        // Setting the padding such that the items will appear in the middle of the screen
        val padding: Int = ScreenUtils.getScreenWidth(this)/2 - ScreenUtils.dpToPx(this, 40)
        rvHorizontalPicker.setPadding(padding, 0, padding, 0)

        // Setting layout manager
        rvHorizontalPicker.layoutManager = SliderLayoutManager(this).apply {
            callback = object : SliderLayoutManager.OnItemSelectedListener {
                override fun onItemSelected(layoutPosition: Int) {
                    tvSelectedItem.setText(data[layoutPosition])
                }
            }
        }

        // Setting Adapter
        rvHorizontalPicker.adapter = SliderAdapter().apply {
            setData(data)
            callback = object : SliderAdapter.Callback {
                override fun onItemClicked(view: View) {
                    rvHorizontalPicker.smoothScrollToPosition(rvHorizontalPicker.getChildLayoutPosition(view))
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle item selection.
        return when (item.itemId) {
            R.id.feedback -> {
                val intent = Intent(this@MainActivity, Feedback::class.java)
                startActivity(intent)
                true
            }
            R.id.map -> {
                //val intent = Intent(this@MainActivity, Map::class.java)
                //startActivity(intent)
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://joshwilkinson.pythonanywhere.com/map"))
                startActivity(browserIntent)
                true
            }
            R.id.chat -> {
                val intent = Intent(this@MainActivity, Chat::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }


    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Create time stamped name and MediaStore entry.
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
        }
        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
            .build()

        // Set up image capture listener, which is triggered after photo has
        // been taken, and save the image - note: this works, but is disabled for testing purposes,
        // so that I won't have to go back and delete them...
        /*
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults){
                    val msg = "Photo capture succeeded: ${output.savedUri}"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                }
            },
        )
        */
        // this will take a picture on success and give an ImageProxy to mess with
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    //get bitmap from image
                    var bitmap = imageProxyToBitmap(image)
                    //super.onCaptureSuccess(image)
                    var resultsList = classifySnakeImage(bitmap)
                    // create popup

                    when (tvSelectedItem.text) {
                        "Snakes" -> {
                            resultsList = classifySnakeImage(bitmap)
                            // open popup
                            val intent = Intent(this@MainActivity, Popup::class.java)
                            intent.putExtra("Classification", resultsList[0])
                            intent.putExtra("maxConfidence", resultsList[1])
                            intent.putExtra("Type", "snake")
                            startActivity(intent)
                        }
                        "Spiders" -> {
                            resultsList = classifySnakeImage(bitmap)
                            // open popup
                            val intent = Intent(this@MainActivity, Popup::class.java)
                            intent.putExtra("Classification", resultsList[0])
                            intent.putExtra("maxConfidence", resultsList[1])
                            intent.putExtra("Type", "spider")
                            startActivity(intent)
                        }
                        "Dangerous Plants" -> {
                            resultsList = classifyPlantImage(bitmap)
                            // open popup
                            val intent = Intent(this@MainActivity, Popup::class.java)
                            intent.putExtra("Classification", resultsList[0])
                            intent.putExtra("maxConfidence", resultsList[1])
                            intent.putExtra("Type", "plant")
                            startActivity(intent)
                        }
                        else -> { // Note the block
                            // do nothing
                        }
                    }
                    image.close()
                }
                override fun onError(exception: ImageCaptureException) {
                    super.onError(exception)
                }
            })
    }

    // Convert ImageProxy data to Bitmap
    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val planeProxy: ImageProxy.PlaneProxy = image.planes[0]
        val buffer: ByteBuffer = planeProxy.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    // Use the snake model to classify an image, returns the species of snake and the confidence in a list
    // of strings, to be used in a popup instance.
    private fun classifySnakeImage(image: Bitmap): List<String> {
        var resize: Bitmap = Bitmap.createScaledBitmap(image, 150, 150, true)
        // create new instance of the model
        val model = Model.newInstance(applicationContext)

        // Creates inputs for reference.
        val inputFeature0 =
            TensorBuffer.createFixedSize(intArrayOf(1, 150, 150, 3), DataType.FLOAT32)

        val byteBuffer = ByteBuffer.allocateDirect(4 * imageSize * imageSize * 3)
        byteBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(imageSize * imageSize)
        resize.getPixels(intValues, 0, resize.width, 0, 0, resize.width, resize.height)
        var pixel = 0
        //iterate over each pixel and extract R, G, and B values. Add those values individually to the byte buffer.
        for (i in 0 until imageSize) {
            for (j in 0 until imageSize) {
                val `val` = intValues[pixel++] // RGB
                byteBuffer.putFloat(((`val` shr 16) and 0xFF) * (1f / 255))
                byteBuffer.putFloat(((`val` shr 8) and 0xFF) * (1f / 255))
                byteBuffer.putFloat((`val` and 0xFF) * (1f / 255))
            }
        }
        inputFeature0.loadBuffer(byteBuffer)

        // Runs model inference and gets result.
        val outputs = model.process(inputFeature0)
        val outputFeature0 = outputs.outputFeature0AsTensorBuffer
        val confidences = outputFeature0.floatArray
        // find the index of the class with the biggest confidence.
        var maxPos = 0
        var maxConfidence = 0.7f
        for (i in confidences.indices) {
            if (confidences[i] > maxConfidence) {
                maxConfidence = confidences[i]
                maxPos = i
            }
        }
        // different snakes: to-do - add the rest of them with the new model
        // maybe map values?
        val classes = arrayOf(
            "Agkistrodon contortrix",
            "Agkistrodon piscivorus",
            "Ahaetulla nasuta",
            "Ahaetulla prasina",
            "Arizona elegans"
        )
        // Releases model resources if no longer used.
        model.close()
        // make this look better as a percentage
        maxConfidence *= 100
        // return a results list containing the classification and max confidence, to be used by the popup window.
        return listOf(classes[maxPos], "$maxConfidence%")
    }

    private fun classifyPlantImage(image: Bitmap): List<String> {
        var resize: Bitmap = Bitmap.createScaledBitmap(image, 150, 150, true)
        // create new instance of the model
        val model = DangerousPlants.newInstance(applicationContext)

        // Creates inputs for reference.
        val inputFeature0 =
            TensorBuffer.createFixedSize(intArrayOf(1, 150, 150, 3), DataType.FLOAT32)

        val byteBuffer = ByteBuffer.allocateDirect(4 * imageSize * imageSize * 3)
        byteBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(imageSize * imageSize)
        resize.getPixels(intValues, 0, resize.width, 0, 0, resize.width, resize.height)
        var pixel = 0
        //iterate over each pixel and extract R, G, and B values. Add those values individually to the byte buffer.
        for (i in 0 until imageSize) {
            for (j in 0 until imageSize) {
                val `val` = intValues[pixel++] // RGB
                byteBuffer.putFloat(((`val` shr 16) and 0xFF) * (1f / 255))
                byteBuffer.putFloat(((`val` shr 8) and 0xFF) * (1f / 255))
                byteBuffer.putFloat((`val` and 0xFF) * (1f / 255))
            }
        }
        inputFeature0.loadBuffer(byteBuffer)

        // Runs model inference and gets result.
        val outputs = model.process(inputFeature0)
        val outputFeature0 = outputs.outputFeature0AsTensorBuffer
        val confidences = outputFeature0.floatArray
        // find the index of the class with the biggest confidence.
        var maxPos = 0
        var maxConfidence = 0.7f // acceptable confidence
        for (i in confidences.indices) {
            if (confidences[i] > maxConfidence) {
                maxConfidence = confidences[i]
                maxPos = i
            }
        }
        // different plants labels
        val classes = arrayOf(
            "Castor oil plant",
            "Dieffenbachia",
            "Foxglove",
            "Lilies",
            "Lily of the valley",
            "Oleander",
            "Rhubarb",
            "Wisteria"
        )
        // Releases model resources if no longer used.
        model.close()
        // make this look better as a percentage
        maxConfidence *= 100
        // return a results list containing the classification and max confidence, to be used by the popup window.
        return listOf(classes[maxPos], "$maxConfidence%")
    }

    // opens the user's photo library, where the can select an image to classify
    private fun openGallery() {
        var intent : Intent = Intent()
        intent.setAction(Intent.ACTION_GET_CONTENT)
        intent.setType("image/")
        startActivityForResult(intent, 100)
    }

    // empty method in case I want to do anything with video capture in the future
    private fun captureVideo() {}

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                }
            imageCapture = ImageCapture.Builder()
                .build()

            val imageAnalyzer = ImageAnalysis.Builder()
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, LuminosityAnalyzer { luma ->
                        //Log.d(TAG, "Luminosity: $luma")
                        if (luma < 10) {
                            textView?.setText("Too dark!")
                        }
                        else {
                            textView?.setText("")
                        }
                    })
                }
            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()
                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalyzer)
            }
            catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    // launches request permissions notification
    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    // checks required permissions
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun isLocationPermissionGranted(): Boolean {
        return if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                500
            )
            false
        } else {
            true
        }
    }

    // shuts down the camera
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    // phone activity result operations
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // requestCode 100 means the user selected an image from their gallery on their device
        if (requestCode == 100){
            var uri = data?.data
            var bitmap : Bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, uri)

            when (tvSelectedItem.text) {
                "Snakes" -> {
                    resultsList = classifySnakeImage(bitmap)
                    // open popup
                    val intent = Intent(this@MainActivity, Popup::class.java)
                    intent.putExtra("Classification", resultsList[0])
                    intent.putExtra("maxConfidence", resultsList[1])
                    intent.putExtra("Type", "snake")
                    startActivity(intent)
                }
                "Spiders" -> {
                    resultsList = classifySnakeImage(bitmap)
                    // open popup
                    val intent = Intent(this@MainActivity, Popup::class.java)
                    intent.putExtra("Classification", resultsList[0])
                    intent.putExtra("maxConfidence", resultsList[1])
                    intent.putExtra("Type", "spider")
                    startActivity(intent)
                }
                "Dangerous Plants" -> {
                    resultsList = classifyPlantImage(bitmap)
                    // open popup
                    val intent = Intent(this@MainActivity, Popup::class.java)
                    intent.putExtra("Classification", resultsList[0])
                    intent.putExtra("maxConfidence", resultsList[1])
                    intent.putExtra("Type", "plant")
                    startActivity(intent)
                }
                else -> { // Note the block
                    // do nothing
                }
            }


        }
    }

    companion object {
        private const val TAG = "FAUNA"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }

    // This image analyser looks at the current luminosity of the camera image
    private class LuminosityAnalyzer(private val listener: LumaListener) : ImageAnalysis.Analyzer {
        private fun ByteBuffer.toByteArray(): ByteArray {
            rewind()    // Rewind the buffer to zero
            val data = ByteArray(remaining())
            get(data)   // Copy the buffer into a byte array
            return data // Return the byte array
        }

        override fun analyze(image: ImageProxy) {
            val buffer = image.planes[0].buffer
            val data = buffer.toByteArray()
            val pixels = data.map { it.toInt() and 0xFF }
            val luma = pixels.average()

            listener(luma)
            image.close()
        }
    }
}


