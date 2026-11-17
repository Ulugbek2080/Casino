package com.fatewheel.client;

import com.fatewheel.FateItems;
import net.execheinz.upgrader.client.ClientCatalog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Категории выигрыша в Апгрейдере (справа). Это только быстрый выбор цели: клик по предмету
 * ставит его целью Апгрейдера как обычно, дальше всё считает сам Upgrader.
 * Предметы, которых нет в этой версии или которые в чёрном списке Upgrader, просто не показываются.
 */
public enum Category {
    FOOD("Еда", "cooked_beef",
            "cooked_beef", "cooked_porkchop", "cooked_chicken", "cooked_mutton", "cooked_rabbit", "cooked_salmon",
            "cooked_cod", "bread", "baked_potato", "golden_carrot", "golden_apple", "pumpkin_pie", "cake", "cookie",
            "mushroom_stew", "rabbit_stew", "beetroot_soup", "honey_bottle", "apple", "carrot", "sweet_berries",
            "glow_berries", "melon_slice", "dried_kelp", "chorus_fruit"),
    DECOR("Декор", "flower_pot",
            "flower_pot", "painting", "item_frame", "glow_item_frame", "lantern", "soul_lantern", "candle", "sea_lantern",
            "glowstone", "shroomlight", "end_rod", "bookshelf", "chiseled_bookshelf", "decorated_pot", "armor_stand",
            "white_banner", "red_bed", "quartz_block", "chiseled_quartz_block", "purpur_block", "prismarine_bricks",
            "dark_prismarine", "white_glazed_terracotta", "light_blue_stained_glass", "tinted_glass", "mud_bricks",
            "cherry_planks", "bamboo_mosaic", "moss_block", "azalea", "flowering_azalea", "poppy", "sunflower",
            "lily_of_the_valley", "amethyst_cluster", "copper_bulb", "polished_blackstone_bricks", "pale_oak_planks"),
    RESOURCES("Ресурсы", "iron_ingot",
            "coal", "iron_ingot", "gold_ingot", "copper_ingot", "diamond", "emerald", "lapis_lazuli", "redstone",
            "quartz", "amethyst_shard", "netherite_scrap", "netherite_ingot", "raw_iron", "raw_gold", "raw_copper",
            "obsidian", "crying_obsidian", "blaze_rod", "ender_pearl", "slime_ball", "string", "leather", "bone",
            "gunpowder", "glowstone_dust", "prismarine_shard", "oak_log", "cobblestone", "iron_block", "gold_block",
            "diamond_block", "emerald_block", "experience_bottle"),
    UNIQUE("Уникальные", "nether_star", FateItems.UNIQUE_IDS),
    /** Предметы этого мода — список берётся из FateItems. */
    SPECIAL("Особые", "fatewheel:chronometer");

    public final String title;
    private final String iconId;
    private final String[] ids;
    private List<Item> resolved;

    Category(String title, String iconId, String... ids) {
        this.title = title;
        this.iconId = iconId;
        this.ids = ids;
    }

    public ItemStack icon() {
        return new ItemStack(item(iconId));
    }

    /** Предметы категории (без недоступных). */
    public List<Item> items() {
        if (resolved != null) return resolved;
        List<Item> out = new ArrayList<>();
        if (this == SPECIAL) {
            FateItems.ALL.forEach(i -> out.add(i.get()));
        } else {
            for (String id : ids) {
                Item it = item(id);
                if (it != null && it != net.minecraft.world.item.Items.AIR) out.add(it);
            }
        }
        if (ClientCatalog.isReady()) {
            Set<Item> allowed = new HashSet<>(ClientCatalog.items());
            out.removeIf(i -> !allowed.contains(i));
        }
        // пока каталог не пришёл, не кешируем — потом отфильтруем по чёрному списку
        if (ClientCatalog.isReady()) resolved = out;
        return out;
    }

    public static void resetCache() {
        for (Category c : values()) c.resolved = null;
    }

    private static Item item(String id) {
        Identifier key = Identifier.tryParse(id.contains(":") ? id : "minecraft:" + id);
        if (key == null) return null;
        return BuiltInRegistries.ITEM.getOptional(key).orElse(null);
    }
}
