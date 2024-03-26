package com.example.ar_module


import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.text.Html
import android.util.Log
import android.view.PixelCopy
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.ar_module.customDetector.Classifier
import com.example.ar_module.customDetector.DetectorFactory
import com.example.ar_module.customDetector.Utils
import com.example.ar_module.databinding.ActivityArBinding
import com.google.ar.core.Config
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.MaterialFactory
import com.google.ar.sceneform.rendering.Renderable
import com.google.ar.sceneform.rendering.ShapeFactory
import com.google.ar.sceneform.rendering.Texture
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.TransformableNode
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.abs
import kotlin.random.Random


class ARActivity : AppCompatActivity() {

    lateinit var binding: ActivityArBinding
    private var arFragment: ArFragment? = null
    private var rectangleNodeList : MutableList<Node?> = mutableListOf()

    private var coveredRenderable : MutableList<Renderable> = mutableListOf()
    private var outlinedRenderable : MutableList<Renderable> = mutableListOf()

    lateinit var detector: Classifier

    private var detectedObjects: MutableList<Classifier.Recognition> = mutableListOf()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityArBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        arFragment = supportFragmentManager.findFragmentById(R.id.sceneform_fragment) as ArFragment


        detector = DetectorFactory.getDetector(this.assets, "mars_mt-int8.tflite")

        startConfiguringSessionWithFocus()


        startDistanceUpdates()

        binding.mybutton.setOnClickListener {
            try {
                takePhoto()
            } catch (e : Exception) {
            }
        }

