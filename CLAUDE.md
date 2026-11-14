# RINCEWINDER — předávka do Claude Code

> Rincewind (Pratchettův mág, který utíká z nebezpečí) + winder (posun filmu) + rewinder (převinutí filmu, aby šel vyndat z těla).
> Sólo projekt Františka „Nickyho“ Novotného. Založeno 30. 10. 2026 v chatu claude.ai.

## Pravidla spolupráce (platí vždy)

- Komunikace **česky**. Nicky často diktuje hlasem: zkomoleniny opravuj potichu, ptej se jen když mění smysl.
- Začni závěrem. Bez úvodů, frází a závěrečných shrnutí. Srovnání a přehledy dávej do tabulek.
- Technická tvrzení značkuj **Doloženo / Odvozeno / Odhad**. „Nevím“ je lepší než věrohodný odhad. Reference nevymýšlej.
- **Volně:** čtení, hledání, analýza, sondy jen pro čtení (adb shell ls, getprop, pull do %TEMP%).
- **Nejdřív návrh, pak provedení:** zápis a mazání souborů, git commit/push, `adb install`, cokoli na těle foťáku, cokoli nevratného.
- **Nikdy nemazat nic z karty v těle** bez výslovného pokynu.
- Dělej zadané, ne víc. Nápady navíc max. jednou větou na konci.
- U větších kroků jednou větou: vhodný model a co delegovat na Fable.

## Cíl

Android aplikace pro **Pixel 9 Pro**, která obsluhuje **Sony A7 II** (OpenMemories Tweak nainstalovaný a funkční):

| Směr | Obsah |
|---|---|
| Tělo → Pixel | kopírování **RAW (ARW)**: inkrementálně, s ověřením, po ověřené kopii volitelné mazání z karty (vždy s potvrzením) |
| Pixel → tělo | instalace **APK** aplikací 3. stran (OpenMemories / PMCA) |
| Oboje | file manager karty i Pixelu, správa nainstalovaných aplikací, diagnostika |
| Cesty | Wi‑Fi (ADB) i **OTG** (USB‑C ↔ micro‑USB): OTG dává rychlost a instalaci Tweaku na čisté tělo |

Nickyho požadavek: maximální uživatelské možnosti a pohodlí.

## Rozsah funkcí

| Modul | Funkce |
|---|---|
| Sync RAW | inkrementální (jméno + velikost + čas), fronta, pokračování po výpadku, foreground service, auto‑start po připojení |
| | ověření přenosu (velikost, případně hash) → teprve pak volitelné mazání z karty s potvrzením |
| | rychlý náhled: vložený JPEG z ARW bez stažení celého souboru → výběr → stáhnout jen vybrané RAWy |
| Metadata | GPS stopa z Pixelu spárovaná podle času do **XMP sidecaru** (A7 II GPS nemá); synchronizace hodin těla, pokud to shell dovolí |
| | kód objektivu z registru (např. `CAN35`) k sérii do sidecaru. **Jen značka.** Plný zápis EXIF/XMP a fingerprint dělá EXIF Lens Tagger a lens_core, neduplikovat. |
| Správce aplikací | seznam, verze, install, uninstall, spuštění (`am start`), kontrola podpisu před instalací, stažení buildů z GitHub Releases |
| Diagnostika | logcat s filtrem, stažení `TRAPFOC.LOG` / `SHARP.CSV`, baterie, volné místo na kartě |
| File manager | procházení karty i Pixelu, mazání, přejmenování, filtr podle typu a data |
| Síť | auto‑nalezení těla (sken podsítě na port 5555), profily: domácí Wi‑Fi / hotspot Pixelu |
| Export | Pixel → PC, napojení na ingest foto_orchestratoru (později) |

**Mimo rozsah:** archivní fixity (dělá foto_orchestrator: SHA‑256, archive.db), odhad clony (aperture_core), analýza logů TrapFocusu (dělají PS skripty na PC). Tethering přes PC Remote je pojmenovaná odbočka, zatím se nedělá.

