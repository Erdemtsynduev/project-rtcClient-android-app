package com.erdemtsynduev.p2pclient.inter

interface InterEvent {

    fun createRoom(room: String?, roomSize: Int)

    fun sendInvite(room: String?, userIds: List<String?>?, audioOnly: Boolean?)

    fun sendRefuse(room: String?, inviteId: String?, refuseType: Int)

    fun sendTransAudio(toId: String?)

    fun sendDisConnect(room: String?, toId: String?)

    fun sendCancel(mRoomId: String?, toId: List<String?>?)

    fun sendJoin(room: String?)

    fun sendRingBack(targetId: String?, room: String?)

    fun sendLeave(room: String?, userId: String?)

    // sendOffer
    fun sendOffer(userId: String?, sdp: String?)

    // sendAnswer
    fun sendAnswer(userId: String?, sdp: String?)

    // sendIceCandidate
    fun sendIceCandidate(userId: String?, id: String?, label: Int?, candidate: String?)

    fun shouldStartRing(isComing: Boolean)

    fun shouldStopRing()
}