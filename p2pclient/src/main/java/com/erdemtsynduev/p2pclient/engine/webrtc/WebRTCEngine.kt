package com.erdemtsynduev.p2pclient.engine.webrtc

import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.projection.MediaProjection
import android.text.TextUtils
import android.view.View
import com.erdemtsynduev.p2pclient.engine.EngineCallback
import com.erdemtsynduev.p2pclient.engine.IEngine
import com.erdemtsynduev.p2pclient.engine.peer.IPeerEvent
import com.erdemtsynduev.p2pclient.engine.peer.Peer
import com.erdemtsynduev.p2pclient.render.ProxyVideoSink
import org.webrtc.*
import org.webrtc.CameraVideoCapturer.CameraSwitchHandler
import org.webrtc.PeerConnection.IceServer
import org.webrtc.audio.AudioDeviceModule
import org.webrtc.audio.JavaAudioDeviceModule
import java.lang.Exception
import java.util.concurrent.ConcurrentHashMap

class WebRTCEngine(var isAudioOnly: Boolean?, private val context: Context?) : IEngine, IPeerEvent {

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var rootEglBase: EglBase? = null
    private var localMediaStream: MediaStream? = null
    private var videoSource: VideoSource? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var androidVideoCapture: VideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var localSink: ProxyVideoSink? = null
    private var localRenderer: SurfaceViewRenderer? = null
    private val peers: ConcurrentHashMap<String, Peer> = ConcurrentHashMap()

    private val iceServers: MutableList<IceServer> = arrayListOf(
        IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
    )

    private var engineCallback: EngineCallback? = null
    private val audioManager: AudioManager? =
        context?.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var isSwitch = false

    override fun init(callback: EngineCallback?) {
        engineCallback = callback
        if (rootEglBase == null) {
            rootEglBase = EglBase.create()
        }
        if (peerConnectionFactory == null) {
            peerConnectionFactory = createConnectionFactory()
        }
        if (localMediaStream == null) {
            createLocalStream()
        }
    }

    override fun joinRoom(userIds: List<String?>?) {
        userIds?.forEach { id ->
            val peer = Peer(peerConnectionFactory, iceServers, id, this)
            peer.setOffer(false)
            peer.addLocalStream(localMediaStream)
            id?.let {
                peers[it] = peer
            }
        }
        engineCallback?.joinRoomSucc()
        audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
    }

    override fun userIn(userId: String?) {
        // create Peer
        val peer = Peer(peerConnectionFactory, iceServers, userId, this)
        peer.setOffer(true)
        // add localStream
        peer.addLocalStream(localMediaStream)
        userId?.let { peers[userId] = peer }
        // createOffer
        peer.createOffer()
    }

    override fun userReject(userId: String?) {}

    override fun receiveOffer(userId: String?, description: String?) {
        val peer = peers[userId]
        if (peer != null) {
            val sdp = SessionDescription(SessionDescription.Type.OFFER, description)
            peer.setOffer(false)
            peer.setRemoteDescription(sdp)
            peer.createAnswer()
        }
    }

    override fun receiveAnswer(userId: String?, sdp: String?) {
        val peer = peers[userId]
        if (peer != null) {
            val sessionDescription = SessionDescription(SessionDescription.Type.ANSWER, sdp)
            peer.setRemoteDescription(sessionDescription)
        }
    }

    override fun receiveIceCandidate(userId: String?, id: String?, label: Int, candidate: String?) {
        val peer = peers[userId]
        if (peer != null) {
            val iceCandidate = IceCandidate(id, label, candidate)
            peer.addRemoteIceCandidate(iceCandidate)
        }
    }

    override fun leaveRoom(userId: String?) {
        val peer = peers[userId]
        if (peer != null) {
            peer.close()
            peers.remove(userId)
        }
        if (peers.size == 0) {
            engineCallback?.exitRoom()
        }
    }

