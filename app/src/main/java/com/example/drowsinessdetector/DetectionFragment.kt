package com.example.drowsinessdetector

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DetectionFragment : Fragment(R.layout.fragment_detection) {

    private lateinit var tflite: Interpreter
    private var closedEyeStartTime: Long = 0L
    private var threshold = 7
    private lateinit var toneGenerator: ToneGenerator
    private lateinit var cameraExecutor: ExecutorService

    private val inputBuffer = ByteBuffer.allocateDirect(1 * 64 * 64 * 1 * 4).apply {
        order(ByteOrder.nativeOrder())
    }

    private lateinit var statusText: TextView
    private lateinit var alertText: TextView
    private lateinit var warningBorder: View
    private lateinit var viewFinder: PreviewView

    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .enableTracking()
            .build()
    )

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startCamera()
            } else {
                Log.e("Camera", "Permission denied")
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewFinder = view.findViewById(R.id.viewFinder)
        statusText = view.findViewById(R.id.statusText)
        alertText = view.findViewById(R.id.alertText)
        warningBorder = view.findViewById(R.id.warningBorder)

        toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
        tflite = Interpreter(loadModelFile(), Interpreter.Options())

        val prefs = requireActivity().getSharedPreferences("DrowsinessPrefs", android.content.Context.MODE_PRIVATE)
        threshold = prefs.getInt("threshold", 7)

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build().also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImageProxy(imageProxy)
                    }
                }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(viewLifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageAnalyzer)
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    @ExperimentalGetImage
    private fun processImageProxy(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)

            faceDetector.process(image)
                .addOnSuccessListener { faces ->
                    var isEyesClosed = false
                    var headTiltDetected = false
                    var yawningDetected = false
                    var certainty = 100

                    if (faces.isNotEmpty()) {
                        try {
                            val rotatedBitmap = imageProxy.toBitmap()

                            for (face in faces) {
                                val angle = kotlin.math.abs(face.headEulerAngleZ)
                                if ((angle > 20f && angle < 70f) || (angle > 110f && angle < 160f)) {
                                    headTiltDetected = true
                                }

                                val mouthLeft = face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.MOUTH_LEFT)?.position
                                val mouthRight = face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.MOUTH_RIGHT)?.position
                                val mouthBottom = face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.MOUTH_BOTTOM)?.position
                                val noseBase = face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.NOSE_BASE)?.position

                                if (mouthLeft != null && mouthRight != null && mouthBottom != null && noseBase != null) {
                                    val mouthWidth = kotlin.math.hypot((mouthRight.x - mouthLeft.x).toDouble(), (mouthRight.y - mouthLeft.y).toDouble())
                                    val mouthHeight = kotlin.math.hypot((mouthBottom.x - noseBase.x).toDouble(), (mouthBottom.y - noseBase.y).toDouble())
                                    
                                    if (mouthHeight > mouthWidth * 1.3) {
                                        yawningDetected = true
                                    }
                                }

                                val leftEye = face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.LEFT_EYE)?.position
                                val rightEye = face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.RIGHT_EYE)?.position
                                val eyes = listOfNotNull(leftEye, rightEye)

                                var totalOpenScore = 0f
                                var totalClosedScore = 0f
                                var eyesChecked = 0

                                for (eyePos in eyes) {
                                    val eyeSize = (face.boundingBox.width() * 0.30f).toInt()
                                    val left = (eyePos.x - eyeSize / 2).toInt().coerceAtLeast(0)
                                    val top = (eyePos.y - eyeSize / 2).toInt().coerceAtLeast(0)
                                    val width = eyeSize.coerceAtMost(rotatedBitmap.width - left)
                                    val height = eyeSize.coerceAtMost(rotatedBitmap.height - top)

                                    if (width > 0 && height > 0) {
                                        val eyeBitmap = Bitmap.createBitmap(rotatedBitmap, left, top, width, height)
                                        val eyeMatrix = Matrix().apply { postRotate(-face.headEulerAngleZ) }
                                        val alignedEye = Bitmap.createBitmap(eyeBitmap, 0, 0, eyeBitmap.width, eyeBitmap.height, eyeMatrix, true)
                                        
                                        val resized = Bitmap.createScaledBitmap(alignedEye, 64, 64, true)
                                        val buffer = convertBitmapToGrayByteBuffer(resized)
                                        val outputBuffer = Array(1) { FloatArray(3) }
                                        
                                        tflite.run(buffer, outputBuffer)
                                        
                                        val scores = outputBuffer[0]
                                        totalOpenScore += scores[0]
                                        totalClosedScore += scores[1]
                                        eyesChecked++
                                    }
                                }

                                if (eyesChecked > 0) {
                                    val openScore = totalOpenScore / eyesChecked
                                    val closedScore = totalClosedScore / eyesChecked
                                    
                                    val currentCertainty = (kotlin.math.max(closedScore, openScore) * 100).toInt().coerceIn(0, 100)
                                    
                                    if (closedScore > openScore) {
                                        isEyesClosed = true
                                        certainty = currentCertainty
                                    } else if (!isEyesClosed) {
                                        certainty = currentCertainty
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("DetectionFragment", "Error processing bitmap", e)
                        }
                    }

                    updateUI(isEyesClosed, headTiltDetected, yawningDetected, certainty)
                }
                .addOnCompleteListener { imageProxy.close() }
        } else {
            imageProxy.close()
        }
    }

    private fun updateUI(isEyesClosed: Boolean, headTiltDetected: Boolean, yawningDetected: Boolean, certainty: Int) {
        if (isEyesClosed) {
            if (closedEyeStartTime == 0L) {
                closedEyeStartTime = System.currentTimeMillis()
            }
        } else {
            closedEyeStartTime = 0L
        }

        activity?.runOnUiThread {
            val alerts = mutableListOf<String>()
            if (headTiltDetected) alerts.add("HEAD TILT DETECTED")
            if (yawningDetected) alerts.add("YAWNING DETECTED")
            alertText.text = alerts.joinToString("\n")

            val elapsedSeconds = if (closedEyeStartTime > 0L) (System.currentTimeMillis() - closedEyeStartTime) / 1000 else 0
            
            if (elapsedSeconds >= threshold) {
                statusText.text = "Alarm Ringing"
                statusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.red_text))
                statusText.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red_bg))
                warningBorder.setBackgroundColor("#44FF9800".toColorInt())
                toneGenerator.startTone(ToneGenerator.TONE_CDMA_HIGH_L, 500) 
            } else {
                if (isEyesClosed) {
                    statusText.text = "Eyes Closed"
                    statusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.red_text))
                    statusText.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red_bg))
                } else {
                    statusText.text = "Eyes Open"
                    statusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.green_text))
                    statusText.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_bg))
                }
                warningBorder.setBackgroundColor(Color.TRANSPARENT)
            }
        }
    }

    private fun convertBitmapToGrayByteBuffer(bitmap: Bitmap): ByteBuffer {
        inputBuffer.rewind()
        val intValues = IntArray(64 * 64)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var pixel = 0
        for (i in 0 until 64) {
            for (j in 0 until 64) {
                val valInt = intValues[pixel++]
                val r = (valInt shr 16 and 0xFF)
                val g = (valInt shr 8 and 0xFF)
                val b = (valInt and 0xFF)
                val gray = (0.299f * r + 0.587f * g + 0.114f * b) / 255.0f
                inputBuffer.putFloat(gray)
            }
        }
        return inputBuffer
    }

    private fun loadModelFile(): ByteBuffer {
        val fileDescriptor = requireContext().assets.openFd("drowsiness_model.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (::tflite.isInitialized) tflite.close()
        toneGenerator.release()
        cameraExecutor.shutdown()
    }
}
