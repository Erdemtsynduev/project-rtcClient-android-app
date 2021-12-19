package com.erdemtsynduev.rtcclient.rtc

import android.app.Application
import android.content.Context
import org.webrtc.*
import io.github.crow_misia.sdp.SdpMediaDescription
import io.github.crow_misia.sdp.SdpSessionDescription
import io.github.crow_misia.sdp.attribute.FormatAttribute
import io.github.crow_misia.sdp.attribute.RTPMapAttribute
import io.github.crow_misia.sdp.getAttribute
import io.github.crow_misia.sdp.getAttributes
import org.webrtc.VideoCodecType.*
import java.util.concurrent.Executors

class RTCClient(
    context: Application,
    observer: PeerConnection.Observer
) {

    companion object {
        private const val LOCAL_TRACK_ID = "local_track"
        private const val LOCAL_STREAM_ID = "local_track"
        private const val VIDEO_CODEC_PARAM_START_BITRATE = "x-google-start-bitrate"
        private const val AUDIO_CODEC_PARAM_BITRATE = "maxaveragebitrate"
        private const val AUDIO_CODEC_OPUS = "opus"
        private const val audioStartBitrate = 0
        private const val VIDEO_CODEC_VP8 = "VP8"
        private const val VIDEO_CODEC_VP9 = "VP9"
        private const val VIDEO_CODEC_H264 = "H264"
        private const val VIDEO_CODEC_H264_BASELINE = "H264 Baseline"
        private const val VIDEO_CODEC_H264_HIGH = "H264 High"
        private const val VIDEO_CODEC_AV1 = "AV1"
    }

    private var isError = false
    private var sdpObserver: SdpObserver? = null

    private val isVideoCallEnabled: Boolean = true
    private var videoCodec = "VP8"

    private val rootEglBase: EglBase = EglBase.create()

    init {
        initPeerConnectionFactory(context)
    }

    private val iceServer = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302")
            .createIceServer()
    )

    private val peerConnectionFactory by lazy { buildPeerConnectionFactory() }
    private val videoCapturer by lazy { getVideoCapturer(context) }
    private val localVideoSource by lazy { peerConnectionFactory.createVideoSource(false) }
    private val peerConnection by lazy { buildPeerConnection(observer) }
    private val constraints = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
    }

    private fun initPeerConnectionFactory(context: Application) {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
    }

    private fun buildPeerConnectionFactory(): PeerConnectionFactory {
        return PeerConnectionFactory
            .builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(rootEglBase.eglBaseContext))
            .setVideoEncoderFactory(
                DefaultVideoEncoderFactory(
                    rootEglBase.eglBaseContext,
                    true,
                    true
                )
            )
            .setOptions(PeerConnectionFactory.Options().apply {
                // Если не отключить то не будет работать с сервером why not
                disableEncryption = false
                disableNetworkMonitor = true
            })
            .createPeerConnectionFactory()
    }

    private fun buildPeerConnection(observer: PeerConnection.Observer) = peerConnectionFactory.createPeerConnection(
        iceServer,
        observer
    )

    // TODO Для эмулятора нужно использовать isBackFacing
    private fun getVideoCapturer(context: Context) =
        Camera2Enumerator(context).run {
            deviceNames.find {
                isFrontFacing(it)
            }?.let {
                createCapturer(it, null)
            } ?: throw IllegalStateException()
        }

    fun initSurfaceView(view: SurfaceViewRenderer) = view.run {
        setMirror(true)
        setEnableHardwareScaler(true)
        setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
        init(rootEglBase.eglBaseContext, null)
    }

    fun startLocalVideoCapture(localVideoOutput: SurfaceViewRenderer) {
        val surfaceTextureHelper = SurfaceTextureHelper.create(Thread.currentThread().name, rootEglBase.eglBaseContext)
        (videoCapturer as VideoCapturer).initialize(surfaceTextureHelper, localVideoOutput.context, localVideoSource.capturerObserver)
        videoCapturer.startCapture(320, 240, 60)
        val localVideoTrack = peerConnectionFactory.createVideoTrack(LOCAL_TRACK_ID, localVideoSource)
        localVideoTrack.addSink(localVideoOutput)
        val localStream = peerConnectionFactory.createLocalMediaStream(LOCAL_STREAM_ID)
        localStream.addTrack(localVideoTrack)
        val audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
        val audioTrack = peerConnectionFactory.createAudioTrack("audio_track", audioSource).apply {
            setEnabled(true)
            setVolume(100.0)
        }
        localStream.addTrack(audioTrack)
        peerConnection?.addStream(localStream)
    }

    private fun PeerConnection.call(sdpObserver: SdpObserver) {
        createOffer(object : SdpObserver by sdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {

                setLocalDescription(object : SdpObserver {
                    override fun onSetFailure(p0: String?) {
                    }

                    override fun onSetSuccess() {
                    }

                    override fun onCreateSuccess(p0: SessionDescription) {
                        val sdp = SdpSessionDescription.parse(p0.description)
                        preferCodec(sdp, getSdpVideoCodecName(), false)

                        val newDesc = SessionDescription(p0.type, sdp.toString())
                        val peerConnection = peerConnection ?: return
                        if (!isError) {
                            peerConnection.setLocalDescription(sdpObserver, newDesc)
                        }
                    }

                    override fun onCreateFailure(p0: String?) {
                    }
                }, desc)
                sdpObserver.onCreateSuccess(desc)
            }
        }, constraints)
    }

    private fun PeerConnection.answer(sdpObserver: SdpObserver) {
        createAnswer(object : SdpObserver by sdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {
                setLocalDescription(object : SdpObserver {
                    override fun onSetFailure(p0: String?) {
                    }

                    override fun onSetSuccess() {
                    }

                    override fun onCreateSuccess(p0: SessionDescription?) {
                    }

                    override fun onCreateFailure(p0: String?) {
                    }
                }, p0)
                sdpObserver.onCreateSuccess(p0)
            }
        }, constraints)
    }

    fun call(sdpObserver: SdpObserver) {
        this.sdpObserver = sdpObserver
        peerConnection?.call(sdpObserver)
    }

    fun answer(sdpObserver: SdpObserver) = peerConnection?.answer(sdpObserver)

    fun onRemoteSessionReceived(sessionDescription: SessionDescription) {
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetFailure(p0: String?) {
            }

            override fun onSetSuccess() {}

            override fun onCreateSuccess(p0: SessionDescription) {
            }

            override fun onCreateFailure(p0: String?) {
            }
        }, getRemoteDescription(sessionDescription))
    }

    fun addIceCandidate(iceCandidate: IceCandidate?) {
        peerConnection?.addIceCandidate(iceCandidate)
    }

    private fun getSdpVideoCodecName(): String {
        return when (videoCodec) {
            VIDEO_CODEC_VP8 -> VIDEO_CODEC_VP8
            VIDEO_CODEC_VP9 -> VIDEO_CODEC_VP9
            VIDEO_CODEC_AV1 -> VIDEO_CODEC_AV1
            VIDEO_CODEC_H264_HIGH, VIDEO_CODEC_H264_BASELINE -> VIDEO_CODEC_H264
            else -> VIDEO_CODEC_VP8
        }
    }

    private fun preferCodec(desc: SdpSessionDescription, codec: String, isAudio: Boolean) {
        val type = if (isAudio) "audio" else "video"
        val mediaDescription =
            desc.getMediaDescriptions().firstOrNull { it.type == type } ?: run {
                return
            }
        // A list with all the payload types with name `codec`. The payload types are integers in the
        // range 96-127, but they are stored as strings here.
        val codecPayloadTypes = mediaDescription.getAttributes<RTPMapAttribute>()
            .filter { it.encodingName == codec }
            .map { it.payloadType.toString() }
            .toList()

        if (codecPayloadTypes.isEmpty()) {
            return
        }
        movePayloadTypesToFront(codecPayloadTypes, mediaDescription)
    }

    private fun movePayloadTypesToFront(
        preferredPayloadTypes: List<String>,
        mediaDescription: SdpMediaDescription
    ) {
        // The format of the media description line should be: m=<media> <port> <proto> <fmt> ...
        val formats = mediaDescription.formats
        // Reconstruct the line with `preferredPayloadTypes` moved to the beginning of the payload types.
        formats.removeAll(preferredPayloadTypes)
        formats.addAll(0, preferredPayloadTypes)
    }

    fun getRemoteDescription(desc: SessionDescription): SessionDescription {
        val sdp = SdpSessionDescription.parse(desc.description)
        if (isVideoCallEnabled) {
            preferCodec(sdp, getSdpVideoCodecName(), false)
        }
        if (audioStartBitrate > 0) {
            setStartBitrate(
                sdp,
                AUDIO_CODEC_OPUS,
                false,
                audioStartBitrate
            )
        }
        return SessionDescription(desc.type, sdp.toString())
    }

    private fun setStartBitrate(
        sdp: SdpSessionDescription,
        codec: String,
        isVideoCodec: Boolean,
        bitrateKbps: Int
    ) {
        sdp.getMediaDescriptions()
            .mapNotNull { media ->
                // Search for codec rtpmap in format
                // a=rtpmap:<payload type> <encoding name>/<clock rate> [/<encoding parameters>]
                media.getAttribute<RTPMapAttribute>()?.let {
                    media to it
                }
            }
            .forEach { (media, rtmp) ->
                // Check if a=fmtp string already exist in remote SDP for this codec and
                // update it with new bitrate parameter.
                media.getAttributes<FormatAttribute>()
                    .filter { it.format == rtmp.payloadType }
                    .forEach {
                        if (isVideoCodec) {
                            it.addParameter(VIDEO_CODEC_PARAM_START_BITRATE, bitrateKbps)
                        } else {
                            it.addParameter(AUDIO_CODEC_PARAM_BITRATE, bitrateKbps * 1000)
                        }
                        return
                    }
            }
    }

    fun changeCaptureFormat(width: Int, height: Int, framerate: Int) {
        changeCaptureFormatInternal(width, height, framerate)
    }

    private fun changeCaptureFormatInternal(width: Int, height: Int, framerate: Int) {
        if (!isVideoCallEnabled || isError || videoCapturer == null) {
            return
        }
        localVideoSource?.adaptOutputFormat(width, height, framerate)
    }
}