package com.fatewheel;

import com.fatewheel.item.ChronometerItem;
import com.fatewheel.item.GrowthHourglassItem;
import com.fatewheel.item.SizeWandItem;
import com.fatewheel.item.SpecialItem;
import com.fatewheel.item.TalismanItem;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;

/** Особые предметы — выигрываются в Апгрейдере (категория «Особые»). */
public final class FateItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FateWheel.MODID);
    /** В порядке показа в Апгрейдере. */
    public static final List<DeferredItem<? extends SpecialItem>> ALL = new ArrayList<>();

    public static final DeferredItem<ChronometerItem> CHRONOMETER = add("chronometer", ChronometerItem::new);
    public static final DeferredItem<GrowthHourglassItem> GROWTH_HOURGLASS = add("growth_hourglass", GrowthHourglassItem::new);
    public static final DeferredItem<SizeWandItem> SIZE_WAND = add("size_wand", SizeWandItem::new);
    public static final DeferredItem<TalismanItem> TALISMAN_STRENGTH = talisman("talisman_strength", MobEffects.STRENGTH, "Сила I");
    public static final DeferredItem<TalismanItem> TALISMAN_SPEED = talisman("talisman_speed", MobEffects.SPEED, "Скорость I");
    public static final DeferredItem<TalismanItem> TALISMAN_HASTE = talisman("talisman_haste", MobEffects.HASTE, "Спешка I");
    public static final DeferredItem<TalismanItem> TALISMAN_JUMP = talisman("talisman_jump", MobEffects.JUMP_BOOST, "Прыгучесть I");
    public static final DeferredItem<TalismanItem> TALISMAN_NIGHT_VISION = talisman("talisman_night_vision", MobEffects.NIGHT_VISION, "Ночное зрение");
    public static final DeferredItem<TalismanItem> TALISMAN_REGENERATION = talisman("talisman_regeneration", MobEffects.REGENERATION, "Регенерация I");
    public static final DeferredItem<TalismanItem> TALISMAN_FIRE = talisman("talisman_fire_resistance", MobEffects.FIRE_RESISTANCE, "Огнестойкость");
    public static final DeferredItem<TalismanItem> TALISMAN_RESISTANCE = talisman("talisman_resistance", MobEffects.RESISTANCE, "Сопротивление I");

    /** Обычные призы по категориям (id без "minecraft:"). Используются и в Апгрейдере, и в Колесе судьбы. */
    public static final String[] FOOD_IDS = {
            "cooked_beef", "cooked_porkchop", "cooked_chicken", "cooked_mutton", "cooked_rabbit", "cooked_salmon",
            "cooked_cod", "bread", "baked_potato", "golden_carrot", "golden_apple", "pumpkin_pie", "cake", "cookie",
            "mushroom_stew", "rabbit_stew", "beetroot_soup", "honey_bottle", "apple", "carrot", "sweet_berries",
            "glow_berries", "melon_slice", "dried_kelp", "chorus_fruit"};
    public static final String[] DECOR_IDS = {
            "flower_pot", "painting", "item_frame", "glow_item_frame", "lantern", "soul_lantern", "candle", "sea_lantern",
            "glowstone", "shroomlight", "end_rod", "bookshelf", "chiseled_bookshelf", "decorated_pot", "armor_stand",
            "white_banner", "red_bed", "quartz_block", "chiseled_quartz_block", "purpur_block", "prismarine_bricks",
            "dark_prismarine", "white_glazed_terracotta", "light_blue_stained_glass", "tinted_glass", "mud_bricks",
            "cherry_planks", "bamboo_mosaic", "moss_block", "azalea", "flowering_azalea", "poppy", "sunflower",
            "lily_of_the_valley", "amethyst_cluster", "copper_bulb", "polished_blackstone_bricks", "pale_oak_planks"};
    public static final String[] RESOURCE_IDS = {
            "coal", "iron_ingot", "gold_ingot", "copper_ingot", "diamond", "emerald", "lapis_lazuli", "redstone",
            "quartz", "amethyst_shard", "netherite_scrap", "netherite_ingot", "raw_iron", "raw_gold", "raw_copper",
            "obsidian", "crying_obsidian", "blaze_rod", "ender_pearl", "slime_ball", "string", "leather", "bone",
            "gunpowder", "glowstone_dust", "prismarine_shard", "oak_log", "cobblestone", "iron_block", "gold_block",
            "diamond_block", "emerald_block", "experience_bottle"};
    /** Уникальные ванильные предметы: категория «Уникальные», у них минимальная цена (FateConfig.UNIQUE_MIN_VALUE). */
    public static final String[] UNIQUE_IDS = {
            "elytra", "totem_of_undying", "nether_star", "beacon", "trident", "mace", "heavy_core", "heart_of_the_sea",
            "conduit", "dragon_egg", "dragon_head", "wither_skeleton_skull", "enchanted_golden_apple",
            "netherite_upgrade_smithing_template", "shulker_box", "ender_chest", "end_crystal", "respawn_anchor",
            "lodestone", "enchanting_table", "echo_shard", "recovery_compass", "sniffer_egg", "ominous_trial_key",
            "music_disc_pigstep", "music_disc_otherside", "spyglass"};
    private static java.util.Set<Item> unique;

    private FateItems() {
    }

    public static boolean isUnique(Item item) {
        if (unique == null) {
            java.util.Set<Item> set = new java.util.HashSet<>();
            for (String id : UNIQUE_IDS) {
                net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getOptional(net.minecraft.resources.Identifier.withDefaultNamespace(id)).ifPresent(set::add);
            }
            unique = set;
        }
        return unique.contains(item);
    }

    private static <I extends SpecialItem> DeferredItem<I> add(String name, java.util.function.Function<Item.Properties, I> f) {
        DeferredItem<I> item = ITEMS.registerItem(name, f, () -> new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
        ALL.add(item);
        return item;
    }

    private static DeferredItem<TalismanItem> talisman(String name, Holder<MobEffect> effect, String title) {
        return add(name, p -> new TalismanItem(p, effect, 0,
                "Пока талисман в инвентаре: " + title + ".", "Действует, где бы он ни лежал."));
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(FateItems::onCreativeTab);
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            for (DeferredItem<? extends SpecialItem> item : ALL) e.accept(new ItemStack(item.get()));
        }
    }

    /** Ценность для Upgrader или -1, если это не наш предмет. */
    public static double value(Item item) {
        if (item instanceof SpecialItem) {
            Integer v = FateConfig.specialValue(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getPath());
            if (v != null) return v;
        }
        return -1;
    }
}
