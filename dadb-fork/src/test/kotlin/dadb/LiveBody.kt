package dadb

/**
 * Ruční kontrola forku proti skutečnému tělu (jen čtecí příkazy). Spuštění:
 *   .\gradlew.bat :dadb-fork:liveBody "-Phost=192.168.1.105"
 * Nezapisuje nic, neinstaluje, nemaže.
 * Ověřeno proti tělu 8. 11. 2026: features prázdné, legacy shell s rc (false→1, chybějící grep→127), pm list OK.
 */
object LiveBody {
    @JvmStatic
    fun main(args: Array<String>) {
        val host = args.firstOrNull() ?: "192.168.1.105"
        Dadb.create(host, 5555, connectTimeout = 4000, socketTimeout = 15000).use { d ->
            println("supportsFeature(cmd)=${d.supportsFeature("cmd")}, shell_v2=${d.supportsFeature("shell_v2")}")
            for (c in listOf("echo ahoj", "getprop ro.build.version.release", "false", "pm list packages -3", "dumpsys battery | grep level")) {
                val r = d.shell(c)
                println("\$ $c  -> rc=${r.exitCode}  ${r.allOutput.trim().replace("\n", " | ")}")
            }
        }
    }
}