## Ověřená fakta (rešerše 30. 10. 2026)

| Fakt | Značka | Zdroj |
|---|---|---|
| dadb: čistě Kotlin/JVM ADB klient bez adb binárky, install/pull/push/shell, Apache 2.0, verze 2.0.0 (22. 6. 2026) | Doloženo | github.com/mobile-dev-inc/dadb, mvnrepository |
| Tweak spouští ADB daemon na portu 5555, připojení přes Wi‑Fi; telnet (root) na portu 23 | Doloženo | github.com/ma1co/OpenMemories-Tweak |
| ADB přes USB Tweak nezmiňuje → OTG cesta = MTP / Mass Storage, ne ADB | Odvozeno | tamtéž |
| Tweak umí tělo trvale připojit k Wi‑Fi AP; prodloužit čas do úsporného režimu | Doloženo | tamtéž |
| Cesta ke kartě z telnetu: `/android/storage/sdcard0`; z Androidu `/storage/sdcard0`. **Z adb (uid shell) je karta nečitelná** (FUSE `FuFsys`, Permission denied i na stat/open/pull); aplikace (uid 10xxx, gid 1015) i root ji čtou | Doloženo (F0) | sonda 30. 10. |
| adb shell těla má `md5`, `date`, `dd`, `am`, `pm`, `dumpsys`, `logcat`; nemá `which`, `head`, `tail`, `grep`, `busybox`. Root telnet (busybox) má `tar`, `grep`, `sed`, `dd`, `cp`; nemá `nc`, `httpd`, `md5sum`, `wc`. Telnet je binárně nebezpečný (LF→CRLF) | Doloženo (F0) | sonda 30. 10. |
| uid shell má `INSTALL_PACKAGES`/`DELETE_PACKAGES` → `pm install -r` / `pm uninstall` / `am start -n` přes adb fungují | Doloženo | AOSP 4.1.2 platform.xml + praxe TrapFocus |
| adbd od Tweaku startuje přes Sony OSAL IPC mimo Android lifecycle → přežije odchod z Tweaku. **Vypnutí a zapnutí hlavním vypínačem přežije adbd i Agent** (Sony obnoví uložený obraz Androidu — „warm boot“, Agent měl po zapnutí uptime 2652 s). Po úplném studeném startu (vyndaná baterie / dlouhé vypnutí) se adbd zapíná v Tweaku — Odvozeno, netestováno | Doloženo (8. 11. 2026) / Odvozeno | test výpadku, zdroje OpenMemories-Tweak |
| **Test výpadku 8. 11. 2026**: tělo vypnuto uprostřed dávky 8 ARW → služba na Pixelu držela offsety (`.part` 39,6 MB), po zapnutí těla sama navázala od offsetu a dokončila; 8/8 OVERENO, křížová kontrola MD5 (Pixel `md5sum` vs. Agent `/md5`) 9/9 shoda, 2,95 MB/s. Nalezené a opravené chyby: ztráta offsetu při výpadku během MD5, sekvenční sken ADB (~100 s), zahlcení notifikací (~800/s, Android zahazoval) | Doloženo | `docs/F0B_STAV.md` |
| Karta FuFsys vrací `open failed: EBUSY` při mnoha souběžně otevřených souborech (~15 paralelních `/thumb`) → Pixel drží max 2 dotazy na náhledy najednou | Doloženo (RINCEAGT.LOG 8. 11.) | — |
| Vložený JPEG v ARW orientaci nenese; otočení je v IFD0 ARW (tag `0x0112`, little‑endian, v prvních 4 KB) → Pixel čte 4 KB přes Range a otáčí náhled (na kartě 6 = 90°, 8 = 270°) | Doloženo (8. 11., NCK01570/1548/1542) | — |
| A7 II s datovým formátem složek ukládá do `DCIM/1nnYMMDD` (např. `10361107`), `100MSDCF` může být prázdná → appka čte všechny podsložky `DCIM` | Doloženo (8. 11.) | — |
| dadb 2.0.0 (okio, Apache 2.0): AUTH správně přeskočí, ale **spadne na banneru `device::` bez `features=`**; `shell()` jen `shell,v2` (4.1 zná jen `shell:`); `uninstall` volá `cmd` (neexistuje); `sync:` push/pull kompatibilní (64 000 B < 65 536) | Doloženo | zdroje dadb master + AOSP jb-release |
| Android 4.1 adbd: `A_VERSION 0x01000000`, `MAX_PAYLOAD 4096`, žádný `A_AUTH` (RSA až 4.2.2) | Doloženo | AOSP jb-release adb.h |
| Sony API: `TimeUtil.setCurrentTime` (instanční, „V6“), `Settings.setAutoPowerOffTime` (V7), `CameraEx.getLensInfo()`, `ScalarProperties`; `date -s` z shellu padá (/dev/alarm). Tweaky zapisují do Backup.bin přes OSAL IPC bez rootu (OpenMemories-Platform, MIT) | Doloženo; dostupnost V6/V7 na API 2.10 **Nevím** | stuby OpenMemories-Framework → ověří F0b |
| Zda Android Service PMCA aplikace přežije odchod z menu (MENU) **nikde nedoloženo**; tělo nevysílá BOOT_COMPLETED; auto power‑off (1–30 min) neruší ani Wi‑Fi, ani adb | Nevím / Doloženo | ma1co issues, Sony help → ověří F0b |
| `am start` z adb Activity spustí; druhá instance rozbije Sony cameraex → před startem kontrola `ps` | Doloženo | TrapFocus 18. 10. 2026 |
| Android 17 (API 37): `ACCESS_LOCAL_NETWORK` runtime, bez něj TCP do LAN tiše timeoutuje; FGS `connectedDevice` (s `CHANGE_WIFI_STATE`) **bez 6h limitu**, `dataSync` má 6 h/24 h; cleartext politika se na raw socket nevztahuje; `MtpDevice` není deprecated, `getPartialObject` čte vložený JPEG | Doloženo | developer.android.com |
| Sony-PMCA-RE: MIT licence; instalace aplikace přes USB servíruje APK z lokálního webového serveru tunelem; v repu složka `certs` (pravděpodobně TLS) | Doloženo / Odvozeno | github.com/ma1co/Sony-PMCA-RE |
| Hotový Android port PMCA instalátoru jsem nenašel | Odvozeno | rešerše |
| Android 17: ochrana lokální sítě povinná pro targetSdk ≥ 37 → deklarovat a za běhu žádat `ACCESS_LOCAL_NETWORK`; bez ní TCP do LAN spadne na timeout | Doloženo | developer.android.com/privacy-and-security/local-network-permission |
| Rychlosti (F0): `adb pull` **0,17 MB/s** (26,5 MB/148 s), telnet `cat` 0,74 MB/s, lokální čtení karty na těle ~2,7 MB/s (1 vzorek). Ping PC→tělo 25–208 ms (prům. 96). Brzda je latence + adb protokol (OKAY po každém 4 KB paketu), ne karta ani pásmo | Doloženo / Odvozeno | sonda 30. 10. |
| ARW na kartě: 24,7 MB komprimovaný / 49,2–49,5 MB nekomprimovaný; ~620 ARW v `DCIM/100MSDCF`, žádné JPG | Doloženo | sonda 30. 10. |
| OTG USB 2.0 → odhad 15–25 MB/s | Odhad | ověří F3 |
| F0b (31. 10.): Agent 0.1-probe na těle, HTTP range GET **2,65 MB/s** (49 MB / 17,7 s, ~15× víc než `adb pull`), MD5 na těle 7,2 MB/s čtení karty; vložený JPEG z ARW: 160×120 (4,8 kB) a 1920×1080 (361 kB), `/thumb` 90–217 ms; `am startservice` z adb funguje; `TimeUtil.setCurrentTime`, `Settings.setAutoPowerOffTime(int)`, `CameraEx.getLensInfo` na API 2.10 existují (zápis nevolán). Karta měla volných jen 0,49 GB z 31 GB. **Service Agenta přežije odchod z aplikace** (2×), **SUSPEND nepřišel**, ale **`PARTIAL_WAKE_LOCK` tělo před auto power-off NEzachrání** (Pwr Save 1 min → tělo se vypnulo samo) → přenos musí umět pokračovat po vypnutí těla. Gettery Sony API (`getAutoPowerOffTime`, firmware) jsou po restartu těla nespolehlivé (300→0, 4.01→0.00). Klávesy do aplikace: střed 23/232, MENU 82/514, odchod 0/207, spoušť 0/516-518. Hodiny těla po startu 1970, po připojení k síti skočí na správný čas. Zbývá vzhled displeje | Doloženo (1 měření, tělo blízko) | `docs/F0B_STAV.md` |

