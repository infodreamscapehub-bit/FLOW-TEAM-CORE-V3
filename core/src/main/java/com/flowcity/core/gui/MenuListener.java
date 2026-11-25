package com.flowcity.core.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/** Routes clicks to {@link Menu}s and makes their items impossible to move or take. */
public final class MenuListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu)) {
            return;
        }
        event.setCancelled(true);
        Menu menu = (Menu) event.getView().getTopInventory().getHolder();
        if (event.getClickedInventory() == event.getView().getTopInventory()
                && event.getWhoClicked() instanceof Player) {
            menu.onClick((Player) event.getWhoClicked(), event.getSlot(), event);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) {
            event.setCancelled(true);
        }
    }
}
