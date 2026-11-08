# UX / design Rincewinderu — zamčený návrh (31. 10. 2026)

> Stav: **rozhodnutí 1–6 zamčena Nickym 31. 10. 2026** (design session). Wireframy: `docs/UX_WIREFRAMES.md` (text) a `docs/ux_preview.html` (offline náhled). Pořadí podle důležitosti, ne podle fáze.
> Řídí se skillem `ramec`: každá funkce ≤ 1 krok v terénu navíc; Nicky pracuje z Pixelu, diktuje hlasem;
> digitální nástroje před focením selhávaly na počtu kroků, nástroje po focení fungují. Rincewinder je „po focení“.

## Zamčené vstupy (z F0 a rešerše, viz CLAUDE.md)

| Fakt | Dopad na UX |
|---|---|
| Karta z adb nečitelná → data jdou přes **Rincewinder Agent** (PMCA aplikace na těle, HTTP) | na těle existuje aplikace s vlastní obrazovkou; spouští se z Application menu |
| Tělo nemá dotykový displej (3", ovládání tlačítky), MENU = konec aplikace | Agent nesmí vyžadovat žádnou interakci na těle |
| adbd po restartu těla nestartuje sám (Odvozeno) → zapíná se v Tweaku | známý krok v terénu č. 1 |
| Spuštění Agenta z menu | známý krok v terénu č. 2; `am start` z Pixelu může odpadnout, pokud F0b potvrdí |
| Auto power‑off těla 1–30 min, Wi‑Fi ani adb ho neruší | přenos musí umět pokračovat po výpadku bez dotazu |
| Wi‑Fi ~1–3 MB/s (Odhad), ARW 25/49 MB | Wi‑Fi je na desítky souborů a výběr; hromadný sync přes OTG |
| Android 17: `ACCESS_LOCAL_NETWORK` runtime | onboarding musí oprávnění vysvětlit v kontextu, bez něj vše tiše timeoutuje |
| Pravidlo projektu: nic z karty nemazat bez výslovného potvrzení | potvrzení musí být jednoznačné, ale ne otravné |

## P0 — bez toho se appka nepoužívá

| # | Úprava | Proč | Pozn. |
|---|---|---|---|
| 1 | **Jedna obrazovka, jedno tlačítko.** Domovská: stav těla, „23 nových ARW · 1,1 GB · ~8 min“, velké tlačítko **Stáhnout nové**; ostatní (apky, diagnostika, soubory) pod spodní lištou | hlavní úkol = nahradit čtečku karet | ETA z naměřené rychlosti posledního přenosu |
| 2 | **Kontextový „co udělat na těle“.** Nápověda podle portů, viz tabulka stavů Domů v `UX_WIREFRAMES.md` §2. Hlavní tok („Stáhnout nové“) potřebuje **jen Agent (8808)**; zapnutí ADB v Tweaku se zmiňuje až v Apkách a Diagnostice, kde je ADB nutné | známé kroky v terénu se nehádají, appka je navádí; po restartu těla stačí 1 krok (spustit Agent) | diagnostika portů je levná |
| 3 | **Auto‑připojení bez zadávání.** Tělo se pozná podle `mac` v odpovědi `GET /status` (appka na Androidu 10+ nesmí číst ARP ani MAC cizího hosta), sken podsítě na pozadí, profily domácí Wi‑Fi / hotspot; první párování **QR kódem z displeje Agenta** (IP:port) | nula psaní IP | QR na 3" displeji čitelný (Odhad) |
| 4 | **Přenos žije v notifikaci** (FGS connectedDevice): postup, pauza/zrušit, výsledek na zamčené obrazovce; auto‑pokračování po výpadku | telefon v kapse, appka nemusí být otevřená | |

## P1 — rychlost výběru a bezpečnost mazání

| # | Úprava | Proč |
|---|---|---|
| 5 | **Mřížka náhledů z vložených JPEG** s časem a názvem; výběr tahem; filtry *nové / stažené / vše*; řazení podle času; „Stáhnout vybrané (7 · 340 MB)“ | desítky souborů, jen některé |
| 6 | **Stavové odznaky souboru:** jen na kartě · staženo+ověřeno · smazáno z karty. Mazat jde jen *ověřené*; dialog s počtem a velikostí; potvrzení **dlouhým podržením 1,5 s**; žádné „smazat vše“ | pravidlo projektu + použitelnost |
| 7 | **Tmavé téma výchozí, velké cíle, vysoký kontrast**, haptika při dokončení; Material 3 dynamické barvy | venek, slunce, jedna ruka |
| 8 | **Žádná textová pole v hlavním toku**; jediná textová položka = název složky exportu, předvyplněný datem | Nicky diktuje |

## P2 — správa a diagnostika (F2)

| # | Úprava | Proč |
|---|---|---|
| 9 | **Apky na těle:** verze, odznak „aktualizace“ z GitHub Releases, jedno klepnutí = stáhnout + ověřit podpis + instalovat; varování při nesedícím podpisu | instalace bez PC |
| 10 | **Diagnostika jako karty:** baterie, místo, RSSI, verze Tweaku; logcat až pod tlačítkem s předvolbami; „stáhnout TRAPFOC.LOG / SHARP.CSV“ | log až když něco nejde |
| 11 | **Dlaždice rychlého nastavení** „Sync z těla“, widget se stavem | nula otevírání appky |

## P3 — později

| # | Úprava |
|---|---|
| 12 | Export na PC, napojení na foto_orchestrator |
| 13 | GPS stopa → XMP sidecar, mapa spárovaných snímků |
| 14 | Kód objektivu k sérii výběrem z registru (jen značka) |

## Agent na těle

| Úprava | Proč |
|---|---|
| Po spuštění **automaticky** server + obrazovka: velký QR (IP:port), „Agent běží · IP:8808 · Wi‑Fi dBm · baterie %“ | nula interakce na těle |
| Během přenosu: název souboru, pruh, rychlost | viditelnost bez telefonu |
| Žádné menu ani nastavení na těle | displej i ovládání těla jsou na to nevhodné |

## Zamčená rozhodnutí (31. 10. 2026)

| # | Rozhodnutí | Volba | Zamítnuté varianty |
|---|---|---|---|
| 1 | Informační architektura | **Spodní lišta 4 položky: Domů · Soubory · Apky · Diagnostika.** Každá sekce 1 klepnutí. | jen Domů + „více“ (Apky/Diagnostika = 2 klepnutí); 3 položky se smíchanou sekcí „Tělo“ |
| 2 | Stavy souboru | **Pět hlavních stavů + chyba**, viz tabulka níž. Mazat z karty jde jen `OVERENO`. | 3 stavy (karta / staženo / smazáno) — skrývaly rozdíl „velikost sedí“ vs. „MD5 sedí“ |
| 3 | Onboarding | **3 obrazovky:** 1) složka pro RAWy (SAF, předvyplněno `Pictures/Rincewinder`, podsložka s datem na přenos), 2) oprávnění `ACCESS_LOCAL_NETWORK` + `POST_NOTIFICATIONS` s vysvětlením v kontextu, 3) sken QR z displeje Agenta; záložní cesty „zadat IP ručně“ a „přeskočit, najdu tělo skenem sítě“ | 2 obrazovky; bez onboardingu, vše kontextově |
| 4 | Vizuální identita | **Akcent oranžová** (Rincewindova róba), tmavé téma výchozí, Material 3 dynamické barvy s oranžovým fallbackem. **Ikona: mágova čepice s hvězdami** („WIZZARD“) na oranžovém pozadí. | cívka filmu se šipkou (neutrální modrá); Zavazadlo (zelená) |
| 5 | Wireframy | **Jak jsou v `docs/UX_WIREFRAMES.md` / `docs/ux_preview.html`** (Domů ×2 stavy, Mřížka, Mazání, Notifikace, Apky, Diagnostika, Onboarding ×3, Agent na těle) | — |
| 6 | Co musí umět F0b | **Stavová obrazovka Agenta s QR** (QR vlevo, IP:port + stav vpravo, řádek přenosu dole). Záloha, pokud QR encoder nestihne F0b: velká IP bez QR, Pixel ji vezme skenem podsítě. Detail v sekci „Dopad na F0b a F1“. | — |

