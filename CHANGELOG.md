# Changelog

## 1.0.0 — 14. 11. 2026

Pixel appka `1.0.0` (versionCode 3), Agent v těle beze změny (`0.2.0`).

### Nové
- **Apky**: detail aplikace v těle (verze, velikost APK, datum instalace, stav „běží“) jako spodní list;
  spustit / ukončit, záloha APK z těla do Pixelu, stažení logů aplikace z karty, vymazat data, odinstalovat.
- **Instalace APK bez kabelu a karty**: soubor z Pixelu, Sdílet / Otevřít v, odkaz https (jen po potvrzení),
  vydání veřejných GitHub rep, katalog rep ze seznamu OpenMemories-AppList. Volitelná záloha stávající verze.
- Srozumitelné hlášky chyb instalace (jiný podpis, starý Android, málo místa).
- Stavový proužek těla na všech kartách, hlavní akce ukotvená dole, nevratné akce podržením 1,5 s.

### Opravy
- `dadb-fork`: kontrolní součet paketu se počítal přes celý buffer místo odeslaného úseku; adbd na
  Androidu 4.1 spojení zavřel a každý `push` (instalace APK, i přibaleného Agenta) spadl.
- Logy z karty: neúplně stažený soubor se smaže a ohlásí s důvodem (dřív zůstal prázdný soubor bez hlášky).

### Ověřeno na těle (A7 II, Tweak 0.11, hotspot Pixelu)
| Funkce | Výsledek |
|---|---|
| Seznam a detail aplikací přes fork dadb | 8 aplikací, údaje sedí s `dumpsys` a `ls -l` |
| Záloha APK | `com.nicky.focusproject_1.14-diag.apk`, 33 325 B = velikost v těle |
| Logy z karty | `TRAPFOC.LOG` 120 968 B, `SHARP.CSV` 2 951 008 B = velikosti na kartě |
| Instalace z GitHubu, spuštění, odinstalace | DoF Math 1.0 (ověřil Nicky na těle) |

### Neověřeno
- Vymazání dat aplikace (`pm clear`) a mazání z karty: netestováno, jen na výslovný pokyn.
- Instalace ze souboru, ze Sdílet a z odkazu: stejná cesta jako GitHub, samostatně nezkoušeno.
- Jeden případ, kdy se `SHARP.CSV` stáhl prázdný (22:55): příčina neznámá, podruhé prošel celý.
- Sestavení je ladicí (debug podpis), ne vydání s vlastním klíčem.

### Odloženo do 1.1+
OTG kopírování, prohlížeč před stažením s překryvy ostrosti a přepalů, značky stavu a 2/3/4 sloupce v mřížce,
diagnostický balík, přehled spojení, historie akcí, značka skla k sérii, seřízení hodin těla, materiálová
varianta vzhledu (návrh na plátně), dálková spoušť (1.2).
