# Zadání F1 pro Fable — Rincewinder (Pixel klient + Agent v1)

> Vzniklo 31. 10. 2026 z výsledků F0b (`docs/F0B_STAV.md`) a zamčeného UX (`docs/UX_NAVRH.md`, `docs/UX_WIREFRAMES.md`, `docs/ux_preview.html`).
> Vhodný model: **Fable** (rozsáhlý vícesouborový kód od nuly). Komunikace s Nickym česky, značky Doloženo / Odvozeno / Odhad.
> Pravidla projektu: nic z karty nemazat bez výslovného potvrzení; zásah do těla (instalace, nastavení) jen se souhlasem; podpisové klíče nikdy do gitu.

## 1. Cíl F1

Pixel 9 Pro appka, která bez PC a bez vyndávání karty **stáhne nové RAWy (ARW) z A7 II přes Wi‑Fi**, ověří je a po potvrzení umí smazat z karty, a **nainstaluje APK na tělo**. Hlavní tok je jedno tlačítko „Stáhnout nové“.

| V rozsahu F1 | Mimo rozsah (později) |
|---|---|
| Agent v1 na těle (rozšíření sondy 0.1.1): mazání s tokenem, spolehlivé `/status` | OTG/MTP hromadný sync (F3) |
| Pixel appka: objevení těla, párování QR, Domů, mřížka náhledů, fronta, ověření MD5, mazání z karty, notifikace přenosu | správce APK z GitHub Releases a podpisy (F2), kromě instalace Agenta |
| Instalace Agenta (přibalené APK) přes fork dadb | PMCA instalátor přes OTG (F4) |
| Diagnostika minimum (karty ze `/status`, stažení logů) | metadata, GPS, export (F5), widget a dlaždice |

## 2. Doložená fakta z F0 a F0b, na kterých stojí návrh

| Fakt | Dopad | Značka |
|---|---|---|
| Karta je z adb nečitelná, čte ji jen aplikace na těle (Agent) | veškerá data jdou přes HTTP Agenta, adb jen pro `pm`/`am`/`logcat`/install | Doloženo |
| HTTP z Agenta **2,65 a 2,89 MB/s** (ARW 25–49 MB), první bajt ~30 ms, latence `/ping` medián 26–32 ms; `adb pull` 0,17 MB/s | ~620 ARW (15 GB) přes Wi‑Fi ≈ 1,5 h; proto práh „moc na Wi‑Fi“ | Doloženo (2 měření, tělo blízko) |
| MD5 na těle 7–9 MB/s | ověření přidá ~30 % času; ověřovat **po každém souboru** paralelně se stahováním dalšího | Doloženo |
| Vložený JPEG v ARW: IFD1 160×120 (~5 kB), IFD0 1920×1080 (310–390 kB), `/thumb` 90–230 ms | mřížka plní nejdřív miniaturou (rychlé), pak velkým náhledem | Doloženo |
| Service Agenta přežije odchod z aplikace (MENU/odchod), `SUSPEND` nepřijde | Agent může běžet při focení | Doloženo (2 odchody) |
| **WakeLock tělo před auto power-off nezachrání** (Pwr Save 1 min → vypnulo se) | přenos musí přežít vypnutí těla: fronta s pokračováním, srozumitelná výzva „probuď tělo a spusť Agent“ | Doloženo (1 test) |
| `am startservice -n com.nicky.rincewinder.agent/.AgentService` z adb funguje | pokud ADB běží, Pixel umí Agent probudit bez sáhnutí na tělo | Doloženo |
| adbd po restartu těla sám nenaskočí; hlavní tok ADB nepotřebuje | stavy Domů viz UX; ADB se vyžaduje jen pro Apky/instalaci/logcat | Doloženo / Odvozeno |
| Gettery Sony API (`getAutoPowerOffTime`, firmware) jsou po restartu nespolehlivé (300→0, 4.01→0.00) | **nespoléhat se** na ně; zápis času a power-off nepatří do F1 | Doloženo |
| Hodiny těla po startu 1970, po síti skočí na správný čas | `mtime` ze `/ls` může být po startu nesmyslný jen u nových souborů vzniklých před synchronizací; klíč souboru viz níž | Doloženo |
| Tělo bere jen názvy 8.3 pro zápis; `RINCEAGT.LOG` v kořeni karty | log i konfigurace Agenta jen 8.3 | Doloženo |
| Karta měla volných jen 0,49 GB z 31 GB | stav „karta téměř plná“ je běžný, nabídnout mazání ověřených | Doloženo |
| dadb 2.0.0 padá na banneru `device::` bez `features=`; `shell()` zná jen `shell,v2`; `uninstall` volá `cmd`; `sync:` push/pull kompatibilní | **fork dadb s patchem**, viz 5.3 | Doloženo (CLAUDE.md) |
| Android 17: `ACCESS_LOCAL_NETWORK` runtime, bez něj TCP do LAN tiše timeoutuje; FGS `connectedDevice` bez 6h limitu | onboarding žádá oprávnění před prvním skenem | Doloženo |