### Datový model stavů souboru (rozhodnutí 2)

Klíč souboru: `název + velikost + mtime` z `GET /ls` Agenta. Neznámý klíč se při výpisu vloží jako `NA_KARTE`. Pro párování mezi cestami (Wi‑Fi × OTG/MTP, F3) platí jen `název + velikost`; `mtime` z MTP nemá časovou zónu a FAT má rozlišení 2 s, proto je jen informativní.

| Stav | Význam | Odznak v mřížce | Přechod dál |
|---|---|---|---|
| `NA_KARTE` | Pixel o souboru ví, nestáhl ho | bez odznaku | start přenosu → `STAHUJE_SE` |
| `STAHUJE_SE` | ve frontě / probíhá; drží offset pro pokračování po výpadku | kruh s % (oranžový rámeček) | velikost sedí → `STAZENO`; výjimka → `CHYBA` |
| `STAZENO` | soubor na Pixelu, velikost sedí, MD5 neověřeno | obrysová ✓ (rozdíl od `OVERENO` je tvarem, ne odstínem; čitelné na slunci) | `GET /md5` sedí → `OVERENO`; nesedí → `CHYBA` |
| `OVERENO` | MD5 z Agenta = MD5 na Pixelu | plná zelená ✓✓ | uživatel smaže z karty → `SMAZANO_Z_KARTY` |
| `SMAZANO_Z_KARTY` | na kartě už není, na Pixelu ověřená kopie | ztlumený náhled, šedé ✓; jen ve filtru „Stažené“ | koncový |
| `CHYBA` | přenos nebo ověření selhalo; důvod v detailu; retry z fronty | červený ! | retry → `STAHUJE_SE` |

