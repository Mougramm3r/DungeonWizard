package de.deinserver.dungeonwizard;

import de.deinserver.dungeonwizard.command.DungeonWizardCommand;
import de.deinserver.wizardcore.instancing.InstanceManager;
import de.deinserver.wizardcore.worldedit.WorldEditRegionManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Hauptklasse von DungeonWizard (Spiel-Plugin der WizardSuite): Dungeons per
 * Item-Wizard bauen und als eigene Instanzen spielen - mit echtem Inventar
 * (Loot mit rein, Loot mit raus, Loot verlieren). Kein Lobby-System, die
 * Spieler starten direkt am Dungeon-Spawn.
 *
 * Phase 0 (v0.0.0): Geruest - WizardCore-Verdrahtung (eigener InstanceManager
 * mit Praefix "dwz_instance_", Aufraeumen verwaister Instanz-Welten) und ein
 * Platzhalter-Befehl /dwz. Alles Weitere folgt ab Phase 1 (siehe DOKUMENTATION.md).
 */
public class DungeonWizardPlugin extends JavaPlugin {

    /** Welt-Namens-Praefix aller Dungeon-Instanzen - nie mit einem anderen Spiel-Plugin teilen. */
    public static final String INSTANCE_WORLD_PREFIX = "dwz_instance_";

    private WorldEditRegionManager worldEditRegionManager;
    private InstanceManager instanceManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // WorldEditRegionManager referenziert WorldEdit-Klassen direkt und darf
        // NUR instanziiert werden, wenn WorldEdit/FAWE installiert ist - sonst
        // NoClassDefFoundError (siehe plugin.yml softdepend).
        if (WorldEditRegionManager.isAvailable()) {
            worldEditRegionManager = new WorldEditRegionManager();
        } else {
            getLogger().warning("WorldEdit/FAWE nicht gefunden - ohne eines der beiden koennen weder Dungeons gespeichert noch Instanzen erzeugt werden.");
        }

        // Muss VOR jeder moeglichen Instanz-Erzeugung laufen: entfernt Instanz-
        // Welten, die ein Absturz/Neustart hinterlassen hat.
        instanceManager = new InstanceManager(this, INSTANCE_WORLD_PREFIX, worldEditRegionManager);
        instanceManager.sweepOrphans();

        PluginCommand dwz = getCommand("dwz");
        if (dwz != null) {
            DungeonWizardCommand command = new DungeonWizardCommand(this);
            dwz.setExecutor(command);
            dwz.setTabCompleter(command);
        }
        getLogger().info("DungeonWizard " + getPluginMeta().getVersion() + " aktiviert.");
    }

    @Override
    public void onDisable() {
        getLogger().info("DungeonWizard wurde deaktiviert.");
    }

    public InstanceManager getInstanceManager() {
        return instanceManager;
    }

    public WorldEditRegionManager getWorldEditRegionManager() {
        return worldEditRegionManager;
    }
}
