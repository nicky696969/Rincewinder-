# Rincewinder — wireframy (31. 10. 2026)

> Textová podoba obrazovek zamčených v `docs/UX_NAVRH.md` (rozhodnutí 1–6). Vizuální náhled: `docs/ux_preview.html` (offline, bez CDN).
> Pixel 9 Pro, tmavé téma, Material 3, akcent oranžová. Spodní lišta: **Domů · Soubory · Apky · Diagnostika**.

## 1. Domů — stav OK

```
┌──────────────────────────────┐
│ ◉ Rincewinder                │
│                              │
│ TĚLO                         │
│ ● A7 II připojena            │
│ Agent běží · 10.98.92.25     │
│ −48 dBm · bat 64 % · 41 GB   │
│                              │
│ NOVÉ SNÍMKY                  │
│ 23 nových ARW                │
│ 1,1 GB · ~8 min při 2,3 MB/s │
│                              │
│ POSLEDNÍ PŘENOS              │
│ Dnes 9:14 · 41 souborů ·     │
│ 2,0 GB · vše ověřeno ✓       │
│                              │
│ ┌──────────────────────────┐ │
│ │   STÁHNOUT NOVÉ (23)     │ │  ← jediné primární tlačítko
│ └──────────────────────────┘ │
│ [ Vybrat ručně… ]            │  → Soubory (mřížka, filtr Nové)
├──────────────────────────────┤
│ Domů │ Soubory │ Apky │ Diag │
└──────────────────────────────┘
```

Data: vše z `GET /status` + `GET /ls`. ETA = velikost / poslední naměřená rychlost; bez měření „~? min“.

## 2. Domů — tři stavy připojení

