package de.deinserver.dungeonwizard.command;

import de.deinserver.dungeonwizard.DungeonWizardPlugin;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

/**
 * /dwz - Platzhalter aus Phase 0. Zeigt nur Version und WorldEdit-Status;
 * die eigentlichen Unterbefehle folgen ab Phase 1.
 */
public class DungeonWizardCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of("info");

    private final DungeonWizardPlugin plugin;

    public DungeonWizardCommand(DungeonWizardPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage(Component.text("DungeonWizard " + plugin.getPluginMeta().getVersion(), NamedTextColor.GOLD));
        boolean worldEdit = plugin.getWorldEditRegionManager() != null;
        sender.sendMessage(Component.text("WorldEdit/FAWE: " + (worldEdit ? "gefunden" : "fehlt"),
                worldEdit ? NamedTextColor.GREEN : NamedTextColor.RED));
        sender.sendMessage(Component.text("Noch im Aufbau - Befehle folgen ab Phase 1.", NamedTextColor.GRAY));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(prefix)).toList();
        }
        return List.of();
    }
}
