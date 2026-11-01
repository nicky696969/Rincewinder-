# Fork dadb pro Rincewinder

Základ: [dadb](https://github.com/mobile-dev-inc/dadb) 2.0.0 (tag `v2.0.0`, commit `c4c87c3`), Copyright (c) 2021 mobile.dev inc.,
Apache License 2.0 (`LICENSE-dadb.txt`). Upravené soubory nesou v hlavičce poznámku „RINCEWINDER FORK“.

## Co je změněno (proč: adbd z Androidu 4.1.2 na Sony A7 II)

| Soubor | Změna | Důvod |
|---|---|---|
| `AdbConnection.kt` | chybějící `features=` v banneru `device::` → prázdná množina | 4.1 features neposílá, jinak spadne připojení |
| `Dadb.kt` | `shell()` bez `shell_v2` použije legacy `shell:` + návratový kód přes `; echo __RC=$?` | 4.1 zná jen `shell:` |
| `Dadb.kt` | `uninstall` → `pm uninstall`; `pm install` výsledek = řádek `Success` (ne `startsWith`); dočasné APK má příponu `.apk` a po instalaci se smaže | `cmd` na 4.1 neexistuje; `pm` tiskne `\tpkg: …` před `Success` |
| `Dadb.kt` | nový `openRawShell` (surový stream, např. logcat) | |
| `Dadb.kt` | `create(..., keyPair = null)`; odstraněno `discover`, `list`, `tcpForward` | 4.1 nemá `A_AUTH`; zbytek potřebuje `adb` server |
| `Legacy.kt` | nový: legacy shell, parser, `isPmSuccess` | |
| (nepřevzato) | `adbserver/`, `forwarding/`, testy | nepotřebné |

Zachováno beze změny: `sync:` push/pull (data v kouscích do 64 000 B), rámování zpráv, `MAX_PAYLOAD` se bere z odpovědi zařízení (4096 u 4.1).