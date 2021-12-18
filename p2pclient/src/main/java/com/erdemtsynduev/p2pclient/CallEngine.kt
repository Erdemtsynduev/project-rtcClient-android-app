package com.erdemtsynduev.p2pclient

import android.content.Context
import com.erdemtsynduev.p2pclient.call.CallSession
import com.erdemtsynduev.p2pclient.except.NotInitializedException
import com.erdemtsynduev.p2pclient.inter.InterEvent
import com.erdemtsynduev.p2pclient.model.calltype.CallState

class CallEngine {

    var mCurrentCallSession: CallSession? = null
    var mEvent: InterEvent? = null

    fun startOutCall(
        context: Context?,
        room: String?,
        targetId: String?,
        audioOnly: Boolean
    ): Boolean {
        if (avEngine == null) {
            return false
        }
        if (mCurrentCallSession != null && mCurrentCallSession?.state != CallState.Idle) {
            return false
        }
        mCurrentCallSession = CallSession(context, room, audioOnly, mEvent)
        mCurrentCallSession?.setTargetId(targetId)
        mCurrentCallSession?.isComing = false
        mCurrentCallSession?.setCallState(CallState.Outgoing)
        mCurrentCallSession?.createHome(room, 2)
        return true
    }

    fun startInCall(
        context: Context?,
        room: String?,
        targetId: String?,
        audioOnly: Boolean
    ): Boolean {
        if (avEngine == null) {
            return false
        }
        if (mCurrentCallSession != null && mCurrentCallSession?.state != CallState.Idle) {
            mCurrentCallSession?.sendBusyRefuse(room, targetId)
            return false
        }
        mCurrentCallSession = CallSession(context, room, audioOnly, mEvent)
        mCurrentCallSession?.setTargetId(targetId)
        mCurrentCallSession?.isComing = true
        mCurrentCallSession?.setCallState(CallState.Incoming)

        mCurrentCallSession?.shouldStartRing()
        mCurrentCallSession?.sendRingBack(targetId, room)
        return true
    }

    fun endCall() {
        if (mCurrentCallSession != null) {
            mCurrentCallSession?.shouldStopRing()
            if (mCurrentCallSession?.isComing == true) {
                if (mCurrentCallSession?.state == CallState.Incoming) {
                    mCurrentCallSession?.sendRefuse()
                } else {
                    mCurrentCallSession?.leave()
                }
            } else {
                if (mCurrentCallSession?.state == CallState.Outgoing) {
                    mCurrentCallSession?.sendCancel()
                } else {
                    mCurrentCallSession?.leave()
                }
            }
            mCurrentCallSession?.setCallState(CallState.Idle)
        }
    }

    fun joinRoom(context: Context?, room: String?): Boolean {
        if (avEngine == null) {
            return false
        }
        if (mCurrentCallSession != null && mCurrentCallSession?.state != CallState.Idle) {
            return false
        }
        mCurrentCallSession = CallSession(context, room, false, mEvent)
        mCurrentCallSession?.isComing = true
        mCurrentCallSession?.joinHome(room)
        return true
    }

    fun createAndJoinRoom(context: Context?, room: String?): Boolean {
        if (avEngine == null) {
            return false
        }
        if (mCurrentCallSession != null && mCurrentCallSession?.state != CallState.Idle) {
            return false
        }
        mCurrentCallSession = CallSession(context, room, false, mEvent)
        mCurrentCallSession?.isComing = false
        mCurrentCallSession?.createHome(room, 9)
        return true
    }

    fun leaveRoom() {
        if (avEngine == null) {
            return
        }
        if (mCurrentCallSession != null) {
            mCurrentCallSession?.leave()
            mCurrentCallSession?.setCallState(CallState.Idle)
        }
    }

    val currentSession: CallSession?
        get() = mCurrentCallSession

    companion object {
        private var avEngine: CallEngine? = null
        fun Instance(): CallEngine {
            var `var`: CallEngine
            return if (avEngine.also {
                    `var` =
                        it!!
                } != null) {
                `var`
            } else {
                throw NotInitializedException()
            }
        }

        fun init(iSocketEvent: InterEvent?) {
            if (avEngine == null) {
                avEngine = CallEngine()
                avEngine?.mEvent = iSocketEvent
            }
        }
    }
}