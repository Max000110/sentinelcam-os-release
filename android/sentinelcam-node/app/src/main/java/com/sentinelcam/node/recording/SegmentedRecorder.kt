package com.sentinelcam.node.recording

import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.Executors

data class BufferedFrame(
    val data: ByteArray,
    val bufferInfo: MediaCodec.BufferInfo,
    val timestampMs: Long
)

class SegmentedRecorder(
    private val context: Context,
    private val deviceId: String,
    private val segmentDurationMs: Long = 60_000L, // 1 minute default segments
    private val preEventBufferMs: Long = 10_000L,  // 10s pre-event buffer
    private val postEventDurationMs: Long = 30_000L // 30s post-event buffer
) {
    private val TAG = "SentinelCam.Recorder"
    private val executor = Executors.newSingleThreadExecutor()

    // Bounded circular buffer for pre-event frames
    private val circularFrameBuffer = ConcurrentLinkedDeque<BufferedFrame>()
    private val MAX_BUFFER_FRAMES = 300 // ~10 seconds at 30 fps

    private var currentMuxer: MediaMuxer? = null
    private var videoTrackIndex = -1
    private var isRecording = false
    private var currentSegmentFile: File? = null
    private var segmentStartTimeMs = 0L

    fun initializeStorageDirectory(): File {
        val dir = File(context.filesDir, "sentinelcam/recordings/$deviceId")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun onEncodedFrameReceived(data: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        val frameBytes = ByteArray(bufferInfo.size)
        val originalPos = data.position()
        data.get(frameBytes)
        data.position(originalPos)

        val now = System.currentTimeMillis()
        val copyInfo = MediaCodec.BufferInfo().apply {
            set(0, bufferInfo.size, bufferInfo.presentationTimeUs, bufferInfo.flags)
        }

        // Maintain 10-second rolling buffer
        circularFrameBuffer.addLast(BufferedFrame(frameBytes, copyInfo, now))
        while (circularFrameBuffer.size > MAX_BUFFER_FRAMES) {
            circularFrameBuffer.pollFirst()
        }

        // If actively recording, write to current MP4 segment
        if (isRecording && currentMuxer != null && videoTrackIndex >= 0) {
            try {
                currentMuxer?.writeSampleData(videoTrackIndex, data, bufferInfo)
                if (now - segmentStartTimeMs >= segmentDurationMs) {
                    finalizeCurrentSegment()
                    startNewSegment()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error writing frame to segment: ${e.message}")
            }
        }
    }

    fun startMotionTriggeredRecording(onFinalized: (File, String, Long) -> Unit) {
        if (isRecording) return
        executor.execute {
            try {
                startNewSegment()
                isRecording = true
                Log.i(TAG, "Motion-triggered recording started with 10s pre-buffer")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start motion recording: ${e.message}")
            }
        }
    }

    fun stopRecording(onFinalized: (File, String, Long) -> Unit) {
        if (!isRecording) return
        executor.execute {
            isRecording = false
            finalizeCurrentSegment(onFinalized)
            Log.i(TAG, "Recording stopped and finalized")
        }
    }

    private fun startNewSegment() {
        val dir = initializeStorageDirectory()
        val filename = "seg_${System.currentTimeMillis()}_${deviceId}.mp4"
        currentSegmentFile = File(dir, filename)
        segmentStartTimeMs = System.currentTimeMillis()

        currentMuxer = MediaMuxer(currentSegmentFile!!.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 1280, 720)
        videoTrackIndex = currentMuxer!!.addTrack(format)
        currentMuxer!!.start()
    }

    private fun finalizeCurrentSegment(onFinalized: ((File, String, Long) -> Unit)? = null) {
        val muxer = currentMuxer ?: return
        val file = currentSegmentFile ?: return
        try {
            muxer.stop()
            muxer.release()
            currentMuxer = null

            val checksum = calculateSha256(file)
            val durationSec = (System.currentTimeMillis() - segmentStartTimeMs) / 1000
            Log.i(TAG, "Finalized MP4 segment: ${file.name}, SHA-256: $checksum, Duration: ${durationSec}s")
            onFinalized?.invoke(file, checksum, durationSec)
        } catch (e: Exception) {
            Log.e(TAG, "Error finalizing segment: ${e.message}")
        }
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun shutdown() {
        try {
            executor.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down recorder executor: ${e.message}")
        }
    }
}
