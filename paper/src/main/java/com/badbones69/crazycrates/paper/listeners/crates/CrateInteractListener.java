package com.badbones69.crazycrates.paper.listeners.crates;

import ch.jalu.configme.SettingsManager;
import com.badbones69.crazycrates.paper.CrazyCrates;
import com.badbones69.crazycrates.paper.api.enums.Messages;
import com.badbones69.crazycrates.paper.api.events.CrateInteractEvent;
import com.badbones69.crazycrates.paper.api.events.KeyCheckEvent;
import com.badbones69.crazycrates.paper.api.objects.Crate;
import com.badbones69.crazycrates.paper.api.objects.crates.CrateLocation;
import com.badbones69.crazycrates.core.config.ConfigManager;
import com.badbones69.crazycrates.core.config.impl.ConfigKeys;
import com.badbones69.crazycrates.paper.managers.BukkitUserManager;
import com.badbones69.crazycrates.paper.managers.InventoryManager;
import com.badbones69.crazycrates.paper.managers.events.enums.EventType;
import com.badbones69.crazycrates.paper.tasks.crates.CrateManager;
import com.badbones69.crazycrates.paper.tasks.menus.CrateMainMenu;
import com.badbones69.crazycrates.paper.utils.ItemUtils;
import com.badbones69.crazycrates.paper.utils.MiscUtils;
import com.badbones69.crazycrates.paper.api.objects.Prize;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import us.crazycrew.crazycrates.api.enums.types.CrateType;
import us.crazycrew.crazycrates.api.enums.types.KeyType;

import java.util.HashMap;
import java.util.Map;

public class CrateInteractListener implements Listener {

    private final CrazyCrates plugin = CrazyCrates.getPlugin();
    private final InventoryManager inventoryManager = this.plugin.getInventoryManager();
    private final SettingsManager config = ConfigManager.getConfig();
    private final CrateManager crateManager = this.plugin.getCrateManager();
    private final BukkitUserManager userManager = this.plugin.getUserManager();

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCrateInteract(final CrateInteractEvent event) {
        if (event.isCancelled()) return; // Do not run this event if it's already cancelled.

        event.cancel(); // Cancel the event to prevent further processing.

        final Player player = event.getPlayer();
        final CrateLocation crateLocation = event.getCrateLocation();
        final Crate crate = crateLocation.getCrate();
        final Action action = event.getAction();

        switch (action) {
            case LEFT_CLICK_BLOCK, LEFT_CLICK_AIR -> {
                if (crate.getCrateType() == CrateType.menu) {
                    preview(player, crate, true);
                    return;
                }

                final boolean isLeftClickToPreview = this.config.getProperty(ConfigKeys.crate_physical_interaction);

                if (isLeftClickToPreview) { // Left click to preview.
                    preview(player, crate, false);
                    return;
                }

                // Left click to open.
                openCrate(player, crateLocation, crate);
            }

            case RIGHT_CLICK_BLOCK, RIGHT_CLICK_AIR -> {
                if (crate.getCrateType() == CrateType.menu) {
                    preview(player, crate, true);
                    return;
                }

                final boolean isRightClickToOpen = this.config.getProperty(ConfigKeys.crate_physical_interaction);

                if (isRightClickToOpen) { // Right click to open.
                    openCrate(player, crateLocation, crate);
                    return;
                }

                // Right click to preview.
                preview(player, crate, false);
            }
        }
    }

