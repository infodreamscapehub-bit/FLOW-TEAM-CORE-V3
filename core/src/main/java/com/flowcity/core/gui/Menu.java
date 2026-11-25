package com.flowcity.core.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Base for server-side chest menus. All clicks inside a Menu are cancelled by {@link MenuListener},
 * so items can never be taken out; subclasses only react to the clicked slot.
 */
public abstract class Menu implements InventoryHolder {

    protected Inventory inventory;

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /** Called for clicks in the menu's own (top) inventory. The event is already cancelled. */
    public abstract void onClick(Player player, int slot, InventoryClickEvent event);

    public void open(Player player) {
        player.openInventory(inventory);
    }
}
