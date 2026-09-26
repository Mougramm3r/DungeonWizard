# DungeonWizard – Bauauftrag (erste Version)

Dieses Dokument ist der Umsetzungsauftrag für **DungeonWizard**, das Dungeon-Spiel-Plugin der
WizardSuite: Admins bauen Dungeons (Räume, Truhen, Mobs, Bosse, Türen, Ereignisse) mit einem
Item-Wizard, Spieler betreten eine eigene Instanz des Dungeons, kämpfen sich durch und nehmen
ihren Loot mit hinaus – oder verlieren ihn.

**Herkunft:** Wunsch des Nutzers (2026-09-26, sinngemäß): „Vom Prinzip wie ArenaWizard aufgebaut.
Man startet den Dungeon-Create-Modus, legt einen Namen fest und kommt in den Create-Modus. Man geht in
die entsprechende Welt, hat das Region-Tool (Kompass) und bekommt eine Holzaxt, um die Region für
die Instanz festzulegen. Ein Spawn-Tool, wo alle Spieler spawnen (keine Lobby nötig). Truhen wie bei
Battle Mode in verschiedenen Arten, mit Dungeon-Loot. Loot von außen darf mit rein und Loot mit raus
genommen werden, man soll Loot aber auch verlieren können. Werkzeuge, um Events zu erstellen. Mobs,
die spawnen, bei denen man vorbestimmen kann, was sie tragen und was sie droppen. Bosse erstellen,
Türen und mehr. Alles in einem Create-Tool mit Items als Werkzeugen wie bei der Map-Erstellung der
anderen Plugins. Aufgebaut ähnlich zu den anderen und kompatibel mit GUIWizard und PartyWizard.“

**Referenzen (nur lesen, nichts daran ändern):**
- `../ArenaWizard2/ArenaWizard2_Auftrag.md` + `../ArenaWizard2/ArenaWizard2/DOKUMENTATION.md` –
  Vorlage für Item-Wizard, Kompass/Holzaxt, Truhen-Rarities + Loot-Dateien, Owner/Sichtbarkeit,
  Join-Befehle, `PlayerSnapshot`, BM-Item-Drop beim Tod.
- `../ArenaWizard2/ArenaWizard2/ADDON_API.md` – Muster der Addon-API (Pflichtlektüre vor Phase 2).
- `../GlideWizard/GlideWizard_Auftrag.md` + `../GlideWizard/DOKUMENTATION.md` – **das jüngste
  Beispiel eines Spiel-Plugins, das NICHT von ArenaWizard2 abhängt** und trotzdem dessen Schema
  übernimmt. DungeonWizard ist strukturell dasselbe Muster (eigener Rundenablauf, WizardCore,
  kein eigenes `/leave`).
- `../WizardCore/CLAUDE.md` + `DOKUMENTATION.md` – Instanzierungs-Engine (Pflicht-Abhängigkeit).
- `../GUIWizard/DOKUMENTATION.md` (Abschnitt Design-Entscheidungen, `GameBackend`) und
  `../PartyWizard/DOKUMENTATION.md` (Abschnitt Backend-Schicht, `MatchBackend`) – daran muss sich
  die DungeonWizard-API ausrichten, damit die Addons sie mit einer weiteren Backend-Implementierung
  ansprechen können.

**Status der Punkte in diesem Dokument** (wie in den anderen Aufträgen):
- **„Entschieden"** = vom Nutzer so gewollt oder direkte Folge aus dem Suite-Schema, nicht mehr
  hinterfragen.
- **„Annahme"** = Vorschlag, vor der Umsetzung der jeweiligen Phase **beim Nutzer rückfragen**.
  Alle Annahmen stehen gesammelt in Abschnitt 11.

**Arbeitsweise:** `../CLAUDE.md` (Suite-Regeln) und `CLAUDE.md` (dieser Ordner) lesen und befolgen:
Rückfragen zuerst, To-do-Liste pro Auftrag, jede inhaltliche Änderung in `DOKUMENTATION.md`.

---

## 0. Ausgangslage und Einordnung in die Suite

Neues **Spiel-Plugin** (kein Addon), gleichrangig neben ArenaWizard2 und GlideWizard.

| Punkt | Festlegung |
|---|---|
| Name / Maven | `DungeonWizard`, `de.deinserver:dungeonwizard`, JAR `DungeonWizard.jar` (**entschieden**, Suite-Schema) |
| Paket | `de.deinserver.dungeonwizard` (**entschieden**) |
| Befehl | `/dwz` (**Annahme** – passt zu `/awz`, `/gwz`, `/pwz`; `/glw` weicht bereits ab) |
| Permissions | `dwz.*` analog `awz.*`/`glw.*` (Abschnitt 9) |
| Abhängigkeiten | WizardCore (compile, eingeshaded), Paper 1.21.4, WorldEdit/FAWE (`provided`, softdepend), Vault nur falls Economy gewünscht (Abschnitt 11) |
| **Keine** Abhängigkeit | auf ArenaWizard2 oder GlideWizard (**entschieden**, `../SUITE_UEBERSICHT.md` Abschnitt 1: Spiel-Plugins sind unabhängig installierbar) |
| Welt-Präfix | `dwz_instance_` für den eigenen `InstanceManager` (**entschieden**, nie mit einem anderen Plugin teilen) |
| `/leave` | **kein** eigener `/leave` (belegt ArenaWizard2) – Verlassen über `/dwz leave` (**entschieden**, wie GlideWizard) |
| Sprache | Doku Deutsch; Code-Kommentare und Spielermeldungen Deutsch in ASCII-Umschrift (ae/oe/ue/ss) |

**Grundunterschied zu ArenaWizard2/GlideWizard:** Dort ist das eigene Inventar während der Runde
weggesperrt (`PlayerSnapshot`) und kommt danach unverändert zurück. Im Dungeon spielt man **mit dem
echten Inventar** – das ist der Kern von „Loot mit rein, Loot mit raus, Loot verlieren“ und
gleichzeitig das größte Risiko (Duplikation, Verlust durch Bugs). Abschnitt 5 ist deshalb
besonders sorgfältig umzusetzen und zu dokumentieren.

