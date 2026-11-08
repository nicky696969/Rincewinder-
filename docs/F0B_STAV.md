# F0b — stav (31. 10. 2026, po prvním měření na těle)

> Agent `0.1-probe` je **nainstalovaný na těle** (po tvém souhlasu) a měření z PC proběhlo.
> Zbývají otázky, které vyžadují tvoje ruce na těle (MENU, Pwr Save): #1, #2, #8 a vzhled displeje.
> Surový výstup: `docs/f0b/vysledky_20261031_110557.txt`.

## Výsledky měření (1 vzorek, PC ve stejné Wi‑Fi, tělo blízko: −29 dBm, link 72 Mb/s)

| # | Otázka | Výsledek | Značka |
|---|---|---|---|
| 3 | propustnost HTTP z Agenta | **2,65 MB/s** (49,2 MB za 17,7 s), první bajt 26 ms. Je to **~15× rychlejší než `adb pull` (0,17 MB/s)**. Velikost i MD5 shodné | Doloženo (1 měření, tělo blízko; ve vzdálenosti a přes hotspot Pixelu ověřit) |
| 3b | `/md5` na těle | 49,2 MB za 5,7–6,5 s = **7,2 MB/s čtení karty**. Ověření tedy přidá ~35 % k času přenosu (6,5 s k 17,7 s) | Doloženo |
| 1 | přežije Service odchod z aplikace? | **Ano.** Při dvou odchodech z aplikace (klávesa `scan=207` v 10:09:07 a dvakrát MENU `code=82 scan=514` po restartu) log ukázal `onPause` a `onStop` s `serviceRunning=true`, žádný `onDestroy`; proces Agenta (pid 1027) žil dál a `/ping` odpovídal. Agent tedy může běžet při focení | Doloženo (2 odchody, nejdéle několik minut) |
| 2 | drží WakeLock tělo vzhůru? | **Ne.** `PARTIAL_WAKE_LOCK` držel (v logu `WakeLock partial`, služba běžela), přesto se tělo s Pwr Save 1 min po odchodu z aplikace (10:09:07) samo vypnulo; potvrdil Nicky. `SCREEN_DIM` jsem nezkoušel (`/wakelock?mode=dim` je připraven). **Přenos proto musí počítat s vypnutím těla**: fronta s pokračováním a text „probuď tělo“ | Doloženo (1 test, Pwr Save 1 min, tělo mimo aplikaci) |
| 5b | spolehlivost getterů Sony API | `getAutoPowerOffTime()`: 300 při prvním měření, **0** po restartu těla; `getSupportedAutoPowerOffTimes()`: `null`; `getFirmwareVersion()`: 4.01, pak **0.00**. Tedy čtení závisí na stavu těla/procesu a nelze na něm stavět; `ScalarProperties.getString("model.name")` a `version.platform` (2.10) jsou stabilní | Doloženo (2 měření) |
| 8 | `SUSPEND` při odchodu z aplikace | **nepřišel**: v logu není žádný řádek `BROADCAST` při žádném z odchodů | Doloženo; při vypnutí těla neznámo (proces zanikl dřív, než šlo zapsat) |
| 3 | druhé měření propustnosti | 24,8 MB za 8,2 s = **2,89 MB/s** při −42 dBm; log Agenta uvádí 2,93 MB/s; `/md5` 9,1 MB/s. Dvě měření: 2,65 a 2,89 MB/s | Doloženo (2 měření) |
| 4 | `am startservice` z adb shellu | **funguje**: služba naběhla, `/ping` odpověděl do 3 s, server na 8808, WakeLock partial | Doloženo |
| 5 | Sony API na API 2.10 | `TimeUtil.setCurrentTime(PlainCalendar, PlainTimeZone)` **existuje** (instanční, getrery statické, konstruktor OK); `Settings.setAutoPowerOffTime(int)` **existuje** (statická, aktuálně `getAutoPowerOffTime() = 300`, tedy 5 min); `CameraEx.getLensInfo()`, `open(int, OpenOptions)`, `release()` existují (nevolal jsem); `Power.getInstance` existuje; `ScalarProperties`: model `ILCE-7M2`, platforma `2.10`, firmware `4.01` | Doloženo (existence i čtení; **zápis času a power-off jsem nevolal**) |
| 6 | vložené JPEG z ARW na těle | IFD1 miniatura **160×120, 4,8 kB**, IFD0 náhled **1920×1080, 361 kB**; `/thumb` 217 ms (velký) a 90 ms (malý), z toho hledání v souboru ~22 ms | Doloženo |
| 7 | `StatFs`, cesta ke kartě | `/storage/sdcard0`, `cardPresent`, čtení i zápis (`canWrite=true`) z uid aplikace; `RINCEAGT.LOG` se zapisuje a je čitelný přes `/file` | Doloženo |

Další zjištění:

