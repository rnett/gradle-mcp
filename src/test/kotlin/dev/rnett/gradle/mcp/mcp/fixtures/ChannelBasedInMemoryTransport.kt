package dev.rnett.gradle.mcp.mcp.fixtures

import io.modelcontextprotocol.kotlin.sdk.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch


class ChannelBasedInMemoryTransport(private val scope: CoroutineScope, private val name: String) :
    AbstractTransport() {
    private lateinit var otherTransport: ChannelBasedInMemoryTransport
    private val incomingChannel = Channel<JSONRPCMessage>(Channel.UNLIMITED)

    companion object {
        fun createLinkedPair(scope: CoroutineScope): Pair<ChannelBasedInMemoryTransport, ChannelBasedInMemoryTransport> {
            val clientTransport = ChannelBasedInMemoryTransport(scope, "ClientTransport")
            val serverTransport = ChannelBasedInMemoryTransport(scope, "ServerTransport")
            clientTransport.otherTransport = serverTransport
            serverTransport.otherTransport = clientTransport
            return Pair(clientTransport, serverTransport)
        }
    }

    override suspend fun start() {
        scope.launch {
            for (message in incomingChannel) {
                scope.launch {
                    _onMessage(message)
                }
            }
        }
    }

    override suspend fun close() {
        if (!incomingChannel.isClosedForReceive) {
            incomingChannel.close()
            otherTransport.incomingChannel.close()
            _onClose()
        }
    }

    override suspend fun send(message: JSONRPCMessage) {
        if (otherTransport.incomingChannel.isClosedForSend) {
            throw IllegalStateException("Not connected")
        }
        otherTransport.incomingChannel.send(message)
    }
}