package com.erdemtsynduev.websocket.client

interface BaseWebSocketClient {

    /**
     * Отправить данные в веб сокет в виде байтового массива
     */
    fun sendByteArray(data: ByteArray?)

    /**
     * Изменить состояние соединения веб сокета
     */
    fun setConnectFlag(connectFlag: Boolean)

    /**
     * Выключить проверку ssl сертификата
     */
    fun enableUnsafeSslConnection(): Boolean
}