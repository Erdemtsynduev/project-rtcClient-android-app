package com.erdemtsynduev.p2pclient.call

import com.erdemtsynduev.p2pclient.model.calltype.CallEndReason
import com.erdemtsynduev.p2pclient.model.calltype.CallState

interface CallSessionCallback {
    fun didCallEndWithReason(var1: CallEndReason?)
    fun didChangeState(var1: CallState?)
    fun didChangeMode(isAudioOnly: Boolean)
    fun didCreateLocalVideoTrack()
    fun didReceiveRemoteVideoTrack(userId: String?)
    fun didUserLeave(userId: String?)
    fun didError(error: String?)
}