## Fakta o těle (z projektu TrapFocus)

| Položka | Hodnota |
|---|---|
| Android | 4.1.2 (SDK 16), Sony Java API 2.10, Tweak 0.11, region CX78601_CEC |
| IP na domácí Wi‑Fi | **192.168.1.105** (8. 11. 2026, síť 192.168.1.x; dřív 10.98.92.25 — jiný AP). Spolehlivé je hledat podle MAC `a0:c9:a0:ce:95:ad` (sken 5555 + `arp -a`) |
| Na těle (8. 11. 2026) | **Rincewinder Agent 0.2.0** (`secured: true`, klíč v QR), spustitelný z PC `adb shell am startservice -n com.nicky.rincewinder.agent/.AgentService` — 0 kroků na těle, pokud běží adbd. Karta 128 GB (106 GB volných) |
| `:dadb-fork` proti tělu | **ověřeno 8. 11.**: handshake bez `features`, legacy `shell:` s rc, `pm list packages -3`. **Install přes fork ověřen 14. 11.** po opravě kontrolního součtu paketu (adbd 4.1 součet ověřuje) |
| ADB přes Wi‑Fi z PC | funguje (`adb install -r`, logcat) |
| Karta | bere jen názvy 8.3 (proto `TRAPFOC.LOG`); FUSE `FuFsys`, kořen: `DCIM`, `PRIVATE/{AVCHD,M4ROOT,SONY}`, `MP_ROOT`, `TWEAKLOG.TXT` |
| Wi‑Fi těla | rozhraní `ath0` (Atheros), 2,4 GHz ch 11, −48 dBm, MCS6 58,5 Mb/s, **power save ON**, IPv6 globální |
| Firmware / vlastnosti | `/version.txt` = `35131_14.2019021501`; `ro.product.model=ScalarA`, `ro.product.device=dslr-diadem`, `ro.build.characteristics=nosdcard`, `ro.crypto.fuse_sdcard=true` |
| Baterie přes adb | `dumpsys battery` funguje (level, voltage, temperature) |
| Aplikace 3. strany | appstore, tweak, timelapse (jonasjuffinger), focusproject (TrapFocus 1.12-diag, není debuggable → `run-as` nejde), focusbracket (obsidium) |
| Vzorový projekt | `C:\Users\Nicky\Documents\GitHub\trapfocus-a7ii` (struktura, `tools\build.ps1`: aapt → javac → d8 `--min-api 16` → apksigner; stuby Sony API v `app\stub`; karta = `Environment.getExternalStorageDirectory()`) |

