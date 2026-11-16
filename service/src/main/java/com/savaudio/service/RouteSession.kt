package com.savaudio.service

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.projection.MediaProjection
import android.util.Log

/**
 * One routed app: captures that app's playback (by UID) and replays it on a pinned output.
 * The capture is limited to the target UID, so our own replay is never fed back in.
 */
@SuppressLint("MissingPermission")
internal class RouteSession(
    val packageName: String,
    uid: Int,
    projection: MediaProjection,
    val deviceId: Int,
    device: AudioDeviceInfo,
) {
    private val record: AudioRecord
    private val track: AudioTrack
    private val bufferBytes: Int
    @Volatile private var running = false
    private var worker: Thread? = null

    init {
        val captureConfig = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUid(uid)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()

        val inFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
            .build()
        val outFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build()

        val minIn = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val minOut = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        bufferBytes = maxOf(minIn, minOut, 4096)

        record = AudioRecord.Builder()
            .setAudioFormat(inFormat)
            .setBufferSizeInBytes(bufferBytes * 2)
            .setAudioPlaybackCaptureConfig(captureConfig)
            .build()

        track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(outFormat)
            .setBufferSizeInBytes(bufferBytes * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()

        if (record.state != AudioRecord.STATE_INITIALIZED || track.state != AudioTrack.STATE_INITIALIZED) {
            release()
            throw IllegalStateException("Audio capture could not start for this app")
        }
        if (!track.setPreferredDevice(device)) {
            release()
            throw IllegalStateException("The output refused the route")
        }
    }

    fun start() {
        running = true
        worker = Thread({
            val buffer = ByteArray(bufferBytes)
            try {
                record.startRecording()
                track.play()
                while (running) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read > 0) track.write(buffer, 0, read) else if (read < 0) break
                }
            } catch (e: Exception) {
                Log.w(TAG, "Route for $packageName ended: ${e.message}")
            }
        }, "route-$packageName").also { it.start() }
    }

    fun stop() {
        running = false
        try { record.stop() } catch (_: Exception) {}
        try { worker?.join(500) } catch (_: InterruptedException) {}
        worker = null
        release()
    }

    private fun release() {
        try { track.pause(); track.flush() } catch (_: Exception) {}
        try { track.release() } catch (_: Exception) {}
        try { record.release() } catch (_: Exception) {}
    }

    private companion object {
        const val TAG = "RouteSession"
        const val SAMPLE_RATE = 48000
    }
}