        binding.resultButton.setOnClickListener{ showDetectedObjects() }
    }

    var sessionConfigured = false
    private val configRetryHandler = Handler()
    private fun configureSessionWithFocus() {
        try {
            // Create a new Config object with the session
            val config = Config(arFragment?.arSceneView?.session)

            // Set focus mode based on the enableAutoFocus variable
            config.focusMode = Config.FocusMode.AUTO

            config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE

            arFragment?.arSceneView?.planeRenderer?.isShadowReceiver = false

            // Configure the session with the created config
            arFragment?.arSceneView?.session?.configure(config)

            sessionConfigured = true
        } catch (e: Exception) {
            sessionConfigured = false
        }
    }

    private fun startConfiguringSessionWithFocus() {
        // Attempt to configure AR session with focus mode every 500 milliseconds
        configRetryHandler.postDelayed(object : Runnable {
            override fun run() {
                if (!sessionConfigured) {
                    configureSessionWithFocus()
                    // Retry after 500 milliseconds
                    configRetryHandler.postDelayed(this, 500)
                }
            }
        }, 500)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Remove the retry mechanism when the activity is destroyed
        configRetryHandler.removeCallbacksAndMessages(null)
    }

    private val INPUT_SIZE = 640
    var sourceBitmap: Bitmap? = null
    var cropBitmap: Bitmap? = null
    private fun getPredictions() {
        cropBitmap = Utils.processBitmap(sourceBitmap, INPUT_SIZE)

        val results: List<Classifier.Recognition> =
            try {
                detector.recognizeImage(cropBitmap)
            } catch (e : Exception) {
                listOf()
            }

        Log.d("PKJ original", "$results")

        sourceBitmap?.let{
            handleResult(it, results)
            runOnUiThread {
                try {
                    appearAtPoint2(
                        arFragment!!.requireView().width / 2.0f,
                        arFragment!!.requireView().height / 2.0f,
                        results
                    )
                } catch (e : Exception) {
                    Toast.makeText(this, "Something went wrong", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun handleResult(bitmap: Bitmap, results: List<Classifier.Recognition>) {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        val paint = Paint()
        paint.color = Color.RED
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.0f

        for (result in results) {
            val location = result.location
            if (location != null && result.confidence >= 0.7f) {
                val newLocation = getOriginalLocation(
                    location, sourceBitmap!!.height.toFloat(), sourceBitmap!!.width.toFloat(),
                    cropBitmap!!
                )
                canvas.drawRect(newLocation, paint)
                val text = result.title
                val textSize = 16f
                paint.textSize = textSize
                paint.color = Color.WHITE
                canvas.drawText(text, newLocation.left, newLocation.top - textSize, paint)
            }
        }

        saveToInternalStorage(mutableBitmap, "drawnBitmap.jpg")
    }

    private fun handleResult2(bitmap: Bitmap, results: List<Classifier.Recognition>, paintStyle: Paint.Style = Paint.Style.STROKE): Bitmap {
        val mutableBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(mutableBitmap)
        val paint = Paint()
        paint.color = Color.RED
        paint.style = paintStyle
        paint.strokeWidth = 4.0f

        for (result in results) {
            val location = result.location
            if (location != null && result.confidence >= 0.5f) {
                val newLocation = getOriginalLocation(
                    location, sourceBitmap!!.height.toFloat(), sourceBitmap!!.width.toFloat(),
                    cropBitmap!!
                )
                canvas.drawRect(newLocation, paint)
                val text = result.title
                val textSize = 25f
                paint.textSize = textSize
                canvas.drawText(text, newLocation.left, newLocation.top - textSize, paint)
            }
        }

        // Set paint for background color (almost transparent blue)
        val backgroundPaint = Paint().apply {
            color = Color.BLUE
            style = Paint.Style.STROKE
            strokeWidth = 10.0f
        }

        // Draw a rectangle to create the background
        canvas.drawRect(0f, 0f, mutableBitmap.width.toFloat(), mutableBitmap.height.toFloat(), backgroundPaint)

        return mutableBitmap
    }

    private fun getOriginalLocation(
        rectF: RectF,
        originalHeight: Float,
        originalWidth: Float,
        miniBitmap: Bitmap
    ): RectF {
        val newRect = RectF(rectF)
        val heightRatio = originalHeight.toDouble() / miniBitmap.height
        val widthRatio = originalWidth.toDouble() / miniBitmap.width
        newRect.left *= widthRatio.toFloat()
        newRect.right *= widthRatio.toFloat()
        newRect.top *= heightRatio.toFloat()
        newRect.bottom *= heightRatio.toFloat()
        return newRect
    }

    private fun takePhoto() {
        // Call the method to capture bitmap from the ArSceneView
        captureBitmapFromArSceneView(arFragment!!.arSceneView) { bitmap ->
            if (bitmap != null) {
                saveToInternalStorage(bitmap, "arFrame.jpg")
                sourceBitmap = bitmap
                getPredictions()

            } else {
                Toast.makeText(this, "Failed to capture image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun captureBitmapFromArSceneView(arSceneView: ArSceneView, callback: (Bitmap?) -> Unit) {
        toggleArNodes(arSceneView, false) // Hide the nodes

        // Create a handler to introduce a delay
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            // After the delay, create the bitmap and request to copy the pixels.
            val bitmap = Bitmap.createBitmap(arSceneView.width, arSceneView.height, Bitmap.Config.ARGB_8888)
            val handlerThread = HandlerThread("PixelCopier")
            handlerThread.start()

            // Make the request to copy the pixels.
            PixelCopy.request(arSceneView, bitmap, { copyResult ->
                if (copyResult == PixelCopy.SUCCESS) {
                    toggleArNodes(arSceneView, true) // Show the nodes again
                    callback(bitmap)
                } else {
                    toggleArNodes(arSceneView, true) // Show the nodes again
                    callback(null)
                }
                handlerThread.quitSafely()
            }, Handler(handlerThread.looper))
        }, 100)
    }


    private fun toggleArNodes(arSceneView: ArSceneView, show: Boolean) {
        runOnUiThread {
            rectangleNodeList.forEachIndexed { index, node ->
                val renderable = if(show) outlinedRenderable[index] else coveredRenderable[index]
                node?.renderable = renderable
            }
            arSceneView.planeRenderer.isVisible = show
        }
    }
    private fun saveToInternalStorage(bitmap: Bitmap, name: String) {
        val outputDirectory = this.filesDir
        val photoFile = File(outputDirectory, name)
        saveImageToFile(photoFile, bitmap)
    }

    private fun saveImageToFile(
        file1: File,
        bitmap1: Bitmap
    ) {
        try {
            FileOutputStream(file1).use { outputStream ->
                bitmap1.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                outputStream.flush()
                outputStream.close()
            }
        } catch (e: IOException) {
        }
    }

    private fun renderPredictions(bitmap: Bitmap, width: Float, height: Float, covered: Boolean = false) {
        Texture.builder().setSource(bitmap)
            .build()
            .thenAccept {
                MaterialFactory.makeTransparentWithTexture(this, it)
                    .thenAccept {material ->
                        ShapeFactory.makeCube(
                            Vector3(width, .001f, height),
                            Vector3(0.0f, 0.05f, 0.0f),
                            material
                        ).let {
                            setNodeRenderable(it, covered)
                        }
                    }
            }
    }

    private fun setNodeRenderable(model: Renderable, covered: Boolean = false) {
        if(!covered) {
            node.renderable = model
            rectangleNodeList.add(node)
            outlinedRenderable.add(model)
        } else {
            coveredRenderable.add(model)
        }

    }

    var anchorNode: AnchorNode? = null

    lateinit var node: Node
    private fun appearAtPoint2(x: Float, y: Float, results: List<Classifier.Recognition>) {
        val hitResult = getHitResult(x, y)
        val anchor = hitResult.createAnchor()
        anchorNode = AnchorNode(anchor)

        val dimensions = getWidthHeight(anchorNode!!)

        val othersIgnored = results.filter { it.title != "others" }

        val finalBitmap = handleResult2(sourceBitmap!!, othersIgnored)
        val coverBitmap = handleResult2(sourceBitmap!!, othersIgnored, paintStyle = Paint.Style.FILL)

        detectedObjects.addAll(othersIgnored)

        anchorNode!!.setParent(arFragment!!.arSceneView.scene)

        val intermediateNode = Node()
        intermediateNode.setParent(anchorNode)
        val anchorUp = anchorNode!!.up
        intermediateNode.setLookDirection(Vector3.up(), anchorUp)

        node = Node()

        val transformableNode = TransformableNode(arFragment!!.transformationSystem)
        node.worldRotation = Quaternion.lookRotation(Vector3.forward(), Vector3.up())
        transformableNode.setParent(intermediateNode)
        node.setParent(transformableNode)

        renderPredictions(finalBitmap, dimensions[0], dimensions[1])
        renderPredictions(coverBitmap, dimensions[0], dimensions[1], covered = true)
    }

    private fun getWidthHeight(centerAnchorNode: AnchorNode): FloatArray {
        val result = floatArrayOf(.5f, .7f)
        val diff = arFragment!!.requireView().width.toFloat() / 20.0f
        val hitResult = getHitResult(arFragment!!.requireView().width.toFloat() / 2.0f + diff,
            arFragment!!.requireView().height / 2.0f)
        val anchor = hitResult.createAnchor()
        val anchorNode = AnchorNode(anchor)

        val centerAnchorCoordinates = centerAnchorNode.worldPosition
        val rightAnchorCoordinates = anchorNode.worldPosition
        result[0] = abs((rightAnchorCoordinates.x - centerAnchorCoordinates.x) * 20)
        val ratio = (arFragment!!.requireView().height / arFragment!!.requireView().width).toFloat()
        result[1] =  ratio * result[0]

        return result
    }

    private fun getHitResult(x: Float, y: Float) :HitResult {
        val hitResult: List<HitResult> = arFragment!!.arSceneView.arFrame!!.hitTest(x, y)
        return hitResult[0]
    }

    private fun showDetectedObjects() {
        val labelMap: MutableMap<String, Int> = mutableMapOf()
        detectedObjects.forEach {
            labelMap[it.title] = labelMap.getOrDefault(it.title, 0) + 1
        }

        val stringBuilder = StringBuilder()
        labelMap.forEach { (label, count) ->
            val labelColor = getRandomColor()
            stringBuilder.append("<font color=\"#$labelColor\">$label:</font> <font color=\"#000000\">$count</font><br/>")
        }
        val finalString = stringBuilder.toString()

        val alertDialogBuilder = AlertDialog.Builder(this)
        alertDialogBuilder.apply {
            setTitle("Detected Objects")
            setMessage(Html.fromHtml(finalString))
            setPositiveButton("OK") { dialog, _ ->
                // Do something when OK button is clicked, if needed
                dialog.dismiss() // Dismiss the dialog
            }
            // Create and show the AlertDialog
            create().show()
        }
    }

    private val updateIntervalMillis = 100L // Update interval in milliseconds
    private val handler = Handler(Looper.getMainLooper())
    private fun startDistanceUpdates() {

            handler.postDelayed(object : Runnable {
                override fun run() {
                    // Perform hit test to find the nearest plane
                    try {
                        arFragment!!.arSceneView.scene?.let { scene ->
                            val frame = arFragment!!.arSceneView.arFrame
                            frame?.let { frame ->
                                val hitResult = frame.hitTest(
                                    arFragment!!.arSceneView.width / 2.0f,
                                    arFragment!!.arSceneView.height / 2.0f
                                ).firstOrNull { it.trackable is Plane }

                                hitResult?.let { hitResult ->
                                    // Calculate the distance between camera (screen) and the hit point on the plane
                                    val distanceToPlane = hitResult.distance
                                    // Update the TextView with the distance value
                                    binding.distanceTextView.text =
                                        "Distance: ${String.format("%.2f", distanceToPlane)} meters"
                                }
                            }
                        }
                    } catch (_: Exception) {
                    }

                    // Schedule the next update after the interval
                    handler.postDelayed(this, updateIntervalMillis)
                }
            }, updateIntervalMillis)
    }

    private fun getRandomColor(): String {
        // Generate random color in hex format
        return String.format("%06X", Random.nextInt(0xFFFFFF))
    }
}