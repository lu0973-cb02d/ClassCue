package com.classcue.kexing.engine

interface TranscriberEngine {
    fun start()
    fun offer(pcm: ByteArray)
    fun stop()
}
