# CLAUDE.md – DungeonWizard

Spiel-Plugin der WizardSuite: Dungeons mit einem Item-Wizard bauen (Grenzen, Spawn, Truhen, Mobs,
Bosse, Türen, Events, Ausgang) und als eigene Instanzen spielen – mit echtem Inventar: Loot mit
rein, Loot mit raus, Loot verlieren. Befehl `/dwz` (Annahme, siehe Auftrag Abschnitt 11). Vom
Grundprinzip wie ArenaWizard2/GlideWizard aufgebaut, aber **ohne Lobby** (Spieler starten am
Dungeon-Spawn) und mit eigenem Rundenablauf.

**Vollständiger Umsetzungsauftrag: [`DungeonWizard_Auftrag.md`](DungeonWizard_Auftrag.md)** –
maßgeblich, vor jeder Phase lesen. Offene Punkte stehen dort in Abschnitt 11: **vor der jeweiligen
Phase beim Nutzer rückfragen, nicht selbst entscheiden.**

**Projektdokumentation:** `DOKUMENTATION.md` in diesem Ordner (in Phase 0 anlegen, Aufbau wie
`../GlideWizard/DOKUMENTATION.md`). Bei jeder inhaltlichen Änderung aktualisieren, keine weitere
Doku-Datei anlegen.

## Suite-Regeln (Kurzfassung – maßgeblich ist `../CLAUDE.md`, falls vorhanden)

Diese Kurzfassung steht hier, weil die Cloud-Session die Suite-Dateien (`../CLAUDE.md`,
`../SUITE_UEBERSICHT.md`) nur sieht, wenn sie mit ausgecheckt wurden.

- **Arbeitsweise:** Rückfragen zuerst; To-do-Liste pro Auftrag; jede Änderung in `DOKUMENTATION.md`.
- **Ein Chat = ein Projekt:** nur DungeonWizard ändern. ArenaWizard2, GlideWizard, WizardCore,
  GUIWizard, PartyWizard sind **Referenz (nur lesen)**. Braucht DungeonWizard etwas von dort, einen
  Eintrag für `SUITE_UEBERSICHT.md` vorschlagen, nicht selbst ändern.
- **Versionierung:** eine Version pro Phase (`0.0.N` in `pom.xml` + annotierter Tag `v0.0.N`),
  Bugfix-Commits ohne neue Version.
- **Git:** nur auf ausdrücklichen Wunsch committen/pushen; Commit-Trailer wie vom Harness vorgegeben.
- **Technik:** Java 21, Paper 1.21.4 (`api-version: '1.21'`), Maven, Paket
  `de.deinserver.dungeonwizard`. Code-Kommentare und Spielermeldungen Deutsch in ASCII-Umschrift
  (ae/oe/ue/ss).
- **Test:** hier läuft kein Minecraft-Server. Verifikation = saubere Kompilierung; der Nutzer testet
  auf seinem Paper-Server. Nie „getestet“ behaupten.

## Referenzprojekte (nur lesen)

- **WizardCore** (`../WizardCore/`) – Pflicht-Abhängigkeit: `InstanceManager`/`InstanceWorldFactory`
  (eigener Präfix `dwz_instance_`), `WorldEditRegionManager`, `RelativeLocation`, `BlockRegion`,
  `IdUtil`, `ItemBuilder`. Vor dem ersten Bauen dort `mvn clean install`. Als normale
  (nicht `provided`) Abhängigkeit einbinden und einshaden.
- **GlideWizard** (`../GlideWizard/`) – **nächste Vorlage**: Spiel-Plugin auf WizardCore ohne
  ArenaWizard2-Abhängigkeit, eigener Rundenablauf, kein eigenes `/leave`, eigene Kopie der
  Item-Wizard-Mechanik.
- **ArenaWizard2** (`../ArenaWizard2/`, Maven-Modul in `../ArenaWizard2/ArenaWizard2/`) – Vorlage für
  Item-Wizard (`wizard/`), Truhen-Rarities + Loot-Loader (`loot/`, `ChestRarity`), Owner/Sichtbarkeit,
  `JoinService`, Tod-Abfangen + Item-Drop (BM), Addon-API (`api/`, `ADDON_API.md`).
  **Keine Code-Abhängigkeit** – nichts aus `de.deinserver.arenawizard2` importieren.
- **GUIWizard** / **PartyWizard** – nur lesen, um die API passend zu `GameBackend` bzw.
  `MatchBackend` zu formen (Auftrag Abschnitt 10).

## Leitplanken

- **Echtes Inventar:** Kein `PlayerSnapshot`-Tausch im Run. Loot-Regeln, Tod-Abfangen,
  Reconnect/Absturz, Stash-Sperren und Duplikationsschutz (Auftrag Abschnitt 5) sind Kernfunktion
  von Phase 1, nicht „Polish“. Jede Stelle, an der Items das Inventar verlassen oder betreten,
  in `DOKUMENTATION.md` beschreiben.
- **Eine Wahrheit:** Beitrittsregeln in einem `JoinService`, den Befehl und API gleichermaßen nutzen.
- **Instanz-Erzeugung blockiert** einige Sekunden – vorher immer eine Meldung an den Spieler.
- **Erst teleportieren, dann entladen**; `disableBuffering()` ist in WizardCore – dort nichts
  nachbauen oder doppelt patchen.
- **Komplette Items speichern** (Mob-Ausrüstung, Drops, Schlüssel, „Item geben“): über
  `ItemStack.serializeAsBytes()`/Base64, nicht nur das Material.
- **Mobs/Bosse** tragen einen PDC-Tag (Instanz, Gruppe, Vorlage); Vanilla-Drops aus, Drops nur aus
  der Vorlage. Kein natürliches Mob-Spawning in Instanz-Welten.
- **Menüs** im Plugin (Editoren im Wizard) über eigene `InventoryHolder` erkennen und jeden Klick
  abbrechen, außer bewusst freigegebene Ablage-Slots (Muster: GUIWizard `DisplayItemMenu`).
- **Addon-API** (`api`/`api.event`) stabil halten wie bei ArenaWizard2; `apiVersion()` als Methode.
- Kein eigener `/leave`.

## Bauen

**Cloud/Linux:** Repos als Geschwister-Ordner auschecken (`WizardSuite/WizardCore`,
`WizardSuite/DungeonWizard`, …), damit die `../`-Verweise stimmen. Netzwerkzugriff auf
`repo.papermc.io`, `maven.enginehub.org`, `jitpack.io`, Maven Central nötig.

```bash
cd ../WizardCore && mvn -q clean install
cd ../DungeonWizard && mvn clean package   # Ergebnis: target/DungeonWizard.jar
```

**Lokal (Windows, beim Nutzer):** siehe `../CLAUDE.md` (IntelliJ-Maven, JDK 21).
