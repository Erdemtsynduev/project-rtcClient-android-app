package com.erdemtsynduev.p2pclient.engine.peer

import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.SessionDescription

interface IPeerEvent {
    fun onSendIceCandidate(userId: String?, candidate: IceCandidate?)
    fun onSendOffer(userId: String?, description: SessionDescription?)
    fun onSendAnswer(userId: String?, description: SessionDescription?)
    fun onRemoteStream(userId: String?, stream: MediaStream?)
    fun onRemoveStream(userId: String?, stream: MediaStream?)
}