## 3. Co už existuje (nepřepisovat bez důvodu)

| Položka | Cesta | Stav |
|---|---|---|
| Agent 0.1.1-probe: jádro | `agent/core/com/nicky/rincewinder/agent/` (`AgentHttpServer`, `ArwThumb`, `Qr`, `Json`, `Transfer`, `SonyProbe`) | pure Java, SDK 16, 36 testů na PC |
| Agent: Android vrstva | `agent/app/` (`MainActivity`, `StatusView`, `AgentService`, `DeviceInfo`, `FileLog`, `SuspendReceiver`) | na těle běží |
| Build a testy | `tools/build_agent.ps1`, `tools/test_agent.ps1`, `tools/f0b_measure.ps1`, `agent/tests/LocalAgent.java` (Agent na PC nad složkou) | funguje |
| Podpis Agenta | `.signing\rincewinder-agent.keystore` (mimo git) | Nicky má zálohovat |
| Prostředí | SDK `%LOCALAPPDATA%\Android\Sdk` (platformy 10 a 37.0, build-tools 36/37), JBR 25 z Android Studia, git z GitHub Desktopu, bez Gradle v PATH | viz CLAUDE.md |

Pozor: Android vrstva Agenta je překládána proti **android-10** jako bootclasspath (kontrola API v obou skriptech). Nic nad API 10 tam nesmí vniknout, tělo je Android 4.1.2.

## 4. Kontrakt Agenta (HTTP, port 8808, jen GET kromě mazání)

| Endpoint | Odpověď | Pozn. |
|---|---|---|
| `GET /ping` | `ok <uptime s>` | uptime monotónní |
| `GET /status` | JSON: `agentVersion, port, serviceRunning, uptime, activeClients, cardPresent, cardFree, cardTotal, root, transferActive, lastMbps, ip, mac (12 hex), wifiDbm, wifiLinkMbps, wifiChannel (null), batteryPct, batteryMv, batteryC, tweakVersion (null možné), fw (null), model, device, sdk, release, wakelock` | co nejde zjistit, je `null` |
| `GET /ls?path=` | řádky `name;size;mtimeMs;d\|f`, třídění podle jména | kořen = `/storage/sdcard0`, cesty relativní |
| `GET /file?path=` | tělo souboru, podpora `Range` (od–do, od, `-N`), 416 mimo rozsah, HEAD | pro pokračování |
| `GET /md5?path=` | hex MD5; hlavičky `X-Elapsed-Ms`, `X-Bytes` | blokující, 5–7 s na 49 MB |
| `GET /thumbs?path=` | `offset;length;WxH;ifd` pro všechny JPEG v ARW | |
| `GET /thumb?path=&min=N` | JPEG; `min` vybere nejmenší s delší stranou ≥ N, jinak největší; hlavičky `X-Thumb-Dim`, `X-Thumb-Ifd` | |
| `GET /stop` | zastaví službu | jen pro vývoj, v produkci zvážit odstranění |
| `GET /probe[?lens=1]`, `/wakelock?mode=` | ladění | jen sonda; v produkční verzi odebrat nebo ochránit |

