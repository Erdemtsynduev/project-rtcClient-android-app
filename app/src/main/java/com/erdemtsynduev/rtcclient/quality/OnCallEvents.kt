package com.erdemtsynduev.rtcclient.quality

interface OnCallEvents {
    fun onCaptureFormatChange(width: Int, height: Int, framerate: Int)
}