## Prostředí na Aceru (F0, 30. 10. 2026)

| Nástroj | Stav |
|---|---|
| Android SDK | `%LOCALAPPDATA%\Android\Sdk`: platforms **android-10, android-37.0**; build-tools **36.0.0, 37.0.0**; platform-tools 37.0.1 (adb 1.0.41); cmdline-tools `latest` (doinstalováno 30. 10.) |
| JDK | JBR 25 v `C:\Program Files\Android\Android Studio\jbr` (Studio 2026.1 / AI-261); `java` není v PATH |
| git | GitHub Desktop: `%LOCALAPPDATA%\GitHubDesktop\app-*\resources\app\git\cmd\git.exe` (2.53); `gh` není |
| exiftool | `C:\Tools\exiftools\exiftool.exe` |
| Gradle | není v PATH; použít wrapper projektu |

## Rozhodnutí o stacku

| Položka | Volba |
|---|---|
| Jazyk / UI (Pixel) | Kotlin + Jetpack Compose, compileSdk/targetSdk 37, AGP ≥ 8.9 |
| ADB | **fork dadb** jako modul repa, patch: `features` chybí → prázdná množina; `shell()` fallback na legacy `shell:` + exit code přes `; echo __RC=$?`; `uninstall` → `pm uninstall`; `keyPair = null`. Slouží jen pro install/`pm`/`am`/`logcat`/`dumpsys`/push, **ne pro RAW** |
| Data z karty přes Wi‑Fi | **Rincewinder Agent** — PMCA aplikace na těle (Java, SDK 16, toolchain TrapFocusu), běží pod app uid → kartu vidí; TCP/HTTP server: list, stat, range GET (stream), md5, vložený JPEG, mazání s potvrzovacím tokenem; navíc čas (`TimeUtil`), power‑save čas, info o objektivu, baterie, místo. Spouští uživatel z Application menu (model Sony Send to Smartphone); `am start` z Pixelu jen po kontrole `ps` |
| Role cest | Wi‑Fi: správa, diagnostika, instalace APK, výběrový přenos (desítky souborů, náhled → výběr). OTG/MTP: hromadný sync celé karty |
| OTG kopírování | `android.mtp.MtpDevice` (MTP), `getPartialObject` pro vložený JPEG; Mass Storage přes SAF jako záloha |
| Síť (Pixel) | `INTERNET` + `ACCESS_LOCAL_NETWORK` (runtime, před skenem kontrola grantu); žádný `usesCleartextTraffic`, případný NSC až ve F4 |
| Dlouhé přenosy | foreground service typu **connectedDevice** (`CHANGE_WIFI_STATE`, bez 6h limitu); fronta s pokračováním po výpadku je povinná (auto power‑off těla 1–30 min) |
| Kritérium návrhu | každá funkce ≤ 1 krok v terénu navíc (skill `ramec`); dnes známé kroky: zapnout adbd v Tweaku po restartu těla, spustit Agent z menu |
| Repo | **veřejné** https://github.com/nicky696969/Rincewinder- (MIT, rozhodnutí Nickyho 8. 11. 2026), lokálně vedle trapfocus-a7ii. Protože je veřejné: do repa nikdy klíče, tokeny, klíč Agenta z QR ani Nickyho fotky; před každým push kontrola |
| Podpis | **nový** klíč jen pro Rincewinder + záloha na zaheslované médium (na Nickym). Pozor: záloha klíče TrapFocus je taky pořád otevřená. |