### Co Agent v1 musí přidat

| Úkol | Požadavek |
|---|---|
| Mazání | `GET /rm-token?path=&size=&mtime=` vrátí jednorázový token (platnost 60 s); `POST /rm?path=&token=` smaže **jen když** `size` a `mtime` souboru pořád sedí, jinak **409** a nic nemaže. Nikdy mazání adresářů ani více souborů najednou. Zápis do logu každého smazání |
| Bezpečnost | omezení na kořen karty zůstává; `/rm` jen pro soubory v `DCIM/`; žádné mazání bez tokenu |
| Ochrana sdílení | server naslouchá na všech rozhraních v LAN: uvážit sdílený tajný klíč z QR (`&k=<8 znaků>`) pro `/rm` a `/stop`; čtení bez klíče je přijatelné (data jsou fotky v LAN), mazání ne |
| MD5 cache | cache `path+size+mtime → md5` v paměti, aby opakované ověření neznovu četlo kartu |
| Čas a hodiny | do `/status` přidat `deviceTimeMs` (hodiny těla) a `clockSynced` (rok ≥ 2024), ať Pixel pozná špatné hodiny |
| QR obsah | beze změny, `http://<IP>:8808/?m=<MAC12>&v=<verze>` (verze QR 4); přidat případný klíč podle výše |
| Stavová obrazovka | ověřit na těle s Nickym (viz otevřené body); ASCII text zůstává |
| Úklid sondy | odebrat `/probe`, `/wakelock` z produkčního buildu nebo je schovat za `&debug=1` |

## 5. Pixel appka

### 5.1 Technologie

Kotlin + Jetpack Compose, `compileSdk/targetSdk 37`, AGP ≥ 8.9, minSdk 34 (Pixel 9 Pro vyšel s Androidem 14, Nicky řeší jen tento telefon; skutečnou verzi doplnit). Hilt není nutný; stačí ruční DI. Room pro stavy souborů a frontu. OkHttp pro HTTP (HTTP/1.1, žádné HTTP/2, `Connection: close` ze strany Agenta). Žádný `usesCleartextTraffic`; cleartext na LAN se řeší síťovým bezpečnostním profilem jen pro privátní rozsahy (10/8, 172.16/12, 192.168/16), nebo raw socket HTTP klientem. **Ověřit na Pixelu**, že Android 17 povolí HTTP na LAN s `ACCESS_LOCAL_NETWORK`.

### 5.2 Moduly

| Modul | Obsah |
|---|---|
| `:app` | UI (Compose), navigace (spodní lišta Domů · Soubory · Apky · Diagnostika), onboarding |
| `:core` | model stavů souboru, fronta, synchronizační logika, klient Agenta (čistá JVM, testovatelná bez Androidu) |
| `:dadb-fork` | fork dadb s patchem 5.3 |
| `:testing` | falešný Agent v JVM (`LocalAgent` z `agent/tests`) pro testy fronty a výpadků |

### 5.3 Fork dadb (jen install, `pm`, `am`, `logcat`, `dumpsys`, push; ne RAW)

| Patch | Důvod |
|---|---|
| chybějící `features=` v banneru → prázdná množina | jinak spadne připojení k Androidu 4.1 |
| `shell()` fallback na legacy `shell:` + návratový kód přes `; echo __RC=$?` | 4.1 nezná `shell,v2` |
| `uninstall` → `pm uninstall` | `cmd` v 4.1 neexistuje |
| `keyPair = null` | adbd 4.1 nemá `A_AUTH` |
| respektovat `MAX_PAYLOAD 4096` a `A_VERSION 0x01000000` | Doloženo (AOSP jb-release) |

### 5.4 Datový model a stavy