---

## 1. Kein Lobby-System – Spawn im Dungeon

**Entschieden (Nutzer):** Es gibt **keine** Lobby-Vorlagen wie bei ArenaWizard2/GlideWizard. Die
Spieler erscheinen direkt am Spawn des Dungeons.

Folgen daraus:
- Kein `/dwz create lobby`, kein Enderauge (Lobby-Auswahl) im Wizard.
- **Warte-Phase im Dungeon selbst (Annahme):** Nach `/dwz join new` wird die Instanz erzeugt, der
  Ersteller steht am Spawn. Solange der Zustand `WAITING` ist, sind Mobs/Events noch nicht aktiv,
  Schaden ist aus, Türen sind zu, der Spawn-Bereich darf nicht verlassen werden (Rückteleport, wenn
  man sich weiter als `waiting-radius` vom Spawn entfernt). Mitspieler/Party treten bei. Start
  über Bereit-Item + Mindestspielerzahl wie in ArenaWizard2/GlideWizard (plus Admin-`forcestart`),
  danach kurzer Countdown → `RUNNING`.
- Ob man **nach dem Start noch beitreten** darf (z. B. Party-Nachzügler), regelt eine
  Dungeon-Einstellung `late-join` (Annahme: Standard aus).

### 1a. Dungeon-Instanzierung

**Entschieden:** exakt wie ArenaWizard2-Maps / GlideWizard-Strecken, über WizardCore:
- Das Speichern im Wizard (Kompass-Region + Emerald) sichert die Region als Schematic.
- Pro Durchlauf eine eigene Bukkit-Welt (`InstanceWorldFactory`, Präfix `dwz_instance_`),
  beliebig viele parallele Instanzen desselben Dungeons, kein Singleton-Limit.
- **Alle** Positionen (Spawns, Truhen, Mob-/Boss-Punkte, Türen, Auslöser, Ausgang, Respawn-Anker)
  relativ zum Schematic-Ursprung (`RelativeLocation`/`BlockRegion` aus WizardCore).
- Abbau der Instanz, sobald kein Spieler mehr drin ist; Cleanup verwaister Welten beim Start
  kommt aus WizardCore (`sweepOrphans()`).
- Instanz-Erzeugung blockiert den Hauptthread einige Sekunden → **jede Oberfläche (Befehl, API,
  GUI) schickt vorher eine Meldung.** Dungeons sind tendenziell größer als Arenen: Paste-Zeit beim
  Test messen und in der Doku festhalten; `disableBuffering()`-Fix steckt bereits in WizardCore.
- Instanz-Welten: `doMobSpawning=false`, `doDaylightCycle`/`doWeatherCycle` aus, keine
  Vanilla-Mobs; nur vom Plugin gespawnte Mobs existieren.

### 1b. Erstellen vs. Beitreten

**Entschieden:** wie ArenaWizard2, ohne Modus/Subtyp-Ebene (es gibt nur „Dungeon“):
- `/dwz join new <dungeon|random>` – erstellt immer eine neue Instanz, Ersteller wird Owner.
  Duplikat-Rückfrage, wenn es von genau diesem Dungeon schon eine offene wartende Instanz gibt.
- `/dwz join <dungeon> [instanceId]` – offene Instanzen dieses Dungeons auflisten bzw. gezielt
  beitreten. `/dwz join random` – irgendeiner offenen Instanz beitreten.
- `/dwz list` – Dungeons mit aggregierter Instanz-/Spielerzahl, Drill-down zur Instanzliste.
- `/dwz leave` – verlassen (Folgen für den Loot: Abschnitt 5).

### 1c/1d. Owner und Sichtbarkeit

**Entschieden:** 1:1 wie ArenaWizard2 (Auftrag dort 1c/1d): Ersteller = Owner, kein Kick-Recht,
kein Selbst-Abbruch (`forcestop` nur Admin), Owner-Wechsel beim Verlassen an zufälligen Spieler,
`/dwz owner <name>`, Offen/Geschlossen-Umschalter per Item (geschlossen = reines Sperren; Einladung
trotz „geschlossen“ ist Sache von PartyWizard über `allowJoin`).

---

## 2. Wizard-System: Create

`/dwz create <name>` (Permission `dwz.wizard`) – Name wird beim Befehl oder per Chat-Abfrage
festgelegt, danach Werkzeuge ins Inventar. Der Admin geht in die Welt, in der er den Dungeon gebaut
hat (oder baut ihn während des Wizards), und markiert alles mit den Werkzeugen.

**Entschieden:** Session-/Werkzeug-Mechanik 1:1 aus ArenaWizard2 übernehmen (Session pro Spieler,
Werkzeuge als Items mit PersistentDataContainer-Tag, Inventar vor dem Start sichern und am Ende
wiederherstellen, Drop-Sperre, sauberes Verhalten bei Tod/Disconnect im Wizard, Hologramm-
Markierungen gesetzter Punkte). Der **Inhalt** der Werkzeuge ist dungeon-eigen.

### 2.1 Werkzeuge

Weil es mehr Werkzeuge als Hotbar-Slots gibt: Hotbar = die häufigsten Werkzeuge + Cancel/Save,
die übrigen liegen in der ersten Inventarreihe und können frei in die Hotbar getauscht werden
(Tausch innerhalb des eigenen Inventars erlaubt, Droppen/Ablegen in Container gesperrt).
Genaue Slot-Belegung: **Annahme**, bei Umsetzung festlegen und dokumentieren.

