package com.flowcity.core.id;

import com.flowcity.core.audit.AdminLog;
import com.flowcity.core.player.IdChangeException;
import com.flowcity.core.player.PlayerRecord;
import com.flowcity.core.player.PlayerService;
import com.flowcity.core.protocol.ClientConnectionService;
import com.flowcity.core.rank.RankService;
import com.flowcity.core.text.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

/** /id, /listid, /viewid, /setid, /resetid, /idreload. */
public final class IdCommands implements TabExecutor {

    public static final String PERM_OWN = "flowcity.id";
    public static final String PERM_LOOKUP = "flowcity.id.lookup";
    public static final String PERM_LIST = "flowcity.id.list";
    public static final String PERM_VIEW = ProfileService.PERM_VIEW;
    public static final String PERM_ADMIN = ProfileService.PERM_ADMIN;

    private final Plugin plugin;
    private final PlayerService players;
    private final RankService ranks;
    private final ProfileService profiles;
    private final NearbyIdService nearby;
    private final ClientConnectionService connections;
    private final AdminLog adminLog;
    private final Messages messages;
    private final Supplier<IdSettings> settings;
    private final Runnable reloadAction;

    public IdCommands(Plugin plugin, PlayerService players, RankService ranks, ProfileService profiles,
                      NearbyIdService nearby, ClientConnectionService connections, AdminLog adminLog,
                      Messages messages, Supplier<IdSettings> settings, Runnable reloadAction) {
        this.plugin = plugin;
        this.players = players;
        this.ranks = ranks;
        this.profiles = profiles;
        this.nearby = nearby;
        this.connections = connections;
        this.adminLog = adminLog;
        this.messages = messages;
        this.settings = settings;
        this.reloadAction = reloadAction;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "id":
                return id(sender, args);
            case "listid":
                return listId(sender);
            case "viewid":
                return viewId(sender, args);
            case "setid":
                return setId(sender, args);
            case "resetid":
                return resetId(sender, args);
            case "idreload":
                return idReload(sender);
            default:
                return false;
        }
    }

    // ------------------------------------------------------------------ /id

    private boolean id(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                messages.send(sender, "general.players-only");
                return true;
            }
            if (!sender.hasPermission(PERM_OWN)) {
                messages.send(sender, "general.no-permission");
                return true;
            }
            PlayerService.Session s = players.session((Player) sender);
            if (s != null) {
                messages.send(sender, "id.own", "id", settings.get().format(s.rpId()));
            }
            return true;
        }
        if (!sender.hasPermission(PERM_LOOKUP)) {
            messages.send(sender, "general.no-permission");
            return true;
        }
        String name = args[0];
        Player online = plugin.getServer().getPlayerExact(name);
        if (online != null && players.session(online) != null) {
            messages.send(sender, "id.other", "target", online.getName(),
                    "id", settings.get().format(players.session(online).rpId()));
            return true;
        }
        async(sender, players.findByName(name), rec -> {
            if (rec == null) {
                messages.send(sender, "general.player-not-found", "target", name);
            } else {
                messages.send(sender, "id.other", "target", rec.lastName, "id", settings.get().format(rec.rpId));
            }
        });
        return true;
    }

    // ------------------------------------------------------------------ /listid

    private boolean listId(CommandSender sender) {
        if (!(sender instanceof Player)) {
            messages.send(sender, "general.players-only");
            return true;
        }
        if (!sender.hasPermission(PERM_LIST)) {
            messages.send(sender, "general.no-permission");
            return true;
        }
        new ListIdMenu(plugin, players, ranks, profiles, messages, settings.get(),
                sender.hasPermission(PERM_ADMIN), 0).open((Player) sender);
        return true;
    }

    // ------------------------------------------------------------------ /viewid

    private boolean viewId(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERM_VIEW)) {
            messages.send(sender, "general.no-permission");
            return true;
        }
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                messages.send(sender, "id.usage-view");
                return true;
            }
            Player p = (Player) sender;
            if (!connections.isConfirmed(p)) {
                messages.send(p, "id.visibility-needs-mod");
                return true;
            }
            messages.send(p, nearby.toggleAlwaysShow(p) ? "id.visibility-on" : "id.visibility-off");
            return true;
        }
        Integer id = parseId(sender, args[0]);
        if (id != null) {
            profiles.show(sender, id);
        }
        return true;
    }

    // ------------------------------------------------------------------ /setid

    private boolean setId(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERM_ADMIN)) {
            messages.send(sender, "general.no-permission");
            return true;
        }
        if (args.length < 2) {
            messages.send(sender, "id.usage-set");
            return true;
        }
        Integer newId = parseId(sender, args[1]);
        if (newId == null) {
            return true;
        }
        String reason = joinFrom(args, 2);
        UUID admin = sender instanceof Player ? ((Player) sender).getUniqueId() : null;
        resolve(sender, args[0], target -> async(sender, players.changeId(target.uuid, newId, admin, reason), newId, old -> {
            messages.send(sender, "id.set-success", "target", target.lastName,
                    "old", settings.get().format(old), "new", settings.get().format(newId));
            adminLog.record(admin, target.uuid, "SET_RP_ID", reason, String.valueOf(old), String.valueOf(newId));
            Player p = plugin.getServer().getPlayer(target.uuid);
            if (p != null) {
                messages.send(p, "id.set-notify", "new", settings.get().format(newId));
            }
        }));
        return true;
    }

    // ------------------------------------------------------------------ /resetid

    private boolean resetId(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERM_ADMIN)) {
            messages.send(sender, "general.no-permission");
            return true;
        }
        if (!settings.get().allowReset) {
            messages.send(sender, "id.reset-disabled");
            return true;
        }
        if (args.length < 1) {
            messages.send(sender, "id.usage-reset");
            return true;
        }
        String reason = joinFrom(args, 1);
        UUID admin = sender instanceof Player ? ((Player) sender).getUniqueId() : null;
        resolve(sender, args[0], target -> async(sender, players.resetId(target.uuid, admin, reason), ids -> {
            messages.send(sender, "id.reset-success", "target", target.lastName,
                    "old", settings.get().format(ids[0]), "new", settings.get().format(ids[1]));
            adminLog.record(admin, target.uuid, "RESET_RP_ID", reason, String.valueOf(ids[0]), String.valueOf(ids[1]));
            Player p = plugin.getServer().getPlayer(target.uuid);
            if (p != null) {
                messages.send(p, "id.set-notify", "new", settings.get().format(ids[1]));
            }
        }));
        return true;
    }

    // ------------------------------------------------------------------ /idreload

    private boolean idReload(CommandSender sender) {
        if (!sender.hasPermission(PERM_ADMIN)) {
            messages.send(sender, "general.no-permission");
            return true;
        }
        try {
            reloadAction.run();
            messages.send(sender, "general.reloaded");
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Reload failed", e);
            sender.sendMessage(messages.prefix() + Messages.color("&c") + e.getMessage());
        }
        return true;
    }

    // ------------------------------------------------------------------ helpers

    /** Online player by exact name, otherwise the database by last known name. */
    private void resolve(CommandSender sender, String name, Consumer<PlayerRecord> then) {
        Player online = plugin.getServer().getPlayerExact(name);
        if (online != null && players.session(online) != null) {
            PlayerService.Session s = players.session(online);
            then.accept(new PlayerRecord(online.getUniqueId(), s.rpId(), online.getName(), s.firstJoin, 0, 0));
            return;
        }
        async(sender, players.findByName(name), rec -> {
            if (rec == null) {
                messages.send(sender, "general.player-not-found", "target", name);
            } else {
                then.accept(rec);
            }
        });
    }

    /** Runs onSuccess on the main thread; turns IdChangeException / DB errors into messages. */
    private <T> void async(CommandSender sender, CompletableFuture<T> future, Consumer<T> onSuccess) {
        async(sender, future, "?", onSuccess);
    }

    private <T> void async(CommandSender sender, CompletableFuture<T> future, Object attemptedId, Consumer<T> onSuccess) {
        future.whenComplete((value, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (error == null) {
                onSuccess.accept(value);
                return;
            }
            Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
            if (cause instanceof IdChangeException) {
                IdChangeException.Reason r = ((IdChangeException) cause).reason();
                messages.send(sender, r.messageKey, "max", settings.get().rules.maxId, "id", attemptedId);
                return;
            }
            plugin.getLogger().log(Level.SEVERE, "ID command failed", cause);
            messages.send(sender, "general.database-error");
        }));
    }

    private Integer parseId(CommandSender sender, String raw) {
        try {
            int id = Integer.parseInt(raw);
            if (id < 1) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException e) {
            messages.send(sender, "id.not-a-number", "value", raw);
            return null;
        }
    }

    private static String joinFrom(String[] args, int from) {
        return args.length > from ? String.join(" ", Arrays.copyOfRange(args, from, args.length)) : "";
    }

    // ------------------------------------------------------------------ tab completion

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if (args.length != 1) {
            return Collections.emptyList();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        switch (cmd) {
            case "id":
                if (sender.hasPermission(PERM_LOOKUP)) {
                    addPlayerNames(out, prefix);
                }
                break;
            case "setid":
            case "resetid":
                if (sender.hasPermission(PERM_ADMIN)) {
                    addPlayerNames(out, prefix);
                }
                break;
            case "viewid":
                if (sender.hasPermission(PERM_VIEW)) {
                    for (PlayerService.Session s : players.sessions()) {
                        String id = String.valueOf(s.rpId());
                        if (id.startsWith(prefix)) {
                            out.add(id);
                        }
                    }
                    Collections.sort(out);
                }
                break;
            default:
                break;
        }
        return out;
    }

    private void addPlayerNames(List<String> out, String prefix) {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                out.add(p.getName());
            }
        }
    }
}
