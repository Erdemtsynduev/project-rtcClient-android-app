package com.erdemtsynduev.p2pclient

import android.view.View
import com.erdemtsynduev.p2pclient.engine.EngineCallback
import com.erdemtsynduev.p2pclient.engine.IEngine

class AVEngine(private val iEngine: IEngine?) : IEngine {

    override fun init(callback: EngineCallback?) {
        if (iEngine == null) {
            return
        }
        iEngine.init(callback)
    }

    override fun joinRoom(userIds: List<String?>?) {
        if (iEngine == null) {
            return
        }
        iEngine.joinRoom(userIds)
    }

    override fun userIn(userId: String?) {
        if (iEngine == null) {
            return
        }
        iEngine.userIn(userId)
    }

    override fun userReject(userId: String?) {
        if (iEngine == null) {
            return
        }
        iEngine.userReject(userId)
    }

    override fun receiveOffer(userId: String?, description: String?) {
        if (iEngine == null) {
            return
        }
        iEngine.receiveOffer(userId, description)
    }

    override fun receiveAnswer(userId: String?, sdp: String?) {
        if (iEngine == null) {
            return
        }
        iEngine.receiveAnswer(userId, sdp)
    }

    override fun receiveIceCandidate(userId: String?, id: String?, label: Int, candidate: String?) {
        if (iEngine == null) {
            return
        }
        iEngine.receiveIceCandidate(userId, id, label, candidate)
    }

    override fun leaveRoom(userId: String?) {
        if (iEngine == null) {
            return
        }
        iEngine.leaveRoom(userId)
    }

    override fun startPreview(isO: Boolean): View? {
        return iEngine?.startPreview(isO)
    }

    override fun stopPreview() {
        if (iEngine == null) {
            return
        }
        iEngine.stopPreview()
    }

    override fun startStream() {
        if (iEngine == null) {
            return
        }
        iEngine.startStream()
    }

    override fun stopStream() {
        if (iEngine == null) {
            return
        }
        iEngine.stopStream()
    }

    override fun setupRemoteVideo(userId: String?, isO: Boolean): View? {
        return iEngine?.setupRemoteVideo(userId, isO)
    }

    override fun stopRemoteVideo() {
        if (iEngine == null) {
            return
        }
        iEngine.stopRemoteVideo()
    }

    override fun switchCamera() {
        if (iEngine == null) {
            return
        }
        iEngine.switchCamera()
    }

    override fun muteAudio(enable: Boolean): Boolean {
        return iEngine?.muteAudio(enable) ?: false
    }

    override fun toggleSpeaker(enable: Boolean): Boolean {
        return iEngine?.toggleSpeaker(enable) ?: false
    }

    override fun release() {
        if (iEngine == null) {
            return
        }
        iEngine.release()
    }

    companion object {
        @Volatile
        private var instance: AVEngine? = null
        fun createEngine(engine: IEngine?): AVEngine? {
            if (null == instance) {
                synchronized(AVEngine::class.java) {
                    if (null == instance) {
                        instance =
                            AVEngine(engine)
                    }
                }
            }
            return instance
        }
    }
}