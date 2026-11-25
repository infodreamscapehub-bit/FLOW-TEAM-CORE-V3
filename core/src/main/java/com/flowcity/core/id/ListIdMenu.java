package com.flowcity.core.id;

import com.flowcity.core.gui.Menu;
import com.flowcity.core.player.PlayerService;
import com.flowcity.core.rank.RankService;
import com.flowcity.core.text.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** /listid: online players sorted by RP ID, 45 per page. Click a head to open that profile. */
public final class ListIdMenu extends Menu {

    private static final int PER_PAGE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_NEXT = 53;

    private final Plugin plugin;
    private final PlayerService players;
    private final RankService ranks;
    private final ProfileService profiles;
    private final Messages messages;
    private final IdSettings settings;
    private final boolean staff;
    private final int page;
    private final int[] slotToRpId = new int[PER_PAGE];

    public ListIdMenu(Plugin plugin, PlayerService players, RankService ranks, ProfileService profiles,
                      Messages messages, IdSettings settings, boolean staff, int page) {
        this.plugin = plugin;
        this.players = players;
        this.ranks = ranks;
        this.profiles = profiles;
        this.messages = messages;
        this.settings = settings;
        this.staff = staff;
        this.page = Math.max(0, page);
        build();
    }

    private void build() {
        List<Player> online = new ArrayList<>();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (players.session(p) != null) {
                online.add(p);
            }
        }
        online.sort(Comparator.comparingInt(p -> players.session(p).rpId()));
        int pages = Math.max(1, (online.size() + PER_PAGE - 1) / PER_PAGE);
        int current = Math.min(page, pages - 1);

        String title = messages.get("id.list-title") + " (" + (current + 1) + "/" + pages + ")";
        inventory = plugin.getServer().createInventory(this, 54, title.length() > 32 ? title.substring(0, 32) : title);

        for (int i = 0; i < PER_PAGE; i++) {
            int index = current * PER_PAGE + i;
            if (index >= online.size()) {
                break;
            }
            Player p = online.get(index);
            int rpId = players.session(p).rpId();
            slotToRpId[i] = rpId;

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(p);
                meta.setDisplayName(messages.get("id.list-entry-name", "id", settings.format(rpId), "name", p.getName()));
                List<String> lore = new ArrayList<>();
                lore.add(messages.get("id.list-entry-rank", "rank", ranks.rankOf(p).chatColored()));
                if (staff) {
                    lore.add(messages.get("id.list-entry-username", "username", p.getName()));
                }
                lore.add(messages.get("id.list-entry-click"));
                meta.setLore(lore);
                head.setItemMeta(meta);
            }
            inventory.setItem(i, head);
        }
        if (current > 0) {
            inventory.setItem(SLOT_PREV, button(messages.get("id.list-previous")));
        }
        if (current < pages - 1) {
            inventory.setItem(SLOT_NEXT, button(messages.get("id.list-next")));
        }
    }

    private static ItemStack button(String name) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(""));
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public void onClick(Player player, int slot, InventoryClickEvent event) {
        if (slot == SLOT_PREV && inventory.getItem(SLOT_PREV) != null) {
            reopen(player, page - 1);
        } else if (slot == SLOT_NEXT && inventory.getItem(SLOT_NEXT) != null) {
            reopen(player, page + 1);
        } else if (slot >= 0 && slot < PER_PAGE && slotToRpId[slot] > 0) {
            player.closeInventory();
            profiles.show(player, slotToRpId[slot]);
        }
    }

    private void reopen(Player player, int newPage) {
        new ListIdMenu(plugin, players, ranks, profiles, messages, settings, staff, newPage).open(player);
    }
}