    override fun startPreview(isOverlay: Boolean): View? {
        localRenderer = SurfaceViewRenderer(context)
        localRenderer?.init(rootEglBase!!.eglBaseContext, null)
        localRenderer?.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_BALANCED)
        localRenderer?.setMirror(false)
        localRenderer?.setZOrderMediaOverlay(isOverlay)
        localRenderer?.setEnableHardwareScaler(true)
        localSink = ProxyVideoSink()
        localSink?.setTarget(localRenderer)
        if (localMediaStream?.videoTracks?.size!! > 0) {
            localMediaStream?.videoTracks!![0].addSink(localSink)
        }
        return localRenderer
    }

    override fun stopPreview() {
        if (localSink != null) {
            localSink?.setTarget(null)
            localSink = null
        }
        if (audioSource != null) {
            audioSource?.dispose()
            audioSource = null
        }

        if (androidVideoCapture != null) {
            try {
                androidVideoCapture?.stopCapture()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
            androidVideoCapture?.dispose()
            androidVideoCapture = null
        }

        if (surfaceTextureHelper != null) {
            surfaceTextureHelper?.dispose()
            surfaceTextureHelper = null
        }
        if (videoSource != null) {
            videoSource?.dispose()
            videoSource = null
        }
        if (localMediaStream != null) {
            localMediaStream = null
        }
        if (localRenderer != null) {
            localRenderer?.release()
        }
    }

    override fun startStream() {}
    override fun stopStream() {}
    override fun setupRemoteVideo(userId: String?, isO: Boolean): View? {
        if (TextUtils.isEmpty(userId)) {
            return null
        }
        val peer = peers[userId] ?: return null
        if (peer.surfaceViewRenderer == null) {
            peer.createRender(rootEglBase, context, isO)
        }
        return peer.surfaceViewRenderer
    }

    override fun stopRemoteVideo() {}

    override fun switchCamera() {
        if (isSwitch) return
        isSwitch = true
        if (androidVideoCapture == null) return
        if (androidVideoCapture is CameraVideoCapturer) {
            val cameraVideoCapturer = androidVideoCapture as CameraVideoCapturer
            try {
                cameraVideoCapturer.switchCamera(object : CameraSwitchHandler {
                    override fun onCameraSwitchDone(isFrontCamera: Boolean) {
                        isSwitch = false
                    }

                    override fun onCameraSwitchError(errorDescription: String) {
                        isSwitch = false
                    }
                })
            } catch (e: Exception) {
                isSwitch = false
            }
        }
    }

    override fun muteAudio(enable: Boolean): Boolean {
        if (localAudioTrack != null) {
            localAudioTrack?.setEnabled(false)
            return true
        }
        return false
    }

    override fun toggleSpeaker(enable: Boolean): Boolean {
        if (audioManager != null) {
            if (enable) {
                audioManager.isSpeakerphoneOn = true
                audioManager.setStreamVolume(
                    AudioManager.STREAM_VOICE_CALL,
                    audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL),
                    AudioManager.STREAM_VOICE_CALL
                )
            } else {
                audioManager.isSpeakerphoneOn = false
                audioManager.setStreamVolume(
                    AudioManager.STREAM_VOICE_CALL,
                    audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL),
                    AudioManager.STREAM_VOICE_CALL
                )
            }
            return true
        }
        return false
    }

    override fun release() {
        if (audioManager != null) {
            audioManager.mode = AudioManager.MODE_NORMAL
        }

        for (peer in peers.values) {
            peer.close()
        }
        peers.clear()

        stopPreview()
        if (peerConnectionFactory != null) {
            peerConnectionFactory?.dispose()
            peerConnectionFactory = null
        }
        if (rootEglBase != null) {
            rootEglBase?.release()
            rootEglBase = null
        }
    }

    fun createConnectionFactory(): PeerConnectionFactory {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions
                .builder(context)
                .createInitializationOptions()
        )

        val encoderFactory: VideoEncoderFactory
        val decoderFactory: VideoDecoderFactory
        encoderFactory = DefaultVideoEncoderFactory(
            rootEglBase?.eglBaseContext,
            true,
            true
        )
        decoderFactory = DefaultVideoDecoderFactory(rootEglBase?.eglBaseContext)

        val audioDeviceModule: AudioDeviceModule =
            JavaAudioDeviceModule.builder(context).createAudioDeviceModule()
        val options = PeerConnectionFactory.Options()
        return PeerConnectionFactory.builder()
            .setOptions(options)
            .setAudioDeviceModule(audioDeviceModule)
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }

    fun createLocalStream() {
        localMediaStream = peerConnectionFactory!!.createLocalMediaStream("ARDAMS")
        audioSource = peerConnectionFactory!!.createAudioSource(createAudioConstraints())
        localAudioTrack = peerConnectionFactory!!.createAudioTrack(AUDIO_TRACK_ID, audioSource)
        localMediaStream?.addTrack(localAudioTrack)

        if (isAudioOnly == false) {
            androidVideoCapture = createVideoCapture()
            surfaceTextureHelper =
                SurfaceTextureHelper.create("CaptureThread", rootEglBase!!.eglBaseContext)
            videoSource = peerConnectionFactory?.createVideoSource(
                androidVideoCapture?.isScreencast == true
            )
            androidVideoCapture?.initialize(
                surfaceTextureHelper,
                context,
                videoSource?.capturerObserver
            )
            androidVideoCapture?.startCapture(VIDEO_RESOLUTION_WIDTH_DEFAULT, VIDEO_RESOLUTION_HEIGHT_DEFAULT, FPS)
            localMediaStream?.addTrack(
                peerConnectionFactory?.createVideoTrack(
                    VIDEO_TRACK_ID,
                    videoSource
                )
            )
        }
    }

    private val screenCaptureEnabled = false

    private fun createVideoCapture(): VideoCapturer? {
        if (screenCaptureEnabled) {
            return createScreenCapture()
        }
        val videoCapturer: VideoCapturer? = if (Camera2Enumerator.isSupported(context)) {
            createCameraCapture(Camera2Enumerator(context))
        } else {
            createCameraCapture(Camera1Enumerator(true))
        }
        return videoCapturer
    }

    private fun createCameraCapture(enumerator: CameraEnumerator): VideoCapturer? {
        val deviceNames = enumerator.deviceNames

        // First, try to find front facing camera
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                val videoCapturer: VideoCapturer? = enumerator.createCapturer(deviceName, null)
                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }

        // Front facing camera not found, try something else
        for (deviceName in deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                val videoCapturer: VideoCapturer? = enumerator.createCapturer(deviceName, null)
                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }
        return null
    }

    @TargetApi(21)
    private fun createScreenCapture(): VideoCapturer? {
        return if (mediaProjectionPermissionResultCode != Activity.RESULT_OK) {
            null
        } else ScreenCapturerAndroid(
            mediaProjectionPermissionResultData, object : MediaProjection.Callback() {
                override fun onStop() {}
            })
    }

    private fun createAudioConstraints(): MediaConstraints {
        val audioConstraints = MediaConstraints()
        audioConstraints.mandatory.add(
            MediaConstraints.KeyValuePair(AUDIO_ECHO_CANCELLATION_CONSTRAINT, "true")
        )
        audioConstraints.mandatory.add(
            MediaConstraints.KeyValuePair(AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT, "true")
        )
        audioConstraints.mandatory.add(
            MediaConstraints.KeyValuePair(AUDIO_HIGH_PASS_FILTER_CONSTRAINT, "true")
        )
        audioConstraints.mandatory.add(
            MediaConstraints.KeyValuePair(AUDIO_NOISE_SUPPRESSION_CONSTRAINT, "true")
        )
        return audioConstraints
    }

    override fun onSendIceCandidate(userId: String?, candidate: IceCandidate?) {
        if (engineCallback != null) {
            engineCallback?.onSendIceCandidate(userId, candidate)
        }
    }

    override fun onSendOffer(userId: String?, description: SessionDescription?) {
        if (engineCallback != null) {
            engineCallback?.onSendOffer(userId, description)
        }
    }

    override fun onSendAnswer(userId: String?, description: SessionDescription?) {
        if (engineCallback != null) {
            engineCallback?.onSendAnswer(userId, description)
        }
    }

    override fun onRemoteStream(userId: String?, stream: MediaStream?) {
        if (engineCallback != null) {
            engineCallback?.onRemoteStream(userId)
        }
    }

    override fun onRemoveStream(userId: String?, stream: MediaStream?) {
        leaveRoom(userId)
    }

    companion object {
        private const val VIDEO_TRACK_ID = "ARDAMSv0"
        private const val AUDIO_TRACK_ID = "ARDAMSa0"
        const val VIDEO_CODEC_H264 = "H264"
        private const val VIDEO_RESOLUTION_WIDTH_DEFAULT = 1280
        private const val VIDEO_RESOLUTION_HEIGHT_DEFAULT = 720
        private const val FPS = 30
        private val mediaProjectionPermissionResultData: Intent? = null
        private const val mediaProjectionPermissionResultCode = 0

        private const val AUDIO_ECHO_CANCELLATION_CONSTRAINT = "googEchoCancellation"
        private const val AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT = "googAutoGainControl"
        private const val AUDIO_HIGH_PASS_FILTER_CONSTRAINT = "googHighpassFilter"
        private const val AUDIO_NOISE_SUPPRESSION_CONSTRAINT = "googNoiseSuppression"
    }
}