| Stav | Zjištění | Text karty TĚLO | Nápověda / akce |
|---|---|---|---|
| Tělo nenalezeno | na žádné kandidátní IP neodpovídá 8808 ani 5555 | ● „Tělo nenalezeno“ | „Na těle: probuď ho (spoušť napůl), pak MENU → Aplikace → **Rincewinder Agent**.“ Tlačítko „Hledat znovu“; chybí-li oprávnění lokální sítě → karta s odkazem do nastavení. ADB v Tweaku se tu **nezmiňuje** |
| Agent neběží | 5555 odpovídá, 8808 mlčí, Agent je v `pm list packages` | ● „Tělo nalezeno, Agent neběží“ | „Na těle: MENU → Aplikace → **Rincewinder Agent**“; tlačítko „Spustit z telefonu“ (`am start`, zůstává do výsledku F0b #4) |
| Agent v těle chybí | 5555 odpovídá, 8808 mlčí, balíček Agenta není nainstalován | ● „Agent není v těle“ | tlačítko „Nainstalovat Agent“ (APK přibalená v appce, `pm install` přes dadb) — zamčeno Nickym 31. 10. |
| Agent OK, ADB vypnuté | 8808 odpovídá, 5555 mlčí | ● „A7 II připojena“ | Domů **plně funkční** (přenos nepotřebuje ADB). V Apky a Diagnostika karta „Pro instalaci a logcat zapni v Tweaku Developer → ADB“ |
| Vše OK | 8808 i 5555 odpovídají | ● „A7 II připojena“ | obrazovka 1 |

Tělo se pozná podle `mac` v `GET /status` nebo podle `ro.product.device = dslr-diadem` přes adb; ARP appka na Androidu 10+ nepřečte.

Karta NOVÉ SNÍMKY a tlačítko Stáhnout jsou v prvních dvou stavech ztlumené („zjistím, až poběží Agent“).

## 3. Soubory — mřížka náhledů

```
┌──────────────────────────────┐
│ Soubory na kartě  620·15,3 GB│
│ (Nové 23)(Stažené)(Vše)(⇅čas)│
│ legenda: ○ karta ◔ stahuje   │
│   ✓ staženo ✓ ověřeno ! chyba│
│ ┌────┐┌────┐┌────┐           │
│ │✓   ││✓   ││✓   │  3 sloupce│
│ │0612││0613││0614│  náhled   │
│ └────┘└────┘└────┘  = /thumb │
│ ┌────┐┌────┐┌────┐           │
│ │    ││✓   ││    │           │
│ └────┘└────┘└────┘           │
│ ┌────┐┌────┐┌────┐           │
│ │ 42%││ ✓  ││ ✓✓ │           │
│ └────┘└────┘└────┘           │
│ Tahem vybereš víc.           │
│ Klepnutí = velký náhled.     │
│ ┌──────────────────────────┐ │
│ │ STÁHNOUT VYBRANÉ (4·190MB)│ │
│ └──────────────────────────┘ │
├──────────────────────────────┤
│ Domů │[Soubory]│ Apky │ Diag │
└──────────────────────────────┘
```

| Prvek | Chování |
|---|---|
| Filtry | *Nové* = `NA_KARTE` + `CHYBA` + nedokončené `STAHUJE_SE` + `LOKALNE_CHYBI`; *Stažené* = `STAZENO` + `OVERENO` + `SMAZANO_Z_KARTY` (ztlumené); *Vše* (vč. `STARE`) |
| Řazení | podle `mtime`, nejnovější nahoře; přepínač na název |
| Výběr | klepnutí na odznak vlevo nahoře nebo tah přes dlaždice; dlouhé podržení = výběr řady |
| Popisek dlaždice | zkrácený název (`NCK0612`) · čas pořízení |
| Odznak vpravo nahoře | podle stavu, viz model v `UX_NAVRH.md` |
| Tlačítko dole | text mění počet a velikost; při filtru *Stažené* se mění na „Smazat z karty (n ověřených)“ |
| Velký náhled | celý vložený JPEG, EXIF řádek (čas, ISO, čas závěrky), tlačítka Stáhnout / Smazat (jen ověřené) |

## 4. Mazání z karty

```
┌──────────────────────────────┐
│ Smazat z karty 5 souborů?    │
│ 240 MB · všech 5 je staženo  │
│ a ověřeno podle MD5 v        │
│ Pictures/Rincewinder/        │
│ 2026-10-31                   │
│                              │
│ Soubor NCK0615 (jen staženo, │
│ neověřeno) vynechán.         │
│                              │
│ ┌──────────────────────────┐ │
│ │▓▓▓▓▓▓▓ Podrž 1,5 s ░░░░░│ │  ← plní se při držení
│ └──────────────────────────┘ │
│ [ Zrušit ]                   │
└──────────────────────────────┘
```

Pravidla: jen `OVERENO`; nikdy „smazat vše“; po smazání snackbar „5 souborů smazáno z karty“ bez Undo (nevratné, proto podržení). Agent: `POST /rm` s tokenem z předchozího `GET /rm-token?path=`.

## 5. Přenos v notifikaci

```
Zamčená obrazovka
┌──────────────────────────────┐
│  ◉ Rincewinder · přenos      │
│  9 z 23 · NCK0620.ARW        │
│  2,3 MB/s · zbývá ~5 min     │
│  ▓▓▓▓▓▓▓▓░░░░░░░░░░░░ 39 %   │
│  Pozastavit      Zrušit      │
└──────────────────────────────┘
┌──────────────────────────────┐
│  ◉ Rincewinder · před 2 min  │
│  Tělo se odmlčelo. Fronta    │
│  čeká, pokračuji po připojení│
└──────────────────────────────┘
```

| Událost | Notifikace |
|---|---|
| Start | trvalá (FGS `connectedDevice`), postup, Pozastavit / Zrušit |
| Výpadek těla | s jednou vibrací, bez dialogu, **s akčním textem podle portů**: nic neodpovídá → „Tělo spí: zmáčkni spoušť napůl“; 8808 mlčí po probuzení → „Agent neběží: MENU → Aplikace → Rincewinder Agent“ (nebo akce „Spustit z telefonu“ po F0b #4). Fronta drží `bytesDone`, auto‑pokračování při dalším nalezení těla. Pokud Pixel hotspot po odpojení těla zhasne, text říká „Zapni hotspot“ (appka ho zapnout nesmí) |
| Dokončení | haptika + „23 staženo, vše ověřeno ✓“ (nebo „21 ověřeno, 2 chyby“ → klepnutí otevře filtr Nové) |
| Zrušení | rozdělané soubory zůstávají `STAHUJE_SE` s offsetem; příští „Stáhnout nové“ je dokončí |

## 6. Apky na těle

```
┌──────────────────────────────┐
│ Apky na těle                 │
│ Rincewinder Agent  0.3 běží  │
│                       [↑ 0.4]│
│ TrapFocus  1.12‑diag  aktuál.│
│ OpenMemories Tweak 0.11      │
│ Timelapse                    │
│ focusbracket                 │
│                              │
│ RINCEWINDER AGENT 0.4        │
│ GitHub Releases · 212 kB     │
│ podpis sedí s verzí v těle ✓ │
│ [ Stáhnout a nainstalovat ]  │
│                              │
│ INSTALOVAT VLASTNÍ APK       │
│ z úložiště Pixelu ·          │
│ před instalací kontrola      │
│ podpisu ⚠ jiný podpis =      │
│ nejdřív odinstalovat         │
├──────────────────────────────┤
│ Domů │ Soubory │[Apky]│ Diag │
└──────────────────────────────┘
```

Aktualizace běžícího Agenta (`pm install -r`) jeho proces ukončí: Apky před instalací varují „přenos se přeruší“ a po ní nabídnou „Spustit z telefonu“ (nebo „MENU → Aplikace → Rincewinder Agent“). Bez ADB (stav „ADB vypnuté“) je celá obrazovka nahrazena kartou s nápovědou k Tweaku.

Řádek apky: klepnutí → detail (verze, balíček, Spustit [`am start`, jen když neběží jiná kamerová apka — kontrola `ps`], Odinstalovat s potvrzením). Zdroj seznamu: `pm list packages -3` přes dadb.

## 7. Diagnostika

```
┌──────────────────────────────┐
│ Diagnostika                  │
│ ┌────────────┐┌────────────┐ │
│ │BATERIE TĚLA││KARTA       │ │
│ │64 %        ││41 GB       │ │
│ │7,9 V·31 °C ││volných z 64│ │
│ └────────────┘└────────────┘ │
│ ┌────────────┐┌────────────┐ │
│ │WI‑FI       ││TWEAK/AGENT │ │
│ │−48 dBm     ││0.11 / 0.3  │ │
│ │ch 11·58Mb/s││ADB ✓ HTTP ✓│ │
│ └────────────┘└────────────┘ │
│ RYCHLOST  poslední 2,3 MB/s  │
│           [Změřit 10 s]      │
│ ↓ TRAPFOC.LOG  ↓ SHARP.CSV   │
│ ↓ RINCEAGT.LOG               │
│ [ Logcat (RinceAgent·TrapFocus·vše) ]│
├──────────────────────────────┤
│ Domů │ Soubory │ Apky │[Diag]│
└──────────────────────────────┘
```

Logy z karty jdou přes Agent (`GET /file`), logcat přes dadb. Logcat obrazovka: předvolby filtru, pauza, sdílet jako soubor.

## 8. Onboarding (3 obrazovky, jen při prvním spuštění)

| # | Nadpis | Obsah | Akce |
|---|---|---|---|
| 1 | Kam ukládat RAWy | „Každý přenos dostane podsložku s datem.“ Pole `Pictures/Rincewinder` (SAF, předvyplněno; jediná položka, kterou lze přepsat) | Pokračovat |
| 2 | Tělo je v místní síti | „Android se zeptá na přístup k **místní síti** a na **notifikace**. Bez sítě appka tělo nikdy nenajde; bez notifikací neuvidíš postup přenosu.“ | Povolit obojí (dva systémové dialogy za sebou) |
| 3 | Naskenuj QR z displeje těla | „Na těle: MENU → Aplikace → Rincewinder Agent.“ Hledáček kamery. Odkazy: *Zadat IP ručně* · *Přeskočit (najdu tělo skenem sítě)* | po skenu rovnou Domů |

Odmítnutí oprávnění: onboarding se nezacyklí, Domů ukáže kartu „Bez přístupu k místní síti tělo nenajdu“ s tlačítkem do nastavení.

## 9. Agent na těle — stavová obrazovka (3", 640×480, bez dotyku)

```
┌─────────────────┬────────────────────────┐
│                 │ Rincewinder Agent      │
│  ▛▀▀▀▀▀▀▀▀▀▀▜   │ běží · 0.3             │
│  ▌ QR ~240px ▐  │                        │
│  ▌           ▐  │ 10.98.92.25            │  ← ~26 px, tučně
│  ▌           ▐  │ :8808                  │
│  ▙▄▄▄▄▄▄▄▄▄▄▟   │                        │
│                 │ Wi‑Fi −48 dBm          │
│   bílé pozadí   │ baterie 64 % · 41 GB   │
│                 │                        │
│                 │ Pixel: 0 spojení       │
│                 │ MENU = konec           │
├─────────────────┴────────────────────────┤
│ NCK0620.ARW  ▓▓▓▓▓▓░░░░░░░░  2,3 MB/s   │  ← jen během přenosu
└──────────────────────────────────────────┘
```

| Prvek | Pravidlo |
|---|---|
| Spuštění | z Application menu; server + obrazovka naběhnou samy, **žádné tlačítko Start** (sonda F0b ho může mít, F1 ne) |
| QR | `http://<IP>:8808/?m=<MAC 12 znaků>&v=<verze>` (≈ 45–48 B), **verze 4 (33 modulů)**, ECC M, bílé pole s tichou zónou 4 moduly, strana ≈ polovina výšky displeje (~7 px na modul) |
| Text | monospace, IP a port největší; stav z téhož zdroje jako `GET /status` |
| Spodní řádek | název souboru, pruh, MB/s; mimo přenos skrytý |
| Ovládání na těle | žádné; MENU = konec Agenta (pokud F0b #1 ukáže, že Service přežije, text se změní na „MENU = na pozadí“) |
| Záloha bez QR | pokud QR encoder nestihne F0b: stejná obrazovka, místo QR velká IP (38 px) a „port 8808“; Pixel tělo najde skenem podsítě |

## 10. Prázdné a chybové stavy Domů

| Situace | Zjištění | Domů ukáže |
|---|---|---|
| Vše staženo | 0 souborů v *Nové* | místo tlačítka „Vše staženo ✓ · poslední přenos …“ |
| Karta bez ARW | `/ls` prázdný | „Na kartě nejsou žádné ARW“ |
| Karta v těle chybí | `cardPresent:false` | „Karta v těle chybí“, tlačítko ztlumené |
| Karta téměř plná | `cardFree` pod 1 GB | „Karta téměř plná — smaž ověřené (n)“ → mřížka, filtr *Stažené* |
| Málo místa na Pixelu | před startem: volné < velikost fronty | „Na Pixelu chybí 3,2 GB“ + počet souborů, které se vejdou |
| Odvolaný přístup ke složce (SAF) | zápis selže při startu | „Ztratil jsem přístup ke složce — vyber ji znovu“ |
| První spárování | žádný známý klíč | volba „Brát jako nové: snímky od dneška / všech N / žádné“ (viz `UX_NAVRH.md`) |
| Velký počet nových | nad 50 souborů nebo 2 GB | „Moc na Wi‑Fi (~2 h) — připoj kabel“ + „Stáhnout jen dnešní“ |
| Kabel (OTG/MTP), F3 | `MtpDevice` připojen | druhé tlačítko „Stáhnout vše kabelem“; MTP nemá MD5, soubory z kabelu jsou `STAZENO` a `OVERENO` se doplní při příštím spojení přes Agent |

## 11. Ikona a barvy

| Prvek | Hodnota |
|---|---|
| Ikona | mágova čepice se hvězdami a nápisem „WIZZARD“ (odkaz na Rincewinda), na oranžovém kruhu/čtverci (adaptive icon: popředí čepice, pozadí oranžová) |
| Akcent | `#FF8A3D` (fallback), jinak Material 3 dynamické barvy |
| Pozadí | `#0F1114`, karty `#1A1D22`, oddělovače `#2F343C` |
| Stavy | OK `#4CD27A`, varování `#F5C542`, chyba `#FF6B6B` |
| Typografie | systémová (Roboto), primární čísla 22 px tučně, popisky 12 px verzálky |
| Cíle dotyku | ≥ 48 dp, primární tlačítko 64 dp, pill |
