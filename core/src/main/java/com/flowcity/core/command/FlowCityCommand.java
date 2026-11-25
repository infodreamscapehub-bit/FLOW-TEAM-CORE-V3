package com.flowcity.core.command;

import com.flowcity.common.protocol.Protocol;
import com.flowcity.core.FlowCityCore;
import com.flowcity.core.protocol.ClientConnectionService;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** /flowcity status - technical status for staff (database, protocol, Forge bridge). */
public final class FlowCityCommand implements TabExecutor {

    private static final String PERMISSION = "flowcity.admin.status";
    private static final String PREFIX = ChatColor.GOLD + "" + ChatColor.BOLD + "FLOWCITY " + ChatColor.DARK_GRAY + "| " + ChatColor.RESET;

    private final FlowCityCore plugin;

    public FlowCityCommand(FlowCityCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "You do not have permission to do that.");
            return true;
        }
        if (args.length == 0 || !"status".equalsIgnoreCase(args[0])) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + "Usage: /" + label + " status");
            return true;
        }
        line(sender, "Version", plugin.getDescription().getVersion() + " (protocol " + Protocol.VERSION + ")");
        line(sender, "Database", plugin.database().dialect().id() + ", schema v" + plugin.database().schemaVersion());
        line(sender, "Pool", plugin.database().poolStats());
        line(sender, "Forge bridge", plugin.bridgeProbe().summary());
        line(sender, "Ranks", plugin.ranks().describe());

        List<Player> players = new ArrayList<>(plugin.getServer().getOnlinePlayers());
        line(sender, "Clients", players.isEmpty() ? "no players online" : players.size() + " online");
        for (Player p : players) {
            ClientConnectionService.Session s = plugin.connections().session(p.getUniqueId());
            String state = s == null ? "handshake not sent yet"
                    : s.state() + (s.modVersion().isEmpty() ? "" : " v" + s.modVersion())
                    + (s.detail().isEmpty() || "ok".equals(s.detail()) ? "" : " - " + s.detail());
            sender.sendMessage(ChatColor.DARK_GRAY + "  - " + ChatColor.WHITE + p.getName() + ChatColor.GRAY + ": " + state);
        }
        return true;
    }

    private static void line(CommandSender sender, String key, String value) {
        sender.sendMessage(PREFIX + ChatColor.YELLOW + key + ChatColor.DARK_GRAY + ": " + ChatColor.WHITE + value);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission(PERMISSION)
                && "status".startsWith(args[0].toLowerCase(Locale.ROOT))) {
            return Collections.singletonList("status");
        }
        return Collections.emptyList();
    }
}