| Item | Werkzeug | Funktion |
|---|---|---|
| Kompass | **Dungeon-Grenzen** | Rechtsklick → Holzaxt ins Inventar (Links = Punkt 1, Rechts = Punkt 2); erneut Kompass → Region bestätigt. Schematic-Grenze der Instanz. Validierung wie ArenaWizard2 (beide Punkte nötig). |
| Totem | **Spieler-Spawn** | Rechtsklick: Spawn an eigener Position + Blickrichtung setzen, Linksklick: entfernen. Mehrere Spawns erlaubt, Spieler werden reihum/zufällig verteilt (wie ArenaWizard2-Lobby). Mindestens ein Spawn Pflicht. |
| Respawn-Anker | **Respawn-Punkt** (optional) | Rechtsklick setzt einen Respawn-Punkt (Checkpoint), Linksklick entfernt. Wird erst aktiv, wenn ein Event ihn freischaltet oder ein Spieler ihn betritt (Annahme). Ohne Respawn-Punkte: Respawn am Spieler-Spawn. |
| Kupfer-Truhe / Truhe / Ender-Truhe | **Loot-Truhen** | Wie BM: Rechtsklick setzt eine Truhe an eigener Position mit schwachem / normalem / starkem Loot-Pool, Linksklick mit einer beliebigen Truhen-Sorte entfernt die Truhe an der Position. Im Dungeon steht immer ein normaler Truhen-Block, nur der Pool unterscheidet sich. Hologramm-Farbe je Rarity. |
| Fass | **Belohnungs-Truhe** (Annahme) | Vierte Rarity „Boss/Belohnung“: bleibt verschlossen, bis ein Event sie freischaltet (z. B. Boss besiegt). |
| Spawn-Ei | **Mob-Spawnpunkt** | Rechtsklick an eigener Position: GUI öffnet sich → Mob-Vorlage wählen (Abschnitt 3), Anzahl, Streuradius, Aktivierung (bei Start / per Event). Linksklick: Punkt an der Position entfernen. Shift-Rechtsklick: Mob-Vorlagen-Editor öffnen. |
| Wither-Skelett-Schädel | **Boss-Spawnpunkt** | Wie Mob-Spawnpunkt, aber mit Boss-Vorlage (Abschnitt 4). Ein Boss wird immer per Event/Auslöser gerufen oder beim Betreten seiner Arena. |
| Eisentür | **Tür** | Region per Holzaxt (wie Kompass). Die Blöcke, die beim Speichern in der Region stehen, sind der **geschlossene** Zustand. Danach GUI: Name der Tür, Startzustand (zu/offen), wie sie aufgeht (Abschnitt 6). |
| Redstone-Fackel | **Auslöser / Event** | Legt einen Auslöser an (Region per Holzaxt **oder** Block, auf den man schaut, z. B. Knopf/Hebel/Druckplatte) und öffnet danach den Event-Editor (Abschnitt 7). Shift-Rechtsklick: Liste aller Events dieses Dungeons (bearbeiten/löschen). |
| Leuchtfeuer | **Ausgang** | Region per Holzaxt. Wer sie nach Erfüllen der Abschluss-Bedingung betritt, verlässt den Dungeon **mit** seinem Loot (Abschnitt 5). Mindestens ein Ausgang Pflicht. |
| Buch / Komparator | **Dungeon-Einstellungen** | GUI: min./max. Spieler, Leben pro Spieler, Loot-Regel (Abschnitt 5), Zeitlimit, Abschluss-Bedingung, Abbauen/Bauen erlaubt, `late-join`, Loot-Datei-Override. |
| Barrier (vorletzter Slot) | **Cancel** | Bestätigung per „cancel“ im Chat. |
| Emerald (letzter Slot) | **Speichern** | Validiert (Grenzen, ≥1 Spawn, ≥1 Ausgang, alle Punkte innerhalb der Grenzen, alle Event-Verweise gültig), erzeugt das Schematic, speichert YAML, beendet den Wizard. |

**Validierung beim Speichern:** alle gesetzten Punkte/Regionen müssen innerhalb der Kompass-Grenzen
liegen (sonst Liste der Fehler im Chat, nichts wird gespeichert). Namenskonflikt wie ArenaWizard2
(„Namen ändern“ oder „Überschreiben“).

**Schematic und platzierte Objekte:** Truhen werden erst in der Instanz gesetzt/gefüllt (wie BM).
Türen werden mit ihrem **Startzustand** ins Schematic übernommen – was der Admin gebaut hat, steht
in der Instanz; eine „offen“ startende Tür wird beim Instanz-Start auf Luft gesetzt.

## 3. Wizard-System: Edit

`/dwz edit <dungeon>` – dieselben Werkzeuge plus (wie ArenaWizard2 Abschnitt 3):
- **Nametag** – Anzeigenamen ändern.
- **Enderperle** – zum ersten Spieler-Spawn teleportieren.

Beim Start von Edit werden alle gespeicherten Punkte wieder als Hologramme angezeigt. Cancel
verwirft nur die Änderungen dieser Session. Edit wird abgelehnt, solange eine Instanz dieses
Dungeons läuft (wie `startEditMap` → `REFUSED`).

**Wichtig:** Edit arbeitet an der **Original-Region in der Bau-Welt** (nicht an einer Instanz).
Relative Positionen werden beim Laden gegen den gespeicherten Ursprung zurückgerechnet.

---

## 4. Mobs (Vorlagen und Spawnpunkte)

**Entschieden (Nutzer):** Man kann Mobs vorbestimmen – was sie tragen und was sie droppen.

### 4a. Mob-Vorlagen

Global wiederverwendbar (ein Skelett-Wächter kann in mehreren Dungeons vorkommen), gespeichert in
`plugins/DungeonWizard/mobs/<id>.yml`. Anlegen/bearbeiten über den **Mob-Vorlagen-Editor** (GUI,
Shift-Rechtsklick mit dem Spawn-Ei oder `/dwz mob`):

