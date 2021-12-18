package com.erdemtsynduev.p2pclient.model.calltype

enum class CallState {
    Idle,
    Outgoing,
    Incoming,
    Connecting,
    Connected;
}