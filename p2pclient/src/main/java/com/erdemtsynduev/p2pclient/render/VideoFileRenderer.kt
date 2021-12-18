package com.erdemtsynduev.p2pclient.render

import android.os.Handler
import android.os.HandlerThread
import org.webrtc.*
import java.io.FileOutputStream
import java.io.IOException
import java.lang.RuntimeException
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.concurrent.CountDownLatch

/**
 * Can be used to save the video frames to file.
 */
class VideoFileRenderer(
    outputFile: String, outputFileWidth: Int, outputFileHeight: Int,
    sharedContext: EglBase.Context?
) : VideoSink {

    private val renderThread: HandlerThread
    private val renderThreadHandler: Handler
    private val fileThread: HandlerThread
    private val fileThreadHandler: Handler
    private val videoOutFile: FileOutputStream
    private val outputFileName: String
    private val outputFileWidth: Int
    private val outputFileHeight: Int
    private val outputFrameSize: Int
    private val outputFrameBuffer: ByteBuffer
    private var eglBase: EglBase? = null
    private var yuvConverter: YuvConverter? = null
    private var frameCount = 0
    override fun onFrame(frame: VideoFrame) {
        frame.retain()
        renderThreadHandler.post { renderFrameOnRenderThread(frame) }
    }

    private fun renderFrameOnRenderThread(frame: VideoFrame) {
        val buffer = frame.buffer

        // If the frame is rotated, it will be applied after cropAndScale. Therefore, if the frame is
        // rotated by 90 degrees, swap width and height.
        val targetWidth = if (frame.rotation % 180 == 0) outputFileWidth else outputFileHeight
        val targetHeight = if (frame.rotation % 180 == 0) outputFileHeight else outputFileWidth
        val frameAspectRatio = buffer.width.toFloat() / buffer.height.toFloat()
        val fileAspectRatio = targetWidth.toFloat() / targetHeight.toFloat()

        // Calculate cropping to equalize the aspect ratio.
        var cropWidth = buffer.width
        var cropHeight = buffer.height
        if (fileAspectRatio > frameAspectRatio) {
            cropHeight = (cropHeight * (frameAspectRatio / fileAspectRatio)).toInt()
        } else {
            cropWidth = (cropWidth * (fileAspectRatio / frameAspectRatio)).toInt()
        }
        val cropX = (buffer.width - cropWidth) / 2
        val cropY = (buffer.height - cropHeight) / 2
        val scaledBuffer =
            buffer.cropAndScale(cropX, cropY, cropWidth, cropHeight, targetWidth, targetHeight)
        frame.release()
        val i420 = scaledBuffer.toI420()
        scaledBuffer.release()
        fileThreadHandler.post {
            YuvHelper.I420Rotate(
                i420.dataY,
                i420.strideY,
                i420.dataU,
                i420.strideU,
                i420.dataV,
                i420.strideV,
                outputFrameBuffer,
                i420.width,
                i420.height,
                frame.rotation
            )
            i420.release()
            try {
                videoOutFile.write("FRAME\n".toByteArray(Charset.forName("US-ASCII")))
                videoOutFile.write(
                    outputFrameBuffer.array(), outputFrameBuffer.arrayOffset(), outputFrameSize
                )
            } catch (e: IOException) {
                throw RuntimeException("Error writing video to disk", e)
            }
            frameCount++
        }
    }

    /**
     * Release all resources. All already posted frames will be rendered first.
     */
    fun release() {
        val cleanupBarrier = CountDownLatch(1)
        renderThreadHandler.post {
            yuvConverter!!.release()
            eglBase!!.release()
            renderThread.quit()
            cleanupBarrier.countDown()
        }
        ThreadUtils.awaitUninterruptibly(cleanupBarrier)
        fileThreadHandler.post {
            try {
                videoOutFile.close()
            } catch (e: IOException) {
                throw RuntimeException("Error closing output file", e)
            }
            fileThread.quit()
        }
        try {
            fileThread.join()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    init {
        require(!(outputFileWidth % 2 == 1 || outputFileHeight % 2 == 1)) { "Does not support uneven width or height" }
        outputFileName = outputFile
        this.outputFileWidth = outputFileWidth
        this.outputFileHeight = outputFileHeight
        outputFrameSize = outputFileWidth * outputFileHeight * 3 / 2
        outputFrameBuffer = ByteBuffer.allocateDirect(outputFrameSize)
        videoOutFile = FileOutputStream(outputFile)
        videoOutFile.write(
            "YUV4MPEG2 C420 W$outputFileWidth H$outputFileHeight Ip F30:1 A1:1\n"
                .toByteArray(Charset.forName("US-ASCII"))
        )
        renderThread = HandlerThread("VideoFileRendererRenderThread")
        renderThread.start()
        renderThreadHandler = Handler(renderThread.looper)
        fileThread = HandlerThread("VideoFileRendererFileThread")
        fileThread.start()
        fileThreadHandler = Handler(fileThread.looper)
        ThreadUtils.invokeAtFrontUninterruptibly(renderThreadHandler) {
            eglBase = EglBase.create(sharedContext, EglBase.CONFIG_PIXEL_BUFFER)
            eglBase?.createDummyPbufferSurface()
            eglBase?.makeCurrent()
            yuvConverter = YuvConverter()
        }
    }
}