Převzít přesně z `docs/UX_NAVRH.md` („Datový model stavů souboru“): `NA_KARTE`, `STAHUJE_SE`, `STAZENO`, `OVERENO`, `SMAZANO_Z_KARTY`, `CHYBA`, `STARE`, `LOKALNE_CHYBI`, `NEZNAMY`. Klíč souboru `název+velikost+mtime`; mezi cestami (Wi‑Fi × OTG) jen `název+velikost`. Pole záznamu: `localPath`, `md5Agent`, `md5Local`, `bytesDone`, `errorReason`, `lastSeenOnCard`, `firstSeen`.

Pravidlo „nové“: `NA_KARTE + CHYBA + nedokončené STAHUJE_SE + LOKALNE_CHYBI`. Jedna definice pro tlačítko, počet na Domů i filtr. První spárování: volba „snímky od dneška / všech N / žádné“ (výchozí od dneška, **zamčeno Nickym**).

### 5.5 Synchronizace a fronta

| Krok | Chování |
|---|---|
| Objevení těla | 1) poslední známá IP z profilu; 2) sken podsítě na portech 8808 a 5555 (paralelně, krátký timeout); 3) tělo se pozná podle `mac` ze `/status` (ARP appka nepřečte). Oprávnění `ACCESS_LOCAL_NETWORK` ověřit před skenem |
| Stavy Domů | 5 stavů z `UX_WIREFRAMES.md` §2 (nic neodpovídá / Agent neběží / Agent chybí / Agent OK + ADB vypnuté / vše OK). Nápověda ADB jen v Apkách a Diagnostice |
| Stažení souboru | `GET /file` s `Range` od `bytesDone`; zápis do `.part` ve složce přenosu, přejmenování po dokončení; po dokončení `GET /md5`, porovnání s MD5 lokální kopie; shoda → `OVERENO`, neshoda → `CHYBA` a jedno automatické opakování od nuly |
| Souběh | max 2 souběžná spojení (tělo je pomalé a má jedno Wi‑Fi rozhraní); MD5 souboru n běží při stahování n+1 |
| Výpadek | jakákoli I/O chyba → fronta se pozastaví, `bytesDone` zůstane; opakované hledání těla s rostoucím intervalem; po nalezení automatické pokračování bez dotazu |
| Auto power-off | WakeLock nepomáhá (Doloženo). Notifikace s vibrací a textem podle portů: nic neodpovídá → „Tělo spí: zmáčkni spoušť napůl“; 8808 mlčí po probuzení → „Agent neběží: MENU → Aplikace → Rincewinder Agent“ (nebo akce „Spustit z telefonu“, pokud ADB běží) |
| Velký počet | nad 50 souborů nebo 2 GB Domů nabídne „Moc na Wi‑Fi (~N h) — připoj kabel“ a „Stáhnout jen dnešní“ (OTG je F3, tlačítko jen odkazuje na nápovědu) |
| Cílová složka | `Pictures/Rincewinder/<datum>` přes SAF (volba v onboardingu 1/3); před startem kontrola volného místa na Pixelu |
| FGS | typ `connectedDevice` (+ `CHANGE_WIFI_STATE`), notifikace s postupem, Pozastavit/Zrušit, haptika po dokončení |

### 5.6 Mazání z karty

Jen `OVERENO`. Těsně před dialogem znovu ověřit, že `localPath` existuje a velikost sedí (jinak `LOKALNE_CHYBI` a mazání zakázáno). Dialog: počet, velikost, cílová složka, vynechané soubory s důvodem; potvrzení **podržením 1,5 s**; žádné „smazat vše“. Pro každý soubor `GET /rm-token` → `POST /rm`; 409 → soubor se zobrazí jako změněný a nemaže se. Po úspěchu `SMAZANO_Z_KARTY`.

### 5.7 Instalace APK a Agenta