| Zjištění | Dopad |
|---|---|
| **Karta má volných jen 0,49 GB z 31,3 GB** | `cardFree` v `/status` funguje; UX stav „Karta téměř plná“ nebude okrajový. Nic z karty se nemazalo |
| 628 ARW v `DCIM/100MSDCF`, soubory 25 až 49 MB | sedí s předchozí sondou F0 |
| Latence `/ping` 23 až 336 ms (medián 32 ms) | mřížka 12 miniatur ≈ 12 × 90 ms ≈ 1 s sériově |
| **Hodiny těla po startu jsou na 1970** a skočí na správný čas až po připojení k síti | `uptime` počítat monotónně (opraveno ve zdroji, `nanoTime`; v těle zatím stará verze); časové značky v logu a `mtime` neověřovat bez kontroly hodin |
| `fw` v `/status` je `null` | `/version.txt` aplikace z uid nepřečte; firmware dává `ScalarProperties.getFirmwareVersion()` (4.01), použít to |
| `wifiChannel` je `null` | očekávané, API 16 frekvenci nezná |
| `tweakVersion` = `com.github.ma1co.openmemories.tweak 0.11` | zjistitelné i z app uid |

## Průběh testu s tvýma rukama (z logu a uptime těla)

| Čas | Událost |
|---|---|
| 10:05:35 | služba nastartovaná z PC (`am startservice`), pid 1524 |
| 10:07:46 | spuštěno z menu těla, `onCreate`; služba zůstala ve stejném procesu |
| 10:09:07 | odchod z aplikace; **proces 1524 pak zmizel a tělo se restartovalo** (uptime těla v 10:15:15 byl 223 s, tedy start ~10:11:32; mezitím nic v logu) |
| po startu | hodiny těla začaly na 1970 a po připojení k síti skočily na 2026; Agent se spustil z menu (pid 1027), 2× MENU → `onPause`, služba žije |
| 10:13:20–10:14:03 | návrat do aplikace; klávesa OK (14×) spouštěla sondu, spoušť napůl a naplno dorazila do aplikace |

Klávesy, které aplikace dostává (`code`/`scan`): střed ovladače `23/232`, MENU `82/514`, odchod `0/207`, spoušť napůl `0/516+517`, naplno `0/518`.

## Co zbývá ověřit

| # | Otázka | Stav |
|---|---|---|
| — | vzhled displeje: QR, čitelnost, ASCII text | potřebuji tvůj popis nebo fotku displeje |
| — | zápis `Settings.setAutoPowerOffTime(int)` (prodloužení času do vypnutí na dobu přenosu) | neotestováno, měnilo by nastavení těla; jen se souhlasem a s návratem původní hodnoty. Čtení je nespolehlivé, viz níž |

## Co jsem změnil na těle

| Změna | Vratnost |
|---|---|
| nainstalována aplikace `com.nicky.rincewinder.agent` (37 kB); 31. 10. po testu WakeLocku aktualizována na **0.1.1-probe** (oprava uptime, sonda čte podporované časy vypnutí), stejný podpis; **8. 11. 2026 aktualizována na 0.2.0** (41 kB, podpis shodný `a0d0513c…`, `adb install -r` přes Wi‑Fi, tělo na 192.168.1.105) | `adb uninstall com.nicky.rincewinder.agent` |
| spuštěna její služba přes `am startservice` (8. 11. znovu po instalaci 0.2.0; `/status`: `secured: true`, `clockSynced: true`, karta 128 GB / 106 GB volných, baterie 76 %) | `GET http://<ip>:8808/stop`, nebo restart těla |
| 8. 11.: `:dadb-fork` živě proti tělu (jen čtení): features prázdné, `echo`/`getprop`/`false` (rc 1)/`pm list -3`/`grep` (rc 127) — fork funguje | nic |
| na kartě vznikl `RINCEAGT.LOG` v kořeni | smaž ručně; do něj aplikace jen přidává řádky |

Nic jiného: žádné nastavení těla, čas ani power-off se nezměnily, z karty se nic nemazalo.

## Co existuje

| Položka | Cesta |
|---|---|
| Jádro (HTTP server, QR, parser ARW, sonda Sony API), 36/36 testů na PC | `agent/core/…`, `tools/test_agent.ps1` |
| Android vrstva | `agent/app/…` |
| APK `0.1-probe`, podepsáno novým klíčem (otisk SHA‑256 `a0d0513c…73d2`) | `dist/RincewinderAgent_v0.1-probe.apk`, build `tools/build_agent.ps1` |
| Měření z PC | `tools/f0b_measure.ps1` |

**Podpisový klíč** je v `.signing\` (mimo git). Zazálohuj ho na zaheslované médium; bez něj půjde Agent na těle jen odinstalovat.

## Vědomé odchylky od návrhu

| Návrh | Skutečnost | Proč |
|---|---|---|
| `targetSdk 16` | **`targetSdk 10`** | jako TrapFocus: starý renderovací režim je na Sony ověřený; navíc bez `NetworkOnMainThreadException` |
| `/thumb/NAME.ARW` | `/thumb?path=…&min=N` | jednotný tvar; `min` vybere miniaturu nebo velký náhled |
| náhled „< 200 kB“ (UX) | skutečně 361 kB (velký) a 4,8 kB (malý) | při 2,65 MB/s je velký náhled ~0,14 s přenosu; mřížka může začít miniaturou |
| tlačítka Start/Stop/SONDA | služba startuje sama, klávesa OK = sonda, `/stop` ji ukončí | tělo nemá dotyk |

Navíc: `/thumbs`, `/wakelock`, `/stop`, `X-Elapsed-Ms` u `/md5`, log stisků kláves, pole `/status` podle `UX_NAVRH.md`.
