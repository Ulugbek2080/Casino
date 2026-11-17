package com.fatewheel.client;

import com.fatewheel.ClientFlags;
import com.fatewheel.Stake;
import com.fatewheel.network.FateSelectPayload;
import com.fatewheel.upgrader.FateScreenAccess;
import com.fatewheel.upgrader.Fortune;
import com.fatewheel.upgrader.Mode;
import com.fatewheel.upgrader.StakeSlots;
import net.execheinz.upgrader.client.StyledButton;
import net.execheinz.upgrader.client.WheelRenderer;
import net.execheinz.upgrader.menu.UpgraderMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.client.gui.screens.Screen;
import net.execheinz.upgrader.client.ClientCatalog;
import net.execheinz.upgrader.network.ServerboundSetTargetPacket;
import net.execheinz.upgrader.network.UpgraderNetwork;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Всё, что дорисовывается в экране Апгрейдера:
 * <ul>
 *     <li>кнопка режима внизу каждой карточки (ставка слева, цель справа);</li>
 *     <li>выбор из трёх: Предметы / Время / Органы;</li>
 *     <li>панель «Время» — три строки, панель «Органы» — силуэт игрока с кликабельными частями.</li>
 * </ul>
 * Координаты — как в UpgraderScreen (окно 288x240, карточки 82 шириной, y 22..116).
 */
public final class FateScreenUi {
    private static final int CARD_L_X = 8;
    private static final int CARD_R_X = 198;
    private static final int CARD_W = 82;
    private static final int CARD_Y1 = 22;
    private static final int CARD_Y2 = 116;
    private static final int PILL_Y = 103;
    private static final int PILL_H = 11;

    // цвета Upgrader
    private static final int CARD_BORDER = -13749180;
    private static final int CARD_TOP = -14802128;
    private static final int CARD_BOTTOM = -15328732;
    private static final int TEXT = -1643790;
    private static final int TEXT_DIM = -7892829;
    private static final int GOLD = -865972;
    private static final int GREEN = 0xFF57D16A;
    private static final int RED = 0xFFE05252;
    private static final int LOST = 0xFF26282E;
    private static final int UNAVAILABLE = 0xFF3A3F4A;

    /** Какая карточка сейчас показывает выбор режима: -1 — никакая, 0 — ставка, 1 — цель. */
    private static int chooser = -1;
    /** Открытая справа категория выигрыша (только на клиенте; на сервере это обычная цель-предмет). */
    private static Category category = null;
    private static int page = 0;

    /** Варианты выигрыша справа: все предметы, категории, время, органы. */
    private record Choice(String title, ItemStack icon, Mode mode, Category cat) {
    }

    private static List<Choice> rightChoices;

    private FateScreenUi() {
    }

    public static void onInit() {
        chooser = -1; // выбранный режим и категория остаются с прошлого раза
        Category.resetCache();
    }

    private static List<Choice> rightChoices() {
        if (rightChoices == null) {
            List<Choice> c = new ArrayList<>();
            c.add(new Choice("Колесо судьбы", new ItemStack(Items.FIREWORK_ROCKET), Mode.FORTUNE, null));
            c.add(new Choice("Все предметы", new ItemStack(Items.CHEST), Mode.ITEMS, null));
            for (Category cat : Category.values()) c.add(new Choice(cat.title, cat.icon(), Mode.ITEMS, cat));
            c.add(new Choice("Время", new ItemStack(Items.CLOCK), Mode.TIME, null));
            c.add(new Choice("Органы", new ItemStack(Items.ROTTEN_FLESH), Mode.ORGANS, null));
            rightChoices = c;
        }
        return rightChoices;
    }

    private static boolean current(Choice c) {
        if (c.mode != mode(1)) return false;
        return c.mode != Mode.ITEMS || c.cat == category;
    }

    private static boolean categoryOpen() {
        return category != null && mode(1) == Mode.ITEMS;
    }

    // ------------------------------------------------------------------ состояние

    private static Mode mode(int side) {
        return Mode.byId(side == 0 ? ClientFlags.inMode : ClientFlags.outMode);
    }

    private static int mask(int side) {
        return side == 0 ? ClientFlags.inMask : ClientFlags.outMask;
    }

    private static void send(Mode inMode, int inMask, Mode outMode, int outMask) {
        // сразу показываем у себя, сервер всё равно пришлёт проверенный вариант
        ClientFlags.inMode = inMode.ordinal();
        ClientFlags.inMask = inMask;
        ClientFlags.outMode = outMode.ordinal();
        ClientFlags.outMask = outMask;
        ClientPacketDistributor.sendToServer(new FateSelectPayload(inMode.ordinal(), inMask, outMode.ordinal(), outMask));
    }

