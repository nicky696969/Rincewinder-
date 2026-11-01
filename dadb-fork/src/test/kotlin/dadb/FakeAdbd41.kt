package dadb

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket

/**
 * Falešný adbd z Androidu 4.1 (Sony A7 II): banner bez features=, žádné AUTH, max payload 4096,
 * shell běží v pty (LF→CRLF) a nezná v2, `pm` před "Success" tiskne "\tpkg: …".
 * Odpovědi na `shell:` příkazy dodává [shell] (vrací výstup a návratový kód).
 */
class FakeAdbd41(
    private val banner: String = "device::",
    private val shell: (String) -> Pair<String, Int>,
) : AutoCloseable {
    private val server = ServerSocket(0)
    val port: Int get() = server.localPort
    val openedServices = ArrayList<String>()
    @Volatile private var running = true

    init {
        Thread({
            while (running) {
                val c = try { server.accept() } catch (_: Exception) { break }
                Thread({ serve(c) }, "fake-adbd").apply { isDaemon = true }.start()
            }
        }, "fake-adbd-accept").apply { isDaemon = true }.start()
    }

    private class Msg(val cmd: Int, val a0: Int, val a1: Int, val data: ByteArray)

    private fun read(i: DataInputStream): Msg {
        val h = ByteArray(24); i.readFully(h)
        fun le(o: Int) = (h[o].toInt() and 0xFF) or ((h[o + 1].toInt() and 0xFF) shl 8) or ((h[o + 2].toInt() and 0xFF) shl 16) or ((h[o + 3].toInt() and 0xFF) shl 24)
        val len = le(12)
        val data = ByteArray(len); i.readFully(data)
        return Msg(le(0), le(4), le(8), data)
    }

    private fun write(o: DataOutputStream, cmd: Int, a0: Int, a1: Int, data: ByteArray = ByteArray(0)) {
        fun le(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
        val sum = data.sumOf { it.toInt() and 0xFF }
        synchronized(o) {
            o.write(le(cmd)); o.write(le(a0)); o.write(le(a1)); o.write(le(data.size)); o.write(le(sum)); o.write(le(cmd.inv()))
            o.write(data); o.flush()
        }
    }

    private fun serve(s: Socket) {
        val i = DataInputStream(s.getInputStream()); val o = DataOutputStream(s.getOutputStream())
        var nextRemote = 100
        try {
            while (true) {
                val m = read(i)
                when (m.cmd) {
                    Constants.CMD_CNXN -> write(o, Constants.CMD_CNXN, 0x01000000, 4096, (banner + "\u0000").toByteArray())
                    Constants.CMD_OPEN -> {
                        val dest = String(m.data).trimEnd('\u0000')
                        openedServices += dest
                        val remote = nextRemote++
                        if (!dest.startsWith("shell:")) { write(o, Constants.CMD_CLSE, 0, m.a0); continue } // sync:, shell,v2 … nezná
                        write(o, Constants.CMD_OKAY, remote, m.a0)
                        val cmd = dest.removePrefix("shell:")
                        val marker = "; echo __RC=\$?"
                        val real = cmd.removeSuffix(marker)
                        val (out, rc) = shell(real)
                        val pty = out.replace("\n", "\r\n") + if (cmd.endsWith(marker)) "__RC=$rc\r\n" else ""
                        write(o, Constants.CMD_WRTE, remote, m.a0, pty.toByteArray())
                        // klient potvrdí OKAY, pak zavřeme stream
                        val ack = read(i)
                        check(ack.cmd == Constants.CMD_OKAY) { "čekal jsem OKAY" }
                        write(o, Constants.CMD_CLSE, remote, m.a0)
                    }
                    Constants.CMD_OKAY, Constants.CMD_CLSE -> Unit
                }
            }
        } catch (_: Exception) {
        } finally {
            try { s.close() } catch (_: Exception) {}
        }
    }

    override fun close() { running = false; server.close() }
}
