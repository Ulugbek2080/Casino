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
    FOOD("Еда", "cooked_beef", FateItems.FOOD_IDS),
    DECOR("Декор", "flower_pot", FateItems.DECOR_IDS),
    RESOURCES("Ресурсы", "iron_ingot", FateItems.RESOURCE_IDS),
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