    private static void setMode(int side, Mode m) {
        if (side == 0) send(m, 0, mode(1), mask(1));
        else send(mode(0), mask(0), m, 0);
    }

    private static void toggle(int side, Stake s) {
        boolean lost = ClientFlags.lost(s);
        if (side == 0 ? unstakeable(s) : !lost) {
            sound(0.6f);
            return;
        }
        int m = mask(side) ^ s.bit;
        if (side == 0) m &= ~Stake.childrenOf(m); // кисть уходит вместе с рукой
        if (side == 0) send(mode(0), m, mode(1), mask(1));
        else send(mode(0), mask(0), mode(1), m);
        sound(1.2f);
    }

    private static void sound(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch, 0.35f));
    }

    // ------------------------------------------------------------------ кнопки Апгрейдера

    /** «ПРОКАЧАТЬ» активна и когда ставка/цель — время или органы; пресеты x2/30% — только для предметной цели. */
    public static void adjustButtons(UpgraderMenu menu, StyledButton upgrade, List<StyledButton> presets, boolean ready) {
        Mode in = mode(0), out = mode(1);
        if (upgrade != null) {
            // ставка предметами — любая из 5 ячеек; время/органы — только при пустых ячейках
            boolean inOk = in == Mode.ITEMS ? !StakeSlots.empty(menu) : mask(0) != 0 && StakeSlots.empty(menu);
            boolean outOk = out == Mode.ITEMS ? menu.getTarget() != null : out == Mode.FORTUNE || mask(1) != 0;
            upgrade.active = ready && inOk && outOk && chooser < 0 && !categoryOpen();
        }
        if (out != Mode.ITEMS) for (StyledButton b : presets) b.active = false;
    }

    // ------------------------------------------------------------------ клики

    public static boolean click(AbstractContainerScreen<UpgraderMenu> screen, FateScreenAccess access, double mx, double my, int button) {
        int left = screen.getLeftPos(), top = screen.getTopPos();
        for (int side = 0; side < 2; side++) {
            int x1 = left + (side == 0 ? CARD_L_X : CARD_R_X);
            int x2 = x1 + CARD_W;
            boolean inCard = mx >= x1 && mx < x2 && my >= top + CARD_Y1 && my < top + CARD_Y2;
            if (!inCard) continue;
            if (access.fatewheel$spinning()) return mode(side) != Mode.ITEMS || chooser == side;

            if (in(mx, my, x1 + 6, top + PILL_Y, CARD_W - 12, PILL_H)) {
                chooser = chooser == side ? -1 : side;
                sound(1.0f);
                return true;
            }
            if (chooser == side && side == 1) {
                List<Choice> choices = rightChoices();
                for (int i = 0; i < choices.size(); i++) {
                    if (!inChoice(x1, top, i, mx, my)) continue;
                    Choice c = choices.get(i);
                    if (c.mode != mode(1)) setMode(1, c.mode);
                    category = c.cat;
                    page = 0;
                    chooser = -1;
                    sound(1.2f);
                    return true;
                }
                return true;
            }
            if (side == 1 && categoryOpen()) {
                clickCategory(screen, x1, top, mx, my);
                return true;
            }
            if (chooser == side) {
                for (Mode m : Mode.values()) {
                    if (m == Mode.FORTUNE) continue; // колесо — только справа
                    if (in(mx, my, x1 + 8, chooserY(top, m), CARD_W - 16, 20)) {
                        if (m != mode(side)) setMode(side, m);
                        chooser = -1;
                        sound(1.2f);
                        return true;
                    }
                }
                return true; // клик мимо кнопок внутри карточки — ничего не делаем
            }
            Mode m = mode(side);
            if (m == Mode.ITEMS) return false; // дальше — обычный Апгрейдер (ячейка, каталог, количество)
            if (m == Mode.FORTUNE) return true; // в таблице колеса кликать нечего
            Stake hit = m == Mode.TIME ? timeAt(x1, top, mx, my) : bodyAt(x1, top, mx, my);
            if (hit != null) toggle(side, hit);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ отрисовка

    public static void render(AbstractContainerScreen<UpgraderMenu> screen, FateScreenAccess access, GuiGraphicsExtractor g, int mx, int my) {
        Font font = Minecraft.getInstance().font;
        int left = screen.getLeftPos(), top = screen.getTopPos();
        g.nextStratum(); // поверх ячеек и предметов Апгрейдера
        if (access.fatewheel$spinning()) category = null;
        List<Component> tooltip = null;
        for (int side = 0; side < 2; side++) {
            int x1 = left + (side == 0 ? CARD_L_X : CARD_R_X);
            Mode m = mode(side);
            if (chooser == side) {
                cardBg(g, x1, top);
                if (side == 1) renderRightChooser(g, font, x1, top, mx, my);
                else renderChooser(g, font, x1, top, side, mx, my);
            } else if (side == 1 && m == Mode.FORTUNE) {
                cardBg(g, x1, top);
                List<Component> tip = renderFortune(g, font, access, x1, top, mx, my);
                if (tip != null) tooltip = tip;
            } else if (side == 1 && categoryOpen()) {
                cardBg(g, x1, top);
                List<Component> tip = renderCategory(g, font, screen, x1, top, mx, my);
                if (tip != null) tooltip = tip;
            } else if (m != Mode.ITEMS) {
                cardBg(g, x1, top);
                List<Component> tip = m == Mode.TIME
                        ? renderTime(g, font, x1, top, side, mx, my)
                        : renderBody(g, font, x1, top, side, mx, my);
                if (tip != null) tooltip = tip;
                renderFooter(g, font, screen, access, x1, top, side);
            }
            renderPill(g, font, x1, top, side, m, mx, my);
        }
        if (mode(1) == Mode.FORTUNE) renderFortuneWheel(g, access, left, top);
        if (tooltip != null) g.setTooltipForNextFrame(font, tooltip, Optional.empty(), mx, my);
    }

    private static void cardBg(GuiGraphicsExtractor g, int x1, int top) {
        WheelRenderer.card(g, x1, top + CARD_Y1, x1 + CARD_W, top + CARD_Y2, 5, CARD_BORDER, CARD_TOP, CARD_BOTTOM);
    }

    private static void renderPill(GuiGraphicsExtractor g, Font font, int x1, int top, int side, Mode m, int mx, int my) {
        int px = x1 + 6, py = top + PILL_Y, pw = CARD_W - 12;
        boolean hover = in(mx, my, px, py, pw, PILL_H);
        int border = chooser == side ? GOLD : (hover ? TEXT_DIM : -13024934);
        WheelRenderer.card(g, px, py, px + pw, py + PILL_H, 3, border, -14275526, -14802130);
        boolean cat = side == 1 && categoryOpen();
        String title = cat ? category.title : m == Mode.FORTUNE ? "Колесо" : m.title;
        g.centeredText(font, Component.literal(title + " ▾"), x1 + CARD_W / 2, py + 2, m == Mode.ITEMS && !cat ? TEXT_DIM : GOLD);
    }

    private static int chooserY(int top, Mode m) {
        return top + CARD_Y1 + 18 + m.ordinal() * 25;
    }

    private static void renderChooser(GuiGraphicsExtractor g, Font font, int x1, int top, int side, int mx, int my) {
        int cx = x1 + CARD_W / 2;
        g.centeredText(font, Component.literal(side == 0 ? "Что ставите?" : "Что хотите?"), cx, top + CARD_Y1 + 5, TEXT);
        for (Mode m : Mode.values()) {
            if (m == Mode.FORTUNE) continue;
            int y = chooserY(top, m);
            boolean hover = in(mx, my, x1 + 8, y, CARD_W - 16, 20);
            boolean current = m == mode(side);
            int border = current ? GOLD : (hover ? TEXT : -13024934);
            WheelRenderer.card(g, x1 + 8, y, x1 + CARD_W - 8, y + 20, 4, border,
                    hover ? -13749180 : -14275526, hover ? -14275526 : -14802130);
            g.centeredText(font, Component.literal(m.title), cx, y + 6, current ? GOLD : TEXT);
        }
    }

    // ------------------------------------------------------------------ выбор выигрыша (справа): сетка 3x3

    private static int choiceX(int x1, int i) {
        return x1 + 6 + (i % 3) * 24;
    }

    private static int choiceY(int top, int i) {
        return top + CARD_Y1 + 17 + (i / 3) * 21;
    }

    private static boolean inChoice(int x1, int top, int i, double mx, double my) {
        return in(mx, my, choiceX(x1, i), choiceY(top, i), 22, 19);
    }

    private static void renderRightChooser(GuiGraphicsExtractor g, Font font, int x1, int top, int mx, int my) {
        List<Choice> choices = rightChoices();
        String title = "Что хотите?";
        for (int i = 0; i < choices.size(); i++) {
            Choice c = choices.get(i);
            int x = choiceX(x1, i), y = choiceY(top, i);
            boolean hover = inChoice(x1, top, i, mx, my);
            boolean cur = current(c);
            if (hover) title = c.title;
            int border = cur ? GOLD : (hover ? TEXT : -13024934);
            WheelRenderer.card(g, x, y, x + 22, y + 19, 3, border,
                    hover ? -13749180 : -14275526, hover ? -14275526 : -14802130);
            g.item(c.icon, x + 3, y + 2);
        }
        g.centeredText(font, Component.literal(title), x1 + CARD_W / 2, top + CARD_Y1 + 5, TEXT);
    }

    // ------------------------------------------------------------------ Колесо судьбы

    /** Центр и кольцо колеса Апгрейдера (UpgraderScreen: WHEEL_CX/CY, RING_IN/OUT). */
    private static final float WHEEL_CX = 144f, WHEEL_CY = 70f, RING_IN = 30f, RING_OUT = 41f;

    /** Секторы поверх кольца Апгрейдера; стрелка снаружи кольца, её не закрываем. */
    /** Показываем результат последнего вращения (после остановки колеса, несколько секунд). */
    private static Fortune.Tier shownResult(FateScreenAccess access) {
        if (access.fatewheel$spinning() || System.currentTimeMillis() > ClientFlags.fortuneUntil) return null;
        int t = ClientFlags.fortuneTier;
        return t >= 0 && t < Fortune.Tier.values().length ? Fortune.Tier.values()[t] : null;
    }

    private static void renderFortuneWheel(GuiGraphicsExtractor g, FateScreenAccess access, int left, int top) {
        float cx = left + WHEEL_CX, cy = top + WHEEL_CY;
        Fortune.Tier result = shownResult(access);
        WheelRenderer.arc(g, cx, cy, RING_IN - 0.5f, RING_OUT + 0.5f, 0f, 360f, 0xFF15171C); // фон — видны щели между секторами
        for (int i = 0; i < Fortune.LAYOUT.length; i++) {
            float from = Fortune.FROM[i], to = Fortune.TO[i];
            float gap = Math.min(0.6f, (to - from) * 0.15f);
            // arc(..., начало, длина дуги) — второй угол это размах, а не конец
            int color = Fortune.LAYOUT[i].color;
            if (result != null && Fortune.LAYOUT[i] != result) color = dim(color); // выпавший сектор горит, остальные гаснут
            WheelRenderer.arc(g, cx, cy, RING_IN, RING_OUT, from + gap, to - from - gap * 2, color);
        }
    }

    private static int fortuneRowY(int top, int i) {
        return top + CARD_Y1 + 17 + i * 10;
    }

    private static List<Component> renderFortune(GuiGraphicsExtractor g, Font font, FateScreenAccess access,
                                                 int x1, int top, int mx, int my) {
        int cx = x1 + CARD_W / 2;
        Fortune.Tier result = shownResult(access);
        if (result != null) return renderFortuneResult(g, font, x1, top, result, mx, my);
        long stake = access.fatewheel$inputValue();
        List<Component> tip = null;
        boolean titleHover = in(mx, my, x1 + 4, top + CARD_Y1 + 3, CARD_W - 8, 11);
        g.centeredText(font, Component.literal("Колесо судьбы"), cx, top + CARD_Y1 + 5, GOLD);
        if (titleHover) {
            tip = List.of(Component.literal("Колесо судьбы").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                    Component.literal("«Ничего» — ставка сгорает.").withStyle(ChatFormatting.RED),
                    Component.literal("Любой другой сектор — ставка остаётся").withStyle(ChatFormatting.GREEN),
                    Component.literal("и вы получаете приз по его цене:").withStyle(ChatFormatting.GREEN),
                    Component.literal("предмет или возврат потерянного.").withStyle(ChatFormatting.GRAY),
                    Component.literal("2 из 3 вращений что-то приносят.").withStyle(ChatFormatting.DARK_GRAY));
        }
        Fortune.Tier[] tiers = Fortune.Tier.values();
        for (int i = 0; i < tiers.length; i++) {
            Fortune.Tier t = tiers[i];
            int y = fortuneRowY(top, i);
            boolean hover = in(mx, my, x1 + 4, y - 1, CARD_W - 8, 10);
            if (hover) g.fill(x1 + 4, y - 1, x1 + CARD_W - 4, y + 9, 0x30FFFFFF);
            g.fill(x1 + 7, y + 1, x1 + 13, y + 7, t.color);
            g.text(font, Component.literal(t.title), x1 + 16, y, hover ? TEXT : TEXT_DIM, false);
            String pct = t.percent + "%";
            g.text(font, Component.literal(pct), x1 + CARD_W - 7 - font.width(pct), y, hover ? TEXT : TEXT_DIM, false);
            if (hover) {
                List<Component> lines = new ArrayList<>();
                lines.add(Component.literal(t.title + " — " + t.percent + "%").withStyle(ChatFormatting.BOLD));
                if (t == Fortune.Tier.NOTHING) {
                    lines.add(Component.literal("Ставка сгорает, приза нет.").withStyle(ChatFormatting.RED));
                } else {
                    lines.add(Component.literal("Ставка остаётся + приз ≈ x" + t.multiplier).withStyle(ChatFormatting.GREEN));
                    if (stake > 0) lines.add(Component.literal("Сейчас: ≈ " + format(Math.round(stake * t.multiplier)))
                            .withStyle(ChatFormatting.YELLOW));
                    if (t == Fortune.Tier.JACKPOT) lines.add(Component.literal("Сначала ищется уникальный или особый предмет.")
                            .withStyle(ChatFormatting.LIGHT_PURPLE));
                }
                tip = lines;
            }
        }
        return tip;
    }

    /** Итог вращения во всю карточку: сектор крупно, приз, что со ставкой. */
    private static List<Component> renderFortuneResult(GuiGraphicsExtractor g, Font font, int x1, int top,
                                                       Fortune.Tier tier, int mx, int my) {
        int cx = x1 + CARD_W / 2, y0 = top + CARD_Y1;
        boolean lost = tier == Fortune.Tier.NOTHING;
        // мигающая рамка в цвет сектора
        boolean blink = (System.currentTimeMillis() / 250) % 2 == 0;
        outline(g, x1 + 2, y0 + 2, CARD_W - 4, CARD_Y2 - CARD_Y1 - 4, blink ? tier.color : dim(tier.color));

        g.pose().pushMatrix();
        g.pose().translate(cx, y0 + 8);
        g.pose().scale(1.5f, 1.5f);
        g.centeredText(font, Component.literal(lost ? "Ничего" : tier.title + "!"), 0, 0, lost ? TEXT_DIM : tier.color);
        g.pose().popMatrix();
        if (!lost) g.centeredText(font, Component.literal("x" + tier.multiplier), cx, y0 + 23, TEXT_DIM);

        List<Component> tip = null;
        ItemStack prize = ClientFlags.fortunePrize;
        if (lost) {
            g.centeredText(font, Component.literal("Ставка сгорела"), cx, y0 + 44, RED);
        } else if (!prize.isEmpty()) {
            int ix = cx - 8, iy = y0 + 35;
            WheelRenderer.card(g, ix - 3, iy - 3, ix + 19, iy + 19, 3, tier.color, -14275526, -14802130);
            g.item(prize, ix, iy);
            g.itemDecorations(font, prize, ix, iy);
            String name = prize.getHoverName().getString() + (prize.getCount() > 1 ? " x" + prize.getCount() : "");
            g.centeredText(font, Component.literal(font.plainSubstrByWidth(name, CARD_W - 8)), cx, y0 + 57, TEXT);
            if (in(mx, my, ix - 3, iy - 3, 22, 22)) tip = Screen.getTooltipFromItem(Minecraft.getInstance(), prize);
        } else if (ClientFlags.fortuneRestore != 0) {
            g.centeredText(font, Component.literal("Возвращено:"), cx, y0 + 38, GREEN);
            List<String> titles = Stake.titles(ClientFlags.fortuneRestore);
            g.centeredText(font, Component.literal(font.plainSubstrByWidth(String.join(", ", titles), CARD_W - 8)), cx, y0 + 50, TEXT);
        } else {
            g.centeredText(font, Component.literal("Приз не нашёлся"), cx, y0 + 44, TEXT_DIM);
        }
        if (!lost) g.centeredText(font, Component.literal("Ставка цела"), cx, y0 + 70, GREEN);
        return tip;
    }

    // ------------------------------------------------------------------ категория: сетка 4x4 предметов

    private static final int CELL = 18;

    private static int cellX(int x1, int i) {
        return x1 + 5 + (i % 4) * CELL;
    }

    private static int cellY(int top, int i) {
        return top + CARD_Y1 + 5 + (i / 4) * CELL;
    }

    /** Сколько предметов на странице: если не влезают все 16, последняя клетка — «дальше». */
    private static int perPage(int n) {
        return n > 16 ? 15 : 16;
    }

    private static int pages(int n) {
        return Math.max(1, (n + perPage(n) - 1) / perPage(n));
    }

    private static void clickCategory(AbstractContainerScreen<UpgraderMenu> screen, int x1, int top, double mx, double my) {
        List<Item> items = category.items();
        int per = perPage(items.size()), pages = pages(items.size());
        page = Math.floorMod(page, pages);
        for (int i = 0; i < 16; i++) {
            if (!in(mx, my, cellX(x1, i), cellY(top, i), CELL, CELL)) continue;
            if (pages > 1 && i == 15) {
                page = (page + 1) % pages;
                sound(1.0f);
                return;
            }
            int idx = page * per + i;
            if (idx >= items.size()) return;
            Item item = items.get(idx);
            screen.getMenu().setTargetClient(item);
            UpgraderNetwork.toServer(ServerboundSetTargetPacket.of(item));
            category = null;
            sound(1.2f);
            return;
        }
    }

    /** Колесо мыши листает страницы категории. */
    public static boolean scroll(AbstractContainerScreen<UpgraderMenu> screen, double mx, double my, double dy) {
        if (!categoryOpen() || chooser == 1) return false;
        int x1 = screen.getLeftPos() + CARD_R_X, top = screen.getTopPos();
        if (!(mx >= x1 && mx < x1 + CARD_W && my >= top + CARD_Y1 && my < top + CARD_Y2)) return false;
        int pages = pages(category.items().size());
        if (pages > 1 && dy != 0) page = Math.floorMod(page + (dy < 0 ? 1 : -1), pages);
        return true;
    }

    private static List<Component> renderCategory(GuiGraphicsExtractor g, Font font, AbstractContainerScreen<UpgraderMenu> screen,
                                                  int x1, int top, int mx, int my) {
        List<Item> items = category.items();
        int per = perPage(items.size()), pages = pages(items.size());
        page = Math.floorMod(page, pages);
        Item target = screen.getMenu().getTarget();
        List<Component> tip = null;
        for (int i = 0; i < 16; i++) {
            int x = cellX(x1, i), y = cellY(top, i);
            boolean hover = in(mx, my, x, y, CELL, CELL);
            if (pages > 1 && i == 15) {
                WheelRenderer.card(g, x + 1, y + 1, x + CELL - 1, y + CELL - 1, 3, hover ? GOLD : -13024934, -14275526, -14802130);
                g.centeredText(font, Component.literal("▶"), x + CELL / 2, y + 5, hover ? GOLD : TEXT_DIM);
                if (hover) tip = List.of(Component.literal("Дальше (" + (page + 1) + "/" + pages + ")").withStyle(ChatFormatting.GRAY),
                        Component.literal("Можно листать колесом мыши").withStyle(ChatFormatting.DARK_GRAY));
                continue;
            }
            int idx = page * per + i;
            if (idx >= items.size()) break;
            Item item = items.get(idx);
            ItemStack stack = new ItemStack(item);
            int border = item == target ? GOLD : (hover ? TEXT : -13024934);
            WheelRenderer.card(g, x + 1, y + 1, x + CELL - 1, y + CELL - 1, 2, border, hover ? -13749180 : -14275526, -14802130);
            g.item(stack, x + 1, y + 1);
            if (hover) {
                tip = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), stack));
                if (ClientCatalog.isReady()) {
                    tip.add(Component.literal("Ценность: " + format(Math.round(ClientCatalog.value(item)))).withStyle(ChatFormatting.YELLOW));
                }
                tip.add(Component.literal("Клик — выбрать целью").withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        return tip;
    }

    private static void renderFooter(GuiGraphicsExtractor g, Font font, AbstractContainerScreen<UpgraderMenu> screen,
                                     FateScreenAccess access, int x1, int top, int side) {
        int cx = x1 + CARD_W / 2;
        int y = top + 92;
        if (side == 0 && !StakeSlots.empty(screen.getMenu())) {
            g.centeredText(font, Component.literal("Уберите предметы"), cx, y, RED);
        } else if (mask(side) == 0) {
            g.centeredText(font, Component.literal(side == 0 ? "Отметьте ставку" : "Что вернуть?"), cx, y, TEXT_DIM);
        } else {
            long v = side == 0 ? access.fatewheel$inputValue() : access.fatewheel$targetValue();
            g.centeredText(font, Component.translatable("gui.upgrader.value", format(v)), cx, y, TEXT_DIM);
        }
    }

    // ------------------------------------------------------------------ панель «Время»

    private static final Stake[] TIME = {Stake.PAST, Stake.PRESENT, Stake.FUTURE};

    private static int timeRowY(int top, int i) {
        return top + CARD_Y1 + 6 + i * 21;
    }

    private static Stake timeAt(int x1, int top, double mx, double my) {
        for (int i = 0; i < TIME.length; i++) {
            if (in(mx, my, x1 + 5, timeRowY(top, i), CARD_W - 10, 19)) return TIME[i];
        }
        return null;
    }

    private static List<Component> renderTime(GuiGraphicsExtractor g, Font font, int x1, int top, int side, int mx, int my) {
        List<Component> tip = null;
        for (int i = 0; i < TIME.length; i++) {
            Stake s = TIME[i];
            int y = timeRowY(top, i);
            boolean hover = in(mx, my, x1 + 5, y, CARD_W - 10, 19);
            int state = state(side, s);
            int fill = switch (state) {
                case SELECTED -> side == 0 ? 0xFF6B5420 : 0xFF245A31;
                case UNAVAILABLE_STATE -> LOST;
                default -> hover ? -13749180 : -14275526;
            };
            int border = switch (state) {
                case SELECTED -> side == 0 ? GOLD : GREEN;
                case UNAVAILABLE_STATE -> -13024934;
                default -> hover ? TEXT_DIM : -13024934;
            };
            WheelRenderer.card(g, x1 + 5, y, x1 + CARD_W - 5, y + 19, 3, border, fill, fill);
            g.item(new ItemStack(s.icon()), x1 + 7, y + 1);
            int color = state == UNAVAILABLE_STATE ? 0xFF6A6F7A : (state == SELECTED ? TEXT : TEXT_DIM);
            g.text(font, Component.literal(s.title), x1 + 26, y + 6, color, false);
            if (hover) tip = tooltip(side, s);
        }
        return tip;
    }

    // ------------------------------------------------------------------ панель «Органы»

    /** Прямоугольник части тела относительно карточки: x от центра, y от верха силуэта. */
    private record Part(Stake stake, int dx, int dy, int w, int h, int color) {
    }

    private static final int SKIN = 0xFFC69C7B;
    private static final int SKIN_DARK = 0xFFAE8264;
    private static final int SHOES = 0xFF5A4030;
    private static final int SHIRT = 0xFF3C7DB3;
    private static final int PANTS = 0xFF3D3F86;

    /** Лево — слева на экране (как и затемнение от потери глаза). Мелкие части — первыми, они поверх. */
    private static final Part[] PARTS = {
            new Part(Stake.LEFT_EAR, -11, 6, 3, 5, SKIN_DARK),
            new Part(Stake.RIGHT_EAR, 8, 6, 3, 5, SKIN_DARK),
            new Part(Stake.LEFT_EYE, -6, 5, 5, 4, 0xFFF2F2F2),
            new Part(Stake.RIGHT_EYE, 1, 5, 5, 4, 0xFFF2F2F2),
            new Part(Stake.TONGUE, -3, 11, 6, 3, 0xFFD45A6A),
            new Part(Stake.HEART, -6, 21, 6, 6, 0xFFC0303A),
            new Part(Stake.LEFT_HAND, -15, 41, 6, 5, SKIN_DARK),
            new Part(Stake.RIGHT_HAND, 9, 41, 6, 5, SKIN_DARK),
            new Part(Stake.LEFT_FOOT, -9, 65, 8, 4, SHOES),
            new Part(Stake.RIGHT_FOOT, 1, 65, 8, 4, SHOES),
            new Part(Stake.LEFT_ARM, -15, 17, 6, 24, SKIN),
            new Part(Stake.RIGHT_ARM, 9, 17, 6, 24, SKIN),
            new Part(Stake.LEFT_LEG, -8, 42, 7, 23, PANTS),
            new Part(Stake.RIGHT_LEG, 1, 42, 7, 23, PANTS),
    };

    private static int bodyTop(int top) {
        return top + CARD_Y1 + 4;
    }

    private static Stake bodyAt(int x1, int top, double mx, double my) {
        int cx = x1 + CARD_W / 2, y0 = bodyTop(top);
        for (Part p : PARTS) {
            if (in(mx, my, cx + p.dx - 1, y0 + p.dy - 1, p.w + 2, p.h + 2)) return p.stake;
        }
        return null;
    }

    private static List<Component> renderBody(GuiGraphicsExtractor g, Font font, int x1, int top, int side, int mx, int my) {
        int cx = x1 + CARD_W / 2, y0 = bodyTop(top);
        // голова и туловище — некликабельная основа
        g.fill(cx - 8, y0, cx + 8, y0 + 16, SKIN);
        g.fill(cx - 8, y0, cx + 8, y0 + 3, 0xFF4A3426); // волосы
        g.fill(cx - 8, y0 + 17, cx + 8, y0 + 41, SHIRT);

        Stake hovered = bodyAt(x1, top, mx, my);
        // сначала крупные части, потом мелкие поверх
        for (int i = PARTS.length - 1; i >= 0; i--) {
            Part p = PARTS[i];
            int x = cx + p.dx, y = y0 + p.dy;
            int state = state(side, p.stake);
            boolean hover = p.stake == hovered;
            int fill = switch (state) {
                case SELECTED -> side == 0 ? GOLD : GREEN;
                case UNAVAILABLE_STATE -> side == 0 ? LOST : p.color;
                default -> side == 1 ? LOST : p.color; // справа доступно только потерянное
            };
            if (side == 1 && state == UNAVAILABLE_STATE) fill = dim(p.color);
            g.fill(x, y, x + p.w, y + p.h, fill);
            if (side == 0 && state == UNAVAILABLE_STATE || side == 1 && state == AVAILABLE) {
                cross(g, x, y, p.w, p.h); // потеряно
            }
            if (hover) outline(g, x - 1, y - 1, p.w + 2, p.h + 2, TEXT);
        }
        return hovered == null ? null : tooltip(side, hovered);
    }

    private static void cross(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        int n = Math.max(w, h);
        for (int i = 0; i < n; i++) {
            int px = x + i * w / n, py = y + i * h / n;
            g.fill(px, py, px + 1, py + 1, RED);
            int qx = x + w - 1 - i * w / n;
            g.fill(qx, py, qx + 1, py + 1, RED);
        }
    }

    private static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static int dim(int argb) {
        int r = (argb >> 16 & 0xFF) / 3, gr = (argb >> 8 & 0xFF) / 3, b = (argb & 0xFF) / 3;
        return 0xFF000000 | r << 16 | gr << 8 | b;
    }

    // ------------------------------------------------------------------ общее

    private static final int AVAILABLE = 0;
    private static final int SELECTED = 1;
    private static final int UNAVAILABLE_STATE = 2;

    /** Слева можно ставить то, что есть; справа можно выбрать то, что потеряно. */
    private static int state(int side, Stake s) {
        if ((mask(side) & s.bit) != 0) return SELECTED;
        boolean lost = ClientFlags.lost(s);
        return (side == 0 ? unstakeable(s) : !lost) ? UNAVAILABLE_STATE : AVAILABLE;
    }

    /** Потеряно само, держалось на потерянном (кисть без руки) или его рука/нога уже в ставке. */
    private static boolean unstakeable(Stake s) {
        return ((Stake.unstakeable(ClientFlags.lostMask) | Stake.childrenOf(ClientFlags.inMask)) & s.bit) != 0;
    }

    private static List<Component> tooltip(int side, Stake s) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(s.title).withStyle(s.color, ChatFormatting.BOLD));
        boolean lost = ClientFlags.lost(s);
        for (String d : s.description) lines.add(Component.literal(d).withStyle(ChatFormatting.GRAY));
        if (side == 0) {
            if (!lost && unstakeable(s) && ClientFlags.lost(s.parent())) {
                lines.add(Component.literal("Нет «" + s.parent().title + "» — ставить нечего.").withStyle(ChatFormatting.DARK_GRAY));
            } else if (!lost && unstakeable(s)) {
                lines.add(Component.literal("«" + s.parent().title + "» уже в ставке —").withStyle(ChatFormatting.DARK_GRAY));
                lines.add(Component.literal("эта часть уходит вместе с ней.").withStyle(ChatFormatting.DARK_GRAY));
            } else if (lost) {
                lines.add(Component.literal("Уже потеряно — ставить нечего.").withStyle(ChatFormatting.DARK_GRAY));
                lines.add(Component.literal("Отыграйте справа.").withStyle(ChatFormatting.DARK_GRAY));
            } else {
                lines.add(Component.literal("Ценность: " + format(s.value())).withStyle(ChatFormatting.YELLOW));
                lines.add(Component.literal("Проигрыш — навсегда.").withStyle(ChatFormatting.DARK_RED));
                lines.add(Component.literal("При выигрыше остаётся при вас.").withStyle(ChatFormatting.GREEN));
            }
        } else {
            if (lost) {
                lines.add(Component.literal("Вернуть стоит: " + format(s.restoreValue())).withStyle(ChatFormatting.YELLOW));
            } else {
                lines.add(Component.literal("У вас это есть.").withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        return lines;
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static String format(long value) {
        if (value >= 1_000_000L) return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0);
        return value >= 10_000L ? String.format(Locale.ROOT, "%.1fK", value / 1000.0) : Long.toString(value);
    }
}
