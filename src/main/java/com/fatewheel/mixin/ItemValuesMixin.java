package com.fatewheel.mixin;

import com.fatewheel.FateConfig;
import com.fatewheel.FateItems;
import net.execheinz.upgrader.value.ItemValues;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Ценность особых предметов берём из нашего конфига: у них нет рецептов, Upgrader оценил бы их наугад. */
@Mixin(value = ItemValues.class, remap = false)
public abstract class ItemValuesMixin {
    @Inject(method = "unitValue", at = @At("HEAD"), cancellable = true)
    private static void fatewheel$unitValue(Level level, Item item, CallbackInfoReturnable<Double> cir) {
        double v = FateItems.value(item);
        if (v >= 0) cir.setReturnValue(v);
    }

    /** Уникальные предметы не дешевле порога — на них надо накопить, а не выиграть органами со старта. */
    @Inject(method = "unitValue", at = @At("RETURN"), cancellable = true)
    private static void fatewheel$uniqueFloor(Level level, Item item, CallbackInfoReturnable<Double> cir) {
        if (!FateItems.isUnique(item)) return;
        double floor = FateConfig.uniqueMinValue();
        if (cir.getReturnValueD() < floor) cir.setReturnValue(floor);
    }
}