| Feld | Inhalt |
|---|---|
| `type` | `EntityType` (nur `LivingEntity`, Spieler/EnderDragon/Wither ausgeschlossen – Wither nur als Boss, Annahme) |
| `name` | Anzeigename (sichtbar ja/nein) |
| `health`, `damage`, `speed`, `armor`, `knockback-resistance` | über Attribute (`Attribute.MAX_HEALTH` usw.) |
| `equipment` | Kopf, Brust, Beine, Füße, Haupthand, Zweithand – **per Drag-and-Drop echter Items** in die Editor-Slots; gespeichert wird das **komplette Item** (nicht nur das Material wie bei GUIWizards Anzeige-Items), z. B. als `ItemStack.serializeAsBytes()` Base64, damit Verzauberungen/Namen/Komponenten erhalten bleiben |
| `equipment-drop` | ob die Ausrüstung selbst droppen kann (Standard: nein, `setDropChance(0)`) |
| `drops` | Drop-Tabelle: Liste aus Item (per Drag-and-Drop, komplett gespeichert) + Menge min/max + Chance in % **oder** Verweis auf eine Loot-Datei (`loot-table: dungeon_normal`, gleiches Format wie ArenaWizard2) |
| `vanilla-drops` / `xp` | Vanilla-Drops aus (Standard), XP-Menge |
| `effects` | Trank-Effekte dauerhaft (z. B. Feuerresistenz) |
| `baby`, `glowing`, `silent` | kleine Schalter (Annahme, nur was billig ist) |

### 4b. Mob-Spawnpunkt (im Dungeon)

`{ template, count, spread-radius, activation: START | EVENT(<eventId>), group }`. Mehrere
Spawnpunkte können dieselbe **Gruppe** haben – „alle Mobs der Gruppe X tot“ ist ein Auslöser
(Abschnitt 7). **Annahme v1:** kein Wellen-/Nachspawn-System; jeder Spawnpunkt feuert einmal pro
Aktivierung. Wellen später über Events („Gruppe tot“ → nächste Gruppe spawnen).

Laufzeit: Mobs bekommen einen PDC-Tag (Instanz-Id, Gruppe, Vorlage), `setRemoveWhenFarAway(false)`,
`setPersistent(true)` innerhalb der Instanz; Drops/XP ausschließlich aus der Vorlage
(`EntityDeathEvent` → Drops ersetzen). Gedroppte Items bekommen den Dungeon-Loot-Tag (Abschnitt 5).

---

## 5. Loot-Regeln: mit rein, mit raus, verlieren

**Entschieden (Nutzer):** Loot von außen darf mit hinein, Loot darf mit hinaus, und man soll Loot
verlieren können.

### 5a. Ablauf

- **Betreten:** Das echte Inventar bleibt, wie es ist (kein `PlayerSnapshot`-Tausch). Gesichert
  werden nur Spielmodus, Position (für die Rückkehr) und ggf. Leben/Hunger/Effekte – Annahme:
  Leben/Hunger werden **nicht** zurückgesetzt, man kommt raus, wie man drinnen war.
- **Gefundener Loot** (Truhen, Mob-/Boss-Drops) bekommt einen PDC-Tag `dwz_loot=<instanceId>`.
- **Erfolgreich raus (Ausgang nach Abschluss):** alles bleibt im Inventar, der `dwz_loot`-Tag wird
  entfernt (danach ist es normales Item). Rückteleport zur gespeicherten Position bzw.
  `main-lobby-world`-Spawn (Muster aus ArenaWizard2-Config).
- **Tod im Dungeon:** Der Tod wird wie in ArenaWizard2 **abgefangen** (kein echtes
  `PlayerDeathEvent`, kein DeadChest-Konflikt, keine XP-Strafe). Was verloren geht, bestimmt die
  Loot-Regel (5b); die verlorenen Items fallen an der Todesstelle (`dropItemNaturally`, wie BM) und
  können von Mitspielern oder vom Respawnten selbst wieder aufgehoben werden, solange die Instanz
  existiert. Danach: Respawn (wenn Leben übrig) am letzten freigeschalteten Respawn-Punkt, sonst
  Zuschauer bis zum Ende.
- **Aufgeben (`/dwz leave`) während `RUNNING`:** zählt wie Tod ohne Respawn (Annahme – sonst wäre
  „leave statt sterben“ ein Exploit zum Loot-Retten).
- **Scheitern** (alle tot / Zeitlimit): jeder noch Lebende wird behandelt wie beim Tod ohne Respawn,
  danach alle hinaus.

### 5b. Loot-Regel pro Dungeon (Einstellungs-GUI)

| Regel | Beim Tod/Aufgeben/Scheitern verloren |
|---|---|
| `HARDCORE` | das gesamte Inventar (mitgebracht + gefunden) |
| `FOUND_ONLY` | nur Items mit `dwz_loot`-Tag dieser Instanz; Mitgebrachtes bleibt |
| `KEEP` | nichts (Übungs-/Story-Dungeon) |

Standard: **Annahme `FOUND_ONLY`** – rückfragen. Rüstung und Zweithand zählen zum Inventar.
Cursor-Item und offenes Crafting-Raster ebenfalls behandeln (Befund aus ArenaWizard2-BM).

### 5c. Absicherung (Pflicht, weil echtes Inventar)

- **Verbindungsabbruch** während `RUNNING`: Schonfrist `reconnect-grace-seconds` (Annahme 60 s),
  Wiedereinstieg an der letzten Position; danach wie Aufgeben. Instanz bleibt bestehen, solange ein
  Spieler (auch getrennt in der Schonfrist) eingetragen ist.
- **Server-Absturz / Neustart:** Instanz-Welten sind danach weg (Sweep). Spieler, die in einer
  `dwz_instance_`-Welt einloggen, werden zur Rückkehr-Position teleportiert. Ihr Inventar ist das,
  was der Server zuletzt gespeichert hat – **Annahme:** wird so akzeptiert (keine Strafe, keine
  Rückgabe); in einer Datei `pending-returns.yml` wird nur die Rückkehr-Position gehalten.
- **Duplikation verhindern:** Items, die beim Tod droppen, sind aus dem Inventar entfernt, **bevor**
  sie gespawnt werden; Instanz-Abbau entfernt liegen gebliebene Items zusammen mit der Welt.
- **Stash-Exploits sperren:** Endertruhe öffnen, Shulker-Kisten ablegen und Item-Rahmen/Rüstungs-
  ständer benutzen im Dungeon aus; Befehle während `RUNNING` nur per Whitelist (`/dwz`, `/msg`,
  `/party`/`/pwz`, konfigurierbar) – sonst `/home`, `/spawn`, `/tpa` als Loot-Rettung.
- **Mitbringen einschränken:** optionale Material-Blacklist pro Dungeon/global (z. B. Enderperlen,
  Elytren, Totems) – beim Beitritt prüfen und mit klarer Meldung ablehnen, nicht still entfernen.