## Fáze

| F | Obsah | Kdo |
|---|---|---|
| ~~F0~~ | **hotovo 30. 10. 2026** — viz Ověřená fakta a Prostředí na Aceru | Claude Code |
| **F0b** | sonda na těle: mini Agent APK (Service + HTTP range GET + reflexe Sony API) — přežije Service MENU? propustnost HTTP? WakeLock drží tělo? `am startservice`? Návrh: `docs/F0B_SONDA.md` | Claude Code (teď) |
| **F1** | M1 `:core`, M2 Agent 0.2.0 (na těle), M3 `:dadb-fork` (ověřen živě), M4 `:app` (Pixel, Android 17: onboarding, Domů, mřížka, FGS; **běží na Pixelu, 8. 11.**), M5 částečně (stažení + ověření 11 ARW, výpadek a navázání — hotovo; **mazání z karty netestováno**) , design kolo **hotovo 8. 11.** (vizuál „Dvě světla · beton“, `docs/UX_NAVRH.md`) — zbývá M6 dokumentace, test mazání. Zadání `docs/ZADANI_F1.md` | Fable |
| F2 | správce APK — **hotovo 14. 11. 2026 ve verzi 1.0.0** (detail, akce, instalace ze souboru / Sdílet / odkazu / GitHubu; ověřeno na těle, viz `CHANGELOG.md`); zbývá diagnostika (balík logů, logcat), kontrola podpisu předem | Fable |
| F3 | OTG kopírování (MTP / Mass Storage) — hromadný sync | Fable |
| F4 | PMCA instalátor přes OTG: největší riziko, port protokolu Sony-PMCA-RE (MIT) | Fable, předtím rešerše protokolu (docs v repu) |
| F5 | metadata (GPS, kód objektivu), export | Fable |

