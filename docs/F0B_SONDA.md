# F0b — sonda „Rincewinder Agent“ na těle

> Návrh 30. 10. 2026. Cíl: do zadání F1 nezůstala žádná otázka označená **Nevím**.
> Status: **návrh, čeká na souhlas.** Instalace na tělo = zásah do těla → provést až po výslovném ano.

## Co sonda rozhodne

| # | Otázka | Proč na ní stojí F1 | Kritérium úspěchu |
|---|---|---|---|
| 1 | Přežije Android **Service** PMCA aplikace odchod z Application menu klávesou MENU? | určuje, zda Agent může běžet „na pozadí“ při fotografování, nebo jen jako otevřená aplikace (model Sony *Send to Smartphone*) | po MENU: `adb shell ps` ukáže proces, HTTP `GET /ping` odpoví |
| 2 | Drží **WakeLock** (`PARTIAL`/`SCREEN_DIM`) tělo vzhůru přes nastavený auto power‑off? | bez toho se tělo při přenosu vypne (Sony help: Wi‑Fi ani adb úsporu neruší) | Pwr Save 1 min, Agent s WakeLockem běží 3 min, tělo zůstane zapnuté |
| 3 | **Propustnost** streamovaného HTTP range GET jednoho ARW z Agenta na PC po Wi‑Fi | rozhoduje, zda Wi‑Fi cesta pro „desítky souborů“ dává smysl (cíl ≥ 1,5 MB/s, Odhad) | `curl -o` 49 MB souboru, MB/s z času |
| 4 | Funguje `am startservice` / `am start` z uid shell na nespuštěný Agent a přepne UI? | určuje, zda Pixel umí Agent probudit bez sáhnutí na tělo (0 kroků v terénu) | služba naběhne, `GET /ping` odpoví; zjistit, zda se UI těla přepnulo |
| 5 | Jsou na API 2.10 dostupné `TimeUtil.setCurrentTime` (V6), `Settings.setAutoPowerOffTime` (V7), `CameraEx.getLensInfo` bez pádu? | rozsah „nastavení z Pixelu“ v F1/F5 | reflexe: existence metod + návratové kódy do logu; **čas se nemění**, jen `getCurrentCalendar` |
| 6 | Vložený JPEG z ARW: offset/délka přes parsování TIFF IFD na těle | rychlý náhled bez stažení celku | `GET /thumb/NCK00001.ARW` vrátí platný JPEG < 2 MB |
| 7 | `StatFs` na `/storage/sdcard0`, `Environment.getExternalStorageDirectory()` z app uid | volné místo, cesta | hodnoty v `GET /status` |
| 8 | Vysílá tělo `com.android.server.SyncAndroidService.SUSPEND` při MENU, nebo jen při vypnutí? | signál „ukliď se“ pro Agent | receiver zapíše do logu čas a akci |

Mimo sondu (jiná cesta nebo později): mazání souborů (nic se nemaže), zápis času, tweaky Backup.bin, OTG.

## Návrh aplikace

