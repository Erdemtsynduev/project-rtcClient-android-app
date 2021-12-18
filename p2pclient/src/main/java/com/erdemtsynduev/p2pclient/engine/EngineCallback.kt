package com.erdemtsynduev.p2pclient.engine

import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

interface EngineCallback {

    fun joinRoomSucc()
    fun exitRoom()
    fun onSendIceCandidate(userId: String?, candidate: IceCandidate?)
    fun onSendOffer(userId: String?, description: SessionDescription?)
    fun onSendAnswer(userId: String?, description: SessionDescription?)
    fun onRemoteStream(userId: String?)
}