## Otevřené otázky

| Otázka | Kdo |
|---|---|
| ~~Verze Androidu na Pixelu 9 Pro~~ | **Android 17, API 37** (build CP3A.261005.005) |
| ~~Přežije Service odchod z menu / WakeLock / propustnost~~ | zodpovězeno F0b, viz Ověřená fakta |
| Jsou `TimeUtil.setCurrentTime` (V6) a `Settings.setAutoPowerOffTime` (V7) na API 2.10? | existují (F0b); zápis netestován |
| ~~Startuje adbd po restartu těla sám?~~ | krátké vypnutí/zapnutí přežije (warm boot); studený start netestován |
| Mazání z karty z Pixelu (token, 409) — jen na testovacím souboru s výslovným pokynem | M5 |
| ~~Design kolo~~ | **zamčeno 8. 11.: „Dvě světla · beton“** (`docs/UX_NAVRH.md`) |
| Sníží `iw dev ath0 set power_save off` latenci? (zásah do těla, vratný restartem) | se souhlasem |
| ~~GitHub repo~~ | **založeno 8. 11.**, veřejné, `origin` = `nicky696969/Rincewinder-` |
| Záloha podpisových klíčů (TrapFocus i nový Rincewinder) | Nicky |

## Dokumenty v repu

| Soubor | Obsah |
|---|---|
| `docs/F0B_SONDA.md` | návrh sondy F0b (mini Agent APK, 8 otázek, postup měření) |
| `docs/UX_NAVRH.md` | UX/design podle důležitosti (P0–P3), Agent na těle, otevřená rozhodnutí — řeší se v samostatné design session |
| `docs/F0B_STAV.md` | výsledky F0b: měření na těle, stav instalace, co zbývá |
| `docs/UX_WIREFRAMES.md`, `docs/ux_preview.html` | zamčené wireframy a offline vizuální náhled |
| `docs/ZADANI_F1.md` | zadání F1 pro Fable (31. 10. 2026) |

Dělba sessions: technika (F0b, dadb, Agent) zde; UX/design ve vlastní session nad `docs/UX_NAVRH.md`. Rozhodnutí z designu se vracejí do CLAUDE.md jen jako zamčené body, ne jako wireframy.

## Aktuální úkol pro Claude Code

1. Na začátku session načíst skilly `ramec` a `sony-trap-focus`.
2. F0b podle `docs/F0B_SONDA.md`: postavit mini Agent APK, **instalace na tělo až po souhlasu**, změřit, zapsat výsledky sem.
3. Podle F0b dopsat `docs/ZADANI_F1.md` pro Fable a založit repo (hotovo 8. 11., veřejné na GitHubu).

Historie: původní sonda F0 (cesta k DCIM, `adb pull`, shell, SDK) proběhla 30. 10. 2026; `adb pull` z karty není možný (viz Ověřená fakta).