Vedlejší stavy (doplněno po revizi 31. 10., viz níž):

| Stav | Význam | Zobrazení |
|---|---|---|
| `STARE` | soubor byl na kartě už při prvním spárování a uživatel zvolil „nebrat jako nové“ | bez odznaku, jen ve filtru *Vše*; stáhnout jde ručně |
| `LOKALNE_CHYBI` | záznam byl `STAZENO`/`OVERENO`, ale kopie na Pixelu zmizela nebo nesedí velikost (smazána v Souborech, přesunuta) | červený obrys; patří do *Nové*; **mazání z karty zakázáno** |
| `NEZNAMY` | soubor je na Pixelu, na kartě chybí a appka ho nesmazala (např. smazán v těle) | jen v detailu |

Pravidla:

| Pravidlo | Znění |
|---|---|
| Co je „nové“ | `NA_KARTE` + `CHYBA` + nedokončené `STAHUJE_SE` + `LOKALNE_CHYBI`. Stejná definice platí pro tlačítko „Stáhnout nové (n)“, počet na Domů i filtr *Nové* |
| Filtr *Stažené* | `STAZENO` + `OVERENO` + `SMAZANO_Z_KARTY` |
| Mazání | tlačítko „Smazat z karty (n ověřených)“ počítá jen `OVERENO`; **těsně před dialogem se u každého znovu ověří, že `localPath` existuje a velikost sedí**; dialog vypíše, kolik vybraných vynechal a proč |
| Neověřené | v dialogu a ve velkém náhledu je akce „Ověřit MD5“, aby `STAZENO` nezůstalo trvale nesmazatelné, kdyby F0b rozhodl o ověřování na vyžádání |
| Zrušený přenos | rozdělané soubory zůstávají `STAHUJE_SE` s `bytesDone` a spadají do *Nové* |
| První spárování | jednorázová volba: „Na kartě je 620 ARW (15,3 GB). Brát jako nové: **[snímky od dneška]** · [všech 620] · [žádné]“. Zbytek dostane `STARE`. Výchozí je „od dneška“ |
| Velký počet nových | nad 50 souborů nebo 2 GB Domů místo tlačítka „Stáhnout nové“ nabídne „Moc na Wi‑Fi (~2 h) — připoj kabel“ (F3) a „Stáhnout jen dnešní“ |

## Revize po zamčení (31. 10. 2026)

Kontrola dokumentů proti CLAUDE.md a F0B_SONDA.md našla mezery. Opravy níž jsou **důsledky zamčených rozhodnutí, ne nová rozhodnutí**, až na dvě položky, které Nicky **potvrdil 31. 10. 2026** (zamčeno): přibalení APK Agenta do Pixel appky s nabídkou instalace a výchozí „brát jako nové snímky od dneška“ při prvním spárování.