- **Spielmodus:** `ADVENTURE` während des Runs (kein Abbauen durch Wände), außer die Einstellung
  „Abbauen/Bauen erlaubt“ ist an; Gamemode-/Flug-Wechsel durch den Spieler selbst gesperrt.

### 5d. Loot-Dateien

**Entschieden:** Format und Loader wie ArenaWizard2 (`material`, `min-amount`, `max-amount`,
`chance` als Gewicht, optional `enchantments`, `potion`), eigene Dateien in
`plugins/DungeonWizard/loot/`: `dungeon_weak.yml`, `dungeon_normal.yml`, `dungeon_strong.yml`,
`dungeon_reward.yml`; Per-Dungeon-Override möglich. **Erweiterung:** zusätzlich zu `material` darf
ein Eintrag ein vollständig serialisiertes Item enthalten (`item: <base64>`), damit Admins eigene
Items (z. B. per GUI hineingelegt) als Loot nutzen können – Annahme, v1 oder später. Truhen werden
**einmal** beim Start gefüllt, **kein** Nachfüllen (anders als BM).

---

## 6. Türen

Region + gespeicherter Geschlossen-Zustand (Blockdaten relativ zum Ursprung, aus dem Schematic).
- **Öffnen** = Blöcke der Region auf Luft setzen (optional schichtweise von unten nach oben über
  einige Ticks als Animation, Sound). **Schließen** = gespeicherte Blöcke zurücksetzen; Spieler in
  der Region werden vorher herausgeschoben (nicht einmauern).
- Eine Tür wird **nur** über Aktionen des Event-Systems geöffnet/geschlossen (Abschnitt 7), damit
  es genau einen Mechanismus gibt. Die typischen Fälle stellt das Tür-GUI als Abkürzung bereit und
  legt intern ein Event an:
  - Schlüssel: Rechtsklick mit bestimmtem Item auf die Tür (Item wird verbraucht, ja/nein),
  - Knopf/Hebel/Druckplatte an einer Position,
  - Mob-Gruppe besiegt, Boss besiegt,
  - Spieler betritt Region.

Schlüssel-Items: im Dungeon aus Truhen/Mobs (Loot-Datei) oder per Aktion „Item geben“. **Annahme:**
Schlüssel tragen einen Tag `dwz_key=<dungeonId>:<keyId>` und verlieren beim Verlassen des Dungeons
ihre Wirkung (werden beim Rausgehen entfernt).

---

## 7. Events (Auslöser → Aktionen)

**Entschieden (Nutzer):** Werkzeuge, um Events zu erstellen. Umsetzung als ein einfaches, lineares
**Auslöser-Bedingung-Aktionen-System** (kein Skript, keine Logik-Programmierung in v1).

Ein Event = `{ id, name, trigger, conditions[], actions[], once: true|false, delay-ticks }`,
gespeichert in der Dungeon-YAML, konfiguriert über den Event-Editor (GUI) im Wizard.

**Auslöser (v1):**

| Auslöser | Parameter |
|---|---|
| `DUNGEON_START` | – |
| `ENTER_REGION` | Region; `first` / `all-players` |
| `INTERACT_BLOCK` | Block-Position (Knopf, Hebel, Druckplatte, beliebiger Block) |
| `USE_KEY` | Block/Region + Schlüssel-Id |
| `GROUP_KILLED` | Mob-Gruppe |
| `BOSS_KILLED` | Boss-Spawnpunkt |
| `BOSS_HEALTH` | Boss + Schwelle in % (für Phasen, Abschnitt 8) |
| `EVENT_FIRED` | anderes Event (Ketten) |
| `TIMER` | Sekunden nach Start oder nach anderem Event |

**Bedingungen (v1, optional):** `event X schon ausgelöst`, `Gruppe X tot`, `Spieleranzahl ≥ n`.

**Aktionen (v1):**

| Aktion | Parameter |
|---|---|
| `SPAWN_GROUP` / `SPAWN_BOSS` | Gruppe / Boss |
| `OPEN_DOOR` / `CLOSE_DOOR` | Tür |
| `UNLOCK_CHEST` | Belohnungs-Truhe |
| `ACTIVATE_RESPAWN` | Respawn-Punkt |
| `MESSAGE` / `TITLE` / `SOUND` | Text / Titel+Untertitel / Sound, an alle im Dungeon |
| `GIVE_ITEM` | Item (serialisiert) an auslösenden Spieler oder alle |
| `TELEPORT` | Zielpunkt, auslösender Spieler oder alle |
| `COMPLETE_DUNGEON` | erfüllt die Abschluss-Bedingung (Ausgang wird aktiv) |
| `FAIL_DUNGEON` | Dungeon scheitert |

**Abschluss-Bedingung:** Standard „alle Bosse besiegt“, alternativ nur über `COMPLETE_DUNGEON`
(Einstellungs-GUI). Erst danach funktioniert der Ausgang (vorher Meldung „Ausgang noch
verschlossen“). **Annahme:** Ausgang ist ohne Boss (Dungeon ohne Boss) sofort aktiv.

Hinweis für die Umsetzung: Event-Editor-GUI ist der aufwendigste UI-Teil. Innerhalb von
DungeonWizard bauen (nicht GUIWizard – das ist die Spieler-/Admin-Menü-Ebene über den Befehlen,
der Editor gehört zum Wizard wie ArenaWizard2s Lobby-Auswahl-Menü). Einträge anderer Objekte
(Türen, Gruppen, Bosse) werden per Klick aus Listen gewählt, nicht per Name getippt.

---

## 8. Bosse

**Entschieden (Nutzer):** Man soll Bosse erstellen können. Boss = Mob-Vorlage (Abschnitt 4a) plus:

| Feld | Inhalt |
|---|---|
| `bossbar` | Titel, Farbe, Stil – sichtbar für alle Spieler der Instanz |
| `arena` | optionale Region; Spieler, die sie betreten, schließen ggf. die Tür hinter sich (per Event) |
| `phases` | Liste von HP-Schwellen (z. B. 75/50/25 %), je Phase: Aktionen (wie Abschnitt 7, z. B. Adds spawnen) + Attribut-Änderungen (Tempo, Schaden) |
| `abilities` (v1, kleine feste Auswahl, Annahme) | `SUMMON` (Gruppe rufen), `KNOCKBACK_WAVE` (Rückstoß im Radius), `EFFECT_AREA` (Trank-Effekt auf Spieler im Radius), `TELEPORT_TO_TARGET`, `PROJECTILE_BARRAGE` – jeweils mit Abkling-Zeit |
| `drops` | wie Mob, zusätzlich optional „jeder Spieler bekommt eigenen Drop“ (Annahme) |
| `scaling` | HP-Multiplikator pro zusätzlichem Spieler (Annahme, einfach linear) |

Gespeichert als `plugins/DungeonWizard/bosses/<id>.yml`, gleicher Editor wie Mobs mit
zusätzlichem Boss-Reiter. Boss-Tod feuert `BOSS_KILLED` und das Addon-Event
`DungeonBossDefeatedEvent`.

---

## 9. Rundenablauf, Befehle, Permissions, Config

### 9a. Zustände

```
WAITING   -> Instanz steht, Spieler am Spawn, nichts aktiv, Beitritt moeglich
STARTING  -> Countdown, Spieler eingefroren
RUNNING   -> Mobs/Events aktiv, Zeit laeuft
ENDING    -> abgeschlossen oder gescheitert: Ergebnis, danach alle hinaus, Instanz weg
```

Spielerzustand: `WAITING, ALIVE, DEAD_RESPAWNING, SPECTATING, EXTRACTED, DISCONNECTED`.
Wer den Ausgang nimmt (`EXTRACTED`), ist sofort draußen; der Run läuft für die anderen weiter
(Annahme). Der Run endet, wenn niemand mehr `ALIVE`/`DEAD_RESPAWNING`/`DISCONNECTED` ist.

### 9b. Befehle

`/dwz join new|join|leave|list|info|owner|create|edit|remove|reload|cancel|forcestart|forcestop|mob|boss`

### 9c. Permissions (alle `default: op`, außer Spielen)

`dwz.wizard` (create/edit, Mob-/Boss-Editor), `dwz.remove`, `dwz.reload`, `dwz.forcestart`,
`dwz.forcestop`, `dwz.bypass.blacklist` (Annahme). Beitreten/Verlassen ohne Permission (wie
ArenaWizard2).

### 9d. `config.yml` (Muster: alle Zeiten einstellbar)

```yaml
min-players-default: 1
countdown-seconds: 5
waiting-radius: 8
reconnect-grace-seconds: 60
end-seconds: 10
main-lobby-world: world
default-loot-rule: FOUND_ONLY
command-whitelist: [dwz, msg, tell, r, pwz, party]
items-per-chest-min: 2
items-per-chest-max: 5
blacklist-materials: []
```

### 9e. Datenhaltung

```
plugins/DungeonWizard/
  config.yml
  dungeons/<id>.yml          Grenzen, Spawns, Truhen, Spawnpunkte, Tueren, Events, Ausgaenge, Einstellungen
  schematics/<id>.schem
  mobs/<id>.yml              Mob-Vorlagen (global)
  bosses/<id>.yml            Boss-Vorlagen (global)
  loot/dungeon_*.yml
  instances.yml              WizardCore-Tracker
  pending-returns.yml        Rueckkehr-Positionen nach Absturz
```

### 9f. Paketvorschlag

```
DungeonWizardPlugin
config/      DungeonConfig, DungeonManager, Settings, LootRule (RelativeLocation, BlockRegion aus WizardCore)
run/         RunManager, DungeonRun, RunState, RunPlayer, PlayerRunState, JoinService (EINE Wahrheit fuer Befehl + API)
loot/        LootManager, LootTable, LootEntry, LootTagger, InventoryRules
mob/         MobTemplate, MobTemplateManager, MobSpawnPoint, MobSpawner, MobListener
boss/        BossTemplate, BossManager, BossController, BossAbility, BossPhase
door/        Door, DoorController
event/       DungeonEvent, Trigger, Condition, Action, EventEngine
wizard/      WizardManager, WizardSession, WizardItems, WizardListener, WizardHologram, gui/ (Editoren)
listener/    RunListener (Tod abfangen, Stash-Sperren, Befehls-Whitelist, Reconnect)
command/     DungeonWizardCommand
api/         + api/event (Abschnitt 10)
```

---

## 10. Kompatibilität mit GUIWizard und PartyWizard

**Entschieden (Nutzer):** kompatibel mit GUIWizard und PartyWizard.

### 10a. Was DungeonWizard liefert (in diesem Projekt)

Eigene Addon-API in `de.deinserver.dungeonwizard.api` / `api.event`, `ADDON_API.md` im Repo, nach
dem Muster von `ArenaWizard2/ADDON_API.md` – **bewusst dieselbe Methodenform**, damit die
vorhandenen Backend-Schnittstellen der Addons (`GameBackend` in GUIWizard, `MatchBackend` in
PartyWizard) nur eine weitere Implementierung brauchen:

