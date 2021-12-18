package com.erdemtsynduev.p2pclient.call

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.View
import com.erdemtsynduev.p2pclient.AVEngine
import com.erdemtsynduev.p2pclient.engine.EngineCallback
import com.erdemtsynduev.p2pclient.engine.webrtc.WebRTCEngine
import com.erdemtsynduev.p2pclient.inter.InterEvent
import com.erdemtsynduev.p2pclient.model.calltype.CallState
import com.erdemtsynduev.p2pclient.model.calltype.RefuseType
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CallSession(
    context: Context?,
    private var roomId: String?,
    private var isAudioOnly: Boolean?,
    private var interEvent: InterEvent?
) : EngineCallback {

    private var sessionCallback: WeakReference<CallSessionCallback?>? = null
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    private var mUserIDList: List<String?>? = null

    var mTargetId: String? = null

    var mMyId: String? = null

    private var mRoomSize = 0
    var isComing = false
    private var _callState: CallState = CallState.Idle

    var startTime: Long = 0

    private val avEngine: AVEngine? = AVEngine.createEngine(WebRTCEngine(isAudioOnly, context))

    fun createHome(room: String?, roomSize: Int) {
        executor.execute {
            interEvent?.createRoom(room, roomSize)
        }
    }

    fun joinHome(roomId: String?) {
        executor.execute {
            _callState = CallState.Connecting
            interEvent?.sendJoin(roomId)
        }
    }

    fun shouldStartRing() {
        interEvent?.shouldStartRing(true)
    }

    fun shouldStopRing() {
        interEvent?.shouldStopRing()
    }

    fun sendRingBack(targetId: String?, room: String?) {
        executor.execute {
            interEvent?.sendRingBack(targetId, room)
        }
    }

    fun sendRefuse() {
        executor.execute {
            interEvent?.sendRefuse(
                roomId,
                mTargetId, RefuseType.Hangup.ordinal
            )
        }
    }

    fun sendBusyRefuse(room: String?, targetId: String?) {
        executor.execute {
            interEvent?.sendRefuse(
                room,
                targetId, RefuseType.Busy.ordinal
            )
        }
    }

    fun sendCancel() {
        executor.execute {
            if (interEvent != null) {
                val list: MutableList<String?> = ArrayList()
                list.add(mTargetId)
                interEvent?.sendCancel(roomId, list)
            }
        }
    }

    fun leave() {
        executor.execute {
            interEvent?.sendLeave(roomId, mMyId)
        }
        release()
    }

    fun sendTransAudio() {
        executor.execute {
            interEvent?.sendTransAudio(mTargetId)
        }
    }

    fun toggleMuteAudio(enable: Boolean): Boolean {
        return avEngine!!.muteAudio(enable)
    }

    fun toggleSpeaker(enable: Boolean): Boolean {
        return avEngine!!.toggleSpeaker(enable)
    }

    fun switchToAudio() {
        isAudioOnly = true
        sendTransAudio()
        if (sessionCallback?.get() != null) {
            sessionCallback?.get()?.didChangeMode(true)
        }
    }

    fun switchCamera() {
        avEngine?.switchCamera()
    }

    private fun release() {
        executor.execute {
            avEngine?.release()
            _callState = CallState.Idle
            if (sessionCallback?.get() != null) {
                sessionCallback?.get()?.didCallEndWithReason(null)
            }
        }
    }

    fun onJoinHome(myId: String?, users: String, roomSize: Int) {
        mRoomSize = roomSize
        startTime = 0
        handler.post {
            executor.execute {
                mMyId = myId
                val strings: List<String?>
                if (!TextUtils.isEmpty(users)) {
                    val split = users.split(",".toRegex()).toTypedArray()
                    strings = listOf(*split)
                    mUserIDList = strings
                }
                if (!isComing) {
                    if (roomSize == 2) {
                        val inviteList: MutableList<String?> = ArrayList()
                        inviteList.add(mTargetId)
                        interEvent?.sendInvite(roomId, inviteList, isAudioOnly)
                    }
                } else {
                    avEngine?.joinRoom(mUserIDList)
                }
                if (isAudioOnly == false) {
                    if (sessionCallback?.get() != null) {
                        sessionCallback?.get()?.didCreateLocalVideoTrack()
                    }
                }
            }
        }
    }

    fun newPeer(userId: String?) {
        handler.post {
            executor.execute {
                avEngine?.userIn(userId)
                interEvent?.shouldStopRing()
                _callState = CallState.Connected
                if (sessionCallback?.get() != null) {
                    startTime = System.currentTimeMillis()
                    sessionCallback?.get()?.didChangeState(_callState)
                }
            }
        }
    }

    fun onRefuse(userId: String?) {
        avEngine?.userReject(userId)
    }

    fun onRingBack(userId: String?) {
        interEvent?.shouldStartRing(false)
    }

    fun onTransAudio(userId: String?) {
        isAudioOnly = true
        if (sessionCallback?.get() != null) {
            sessionCallback?.get()?.didChangeMode(true)
        }
    }

    fun onDisConnect(userId: String?) {}

    fun onCancel(userId: String?) {
        release()
    }

    fun onReceiveOffer(userId: String?, description: String?) {
        executor.execute { avEngine?.receiveOffer(userId, description) }
    }

    fun onReceiverAnswer(userId: String?, sdp: String?) {
        executor.execute { avEngine?.receiveAnswer(userId, sdp) }
    }

    fun onRemoteIceCandidate(userId: String?, id: String?, label: Int, candidate: String?) {
        executor.execute {
            avEngine?.receiveIceCandidate(
                userId,
                id,
                label,
                candidate
            )
        }
    }

    fun onLeave(userId: String?) {
        if (mRoomSize > 2) {
            if (sessionCallback?.get() != null) {
                sessionCallback?.get()?.didUserLeave(userId)
            }
        }
        executor.execute { avEngine?.leaveRoom(userId) }
    }

    fun setupLocalVideo(isOverlay: Boolean): View? {
        return avEngine?.startPreview(isOverlay)
    }

    fun setupRemoteVideo(userId: String?, isOverlay: Boolean): View? {
        return avEngine?.setupRemoteVideo(userId, isOverlay)
    }

    fun setTargetId(targetIds: String?) {
        mTargetId = targetIds
    }

    fun setRoom(_room: String) {
        roomId = _room
    }

    val state: CallState
        get() = _callState

    fun setCallState(callState: CallState) {
        _callState = callState
    }

    fun setSessionCallback(sessionCallback: CallSessionCallback?) {
        this.sessionCallback = WeakReference(sessionCallback)
    }

    override fun joinRoomSucc() {
        interEvent?.shouldStopRing()
        _callState = CallState.Connected
        if (sessionCallback?.get() != null) {
            startTime = System.currentTimeMillis()
            sessionCallback?.get()?.didChangeState(_callState)
        }
    }

    override fun exitRoom() {
        if (mRoomSize == 2) {
            handler.post { release() }
        }
    }

    override fun onSendIceCandidate(userId: String?, candidate: IceCandidate?) {
        executor.execute {
            if (interEvent != null) {
                try {
                    Thread.sleep(100)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
                interEvent?.sendIceCandidate(
                    userId,
                    candidate?.sdpMid,
                    candidate?.sdpMLineIndex,
                    candidate?.sdp
                )
            }
        }
    }

    override fun onSendOffer(userId: String?, description: SessionDescription?) {
        executor.execute {
            interEvent?.sendOffer(userId, description?.description)
        }
    }

    override fun onSendAnswer(userId: String?, description: SessionDescription?) {
        executor.execute {
            interEvent?.sendAnswer(userId, description?.description)
        }
    }

    override fun onRemoteStream(userId: String?) {
        if (sessionCallback?.get() != null) {
            sessionCallback?.get()?.didReceiveRemoteVideoTrack(userId)
        }
    }

    init {
        avEngine?.init(this)
    }
}