| Oblast | Oprava | Dopad |
|---|---|---|
| QR kód | původní „verze 2–3, ECC M“ se nevejde (v2‑M = 26 B, v3‑M = 42 B, obsah měl ~52 B). Nově **verze 4 (33 modulů), ECC M, byte mode, max 62 B**; obsah `http://<IP>:8808/?m=<MAC 12 znaků>&v=<verze>` ≈ 45–48 B; při ~240 px ≈ 7 px na modul | F0b encoder |
| Identita těla | MAC z ARP appka na Androidu 10+ nezíská. Tělo se pozná podle `mac` v `GET /status` (nebo z QR); přes adb podle `ro.product.device=dslr-diadem` | Pixel, onboarding |
| Stavy Domů | doplněny: Agent běží a ADB vypnuté (sync funguje), Agent v těle chybí (nabídnout instalaci), tělo spí. Po restartu těla tak stačí **1 krok**, ne 2 | wireframy §2 |
| Instalace Agenta | **zamčeno Nickym:** APK Agenta se přibalí do Pixel appky a při stavu „ADB OK, Agent chybí“ se nabídne „Nainstalovat Agent“ (`pm install` přes dadb). Bez toho nemá cíl „instalace bez PC“ tok pro samotný Agent | F1 |
| Výpadek těla | notifikace s vibrací a **akčním textem** podle portů („Tělo spí: zmáčkni spoušť napůl“, „Agent neběží: MENU → Aplikace → Rincewinder Agent“, jinak „Spustit z telefonu“ po F0b #4) | wireframy §5 |
| První spárování | volba, co brát jako nové (viz pravidla výš). **Zamčeno Nickym:** výchozí „od dneška“ | stavy souboru |
| Mazání z karty | před dialogem kontrola existence a velikosti lokální kopie; token pro `POST /rm` váže cestu **+ velikost + mtime** a Agent při neshodě vrací 409 | F1, Agent |
| Prázdné a chybové stavy | doplněny do wireframů §11 | wireframy |
| Zbytek | jediné textové pole je název složky na obrazovce onboardingu 1 (SAF, předvyplněno); aktualizace běžícího Agenta přes `pm install -r` ho ukončí, Apky na to před instalací upozorní a po ní nabídnou „Spustit z telefonu“ | wireframy §6 |

## Dopad na F0b a F1

Tuto sekci přebírá technická session do `docs/ZADANI_F1.md`.

### Co musí umět už sonda F0b (Agent na těle)

| Položka | Požadavek z designu | Pozn. |
|---|---|---|
| Stavová obrazovka | po spuštění z Application menu **bez interakce** nastartovat server a zobrazit: vlevo QR, vpravo „Rincewinder Agent“, verze, `IP` a `:8808` velkým písmem, Wi‑Fi dBm, baterie %, volné místo na kartě, počet spojení; spodní řádek „MENU = konec“ | displej 3" 640×480 (4:3), jen tlačítka; žádné menu ani nastavení na těle |
| QR kód | obsah `http://<IP>:8808/?m=<MAC ath0 bez dvojteček>&v=<verze>` (≈ 45–48 B); **verze QR 4 (33 modulů), ECC M, byte mode**, strana ≈ 1/2 výšky displeje (~240 px, ≈ 7 px na modul) | generátor QR bez knihovny na SDK 16 (vlastní minimal encoder, byte mode, maska 0–7 s penalizací) — **Odhad** ~250–350 řádků Javy; pokud nestihne F0b, záloha: velká IP bez QR a Pixel skenuje podsíť |
| Řádek přenosu | během `GET /file` ukázat název souboru, pruh, MB/s; po skončení zmizí | viditelnost bez telefonu |
| `GET /status` | musí vracet: `ip`, `port`, `mac`, `agentVersion`, `battery` (%, V, °C), `cardPresent`, `cardFree`/`cardTotal`, `wifiDbm`, `wifiChannel`, `wifiLinkMbps`, `tweakVersion` (pokud zjistitelná, jinak `null`), `activeClients`, `serviceRunning`, `uptime` | Domů i karty Diagnostiky čtou jen tento endpoint; co se zjistit nedá, je `null` a karta ukáže „—“ |
| `GET /ls` | `name;size;mtime;dir` — všechny tři složky klíče souboru | inkrementální „nové“ |
| `GET /thumb` | vložený JPEG z ARW, ideálně < 200 kB (menší z vložených náhledů, pokud ARW nese víc) | mřížka 3 sloupce, stačí ~400 px |
| `GET /md5` | pro stav `OVERENO` | čas výpočtu na těle (49 MB při ~2,7 MB/s ≈ 18 s, tedy zhruba stejně dlouho jako přenos) zapsat do výsledků F0b — rozhodne, zda ověřovat vždy (ETA na Domů pak počítá přenos + ověření), nebo jen na vyžádání (akce „Ověřit MD5“, viz pravidla stavů) |
| `POST /rm` | token z `GET /rm-token?path=&size=&mtime=` | Agent před smazáním porovná `size` a `mtime`; při neshodě 409 a nic nemaže (ochrana před výměnou karty a stejným jménem po formátu) |
| Otázka #4 sondy (`am start`) | pokud funguje a přepne UI těla, Domů ukáže tlačítko „Spustit Agent z telefonu“ místo nápovědy „MENU → Aplikace“ | 0 kroků v terénu místo 1 |

### Co má držet Pixel appka (F1)

| Oblast | Požadavek |
|---|---|
| Databáze souborů | tabulka s klíčem `název+velikost+mtime`, stav podle modelu výš, `localPath`, `md5Agent`, `md5Local`, `bytesDone` (pokračování), `errorReason`, `lastSeenOnCard` |
| Fronta | persistentní (Room), přežije pád i restart; po výpadku těla auto‑pokračování bez dotazu; notifikace FGS `connectedDevice` s Pozastavit / Zrušit |
| Profily těla | `mac`, poslední IP, SSID/profil sítě (domácí Wi‑Fi, hotspot Pixelu), poslední naměřená rychlost (pro ETA) |
| Diagnostika portů | 5555 (adb) a 8808 (Agent) zvlášť → tři stavy Domů: tělo nenalezeno / ADB OK + Agent neběží / vše OK; text nápovědy podle stavu |
| Mazání z karty | jen `OVERENO`; dialog s počtem, velikostí a cílovou složkou; potvrzení podržením 1,5 s; Agent: `POST /rm` s potvrzovacím tokenem z předchozího `GET` |
| Oprávnění | `ACCESS_LOCAL_NETWORK` + `POST_NOTIFICATIONS` v onboardingu 2/3; při odmítnutí karta na Domů s odkazem do nastavení, žádný tichý timeout |
| Textová pole | jediné: název složky exportu (SAF, předvyplněno) |
| Téma | ~~tmavé výchozí, Material 3 dynamic colors, fallback akcent oranžová `#FF8A3D`~~ → nahrazeno vizuálem „Dvě světla · beton“ (níž); haptika při dokončení přenosu |

## Vizuál „Dvě světla · beton“ (zamčeno Nickym 8. 11. 2026)

Vychází z měření všech 435 snímků na kartě: dva póly světla — **sodíková amber** (190 velmi teplých) a **modrá hodina / LED** (68 velmi chladných), liminální prázdná místa (parkoviště 07:03, kolonáda s LED, sila, bar ve 2:26), 16:9. Retro‑futurismus = LED číslice; brutalismus = pohledový beton.

| Prvek | Pravidlo |
|---|---|
| Pozadí | svislý přechod modrá hodina nahoře (`#13222D`) → sodík dole (`#24170A`), jemné zrno; žádné dynamic colors |
| Barvy rolí | **tyrkys** `#5FD3C4` = ověřeno / Agent běží; **sodík** `#FFB34A` = výběr a akce; **červená** `#FF6B5A` = chyba a mazání; chladný text `#CFEEFC` a LED bílá `#E9F7FF` pro čísla |
| Beton | karty Domů s otisky bednění (prkna po 18 dp), seříznuté rohy (`CutCornerShape` 10 dp vlevo nahoře a vpravo dole), otvory po spínacích tyčích, šablonová čísla podlaží `01`, `02`; mezi kartami spára |
| Čísla | LED číslice s „duchem“ `888` a září (počet nových snímků) |
| Průběh | sloupořadí (≤ 24 sloupů), plní se sodíkem |
| Hlavní akce | vykonzolované tlačítko (sodík, stín pod konzolou); vedlejší jako obrysové |
| Mřížka | 2 sloupce 16:9, zapuštěné rámy; snímky na výšku otočené podle EXIF Orientation z hlavičky ARW (první 4 KB přes Range, cache) a vložené celé (`Fit`) |
| Dialogy | plochý beton `#151B21`, seříznuté rohy; mazání: červené „Nevratné…“, podržení 1,5 s s červenou výplní |
| Texty | lidské věty („A7 II je blízko.“, „Tělo spí.“), lidská jména apek, bez interních kódů fází |

Kód: `app/.../ui/Theme.kt` (paleta), `ui/Beton.kt` (komponenty). Makety s Nickyho fotkami zůstávají mimo repo.
