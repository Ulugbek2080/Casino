package com.fatewheel;

import com.fatewheel.network.FateSyncPayload;
import com.fatewheel.upgrader.FateUpgrader;
import net.execheinz.upgrader.UpgraderRegistry;
import net.execheinz.upgrader.menu.UpgraderMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.util.TriState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Серверная логика: проклятия, задания, органы. Ставки в Апгрейдере — см. FateUpgrader. */
public final class FateManager {
    private static final String TAG = "fatewheel";
    private static final Map<UUID, FateState> STATES = new HashMap<>();
    static final Random RNG = new Random();

    private static final Identifier MOD_SPEED = FateWheel.id("legs_speed");
    private static final Identifier MOD_JUMP = FateWheel.id("legs_jump");
    private static final Identifier MOD_HEALTH = FateWheel.id("max_health");
    private static final Identifier MOD_FEET_SPEED = FateWheel.id("feet_speed");
    private static final Identifier MOD_FEET_FALL = FateWheel.id("feet_fall");
    private static final Identifier MOD_HAND_DIG = FateWheel.id("hand_dig");
    private static final Identifier MOD_HAND_ATTACK = FateWheel.id("hand_attack");

    private FateManager() {
    }

    public static void register(IEventBus bus) {
        bus.addListener(FateManager::onTick);
        bus.addListener(FateManager::onBreak);
        bus.addListener(FateManager::onPlace);
        bus.addListener(FateManager::onJump);
        bus.addListener(FateManager::onEat);
        bus.addListener(FateManager::onDeath);
        bus.addListener(FateManager::onChat);
        bus.addListener(FateManager::onToss);
        bus.addListener(FateManager::onPickup);
        bus.addListener(FateManager::onLogin);
        bus.addListener(FateManager::onLogout);
        bus.addListener(FateManager::onClone);
        // руки: работают на обеих сторонах, чтобы клиент не «предсказывал» запрещённое
        bus.addListener(FateManager::onRightClickItem);
        bus.addListener(FateManager::onRightClickBlock);
        bus.addListener(FateManager::onEntityInteract);
        bus.addListener(FateManager::onAttack);
        bus.addListener(FateManager::onBreakSpeed);
        // ступни: бег отключаем на обеих сторонах, иначе клиент сам снова побежит
        bus.addListener(FateManager::onAnyTick);
    }

    // ------------------------------------------------------------------ состояние

    public static FateState get(ServerPlayer p) {
        return STATES.computeIfAbsent(p.getUUID(),
                u -> FateState.load(p.getPersistentData().getCompoundOrEmpty(TAG)));
    }

    private static void save(ServerPlayer p, FateState s) {
        p.getPersistentData().put(TAG, s.save());
    }

    /** Что проиграно — на любой стороне. */
    public static boolean lost(Player p, Stake s) {
        if (p instanceof ServerPlayer sp) return get(sp).lost(s);
        return p.level().isClientSide() && ClientFlags.lost(s);
    }

    /** Открыть Апгрейдер (ставки временем и органами — внутри него). */
    public static void openUpgrader(ServerPlayer p) {
        if (UpgraderRegistry.menuOpener == null) return;
        UpgraderRegistry.menuOpener.open(p, new SimpleMenuProvider(
                (id, inv, pl) -> new UpgraderMenu(id, inv), Component.translatable("menu.upgrader.title")));
    }

    // ------------------------------------------------------------------ тик

    private static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        FateState s = get(p);
        if (s.warnTicks > 0) s.warnTicks--;
        if (!p.isAlive()) return;

        tickPresent(p, s);
        tickCombos(p, s);
        tickBody(p, s);
        if (s.needSync || s.sentLost != s.lostMask) sendSync(p);