    private void openCrate(Player player, CrateLocation crateLocation, Crate crate) {
        final KeyCheckEvent key = new KeyCheckEvent(player, crateLocation);
        player.getServer().getPluginManager().callEvent(key);

        if (key.isCancelled()) return;

        final Location location = crateLocation.getLocation();

        boolean hasKey = false;
        boolean isPhysical = false;
        boolean useQuickCrateAgain = false;

        final int requiredKeys = crate.getRequiredKeys();
        final String fileName = crate.getFileName();
        final int totalKeys = this.userManager.getTotalKeys(player.getUniqueId(), fileName);
        final String fancyName = crate.getCrateName();

        if (requiredKeys > 0 && totalKeys < requiredKeys) {
            final Map<String, String> placeholders = new HashMap<>();
            placeholders.put("{required_amount}", String.valueOf(requiredKeys));
            placeholders.put("{key_amount}", String.valueOf(requiredKeys)); // Deprecated, remove in next major version of Minecraft.
            placeholders.put("{amount}", String.valueOf(totalKeys));
            placeholders.put("{crate}", fancyName);
            placeholders.put("{key}", crate.getKeyName());

            Messages.not_enough_keys.sendMessage(player, placeholders);
            lackingKey(player, crate, location, false);

            key.setCancelled(true);
            return;
        }

        final ItemStack itemStack = player.getInventory().getItemInMainHand();

        if (this.config.getProperty(ConfigKeys.physical_accepts_physical_keys) && crate.getCrateType() != CrateType.crate_on_the_go && ItemUtils.isSimilar(itemStack, crate)) {
            hasKey = true;
            isPhysical = true;
        } else if (this.config.getProperty(ConfigKeys.physical_accepts_virtual_keys) && this.userManager.getVirtualKeys(player.getUniqueId(), fileName) >= 1) {
            hasKey = true;
        }

        if (hasKey) {
            // Check if the player uses the quick crate again.
            if (this.crateManager.isInOpeningList(player) && this.crateManager.getOpeningCrate(player).getCrateType() == CrateType.quick_crate && this.crateManager.isCrateInUse(player) && this.crateManager.getCrateInUseLocation(player).equals(crateLocation.getLocation())) {
                useQuickCrateAgain = true;
            }

            if (!useQuickCrateAgain) {
                if (this.crateManager.isInOpeningList(player)) {
                    Messages.already_opening_crate.sendMessage(player, "{crate}", fancyName);
                    return;
                }

                if (this.crateManager.getCratesInUse().containsValue(crateLocation.getLocation())) {
                    Messages.crate_in_use.sendMessage(player, "{crate}", fancyName);
                    return;
                }
            }

            if (MiscUtils.isInventoryFull(player)) {
                Messages.inventory_not_empty.sendMessage(player, "{crate}", fancyName);
                return;
            }

            if (useQuickCrateAgain) this.crateManager.endQuickCrate(player, crateLocation.getLocation(), crate, true);

            final KeyType keyType = isPhysical ? KeyType.physical_key : KeyType.virtual_key;

            // Only cosmic crate type uses this method.
            if (crate.getCrateType() == CrateType.cosmic) this.crateManager.addPlayerKeyType(player, keyType);

            this.crateManager.addPlayerToOpeningList(player, crate);

            // Call pickPrize and handle null case.
            Prize prize = crate.pickPrize(player);
            if (prize == null) {
                player.sendMessage(ChatColor.RED + "You do not have permission to win any prizes from this crate.");
                this.crateManager.removePlayerFromOpeningList(player);
                return;
            }

            this.crateManager.openCrate(player, crate, keyType, location, false, true, EventType.event_crate_opened);
            return;
        }

        lackingKey(player, crate, location, true);
        key.setCancelled(true);
    }

    private void lackingKey(final Player player, final Crate crate, final Location location, final boolean sendMessage) {
        final String keyName = crate.getKeyName();

        final Map<String, String> placeholders = new HashMap<>() {{
            put("{crate}", crate.getCrateName());
            put("{key}", keyName);
        }};

        if (crate.getCrateType() != CrateType.crate_on_the_go) {
            if (this.config.getProperty(ConfigKeys.knock_back)) knockback(player, location);

            if (this.config.getProperty(ConfigKeys.need_key_sound_toggle)) {
                net.kyori.adventure.sound.Sound sound = net.kyori.adventure.sound.Sound.sound(Key.key(this.config.getProperty(ConfigKeys.need_key_sound)), Sound.Source.MASTER, 1f, 1f);
                player.playSound(sound);
            }

            if (sendMessage) Messages.no_keys.sendMessage(player, placeholders);
        }
    }

    private void knockback(final Player player, final Location location) {
        final Vector vector = player.getLocation().toVector().subtract(location.toVector()).normalize().multiply(1).setY(.1);

        if (player.isInsideVehicle() && player.getVehicle() != null) {
            player.getVehicle().setVelocity(vector);
            return;
        }

        player.setVelocity(vector);
    }

    private void preview(final Player player, final Crate crate, boolean skipTypeCheck) {
        if (skipTypeCheck || crate.getCrateType() == CrateType.menu) {
            // This is to stop players in QuadCrate from trying to open a crate set to menu.
            if (!this.crateManager.isInOpeningList(player) && this.config.getProperty(ConfigKeys.enable_crate_menu)) {
                new CrateMainMenu(
                        player,
                        this.config.getProperty(ConfigKeys.inventory_name),
                        this.config.getProperty(ConfigKeys.inventory_rows)
                ).open();
            } else {
                Messages.feature_disabled.sendMessage(player);
            }
        } else {
            if (crate.isPreviewEnabled()) {
                this.inventoryManager.openNewCratePreview(player, crate);
            } else {
                Messages.preview_disabled.sendMessage(player, "{crate}", crate.getCrateName());
            }
        }
    }
}