Jedno tlačítko pro přibalené APK Agenta (**zamčeno Nickym**): stav „ADB OK, Agent v těle chybí“ nabídne „Nainstalovat Agent“ (`push` + `pm install -r` přes dadb fork). Před aktualizací běžícího Agenta varovat „přenos se přeruší“; po ní nabídnout „Spustit z telefonu“ (`am startservice`, poté `am start`, **před startem kontrola `ps`**, aby neběžela druhá kamerová aplikace). Kontrola podpisu před instalací: při neshodě otisku varovat (nejdřív odinstalovat). Vlastní APK z úložiště Pixelu a GitHub Releases patří do F2.

### 5.8 UI

Implementovat podle `docs/UX_WIREFRAMES.md` a `docs/ux_preview.html`: tmavé téma výchozí, Material 3 dynamické barvy s fallbackem oranžová `#FF8A3D`, ikona mágova čepice, cíle ≥ 48 dp, primární tlačítko 64 dp, odznaky ✓ (obrys, staženo) a ✓✓ (plná, ověřeno). Žádná textová pole mimo onboarding 1/3.

## 6. Akceptační kritéria F1

| # | Kritérium | Jak se ověří |
|---|---|---|
| 1 | „Stáhnout nové“ stáhne 20 ARW, všechny `OVERENO`, MD5 sedí s `Get-FileHash` na PC | na těle, protokol v `docs/f1/` |
| 2 | přerušení Wi‑Fi nebo vypnutí těla uprostřed souboru → po návratu se pokračuje od `bytesDone`, výsledek stejný MD5 | falešný Agent v JVM + jednou na těle |
| 3 | mazání: bez `OVERENO` se nesmaže nic; změněný soubor (jiná velikost) → 409 a nic nesmazáno | testy falešného Agenta; na těle jen na kopii souboru, **na ostrých snímcích jen s Nickyho výslovným pokynem** |
| 4 | první spárování přes QR z displeje těla ≤ 1 krok navíc (spustit Agent) | ruční test |
| 5 | po restartu těla stačí 1 krok (spustit Agent) k použitelné appce, ADB se nevyžaduje | ruční test |
| 6 | instalace Agenta na čisté tělo z Pixelu bez PC | ruční test, jen se souhlasem |
| 7 | appka nic netahá do cloudu, vše lokálně; `ACCESS_LOCAL_NETWORK` odmítnutý → srozumitelná karta, žádný tichý timeout | ruční test |

## 7. Pořadí práce (návrh)

| M | Obsah | Výstup |
|---|---|---|
| M1 | `:core`: model stavů, klient Agenta, falešný Agent, testy fronty a výpadků na JVM | zelené testy bez zařízení |
| M2 | Agent v1: `/rm-token`, `/rm`, MD5 cache, `deviceTimeMs`, klíč v QR; testy jádra a nasazení na tělo se souhlasem | `agent/…` + build |
| M3 | `:dadb-fork` s patchem a krátký test proti tělu (`pm list packages`, install) | modul + log |
| M4 | `:app`: onboarding, Domů, objevení těla, mřížka, přenos ve FGS | APK pro Pixel |
| M5 | ověření, mazání, výpadky na těle, doladění textů | protokol měření |
| M6 | dokumentace a předání (`CLAUDE.md` aktualizovat) | |

## 8. Otevřené body (nezávisí na kódu)

| Bod | Kdo |
|---|---|
| Vzhled stavové obrazovky Agenta na těle, čitelnost QR a ASCII textu (zatím neviděno) | Nicky: fotka nebo popis |
| Verze Androidu na Pixelu 9 Pro | Nicky |
| HTTP na LAN na Androidu 17 s `targetSdk 37` (síťový profil vs. raw socket) | M4, ověřit hned v prvním týdnu |
| Přenos přes hotspot Pixelu (profil sítě) a stejná propustnost | M5 |
| Zápis `Settings.setAutoPowerOffTime` (prodloužení do vypnutí) | jen test se souhlasem; zatím mimo F1 |
| Záloha podpisových klíčů (TrapFocus i Rincewinder Agent), plus nový klíč Pixel appky | Nicky |
| GitHub repo `rincewinder` (privátní): založit v GitHub Desktopu, složka dosud není git repo | Nicky |
