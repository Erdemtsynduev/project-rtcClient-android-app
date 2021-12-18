package com.erdemtsynduev.p2pclient.engine.peer

import android.content.Context
import com.erdemtsynduev.p2pclient.render.ProxyVideoSink
import org.webrtc.*
import org.webrtc.PeerConnection.*
import org.webrtc.RendererCommon.RendererEvents
import java.util.ArrayList

class Peer(
    private val peerConnectionFactory: PeerConnectionFactory?,
    private val iceServerList: List<IceServer>?,
    private val userId: String?,
    private val iPeerEvent: IPeerEvent?
) : SdpObserver, Observer {

    private var peerConnection: PeerConnection? = createPeerConnection()
    private var queuedRemoteCandidates: MutableList<IceCandidate>? = ArrayList<IceCandidate>()
    private var localSdp: SessionDescription? = null
    private var isOffer = false

    var remoteMediaStream: MediaStream? = null
    var surfaceViewRenderer: SurfaceViewRenderer? = null
    var proxyVideoSink: ProxyVideoSink? = null

    private fun createPeerConnection(): PeerConnection? {
        val rtcConfig = RTCConfiguration(iceServerList)
        return peerConnectionFactory?.createPeerConnection(rtcConfig, this)
    }

    fun setOffer(isOffer: Boolean) {
        this.isOffer = isOffer
    }

    fun createOffer() {
        if (peerConnection == null) return
        peerConnection?.createOffer(this, offerOrAnswerConstraint())
    }

    fun createAnswer() {
        if (peerConnection == null) return
        peerConnection?.createAnswer(this, offerOrAnswerConstraint())
    }

    fun setLocalDescription(sdp: SessionDescription?) {
        if (peerConnection == null) return
        peerConnection?.setLocalDescription(this, sdp)
    }

    fun setRemoteDescription(sdp: SessionDescription?) {
        if (peerConnection == null) return
        peerConnection?.setRemoteDescription(this, sdp)
    }

    fun addLocalStream(stream: MediaStream?) {
        if (peerConnection == null) return
        peerConnection?.addStream(stream)
    }

    fun addRemoteIceCandidate(candidate: IceCandidate) {
        if (peerConnection != null) {
            if (queuedRemoteCandidates != null) {
                queuedRemoteCandidates?.add(candidate)
            } else {
                peerConnection?.addIceCandidate(candidate)
            }
        }
    }

    fun removeRemoteIceCandidates(candidates: Array<IceCandidate?>?) {
        if (peerConnection == null) {
            return
        }
        drainCandidates()
        peerConnection?.removeIceCandidates(candidates)
    }

    fun createRender(rootEglBase: EglBase?, context: Context?, isOverlay: Boolean) {
        surfaceViewRenderer = SurfaceViewRenderer(context)
        surfaceViewRenderer?.init(rootEglBase?.eglBaseContext, object : RendererEvents {
            override fun onFirstFrameRendered() {}

            override fun onFrameResolutionChanged(
                videoWidth: Int,
                videoHeight: Int,
                rotation: Int
            ) {}
        })
        surfaceViewRenderer?.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
        surfaceViewRenderer?.setMirror(true)
        surfaceViewRenderer?.setZOrderMediaOverlay(isOverlay)
        proxyVideoSink = ProxyVideoSink()
        proxyVideoSink?.setTarget(surfaceViewRenderer)
        if (remoteMediaStream != null && remoteMediaStream!!.videoTracks.size > 0) {
            remoteMediaStream?.videoTracks?.get(0)?.addSink(proxyVideoSink)
        }
    }

    fun close() {
        if (surfaceViewRenderer != null) {
            surfaceViewRenderer?.release()
            surfaceViewRenderer = null
        }
        if (proxyVideoSink != null) {
            proxyVideoSink?.setTarget(null)
        }
        if (peerConnection != null) {
            peerConnection?.close()
            peerConnection?.dispose()
        }
    }

    override fun onSignalingChange(signalingState: SignalingState) {}

    override fun onIceConnectionChange(newState: IceConnectionState) {}
    override fun onIceConnectionReceivingChange(receiving: Boolean) {}

    override fun onIceGatheringChange(newState: IceGatheringState) {}

    override fun onIceCandidate(candidate: IceCandidate) {
        iPeerEvent?.onSendIceCandidate(userId, candidate)
    }

    override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) {}

    override fun onAddStream(stream: MediaStream) {
        stream.audioTracks[0].setEnabled(true)
        remoteMediaStream = stream
        iPeerEvent?.onRemoteStream(userId, stream)
    }

    override fun onRemoveStream(stream: MediaStream) {
        iPeerEvent?.onRemoveStream(userId, stream)
    }

    override fun onDataChannel(dataChannel: DataChannel) {}

    override fun onRenegotiationNeeded() {}

    override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<MediaStream>) {}

    override fun onCreateSuccess(origSdp: SessionDescription) {
        val sdpString = origSdp.description
        val sdp = SessionDescription(origSdp.type, sdpString)
        localSdp = sdp
        setLocalDescription(sdp)
    }

    override fun onSetSuccess() {
        if (peerConnection == null) return
        if (isOffer) {
            if (peerConnection?.remoteDescription == null) {
                if (!isOffer) {
                    iPeerEvent?.onSendAnswer(userId, localSdp)
                } else {
                    iPeerEvent?.onSendOffer(userId, localSdp)
                }
            } else {
                drainCandidates()
            }
        } else {
            if (peerConnection?.localDescription != null) {
                if (!isOffer) {
                    iPeerEvent?.onSendAnswer(userId, localSdp)
                } else {
                    iPeerEvent?.onSendOffer(userId, localSdp)
                }
                drainCandidates()
            }
        }
    }

    override fun onCreateFailure(error: String) {}

    override fun onSetFailure(error: String) {}

    private fun drainCandidates() {
        if (queuedRemoteCandidates != null) {
            queuedRemoteCandidates?.forEach { candidate ->
                peerConnection?.addIceCandidate(candidate)
            }
            queuedRemoteCandidates?.clear()
        }
    }

    private fun offerOrAnswerConstraint(): MediaConstraints {
        val mediaConstraints = MediaConstraints()
        val keyValuePairs = ArrayList<MediaConstraints.KeyValuePair>()
        keyValuePairs.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        keyValuePairs.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        mediaConstraints.mandatory.addAll(keyValuePairs)
        return mediaConstraints
    }
}