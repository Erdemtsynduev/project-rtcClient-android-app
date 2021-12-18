package com.erdemtsynduev.rtcclient.signalling

import android.util.Log
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.erdemtsynduev.rtcclient.utils.Urls
import com.erdemtsynduev.websocket.WebSocketClientCallback
import com.erdemtsynduev.websocket.client.CustomWebSocketClient
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import java.lang.Exception
import java.net.URI
import java.net.URISyntaxException
import java.nio.ByteBuffer
import java.util.HashMap

@ExperimentalCoroutinesApi
class SignallingClient(
    private val listener: SignallingClientListener
) : CoroutineScope, WebSocketClientCallback {

    private val job = Job()

    private val gson = Gson()

    override val coroutineContext = Dispatchers.IO + job

    private var client: CustomWebSocketClient? = null

    private val sendChannel = ConflatedBroadcastChannel<String>()

    init {
        connectInit()
        connect()
    }

    private fun connect() = launch {
        val sendData = sendChannel.openSubscription()
        try {
            while (true) {
                sendData.poll()?.let {
                    Log.v(this@SignallingClient.javaClass.simpleName, "Sending: $it")
                    sendString(it)
                }
            }
        } catch (exception: Throwable) {
            Log.e("asd", "asd", exception)
        }
    }

    private fun connectInit() {
        connectSocket(Urls.WS, "4321", 0, this)
    }

    // Подключение к веб сокету
    fun connectSocket(url: String, userId: String, device: Int, callback: WebSocketClientCallback) {
        if (client == null || client?.isOpen == false) {
            val uri: URI = try {
                val urls = "$url/$userId/$device"
                URI(urls)
            } catch (e: URISyntaxException) {
                e.printStackTrace()
                return
            }
            client = CustomWebSocketClient.instance(
                serverUri = uri,
                webSocketClientCallback = callback
            )
            if (url.startsWith("wss")) {
                client?.enableUnsafeSslConnection()
            }
            client?.connect()
        }
    }

    fun send(dataObject: Any?) = runBlocking {
        sendDataJson(
            myId = "4321",
            userId = "1234",
            data = dataObject
        )
    }

    suspend fun sendString(string: String?) {
        withContext(Dispatchers.Main) {
            client?.send(string)
        }
    }

    // send offer
    private suspend fun sendDataJson(myId: String, userId: String, data: Any?) {
        val map: MutableMap<String, Any> = HashMap()
        val childMap: MutableMap<String, Any> = HashMap()
        data?.let {
            childMap["custom_data"] = it
        }
        childMap["userID"] = userId
        childMap["fromID"] = myId
        map["data"] = childMap
        map["eventName"] = "__offer"
        val jsonString = gson.toJson(map)
        sendChannel.send(jsonString)
    }

    private fun handleData(message: String) = runBlocking {
        Log.v(this@SignallingClient.javaClass.simpleName, "handleData message: $message")
        val map = JSON.parseObject<Map<*, *>>(message, MutableMap::class.java)
        val eventName = map["eventName"] as String?
        if (eventName == "__offer") {
            handleOffer(map)
        }
    }


    private suspend fun handleOffer(map: Map<*, *>) {
        Log.v(this@SignallingClient.javaClass.simpleName, "handleOffer map: $map")
        val data = map["data"] as Map<*, *>?
        if (data != null) {
            val customData = data["custom_data"] as JSONObject?
            Log.v(this@SignallingClient.javaClass.simpleName, "handleOffer customData: $customData")
            customData?.let {
                val jsons = JSON.toJSONString(it)
                sendDataHandle(jsons)
            }
        }
    }

    private suspend fun sendDataHandle(data: String) {
        val jsonObject = gson.fromJson(data, JsonObject::class.java)
        withContext(Dispatchers.Main) {
            if (jsonObject.has("serverUrl")) {
                listener.onIceCandidateReceived(
                    gson.fromJson(
                        jsonObject,
                        IceCandidate::class.java
                    )
                )
            } else if (jsonObject.has("type") && jsonObject.get("type").asString == "OFFER") {
                listener.onOfferReceived(
                    gson.fromJson(
                        jsonObject,
                        SessionDescription::class.java
                    )
                )
            } else if (jsonObject.has("type") && jsonObject.get("type").asString == "ANSWER") {
                listener.onAnswerReceived(
                    gson.fromJson(
                        jsonObject,
                        SessionDescription::class.java
                    )
                )
            }
        }
    }

    fun destroy() {
        Log.v(this@SignallingClient.javaClass.simpleName, "destroy")
        client?.close()
        job.complete()
    }

    override fun onOpen() {
        Log.v(this@SignallingClient.javaClass.simpleName, "onOpen")
        listener.onConnectionEstablished()
    }

    override fun onReconnect() {
        Log.v(this@SignallingClient.javaClass.simpleName, "onReconnect")
    }

    override fun onClose() {
        Log.v(this@SignallingClient.javaClass.simpleName, "onClose")
    }

    override fun onMessage(bytes: ByteBuffer) {
        Log.v(this@SignallingClient.javaClass.simpleName, "onMessage bytes: $bytes")
    }

    override fun onMessage(message: String) {
        Log.v(this@SignallingClient.javaClass.simpleName, "onMessage message: $message")
        handleData(message)
    }

    override fun onError(exception: Exception) {
        Log.v(this@SignallingClient.javaClass.simpleName, "onError: $exception")
    }
}