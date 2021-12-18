package com.erdemtsynduev.p2pclient.engine

import android.view.View

interface IEngine {

    fun init(callback: EngineCallback?)
    fun joinRoom(userIds: List<String?>?)
    fun userIn(userId: String?)
    fun userReject(userId: String?)
    fun receiveOffer(userId: String?, description: String?)
    fun receiveAnswer(userId: String?, sdp: String?)
    fun receiveIceCandidate(userId: String?, id: String?, label: Int, candidate: String?)
    fun leaveRoom(userId: String?)
    fun startPreview(isOverlay: Boolean): View?
    fun stopPreview()
    fun startStream()
    fun stopStream()
    fun setupRemoteVideo(userId: String?, isO: Boolean): View?
    fun stopRemoteVideo()
    fun switchCamera()
    fun muteAudio(enable: Boolean): Boolean
    fun toggleSpeaker(enable: Boolean): Boolean
    fun release()
}