| Položka | Volba | Zdůvodnění |
|---|---|---|
| Název / balíček | `Rincewinder Agent` / `com.nicky.rincewinder.agent` | odděleno od budoucí Pixel appky `com.nicky.rincewinder` |
| Jazyk, API | Java 8 (`javac --release 8`), minSdk 16, targetSdk 16 | Doloženo: toolchain TrapFocusu; Sony stuby jen pro `CameraEx`, `ScalarProperties`, `TimeUtil`, `Settings` (reflexe → stuby nejsou nutné, ale usnadní kompilaci) |
| Komponenty | `MainActivity` (jedna obrazovka: stav, IP:port, tlačítka Start/Stop služby, SONDA), `AgentService` (foreground‑like Service s notifikací + WakeLock), `SuspendReceiver` | Activity je nutná (Sony spouští jen z menu); Service je předmět testu #1 |
| Server | vlastní minimalistický HTTP/1.1 nad `ServerSocket`, port **8808**, jen GET, `Range:` bytes | žádné závislosti (NanoHTTPD by šel, ale sonda má zůstat ~400 řádků); Odvozeno: `ServerSocket` na 4.1 OK |
| Endpointy | `GET /ping` → `ok <uptime>`; `GET /status` → JSON (model, fw, baterie přes `BATTERY_CHANGED`, StatFs, WakeLock stav, výsledky reflexe); `GET /ls?path=` → řádky `name;size;mtime;dir`; `GET /file?path=` (+Range) → stream; `GET /md5?path=` → hex (čtení celého souboru na těle, měří i rychlost karty); `GET /thumb?path=` → vložený JPEG | pokrývá #3, #5, #6, #7 a je zároveň prototyp API pro F1 |
| Bezpečnost | jen cesty pod `/storage/sdcard0`, normalizace `..`, jen čtení | sonda nic nemaže |
| Log | `RINCEAGT.LOG` v kořeni karty (8.3) + `logcat` tag `RinceAgent` | stejná konvence jako TrapFocus; log je čitelný přes HTTP `GET /file?path=RINCEAGT.LOG` |
| Podpis | **nový klíč** `rincewinder-agent` v `.signing\` (v `.gitignore`), heslo v `.signing\keypass.txt` | per CLAUDE.md; zálohu klíče si poznamenat k TrapFocus klíči |
| Build | `tools\build_agent.ps1` — kopie `trapfocus-a7ii\tools\build.ps1` bez nativní knihovny | Odvozeno: beze změny kroků |

### Reflexní sonda Sony API (tlačítko SONDA i `GET /status`)

Pro každou metodu zapsat `existuje / NoSuchMethod / výjimka při volání`:

| Třída | Metoda | Volat? |
|---|---|---|
| `com.sony.scalar.sysutil.TimeUtil` | `<init>()`, `getCurrentCalendar()`, `getCurrentTimeZone()`, `setCurrentTime(PlainCalendar, PlainTimeZone)` | konstruktor + gettery ano; **setter jen `getMethod`**, nevolat |
| `com.sony.scalar.sysutil.didep.Settings` | `getAutoPowerOffTime()`, `setAutoPowerOffTime(int)` | getter ano; setter jen `getMethod` |
| `com.sony.scalar.hardware.CameraEx` | `open(int, OpenOptions)`, `getLensInfo()` | ano, ale jen když neběží jiná kamerová aplikace (TrapFocus) — cameraex snese jednoho klienta |
| `com.sony.scalar.sysutil.ScalarProperties` | `getString("model.name")`, `getString("version.platform")`, `getFirmwareVersion()` | ano |
| `com.sony.scalar.sysutil.didep.Power` | `getInstance()` | ano, jen existence |

## Postup měření (po souhlasu)

| Krok | Příkaz / akce | Měří |
|---|---|---|
| 0 | build, `apksigner verify --print-certs`, **zkontrolovat, že TrapFocus neběží** | — |
| 1 | `adb install -r dist\RincewinderAgent_v0.1.apk` (push + `pm install`) | instalace bez PC kabelu funguje přes Wi‑Fi (Doloženo z praxe, potvrdit) |
| 2 | spustit Agent **ručně z Application menu**, Start služby; z PC `curl http://10.98.92.25:8808/status` | #5, #7 |
| 3 | `Measure-Command { curl -o %TEMP%\a.arw .../file?path=DCIM/100MSDCF/NCK00002.ARW }`; porovnat velikost a `md5` z `GET /md5` s `Get-FileHash -Algorithm MD5` | #3 + ověření integrity |
| 4 | `GET /thumb?path=DCIM/100MSDCF/NCK00001.ARW` → otevřít JPEG | #6 |
| 5 | **MENU** na těle (odchod z aplikace); z PC `adb shell ps`, `curl /ping`, znovu krok 3 | #1, #8, propustnost mimo app režim |
| 6 | Pwr Save Start Time na těle = 1 min (Nicky ručně, pak vrátit); Agent se službou a WakeLockem; 3 min čekat | #2 |
| 7 | zastavit službu; z PC `adb shell am startservice -n com.nicky.rincewinder.agent/.AgentService` → `curl /ping`; pak `am start -n .../.MainActivity` při nespuštěné aplikaci | #4 |
| 8 | stáhnout `RINCEAGT.LOG` přes HTTP, výsledky do CLAUDE.md | — |

Rozsah: build ~1 h, měření na těle ~30 min. Baterie těla 29 % → před krokem 6 nabít nebo akceptovat riziko vypnutí.

## Rizika

| Riziko | Dopad | Ošetření |
|---|---|---|
| Service tělo zabije při MENU (#1 = ne) | Agent funguje jen jako otevřená aplikace; Wi‑Fi přenos vyžaduje „otevřít Agent na těle“ = 1 krok v terénu, přijatelné | F1 počítá s oběma modely |
| WakeLock nedrží (#2 = ne) | tělo se při přenosu vypne; nutná fronta s pokračováním (už v návrhu) a doporučení Pwr Save 30 min | F1 |
| HTTP < 1 MB/s (#3) | Wi‑Fi jen pro výběr a náhledy, hromadný sync výhradně OTG | změnit prioritu F3 |
| Reflexe spadne na `cameraex` | jen informativní; sonda chytá výjimky | — |
| Nový klíč → při přeinstalaci vždy stejný | `.signing\` mimo git, záloha na Nickym | — |

## Co z F0b přejde do F1 beze změny

HTTP API (endpointy, formát `ls`), parser vloženého JPEG, `FileLog` 8.3, build skript. Agent ve F1 doplní: mazání s potvrzovacím tokenem (`POST /rm` s `X-Confirm`), md5 cache, mDNS ohlášení (tělo má `mdnsd`, ověřit z app uid), nastavení času.
