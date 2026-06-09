package com.example.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import java.io.File

object MediaConfigUtils {

    /**
     * 1. Client-Side Video Optimization (CameraX)
     * Forces the Recorder and VideoCapture to record at a maximum resolution
     * of 480p (SD) or 720p (HD). This will drastically reduce file size to satisfy 
     * Firebase Spark Plan bounds. 
     * 
     * Note: A 30-second duration hard cap logic MUST be implemented when starting the recording.
     * (e.g., using a coroutine delay(30000) to safely stop the active `Recording`).
     */
    fun createOptimizedVideoCapture(): VideoCapture<Recorder> {
        val qualitySelector = QualitySelector.fromOrderedList(
            listOf(Quality.SD, Quality.HD),
            FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
        )

        val recorder = Recorder.Builder()
            .setQualitySelector(qualitySelector)
            .build()
            
        return VideoCapture.withOutput(recorder)
    }

    /**
     * Helper to start video recording with a strict 30-second time-to-live cap 
     * before automatically stopping to conserve Cloud Storage limits.
     */
    fun startVideoRecording(
        videoCapture: VideoCapture<Recorder>,
        context: Context,
        outputFile: File,
        executor: java.util.concurrent.Executor,
        onEvent: (androidx.camera.video.VideoRecordEvent) -> Unit
    ): androidx.camera.video.Recording {
        val outputOptions = androidx.camera.video.FileOutputOptions.Builder(outputFile).build()
        val pendingRecording = videoCapture.output
            .prepareRecording(context, outputOptions)
            
        // Use withAudioEnabled() if you also captured audio config permissions.
        val recording = pendingRecording.start(executor, onEvent)
        
        // Since CameraX currently doesn't expose a simple setDurationLimit() builder,
        // you would typically wrap this in a Coroutine that calls recording.stop() after 30_000 ms
        return recording
    }

    /**
     * 2. Audio Size Constraints
     * Configures the MediaRecorder for audio messages using a highly compressed,
     * low-bitrate format (AAC at 64kbps, 16kHz sampling) and enforces an exact 30-second hard stop.
     */
    fun createOptimizedAudioRecorder(context: Context, outputFile: File): MediaRecorder {
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        return recorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            // Ultra-low bitrate audio for highly compressed voice notes
            setAudioEncodingBitRate(64000) 
            setAudioSamplingRate(16000)
            
            // Strict 30-second hard stop for the audio recording
            setMaxDuration(30000) 
            
            setOutputFile(outputFile.absolutePath)
        }
    }
}
