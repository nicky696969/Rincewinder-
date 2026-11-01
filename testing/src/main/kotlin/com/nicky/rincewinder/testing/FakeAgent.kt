package com.nicky.rincewinder.testing

import com.nicky.rincewinder.agent.AgentHttpServer
import com.nicky.rincewinder.agent.Json
import com.nicky.rincewinder.agent.Sink
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicLong

/** Skutečné jádro Agenta (agent/core) nad složkou na PC. Co Agent umí na těle, umí i tady, jen bez Sony API. */
class FakeAgent(val root: File, port: Int = 0) : AutoCloseable {
    val log = ArrayList<String>()
    private val server = AgentHttpServer(port, root, "fake-1", Sink { synchronized(log) { log += it } }, object : AgentHttpServer.Hooks {
        override fun fillStatus(j: Json) { j.str("ip", "127.0.0.1").str("mac", "000000000000").num("batteryPct", 77).num("wifiDbm", -40) }
        override fun probe(lens: Boolean) = "fake\n"
        override fun wakelock(mode: String?) = "fake"
        override fun stop() {}
    })

    init { server.start() }
    val port: Int get() = server.port()
    override fun close() = server.stop()
}

/**
 * TCP proxy, která umí spojení přerušit po N bajtech směrem k klientovi (simulace vypnutí těla / výpadku Wi‑Fi).
 * [cutAfter] < 0 = nic nepřerušovat. Po jednom přerušení se sama vrátí do normálu.
 */
class FlakyProxy(private val targetPort: Int) : AutoCloseable {
    private val server = ServerSocket(0)
    val port: Int get() = server.localPort
    val cutAfter = AtomicLong(-1)
    @Volatile var cuts = 0
    @Volatile private var running = true

    init {
        Thread({
            while (running) {
                val c = try { server.accept() } catch (_: Exception) { break }
                Thread({ handle(c) }, "flaky-conn").apply { isDaemon = true }.start()
            }
        }, "flaky-accept").apply { isDaemon = true }.start()
    }

    private fun handle(client: Socket) {
        val up = try { Socket("127.0.0.1", targetPort) } catch (_: Exception) { client.close(); return }
        val t1 = Thread { pump(client, up, false) }.apply { isDaemon = true }
        val t2 = Thread { pump(up, client, true) }.apply { isDaemon = true }
        t1.start(); t2.start()
        t2.join(); // odpověď skončila (nebo byla uříznuta)
        try { client.close() } catch (_: Exception) {}
        try { up.close() } catch (_: Exception) {}
    }

    private fun pump(from: Socket, to: Socket, downstream: Boolean) {
        val buf = ByteArray(16384)
        var sent = 0L
        try {
            val inp = from.getInputStream()
            val out = to.getOutputStream()
            while (true) {
                val n = inp.read(buf)
                if (n <= 0) break
                val limit = if (downstream) cutAfter.get() else -1L
                if (limit >= 0 && sent + n > limit) {
                    val allowed = (limit - sent).toInt().coerceAtLeast(0)
                    if (allowed > 0) out.write(buf, 0, allowed)
                    out.flush()
                    cuts++
                    cutAfter.set(-1)
                    to.close(); from.close()
                    return
                }
                out.write(buf, 0, n)
                sent += n
            }
            out.flush()
            try { to.shutdownOutput() } catch (_: Exception) {}
        } catch (_: Exception) {
        }
    }

    override fun close() { running = false; server.close() }
}