        if (p.tickCount % 20 == 0) {
            actionBar(p, s);
            save(p, s);
        }
    }

    // ------------------------------------------------------------------ Настоящее

    private static void tickPresent(ServerPlayer p, FateState s) {
        trackMovement(p, s);
        if (!s.present()) {
            if (s.taskId >= 0) s.clearTask();
            return;
        }
        PresentTask task = PresentTask.byId(s.taskId);
        if (task == null || !task.possible(s)) {
            newTask(p, s);
            return;
        }

        if (!s.taskDone) {
            switch (task) {
                case CROUCH -> {
                    boolean c = p.isShiftKeyDown();
                    if (c && !s.wasCrouching) s.taskProgress++;
                    s.wasCrouching = c;
                }
                case WALK -> s.taskProgress = (int) s.walked;
                case CLIMB -> s.taskProgress = Math.max(0, (int) Math.floor(p.getY() - s.startY));
                case STILL -> s.taskProgress = s.stillTicks / 20;
                case LOOK_UP -> {
                    if (p.getXRot() < -75f) s.taskProgress = 1;
                }
                default -> {
                }
            }
            checkDone(p, s);
        }

        s.cycleTicks--;
        if (s.cycleTicks <= 0) {
            if (s.taskDone) newTask(p, s);
            else failTask(p, s);
        }
    }

    private static void trackMovement(ServerPlayer p, FateState s) {
        double x = p.getX(), y = p.getY(), z = p.getZ();
        if (s.hasLast) {
            double dx = x - s.lastX, dz = z - s.lastZ, dy = y - s.lastY;
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            if (horizontal < 8) s.walked += horizontal; // телепорты не считаем
            if (horizontal < 0.01 && Math.abs(dy) < 0.01) s.stillTicks++;
            else s.stillTicks = 0;
        }
        s.lastX = x;
        s.lastY = y;
        s.lastZ = z;
        s.hasLast = true;
    }

    private static void newTask(ServerPlayer p, FateState s) {
        List<PresentTask> pool = new ArrayList<>();
        for (PresentTask t : PresentTask.values()) {
            if (t.possible(s) && t.ordinal() != s.taskId) pool.add(t);
        }
        if (pool.isEmpty()) pool.add(PresentTask.STILL);
        PresentTask t = pool.get(RNG.nextInt(pool.size()));
        s.clearTask();
        s.taskId = t.ordinal();
        s.taskGoal = t.min + RNG.nextInt(t.max - t.min + 1);
        if (t == PresentTask.SAY) s.taskWord = PresentTask.WORDS.get(RNG.nextInt(PresentTask.WORDS.size()));
        s.cycleTicks = FateConfig.TASK_SECONDS.get() * 20;
        s.startY = p.getY();
        s.walked = 0;
        s.stillTicks = 0;
        s.wasCrouching = p.isShiftKeyDown();

        p.sendSystemMessage(Component.literal("[Настоящее] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(t.describe(s)).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("  (" + FateConfig.TASK_SECONDS.get() + " сек)").withStyle(ChatFormatting.GRAY)));
        sound(p, SoundEvents.NOTE_BLOCK_BELL, 1f, 1.2f);
    }

    private static void checkDone(ServerPlayer p, FateState s) {
        if (!s.taskDone && s.taskProgress >= s.taskGoal) {
            s.taskDone = true;
            p.sendSystemMessage(Component.literal("[Настоящее] Выполнено. Следующее задание через "
                    + (s.cycleTicks / 20) + " сек.").withStyle(ChatFormatting.GREEN));
            sound(p, SoundEvents.EXPERIENCE_ORB_PICKUP, 1f, 1f);
        }
    }

    private static void failTask(ServerPlayer p, FateState s) {
        s.clearTask();
        p.sendSystemMessage(Component.literal("[Настоящее] Вы не успели. Настоящее не ждёт.")
                .withStyle(ChatFormatting.DARK_RED));
        if (!p.isSpectator()) p.kill((ServerLevel) p.level());
    }

    /** Прогресс заданий, которые засчитываются по событиям. */
    private static void progress(ServerPlayer p, PresentTask task, int amount) {
        FateState s = get(p);
        if (s.present() && s.taskId == task.ordinal() && !s.taskDone) {
            s.taskProgress += amount;
            checkDone(p, s);
        }
    }

    // ------------------------------------------------------------------ комбо

    private static void tickCombos(ServerPlayer p, FateState s) {
        // Прошлое + Настоящее: всё построенное игроком исчезает
        if (s.past() && s.present()) {
            if (++s.eraseTicks >= FateConfig.ERASE_SECONDS.get() * 20) {
                s.eraseTicks = 0;
                int n = erasePlaced(p, s);
                if (n > 0) {
                    p.sendSystemMessage(Component.literal("Прошлое стирает всё, что вы построили (" + n + " бл.)")
                            .withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
                    sound(p, SoundEvents.ENDERMAN_TELEPORT, 0.8f, 0.6f);
                }
            }
        } else {
            s.eraseTicks = 0;
        }
        // Настоящее + Будущее: нельзя ставить и использовать предметы — см. itemsBlocked
        // Прошлое + Будущее: одно сердце — см. tickBody (модификатор здоровья)
    }

    /** Убрать все блоки, которые ставил игрок (в загруженных чанках). Возвращает, сколько исчезло. */
    private static int erasePlaced(ServerPlayer p, FateState s) {
        int count = 0;
        for (ServerLevel level : p.level().getServer().getAllLevels()) {
            java.util.LinkedHashSet<Long> set = s.placed.get(level.dimension().identifier().toString());
            if (set == null) continue;
            java.util.Iterator<Long> it = set.iterator();
            while (it.hasNext()) {
                BlockPos pos = BlockPos.of(it.next());
                if (!level.isLoaded(pos)) continue; // доберёмся, когда чанк загрузится
                it.remove();
                s.placedCount--;
                if (!level.getBlockState(pos).isAir() && level.destroyBlock(pos, false)) count++;
            }
        }
        return count;
    }


    // ------------------------------------------------------------------ органы

    private static void tickBody(ServerPlayer p, FateState s) {
        int legs = s.lostLegs();
        setMod(p, Attributes.MOVEMENT_SPEED, MOD_SPEED, legs == 2 ? -1.0 : legs == 1 ? -0.4 : 0,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        setMod(p, Attributes.JUMP_STRENGTH, MOD_JUMP, legs == 2 ? -1.0 : legs == 1 ? -0.5 : 0,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

        int feet = s.lostFeet();
        setMod(p, Attributes.MOVEMENT_SPEED, MOD_FEET_SPEED, feet * -0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        setMod(p, Attributes.FALL_DAMAGE_MULTIPLIER, MOD_FEET_FALL, feet * 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

        boolean rightHand = s.lost(Stake.RIGHT_HAND);
        setMod(p, Attributes.BLOCK_BREAK_SPEED, MOD_HAND_DIG, rightHand ? -0.5 : 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        setMod(p, Attributes.ATTACK_SPEED, MOD_HAND_ATTACK, rightHand ? -0.5 : 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

        AttributeInstance hp = p.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) {
            double amount = 0;
            if (s.past() && s.future()) amount = 2.0 - hp.getBaseValue(); // ровно одно сердце
            else if (s.lost(Stake.HEART)) amount = -10.0;
            setMod(p, Attributes.MAX_HEALTH, MOD_HEALTH, amount, AttributeModifier.Operation.ADD_VALUE);
            if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
        }

        // без левой руки во второй руке ничего не удержать
        if (s.lost(Stake.LEFT_ARM) && !p.getOffhandItem().isEmpty()) {
            ItemStack off = p.getOffhandItem().copy();
            p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            give(p, off);
            warn(p, s, "Левой руки нет — во второй руке ничего не удержать.");
        }

        // без обоих глаз — слепота; без одного — затемнение четверти экрана (на клиенте)
        if (s.lost(Stake.LEFT_EYE) && s.lost(Stake.RIGHT_EYE)) {
            MobEffectInstance cur = p.getEffect(MobEffects.BLINDNESS);
            if (cur == null || cur.getDuration() < 60) {
                p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 400, 0, false, false, true));
            }
        }
    }

    private static void setMod(ServerPlayer p, Holder<Attribute> attribute, Identifier id, double amount,
                               AttributeModifier.Operation op) {
        AttributeInstance inst = p.getAttribute(attribute);
        if (inst == null) return;
        AttributeModifier cur = inst.getModifier(id);
        if (amount == 0) {
            if (cur != null) inst.removeModifier(id);
            return;
        }
        if (cur != null && cur.amount() == amount && cur.operation() == op) return;
        inst.addOrUpdateTransientModifier(new AttributeModifier(id, amount, op));
    }

    // ------------------------------------------------------------------ руки

    /** Можно ли действовать этой рукой. Без правой руки основная рука работает только пустой (это «левая»). */
    private static boolean handBlocked(Player p, InteractionHand hand) {
        if (p.isCreative() || p.isSpectator()) return false;
        if (isUpgrader(p.getItemInHand(hand))) return false; // отыграться можно всегда
        boolean noLeft = lost(p, Stake.LEFT_ARM), noRight = lost(p, Stake.RIGHT_ARM);
        if (noLeft && noRight) return true;
        if (hand == InteractionHand.OFF_HAND) return noLeft || lost(p, Stake.LEFT_HAND);
        return noRight && !p.getMainHandItem().isEmpty();
    }

    /** Атака и копание идут «основной» рукой: без правой — только пустой рукой (левой). */
    private static boolean swingBlocked(Player p) {
        return handBlocked(p, InteractionHand.MAIN_HAND);
    }

    private static void handWarning(Player p) {
        if (!(p instanceof ServerPlayer sp)) return;
        FateState s = get(sp);
        if (s.noArms()) warn(sp, s, "У вас нет рук.");
        else if (s.lost(Stake.RIGHT_ARM)) warn(sp, s, "Правой руки нет — возьмите предмет в левую (F).");
        else if (s.lost(Stake.LEFT_ARM)) warn(sp, s, "Левой руки нет.");
        else warn(sp, s, "Левой кисти нет — во второй руке предмет только держится.");
    }

    private static void warn(ServerPlayer p, FateState s, String text) {
        if (s.warnTicks > 0) return;
        s.warnTicks = 40;
        p.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.RED), true);
    }

    /** Апгрейдер работает при любых потерях — иначе проигравший не сможет отыграться. */
    private static boolean isUpgrader(ItemStack stack) {
        return UpgraderRegistry.upgraderItem != null && stack.is(UpgraderRegistry.upgraderItem);
    }

    /** Настоящее + Будущее: ни поставить, ни использовать предмет. */
    private static boolean itemsBlocked(Player p, ItemStack stack) {
        if (stack.isEmpty() || p.isCreative() || p.isSpectator() || isUpgrader(stack)) return false;
        return lost(p, Stake.PRESENT) && lost(p, Stake.FUTURE);
    }

    private static void itemsWarning(Player p) {
        if (p instanceof ServerPlayer sp) warn(sp, get(sp), "Настоящее + Будущее: предметы вам больше не подчиняются.");
    }

    private static void onRightClickItem(PlayerInteractEvent.RightClickItem e) {
        if (itemsBlocked(e.getEntity(), e.getItemStack())) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.FAIL);
            itemsWarning(e.getEntity());
            return;
        }
        if (handBlocked(e.getEntity(), e.getHand())) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.PASS); // пусть попробует другая рука
            handWarning(e.getEntity());
        }
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        if (itemsBlocked(e.getEntity(), e.getItemStack())) {
            // двери, сундуки, кнопки работают; поставить блок или применить предмет — нет
            e.setUseItem(TriState.FALSE);
            itemsWarning(e.getEntity());
        }
        if (handBlocked(e.getEntity(), e.getHand())) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.PASS);
            handWarning(e.getEntity());
        }
    }

    private static void onEntityInteract(PlayerInteractEvent.EntityInteract e) {
        if (itemsBlocked(e.getEntity(), e.getItemStack())) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.FAIL);
            itemsWarning(e.getEntity());
            return;
        }
        if (handBlocked(e.getEntity(), e.getHand())) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.PASS);
            handWarning(e.getEntity());
        }
    }

    private static void onAttack(AttackEntityEvent e) {
        if (swingBlocked(e.getEntity())) {
            e.setCanceled(true);
            handWarning(e.getEntity());
        }
    }

    private static void onBreakSpeed(PlayerEvent.BreakSpeed e) {
        if (swingBlocked(e.getEntity())) {
            e.setCanceled(true);
            handWarning(e.getEntity());
        }
    }

    // ------------------------------------------------------------------ синхронизация и HUD

    /** Отправить клиенту проигранное и выбор в Апгрейдере. */
    public static void sendSync(ServerPlayer p) {
        FateState s = get(p);
        FateUpgrader.Selection sel = FateUpgrader.selection(p);
        PacketDistributor.sendToPlayer(p, new FateSyncPayload(s.lostMask,
                sel.inMode.ordinal(), sel.inMask, sel.outMode.ordinal(), sel.outMask));
        s.sentLost = s.lostMask;
        s.needSync = false;
    }

    private static void actionBar(ServerPlayer p, FateState s) {
        PresentTask task = PresentTask.byId(s.taskId);
        if (s.present() && task != null) {
            int sec = Math.max(0, s.cycleTicks / 20);
            MutableComponent c;
            if (s.taskDone) {
                c = Component.literal("✔ Выполнено — новое задание через " + sec + "с").withStyle(ChatFormatting.GREEN);
            } else {
                String prog = s.taskGoal > 1 ? " [" + Math.min(s.taskProgress, s.taskGoal) + "/" + s.taskGoal + "]" : "";
                c = Component.literal("⏳ " + task.describe(s) + prog + " — ")
                        .withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal(sec + "с").withStyle(sec <= 10 ? ChatFormatting.RED : ChatFormatting.GOLD));
            }
            p.sendSystemMessage(c, true);
        } else if (s.anyCurse() && s.warnTicks == 0) {
            p.sendSystemMessage(curseSummary(s), true);
        }
    }

    private static MutableComponent curseSummary(FateState s) {
        MutableComponent c = Component.literal("⌛ ").withStyle(ChatFormatting.GRAY);
        boolean first = true;
        for (Stake st : new Stake[]{Stake.PAST, Stake.PRESENT, Stake.FUTURE}) {
            if (!s.lost(st)) continue;
            if (!first) c.append(Component.literal("  |  ").withStyle(ChatFormatting.DARK_GRAY));
            c.append(Component.literal(st.title).withStyle(st.color));
            first = false;
        }
        return c.append(Component.literal("  — навсегда").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Строки для команды /fate status. */
    static List<Component> statusLines(FateState s) {
        List<Component> out = new ArrayList<>();
        for (Stake st : new Stake[]{Stake.PAST, Stake.PRESENT, Stake.FUTURE}) {
            if (s.lost(st)) out.add(Component.literal("• Проклятие: " + st.title).withStyle(st.color));
        }
        if (s.past() && s.present()) out.add(Component.literal("  + построенное вами исчезает").withStyle(ChatFormatting.DARK_AQUA));
        if (s.present() && s.future()) out.add(Component.literal("  + нельзя ставить и использовать предметы").withStyle(ChatFormatting.GOLD));
        if (s.past() && s.future()) out.add(Component.literal("  + осталось одно сердце").withStyle(ChatFormatting.DARK_RED));
        for (Stake st : Stake.values()) {
            if (!st.time && s.lost(st)) out.add(Component.literal("• Нет: " + st.title).withStyle(ChatFormatting.RED));
        }
        if (out.isEmpty()) {
            out.add(Component.literal("Вы целы и свободны. Пока что.").withStyle(ChatFormatting.GREEN));
        } else {
            out.add(Component.literal("Всё потерянное — навсегда.").withStyle(ChatFormatting.DARK_GRAY));
            out.add(Component.literal("Отыграть можно в Апгрейдере: справа выберите «Время» или «Органы».")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        return out;
    }

    // ------------------------------------------------------------------ потеря и возврат

    public static void lose(ServerPlayer p, int mask) {
        FateState s = get(p);
        s.lostMask |= mask;
        save(p, s);
        if (s.past() && s.present() && (mask & (Stake.PAST.bit | Stake.PRESENT.bit)) != 0) {
            int n = erasePlaced(p, s);
            s.eraseTicks = 0;
            p.sendSystemMessage(Component.literal("Прошлое + Настоящее: всё, что вы построили, исчезло"
                    + (n > 0 ? " (" + n + " бл.)" : "") + ". Новые блоки будут исчезать каждые "
                    + FateConfig.ERASE_SECONDS.get() + " сек.").withStyle(ChatFormatting.DARK_AQUA));
        }
        if (s.present() && s.future() && (mask & (Stake.PRESENT.bit | Stake.FUTURE.bit)) != 0)
            p.sendSystemMessage(Component.literal("Настоящее + Будущее: вы больше не можете ставить блоки и использовать предметы.")
                    .withStyle(ChatFormatting.GOLD));
        if (s.past() && s.future() && (mask & (Stake.PAST.bit | Stake.FUTURE.bit)) != 0)
            p.sendSystemMessage(Component.literal("Прошлое + Будущее: у вас осталось одно сердце.")
                    .withStyle(ChatFormatting.DARK_RED));
    }

    public static void restore(ServerPlayer p, int mask) {
        FateState s = get(p);
        boolean blind = s.lost(Stake.LEFT_EYE) && s.lost(Stake.RIGHT_EYE);
        s.lostMask &= ~mask;
        if (blind && !(s.lost(Stake.LEFT_EYE) && s.lost(Stake.RIGHT_EYE))) p.removeEffect(MobEffects.BLINDNESS);
        if ((mask & Stake.PRESENT.bit) != 0) s.clearTask();
        save(p, s);
    }

    public static void announceWin(ServerPlayer p, Component got, boolean keptStake) {
        title(p, Component.literal("ПОБЕДА").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), got);
        p.sendSystemMessage(got.copy().withStyle(ChatFormatting.GREEN));
        if (keptStake) p.sendSystemMessage(Component.literal("Ставка остаётся при вас.").withStyle(ChatFormatting.GREEN));
        sound(p, SoundEvents.PLAYER_LEVELUP, 1f, 1f);
    }

    public static void announceLoss(ServerPlayer p, String lostText, boolean forever) {
        title(p, Component.literal("ПРОИГРЫШ").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                Component.literal("Вы теряете: " + lostText).withStyle(ChatFormatting.RED));
        p.sendSystemMessage(Component.literal((forever ? "Колесо забирает навсегда: " : "Колесо забирает: ") + lostText)
                .withStyle(ChatFormatting.DARK_RED));
        if (forever) {
            p.sendSystemMessage(Component.literal("Отыграть можно в Апгрейдере: справа выберите «Время» или «Органы».")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            sound(p, SoundEvents.ELDER_GUARDIAN_CURSE, 1f, 1f);
        }
    }

    /**
     * Отдать предметы: в инвентарь, если не влезло — в эндер-сундук, и только потом на землю.
     */
    public static void give(ServerPlayer p, ItemStack stack) {
        ItemStack rest = stack.copy();
        p.getInventory().add(rest);
        if (rest.isEmpty()) return;
        rest = p.getEnderChestInventory().addItem(rest);
        if (rest.isEmpty()) {
            p.sendSystemMessage(Component.literal("Инвентарь полон — предмет в эндер-сундуке.")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            return;
        }
        p.drop(rest, false, Prediction.SERVER_ONLY);
    }

    // ------------------------------------------------------------------ админ

    static void healBody(ServerPlayer p) {
        restore(p, Stake.BODY_MASK);
    }

    static void clearCurses(ServerPlayer p) {
        restore(p, Stake.TIME_MASK);
    }

    // ------------------------------------------------------------------ утилиты

    static void sound(ServerPlayer p, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    static void sound(ServerPlayer p, Holder<net.minecraft.sounds.SoundEvent> sound, float volume, float pitch) {
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static void title(ServerPlayer p, Component title, Component subtitle) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 50, 15));
        p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        p.connection.send(new ClientboundSetTitleTextPacket(title));
    }

    // ------------------------------------------------------------------ события

    private static void onBreak(BreakBlockEvent e) {
        if (e.getPlayer() instanceof ServerPlayer p) {
            progress(p, PresentTask.BREAK, 1);
            // блок сломан — он больше ничей, чужую постройку на этом месте не трогаем
            String dim = p.level().dimension().identifier().toString();
            long pos = e.getPos().asLong();
            for (FateState s : STATES.values()) s.removePlaced(dim, pos);
        }
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            progress(p, PresentTask.PLACE, 1);
            if (!p.isCreative() && !p.isSpectator()) {
                String dim = p.level().dimension().identifier().toString();
                long pos = e.getPos().asLong();
                for (FateState s : STATES.values()) s.removePlaced(dim, pos);
                get(p).addPlaced(dim, pos);
            }
        }
    }

    /** Без обеих ступней бегать нельзя (на обеих сторонах, чтобы клиент не дёргался). */
    private static void onAnyTick(PlayerTickEvent.Pre e) {
        Player p = e.getEntity();
        if (p.isSprinting() && !p.isCreative() && !p.isSpectator()
                && lost(p, Stake.LEFT_FOOT) && lost(p, Stake.RIGHT_FOOT)) {
            p.setSprinting(false);
        }
    }

    private static void onJump(LivingEvent.LivingJumpEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) progress(p, PresentTask.JUMP, 1);
    }

    private static void onEat(LivingEntityUseItemEvent.Finish e) {
        if (e.getEntity() instanceof ServerPlayer p && e.getItem().has(DataComponents.FOOD)) {
            progress(p, PresentTask.EAT, 1);
        }
    }

    private static void onDeath(LivingDeathEvent e) {
        if (e.getSource().getEntity() instanceof ServerPlayer killer && e.getEntity() != killer) {
            progress(killer, PresentTask.KILL, 1);
        }
        if (e.getEntity() instanceof ServerPlayer dead) {
            FateState s = get(dead);
            s.clearTask(); // после возрождения — новое задание с полным таймером; потери остаются навсегда
            save(dead, s);
        }
    }

    private static void onChat(ServerChatEvent e) {
        ServerPlayer p = e.getPlayer();
        FateState s = get(p);
        if (s.lost(Stake.TONGUE)) {
            String[] moo = {"Ммм!", "Мм-ммм...", "Ммммм?!", "М. Мм. Ммм.", "Ыыы-ммм!"};
            e.setMessage(Component.literal(moo[RNG.nextInt(moo.length)]).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            return;
        }
        if (s.present() && s.taskId == PresentTask.SAY.ordinal()
                && e.getRawText().toLowerCase().contains(s.taskWord.toLowerCase())) {
            progress(p, PresentTask.SAY, 1);
        }
    }

    private static void onToss(ItemTossEvent e) {
        if (e.getPlayer() instanceof ServerPlayer p) progress(p, PresentTask.TOSS, 1);
    }

    private static void onPickup(ItemEntityPickupEvent.Pre e) {
        if (e.getPlayer() instanceof ServerPlayer p && get(p).future()) {
            e.setCanPickup(TriState.FALSE);
        }
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            STATES.remove(p.getUUID());
            get(p).needSync = true;
        }
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            FateState s = STATES.remove(p.getUUID());
            if (s != null) save(p, s);
            FateUpgrader.forget(p.getUUID());
        }
    }

    private static void onClone(PlayerEvent.Clone e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            FateState s = STATES.get(p.getUUID());
            if (s != null) {
                s.hasLast = false;
                s.needSync = true;
                save(p, s);
            }
        }
    }
}
