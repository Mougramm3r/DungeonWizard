# DungeonWizard – Dokumentation

Spiel-Plugin der WizardSuite: Dungeons per Item-Wizard bauen und als eigene Instanzen spielen,
mit echtem Inventar (Loot mit rein, Loot mit raus, Loot verlieren). Anforderungen und
Entscheidungen stehen in `DungeonWizard_Auftrag.md` (Antworten des Nutzers: Abschnitt 11a) und
`CLAUDE.md`. Diese Datei dokumentiert den tatsächlichen Stand.

## Status (2026-09-26): Phase 0 geschrieben (v0.0.0), noch nicht kompiliert

Gerüst steht. **Noch nicht kompiliert:** In der Cloud-Umgebung waren `repo.papermc.io` und
`maven.enginehub.org` von der Netzwerk-Richtlinie gesperrt (HTTP 403), dadurch fehlen Paper-API
und WorldEdit zum Bauen. Die Kompilierung wird nachgeholt, sobald die Hosts freigegeben sind
(oder lokal beim Nutzer). Kein Praxistest.

Hängt an **WizardCore** `0.0.1` (eingeshaded), **nicht** an ArenaWizard2 oder GlideWizard.

## Geplanter Funktionsumfang und Phasen

Phase N = Version `0.0.N`. Abschnittsverweise beziehen sich auf `DungeonWizard_Auftrag.md`.

- [x] **Phase 0 – Gerüst:** `pom.xml` (WizardCore, paper-api, worldedit `provided`, Shade),
      `plugin.yml`, `config.yml`, Hauptklasse mit `InstanceManager` (`dwz_instance_`) und
      `sweepOrphans()`, Platzhalter-Befehl `/dwz`. **Noch nicht kompiliert** (siehe Status).
- [ ] **Phase 1 – Kern:** Wizard Create/Edit (Kompass, Spawn, Truhen, Ausgang, Einstellungen,
      Cancel/Save), Instanzierung, `join new`/`join`/`leave`/`list`/`info`/`owner`, Owner +
      offen/geschlossen, Start direkt bei Erzeugung (keine Warte-Phase, late-join), Loot-Regel und
      Leben bei `join new` wählbar, Loot-Regeln inkl. Absicherung (Abschnitt 5).
- [ ] **Phase 2 – Addon-API** + `ADDON_API.md` (Abschnitt 10a).
- [ ] **Phase 3 – Mobs:** Vorlagen + Editor, Spawnpunkte, Gruppen, Drops mit Loot-Tag.
- [ ] **Phase 4 – Türen + Events:** Auslöser/Bedingungen/Aktionen, Editor, Trigger-Punkt-Werkzeug
      mit GUI (11a), Schlüssel, Respawn-Punkte, Belohnungs-Truhe.
- [ ] **Phase 5 – Bosse:** Bossbar, Phasen, Fähigkeiten, Skalierung.
- [ ] **Phase 6 – optional:** HUD, Statistik, Economy (je nach Antwort auf Punkt 11.11).

## Phase 0 im Detail (v0.0.0)

- `DungeonWizardPlugin`: legt beim Start `config.yml` an, erzeugt den `WorldEditRegionManager` nur,
  wenn WorldEdit/FAWE installiert ist (sonst Warnung im Log), und einen eigenen
  `InstanceManager` mit dem Präfix `dwz_instance_`. `sweepOrphans()` läuft vor jeder möglichen
  Instanz-Erzeugung und entfernt Instanz-Welten, die ein Absturz hinterlassen hat.
- `/dwz` (ohne Permission): zeigt Version und ob WorldEdit/FAWE gefunden wurde.
- `plugin.yml`: alle Permissions aus Abschnitt 9c sind schon angelegt (`default: op`).
  `dwz.forcestart` entfällt, weil es keine Warte-Phase gibt (11a).
- `config.yml`: Werte aus Abschnitt 9d, angepasst an 11a (kein `countdown-seconds`/
  `waiting-radius`, dafür `default-lives`, `default-late-join`, Blacklist
  `ENDER_PEARL, CHORUS_FRUIT, ELYTRA`). In Phase 0 wird noch keiner davon gelesen.

### Zum Testen (Phase 0)

1. `../WizardCore`: `mvn clean install`, dann hier `mvn clean package` → `target/DungeonWizard.jar`.
2. JAR in `plugins/` legen, Server starten: Log zeigt „DungeonWizard 0.0.0 aktiviert.“, ohne
   WorldEdit/FAWE zusätzlich die Warnung.
3. `/dwz` zeigt Version und WorldEdit-Status.
4. Ordner `plugins/DungeonWizard/` enthält `config.yml`.

## Ordnerstruktur

```
DungeonWizard/
  pom.xml
  CLAUDE.md
  DungeonWizard_Auftrag.md
  DOKUMENTATION.md
  src/main/java/de/deinserver/dungeonwizard/
    DungeonWizardPlugin.java
    command/DungeonWizardCommand.java
  src/main/resources/
    plugin.yml
    config.yml
```

## Bauen

```bash
cd ../WizardCore && mvn -q clean install
cd ../DungeonWizard && mvn clean package   # Ergebnis: target/DungeonWizard.jar
```

## Bekannte Einschränkungen / TODOs

- Phase 0 ist noch nicht kompiliert (siehe Status).
- Offene Punkte aus Auftrag Abschnitt 11: 7, 8, 9, 10, 11, 13, vor der jeweiligen Phase klären.