| Baustein | Inhalt |
|---|---|
| `DungeonWizardApi` | `runs()`, `dungeons()`, `wizards()` und **`apiVersion()` als Methode** (Lehre aus ArenaWizard2: die Konstante wird in den Addon-Bytecode einkompiliert, siehe `SUITE_UEBERSICHT.md` 3.5) |
| `RunService` | `getRuns()`, `getJoinableRuns()`, `getRun(instanceId)`, `getRunOf(player)`, `join`, `createAndJoin(player, dungeonId, confirmDuplicate)`, `joinGroup(players, instanceId)` (alle oder keiner), `createAndJoinGroup(...)` (erster = Owner), `leave`, `allowJoin`/`revokeJoin`, optional `spectate` |
| `RunInfo` (Record) | `instanceId`, `dungeonId`, `dungeonDisplayName`, `state`, `open`, `joinable`, `playerCount`, `maxPlayers`, `ownerId`, `lootRule`, `aliveCount` |
| `DungeonInfo` | `id`, `displayName`, `minPlayers`, `maxPlayers`, `activeInstances`, `lootRule` |
| `JoinResult` | gleiche Namen wie ArenaWizard2 (`OK`, `UNKNOWN_MATCH`, `UNKNOWN_MAP`, `ALREADY_IN_MATCH`, `ROUND_RUNNING`, `CLOSED`, `FULL`, `DUPLICATE_INSTANCE_CONFIRM_NEEDED`, `INVALID_REQUEST`, `JOIN_FAILED`) + `BLACKLISTED_ITEM`, `IN_WIZARD` – Addons haben ohnehin einen `default`-Zweig |
| `WizardService` | `startCreate(player, name)`, `startEdit(player, dungeonId)`, `isInWizard(player)` – prüft `dwz.wizard` |
| Events | `RunCreatedEvent`, `RunDisbandedEvent`, `RunStateChangeEvent`, `RunOpenChangeEvent`, `RunJoinEvent`, `RunLeaveEvent`, `RunStartEvent`, `RunEndEvent` (erfolgreich/gescheitert, Spieler), `PlayerExtractEvent`, `DungeonBossDefeatedEvent` – alle mit `RunInfo`-Schnappschuss, gefeuert **nach** der Zustandsänderung |

`RunService` ruft denselben `JoinService` wie der Befehl (eine Wahrheit für Regeln). Die API prüft
auch den Wizard-Zustand (Befund aus `SUITE_UEBERSICHT.md` 3.5, dort bei ArenaWizard2 offen).
Instanz-Erzeugung meldet sich vorher selbst beim Spieler (wie ArenaWizard2).

**Zeitpunkt:** Die API kommt **früh (Phase 2)**, direkt nach dem Kern-Ablauf, nicht wie bei
GlideWizard als letzte Phase – dann können GUIWizard/PartyWizard parallel zu Mobs/Bossen andocken.

### 10b. Was in GUIWizard und PartyWizard nötig ist (NICHT in diesem Projekt)

Befund beim Lesen der Addons (2026-09-26): **beide können derzeit nur EIN Spiel-Backend
gleichzeitig** (`GUIWizardPlugin.backend()`; PartyWizards `BackendLoader` nimmt „das erste
benutzbare Spiel“). Mit ArenaWizard2 + GlideWizard + DungeonWizard reicht das nicht. Das ist
dieselbe Arbeit wie Roadmap-Zeile 16 (GlideWizard-Anbindung) und sollte **einmal für beide**
erledigt werden. Einträge dafür in `../SUITE_UEBERSICHT.md` (Abschnitt 3/Roadmap), umgesetzt in
eigenen Sessions der Addons:

- **GUIWizard:** mehrere `GameBackend`s gleichzeitig + Spiel-Auswahl-Ebene im Spieler- und
  Admin-Menü; `backend/dungeonwizard/` mit `DungeonWizardBackend` (ein einziger „Modus“ `dungeon`
  ohne Untertypen, Maps = Dungeons, Matches = Runs); Admin-Menü: Create/Edit über
  `WizardService`, **kein** „Lobby erstellen“ für dieses Backend (Fähigkeit im `WizardSupport`
  abfragbar machen); `softdepend: DungeonWizard`; Anzeige-Items je Backend-`id()` getrennt.
- **PartyWizard:** mehrere `MatchBackend`s gleichzeitig (Match eines Spielers kann in jedem Spiel
  sein; Nachziehen `follow-leader` für jedes Backend); `backend/dungeonwizard/` mit
  `DungeonWizardBackend`; `softdepend: DungeonWizard`. Keine Teams → „gleiches Team“ entfällt.
- **Party-Größe vs. `maxPlayers`** des Dungeons: Prüfung macht `joinGroup` (alle oder keiner).

### 10c. Suite-übergreifend (Vorschlag, Entscheidung beim Nutzer)

- **„Spieler ist beschäftigt“:** Heute weiß kein Spiel-Plugin, ob ein Spieler gerade in einem
  anderen steckt (Arena-Match, Rennen, Dungeon). Mit echtem Inventar im Dungeon ist das riskant
  (Dungeon-Loot in eine Arena-Runde mitnehmen, dort `PlayerSnapshot` …). Vorschlag: winziger
  neutraler Helfer in WizardCore (`PlayerActivity.mark/clear/isBusy(player)`). Eine statische Map
  in WizardCore reicht dafür **nicht**, weil jedes Spiel-Plugin WizardCore in seine eigene JAR
  einshaded (jedes hat seine eigene Kopie der Klasse) – der Zustand muss deshalb am Spieler selbst
  hängen, z. B. als Bukkit-Metadata mit festem Schlüssel `wizardsuite_busy` und dem Plugin-Namen
  als Wert. Jedes Spiel-Plugin prüft das beim Beitritt. Eigene WizardCore-Session + kleine
  Anpassung in ArenaWizard2/GlideWizard.
- **Item-Wizard-Mechanik nach WizardCore?** Mit DungeonWizard gibt es drei Verwender derselben
  Session-/Werkzeug-/Hologramm-/Kompass-Holzaxt-Mechanik. `SUITE_UEBERSICHT.md` 3a sagt: neu
  entscheiden, wenn der echte Überlapp sichtbar ist. **Empfehlung für DungeonWizard v1:** noch
  eigene Kopie (wie GlideWizard), Extraktion als eigene spätere WizardCore-Phase.

---

## 11. Offene Punkte / Annahmen (vor der jeweiligen Phase mit dem Nutzer klären)

