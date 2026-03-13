package com.iadelerim;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ReturnDataStore {
    private final JavaPlugin plugin;
    private final File file;

    private final Map<UUID, Map<String, DeathReturnRecord>> recordsByVictim = new LinkedHashMap<>();

    public ReturnDataStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "returns.yml");
    }

    public void load() {
        recordsByVictim.clear();
        if (!file.exists()) {
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection deaths = yaml.getConfigurationSection("deaths");
        if (deaths == null) {
            return;
        }

        for (String victimKey : deaths.getKeys(false)) {
            UUID victimUuid;
            try {
                victimUuid = UUID.fromString(victimKey);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Gecersiz victim UUID: " + victimKey);
                continue;
            }

            ConfigurationSection victimSection = deaths.getConfigurationSection(victimKey);
            if (victimSection == null) {
                continue;
            }

            Map<String, DeathReturnRecord> victimRecords = new LinkedHashMap<>();
            for (String recordId : victimSection.getKeys(false)) {
                ConfigurationSection recordSection = victimSection.getConfigurationSection(recordId);
                if (recordSection == null) {
                    continue;
                }

                String killerRaw = recordSection.getString("killer");
                if (killerRaw == null) {
                    continue;
                }

                UUID killerUuid;
                try {
                    killerUuid = UUID.fromString(killerRaw);
                } catch (IllegalArgumentException ex) {
                    continue;
                }

                List<ItemStack> items = new ArrayList<>();
                List<?> rawItems = recordSection.getList("items", Collections.emptyList());
                for (Object rawItem : rawItems) {
                    if (rawItem instanceof ItemStack item && item.getType().isItem()) {
                        items.add(item);
                    }
                }

                if (items.isEmpty()) {
                    continue;
                }

                victimRecords.put(recordId, new DeathReturnRecord(recordId, killerUuid, items));
            }

            if (!victimRecords.isEmpty()) {
                recordsByVictim.put(victimUuid, victimRecords);
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();

        for (Map.Entry<UUID, Map<String, DeathReturnRecord>> victimEntry : recordsByVictim.entrySet()) {
            String victimRoot = "deaths." + victimEntry.getKey();
            for (DeathReturnRecord record : victimEntry.getValue().values()) {
                String root = victimRoot + "." + record.id();
                yaml.set(root + ".killer", record.killerUuid().toString());
                yaml.set(root + ".items", record.items());
            }
        }

        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("returns.yml kaydedilemedi: " + e.getMessage());
        }
    }

    public DeathReturnRecord addRecord(UUID victim, UUID killer, Collection<ItemStack> items) {
        List<ItemStack> safeItems = new ArrayList<>();
        for (ItemStack item : items) {
            if (item == null || !item.getType().isItem() || item.getAmount() <= 0) {
                continue;
            }
            safeItems.add(item.clone());
        }

        if (safeItems.isEmpty()) {
            return null;
        }

        String id = UUID.randomUUID().toString();
        DeathReturnRecord record = new DeathReturnRecord(id, killer, safeItems);
        recordsByVictim.computeIfAbsent(victim, ignored -> new LinkedHashMap<>()).put(id, record);
        return record;
    }

    public List<DeathReturnRecord> getRecords(UUID victim) {
        Map<String, DeathReturnRecord> map = recordsByVictim.get(victim);
        if (map == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(map.values());
    }

    public boolean removeRecord(UUID victim, String id) {
        Map<String, DeathReturnRecord> map = recordsByVictim.get(victim);
        if (map == null) {
            return false;
        }

        DeathReturnRecord removed = map.remove(id);
        if (map.isEmpty()) {
            recordsByVictim.remove(victim);
        }
        return removed != null;
    }

    public boolean isBanned(UUID playerUuid) {
        return Bukkit.getOfflinePlayer(playerUuid).isBanned();
    }
}
