package com.iadelerim;

import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

public record DeathReturnRecord(String id, UUID killerUuid, List<ItemStack> items) {
}