1. Befehlsname `/dwz`?
2. Warte-Phase am Spawn + Bereit-System/`forcestart`; Nachträglicher Beitritt (`late-join`)?
3. Standard-Loot-Regel (`HARDCORE` / `FOUND_ONLY` / `KEEP`), Aufgeben = Verlust?
4. Leben pro Spieler / Respawn (Standard 1 Leben = kein Respawn? oder 3?), Respawn-Punkte nötig?
5. Verhalten nach Server-Absturz (Inventar so lassen?).
6. Material-Blacklist fürs Mitbringen – welche Items?
7. Ausgang: Wer rausgeht, ist draußen, Run läuft weiter – ok? Abschluss-Bedingung Standard „alle Bosse tot“?
8. Belohnungs-Truhe als vierte Truhenart?
9. Boss-Fähigkeiten: reicht die kleine feste Auswahl für v1?
10. Skalierung nach Spielerzahl (Mob-/Boss-HP) schon in v1?
11. Wetten/Economy oder Eintrittsgebühr? Statistik (Abschlüsse, Bestzeit, Tode – eigenes SQLite)?
12. Abbauen/Bauen im Dungeon grundsätzlich aus (Adventure)?
13. Suite: Mehr-Backend-Umbau in GUIWizard/PartyWizard und „busy“-Register in WizardCore freigeben?

---

### 11a. Antworten des Nutzers (2026-09-26, vor Phase 0/1)

Diese Antworten gehen den Annahmen in den Abschnitten oben vor.

- **Punkt 1 – Befehl:** `/dwz` bleibt (keine Einwände).
- **Punkt 2 – Warte-Phase: entfällt.** Kein Bereit-Item, kein Bereit-System, kein Countdown vor
  dem Start. Man tritt bei und läuft los. Nachträglicher Beitritt ist **möglich** (`late-join`
  Standard **an**, pro Dungeon abschaltbar). Beispiel des Nutzers: Der Dungeon ist eine Höhle,
  der Spawn liegt davor, und wenn alle da sind, gehen die Spieler gemeinsam hinein. Folgen daraus:
  - Die Instanz ist ab der Erzeugung `RUNNING`, und `DUNGEON_START` feuert bei der Erzeugung.
    `WAITING`/`STARTING` (9a) und das Rückteleportieren über `waiting-radius` (1) entfallen,
    ebenso `forcestart` und `countdown-seconds`.
  - Der Ablauf im Dungeon wird über **Trigger-Punkte** gesteuert: Ein Bereich wird erreicht und
  löst ein anderes Event aus (z. B. Höhleneingang betreten → Mobs spawnen / Tür öffnen). Dafür
  gibt es im Create- **und** Edit-Wizard ein **eigenes Werkzeug mit GUI** (Bereich per Holzaxt,
  danach GUI: Name, `first`/`all-players`, einmalig ja/nein, welches Event bzw. welche Aktionen).
  Technisch ist das der Auslöser `ENTER_REGION` aus Abschnitt 7 mit einem eigenen Werkzeug.
- **Punkte 3+4 – Loot-Regel und Leben:** **einstellbar**, und zwar beim Erstellen der Instanz
  (`/dwz join new`) durch den Owner. Umsetzungsvorschlag (bei Phase 1 so bauen, falls der Nutzer
  nichts anderes sagt): Der Dungeon legt in den Einstellungen Standardwerte fest (Loot-Regel
  Standard `FOUND_ONLY`, Leben Standard 3) und kann die Änderung durch den Owner sperren.
  Auswahl über `/dwz join new <dungeon> [lootrule] [leben]`, später auch per GUIWizard/API.
  Die gewählten Werte sind im `RunInfo` sichtbar, damit Mitspieler vor dem Beitritt wissen,
  worauf sie sich einlassen. Aufgeben (`/dwz leave`) im laufenden Run zählt wie Tod ohne Respawn.
- **Punkte 5, 6, 12 – Absicherung:** Nach einem Absturz bleibt das Inventar so, wie es zuletzt
  gespeichert war (nur Rückteleport). Standard-Blacklist fürs Mitbringen: **Enderperle,
  Chorusfrucht, Elytra** (Totems erlaubt, Liste in `config.yml` anpassbar). Abbauen/Bauen
  standardmäßig aus (`ADVENTURE`), pro Dungeon einschaltbar.
- **Noch offen** (vor der jeweiligen Phase fragen): 7, 8, 9, 10, 11, 13.
- **Blocker Phase 0:** WizardCore und GlideWizard waren auf GitHub leer, ArenaWizard2 veraltet.
  Der Nutzer pusht die aktuellen Stände, erst danach wird gebaut.

## 12. Phasenplan (Phase N = Version `0.0.N`, Git-Tag `v0.0.N`)

| Phase | Inhalt |
|---|---|
| 0 | Gerüst: `pom.xml` (WizardCore, paper-api, worldedit provided, shade), `plugin.yml`, Hauptklasse mit InstanceManager (`dwz_instance_`) + `sweepOrphans()`, `/dwz` Platzhalter |
| 1 | Kern: Wizard Create/Edit mit Kompass, Spawn, Truhen (3 Rarities + Loot-Dateien), Ausgang, Einstellungen, Cancel/Save; Instanzierung; `join new`/`join`/`leave`/`list`/`info`/`owner`; Owner + offen/geschlossen; Start direkt bei Erzeugung (keine Warte-Phase, late-join, 11a), Loot-Regel/Leben bei `join new` waehlbar, Ende; **Loot-Regeln komplett inkl. Absicherung (Abschnitt 5)** |
| 2 | Addon-API + `ADDON_API.md` (Abschnitt 10a). Danach können GUIWizard/PartyWizard in eigenen Sessions andocken (10b) |
| 3 | Mob-Vorlagen + Editor, Mob-Spawnpunkte, Gruppen, Drops mit Loot-Tag |
| 4 | Türen + Event-System (Auslöser/Bedingungen/Aktionen, Editor), Trigger-Punkt-Werkzeug mit GUI (11a), Schlüssel, Respawn-Punkte, Belohnungs-Truhe |
| 5 | Bosse: Bossbar, Phasen, Fähigkeiten, Skalierung |
| 6 | optional: Scoreboard/HUD, Statistik (SQLite), Economy – je nach Antwort auf 11.11 |

Jede Phase: sauber kompilieren (`mvn clean package`), Abschnitt „Zum Testen“ in `DOKUMENTATION.md`
schreiben (Muster ArenaWizard2), **nie „getestet“ behaupten** – getestet wird vom Nutzer auf seinem
Paper-Server. Commit/Push nur auf ausdrücklichen Wunsch.
