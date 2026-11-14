package com.nicky.rincewinder.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PmParseTest {
    private val dump = """
        Packages:
          Package [com.nicky.focusproject] (41a2b3c8):
            userId=10041 gids=[1015, 1006]
            codePath=/data/app/com.nicky.focusproject-1.apk
            versionCode=15
            versionName=1.12-diag
            targetSdk=10
            firstInstallTime=2026-10-11 18:02:11
            lastUpdateTime=2026-10-27 20:11:12
    """.trimIndent()

    @Test fun `appInfo cte pole z dumpsys`() {
        val a = PmParse.appInfo("com.nicky.focusproject", dump)!!
        assertEquals("1.12-diag", a.versionName)
        assertEquals(15, a.versionCode)
        assertEquals("/data/app/com.nicky.focusproject-1.apk", a.codePath)
        assertEquals(10, a.targetSdk)
        assertEquals("2026-10-27 20:11:12", a.lastUpdate)
        assertNull(PmParse.appInfo("com.jiny", dump))
    }

    @Test fun `launcher z tabulky resolveru`() {
        val all = """
            Activity Resolver Table:
              Non-Data Actions:
                  android.intent.action.MAIN:
                    41a0 com.jina.apka/.Main filter 41b0
                    41a1 com.nicky.focusproject/.MainActivity filter 41b1
                  android.intent.action.VIEW:
                    41a2 com.nicky.focusproject/.Jina filter 41b2
        """.trimIndent()
        assertEquals("com.nicky.focusproject/.MainActivity", PmParse.launcher("com.nicky.focusproject", all))
        assertNull(PmParse.launcher("com.neni", all))
    }

    @Test fun `running podle posledniho sloupce ps`() {
        val ps = "USER PID PPID VSIZE RSS WCHAN PC NAME\napp_41 1027 80 1 1 ffffffff 0 S com.nicky.rincewinder.agent\nroot 1 0 1 1 0 0 S /init"
        assertEquals(setOf("com.nicky.rincewinder.agent"), PmParse.running(ps, listOf("com.nicky.rincewinder.agent", "com.nicky.focusproject")))
    }

    @Test fun `lsSize a chyby instalace`() {
        assertEquals(41234L, PmParse.lsSize("-rw-r--r-- system   system      41234 2026-11-08 10:20 com.x-1.apk"))
        assertNull(PmParse.lsSize("/data/app/x.apk: Permission denied"))
        assertTrue(PmParse.installError("\tpkg: /data/local/tmp/a.apk\nFailure [INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES]").startsWith("Jiný podpis"))
        assertEquals("INSTALL_FAILED_NECO_NOVEHO", PmParse.installError("Failure [INSTALL_FAILED_NECO_NOVEHO]"))
        assertEquals("push failed: closed", PmParse.installError("push failed: closed"))
    }

    @Test fun `pole na jednom radku, odkaz ze sdileneho textu, vysledek spusteni`() {
        val a = PmParse.appInfo("com.x", "  Package [com.x] (1):\n    versionCode=7 targetSdk=16\n    versionName=2.0\n")!!
        assertEquals(7, a.versionCode)
        assertEquals(16, a.targetSdk)
        assertEquals("https://example.com/a.apk", PmParse.firstHttpsUrl("Koukni (https://example.com/a.apk)."))
        assertNull(PmParse.firstHttpsUrl("http://example.com/a.apk"))
        assertTrue(PmParse.launched("Starting: Intent { cmp=com.x/.Main }"))
        assertTrue(!PmParse.launched("monkey: not found"))
    }

    @Test fun `githubRepo a safeApkName`() {
        assertEquals("ma1co/OpenMemories-Tweak", PmParse.githubRepo("https://github.com/ma1co/OpenMemories-Tweak/releases"))
        assertEquals("nicky696969/Rincewinder-", PmParse.githubRepo(" nicky696969/Rincewinder- "))
        assertNull(PmParse.githubRepo("neco jineho"))
        assertEquals("Trap_Focus_1.12.apk", PmParse.safeApkName("x/Trap Focus 1.12.apk?token=1"))
        assertEquals("download.apk", PmParse.safeApkName("download"))
    }
}
