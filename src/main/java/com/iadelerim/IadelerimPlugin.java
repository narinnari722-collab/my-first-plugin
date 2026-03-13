package com.iadelerim;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameRule;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class IadelerimPlugin extends JavaPlugin implements Listener {
    private static final String MENU_TITLE = ChatColor.DARK_RED + "Iadelerim";

    private ReturnDataStore store;
    private NamespacedKey recordIdKey;
    private final Map<UUID, Inventory> openMenus = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        this.recordIdKey = new NamespacedKey(this, "record_id");
        this.store = new ReturnDataStore(this);
        this.store.load();
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        if (store != null) {
            store.save();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Bu komutu sadece oyuncu kullanabilir.");
            return true;
        }

        if (!command.getName().equalsIgnoreCase("iadelerim")) {
            return false;
        }

        openMenu(player);
        return true;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        Player killer = victim.getKiller();
        if (killer == null) {
            return;
        }

        World world = victim.getWorld();
        Boolean keepInventory = world.getGameRuleValue(GameRule.KEEP_INVENTORY);
        if (Boolean.TRUE.equals(keepInventory)) {
            return;
        }

        List<ItemStack> drops = new ArrayList<>(event.getDrops());
        DeathReturnRecord added = store.addRecord(victim.getUniqueId(), killer.getUniqueId(), drops);
        if (added != null) {
            store.save();
        }
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory menu = openMenus.get(player.getUniqueId());
        if (menu == null || !event.getView().getTitle().equals(MENU_TITLE)) {
            return;
        }

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() != Material.PLAYER_HEAD || !clicked.hasItemMeta()) {
            return;
        }

        SkullMeta meta = (SkullMeta) clicked.getItemMeta();
        if (meta == null) {
            return;
        }

        String recordId = meta.getPersistentDataContainer().get(recordIdKey, PersistentDataType.STRING);
        if (recordId == null) {
            return;
        }

        DeathReturnRecord record = store.getRecords(player.getUniqueId()).stream()
                .filter(r -> r.id().equals(recordId))
                .findFirst()
                .orElse(null);

        if (record == null) {
            player.sendMessage(ChatColor.RED + "Bu iade kaydi bulunamadi.");
            openMenu(player);
            return;
        }

        giveItems(player, record.items());
        store.removeRecord(player.getUniqueId(), record.id());
        store.save();

        player.sendMessage(ChatColor.GREEN + "Olumde kaybettigin itemler iade edildi.");
        openMenu(player);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getView().getTitle().equals(MENU_TITLE)) {
            openMenus.remove(event.getPlayer().getUniqueId());
        }
    }

    private void openMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, MENU_TITLE);
        List<DeathReturnRecord> available = store.getRecords(player.getUniqueId()).stream()
                .filter(r -> store.isBanned(r.killerUuid()))
                .toList();

        int slot = 0;
        for (DeathReturnRecord record : available) {
            if (slot >= inv.getSize()) {
                break;
            }

            inv.setItem(slot++, createHead(record));
        }

        openMenus.put(player.getUniqueId(), inv);
        player.openInventory(inv);
    }

    private ItemStack createHead(DeathReturnRecord record) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null) {
            return head;
        }

        OfflinePlayer killer = Bukkit.getOfflinePlayer(record.killerUuid());
        meta.setOwningPlayer(killer);
        meta.setDisplayName(ChatColor.RED + (killer.getName() == null ? record.killerUuid().toString() : killer.getName()));
        meta.setLore(List.of(
                ChatColor.GRAY + "Bu oyuncu tarafindan olduruldun.",
                ChatColor.YELLOW + "Sol tikla: item iadeni al"
        ));
        meta.getPersistentDataContainer().set(recordIdKey, PersistentDataType.STRING, record.id());
        head.setItemMeta(meta);
        return head;
    }

    private void giveItems(Player player, List<ItemStack> items) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(items.toArray(new ItemStack[0]));
        leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
    }
}
