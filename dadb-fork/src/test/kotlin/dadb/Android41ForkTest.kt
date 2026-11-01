package dadb

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Android41ForkTest {

    private fun pm(cmd: String): Pair<String, Int> = when {
        cmd == "echo hi" -> "hi\n" to 0
        cmd == "false" -> "" to 1
        cmd == "exit3" -> "boom\n" to 3
        cmd.startsWith("pm uninstall com.ok") -> "Success\n" to 0
        cmd.startsWith("pm uninstall com.no") -> "Failure [DELETE_FAILED_INTERNAL_ERROR]\n" to 1
        cmd.startsWith("pm list packages") -> "package:com.a\npackage:com.b\n" to 0
        else -> "sh: $cmd: not found\n" to 127
    }

    @Test fun `banner bez features se prijme a features jsou prazdne`() {
        FakeAdbd41(shell = ::pm).use { adbd ->
            Dadb.create("127.0.0.1", adbd.port).use { d ->
                assertFalse(d.supportsFeature("cmd"))
                assertFalse(d.supportsFeature("shell_v2"))
            }
        }
    }

    @Test fun `puvodni dadb na banneru device bez features padal - fork ne (i bez textu za device)`() {
        FakeAdbd41(banner = "device::ro.product.name=dslr-diadem;ro.product.model=ScalarA", shell = ::pm).use { adbd ->
            Dadb.create("127.0.0.1", adbd.port).use { d -> assertEquals("hi\n", d.shell("echo hi").output) }
        }
    }

    @Test fun `legacy shell - vystup bez CR a navratovy kod z __RC`() {
        FakeAdbd41(shell = ::pm).use { adbd ->
            Dadb.create("127.0.0.1", adbd.port).use { d ->
                val ok = d.shell("echo hi")
                assertEquals("hi\n", ok.output); assertEquals(0, ok.exitCode)
                assertEquals(1, d.shell("false").exitCode)
                val bad = d.shell("exit3")
                assertEquals(3, bad.exitCode); assertEquals("boom\n", bad.output)
                assertEquals(127, d.shell("neexistuje").exitCode)
                assertTrue(adbd.openedServices.all { it.startsWith("shell:") }, "nesmí se otevřít shell,v2: ${adbd.openedServices}")
            }
        }
    }

    @Test fun `uninstall pres pm - Success a Failure`() {
        FakeAdbd41(shell = ::pm).use { adbd ->
            Dadb.create("127.0.0.1", adbd.port).use { d ->
                assertEquals(UninstallResult.Success, d.uninstall("com.ok"))
                val f = d.uninstall("com.no") as UninstallResult.Failure
                assertTrue(f.reason.contains("DELETE_FAILED"))
                assertEquals(1, f.exitCode)
            }
        }
    }

    @Test fun `pm vystup s tab pkg radkem je uspech`() {
        assertTrue(isPmSuccess("\tpkg: /data/local/tmp/rwagent.apk\nSuccess\n"))
        assertTrue(isPmSuccess("\tpkg: /data/local/tmp/rwagent.apk\r\nSuccess\r\n"))
        assertFalse(isPmSuccess("\tpkg: /data/local/tmp/x.apk\nFailure [INSTALL_FAILED_UPDATE_INCOMPATIBLE]\n"))
        assertFalse(isPmSuccess(""))
    }

    @Test fun `raw shell stream pro logcat`() {
        FakeAdbd41(shell = ::pm).use { adbd ->
            Dadb.create("127.0.0.1", adbd.port).use { d ->
                val txt = d.openRawShell("pm list packages").use { it.source.readString(Charsets.UTF_8) }
                assertTrue(txt.contains("package:com.a"))
            }
        }
    }
}
