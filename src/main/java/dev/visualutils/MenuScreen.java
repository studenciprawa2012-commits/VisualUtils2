package dev.visualutils;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/**
 * Menu otwierane klawiszem . - wlasny, ladniejszy wyglad: zakladki, karty modulow,
 * plynne przelaczniki i wybor koloru motywu. Klikanie obslugujemy na podstawie
 * pozycji myszy w render(), wiec nie zalezy od zmian w obsludze zdarzen.
 */
public class MenuScreen extends Screen {

    private static final int PW = 340;
    private static final int HEAD = 36;
    private static final int TABS = 26;
    private static final int FOOT = 40;

    private Module.Category tab = Module.Category.SWIAT;
    private final EnumMap<Module, Float> anim = new EnumMap<>(Module.class);
    private boolean mouseWasDown = true; // zeby klik otwierajacy menu nie przelaczyl modulu
    private long lastNanos = System.nanoTime();

    public MenuScreen() {
        super(Component.literal("VisualUtils"));
        for (Module m : Module.values()) {
            anim.put(m, Config.isOn(m) ? 1f : 0f);
        }
    }

    private static List<Module> modulesOf(Module.Category c) {
        List<Module> list = new ArrayList<>();
        for (Module m : Module.values()) {
            if (m.category() == c) list.add(m);
        }
        return list;
    }

    private static int maxRows() {
        int max = 1;
        for (Module.Category c : Module.Category.values()) {
            max = Math.max(max, modulesOf(c).size());
        }
        return max;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastNanos) / 1_000_000_000f);
        lastNanos = now;

        boolean down = Minecraft.getInstance().mouseHandler.isLeftPressed();
        boolean click = down && !mouseWasDown;
        mouseWasDown = down;

        int accent = Theme.accent();
        int rows = maxRows();
        int rowH = Math.max(22, Math.min(28, (this.height - 16 - HEAD - TABS - FOOT) / rows));
        int ph = HEAD + TABS + rows * rowH + FOOT;
        int px = (this.width - PW) / 2;
        int py = (this.height - ph) / 2;

        // tlo panelu z delikatna poswiata w kolorze motywu
        Draw.roundRect(g, px - 1, py - 1, PW + 2, ph + 2, 7, Theme.withAlpha(accent, 0x70));
        Draw.roundRect(g, px, py, PW, ph, 6, 0xF2121218);

        // naglowek
        g.drawString(this.font, Component.literal("VisualUtils").withStyle(ChatFormatting.BOLD),
                px + 14, py + 11, accent, false);
        String menuHint = "Menu: [" + VisualUtilsClient.menuKeyName() + "]";
        g.drawString(this.font, menuHint, px + PW - 14 - this.font.width(menuHint), py + 11, 0xFF8A8A9A, false);
        g.fill(px + 12, py + HEAD - 8, px + PW - 12, py + HEAD - 7, Theme.withAlpha(accent, 0x90));

        // zakladki
        Module.Category[] cats = Module.Category.values();
        int gap = 6;
        int tabW = (PW - 24 - gap * (cats.length - 1)) / cats.length;
        int tabY = py + HEAD;
        for (int i = 0; i < cats.length; i++) {
            int tx = px + 12 + i * (tabW + gap);
            boolean active = cats[i] == tab;
            boolean hover = Draw.inside(mouseX, mouseY, tx, tabY, tabW, 18);
            int bg = active ? accent : (hover ? 0x45FFFFFF : 0x28FFFFFF);
            Draw.roundRect(g, tx, tabY, tabW, 18, 4, bg);
            g.drawCenteredString(this.font, cats[i].label(), tx + tabW / 2, tabY + 5,
                    active ? 0xFFFFFFFF : 0xFFC8C8D4);
            if (click && hover) {
                tab = cats[i];
            }
        }

        // wiersze z modulami
        List<Module> list = modulesOf(tab);
        int ry = py + HEAD + TABS;
        for (int i = 0; i < list.size(); i++) {
            Module m = list.get(i);
            int y = ry + i * rowH;
            int rx = px + 8;
            int rw = PW - 16;
            int rh = rowH - 2;
            boolean hover = Draw.inside(mouseX, mouseY, rx, y, rw, rh);

            Draw.roundRect(g, rx, y, rw, rh, 4, hover ? 0x32FFFFFF : 0x16FFFFFF);

            boolean on = Config.isOn(m);
            float a = anim.get(m);
            a += ((on ? 1f : 0f) - a) * Math.min(1f, dt * 16f);
            anim.put(m, a);

            // pasek akcentu po lewej, gdy modul wlaczony
            if (on) {
                g.fill(rx, y + 3, rx + 2, y + rh - 3, accent);
            }

            int textY = y + (rh - 17) / 2;
            g.drawString(this.font, m.label(), rx + 10, textY, on ? 0xFFFFFFFF : 0xFFD0D0DC, false);
            g.drawString(this.font, m.description(), rx + 10, textY + 10, 0xFF808090, false);

            // przelacznik
            int swW = 30;
            int swH = 14;
            int swX = rx + rw - 10 - swW;
            int swY = y + (rh - swH) / 2;
            Draw.roundRect(g, swX, swY, swW, swH, 7, Theme.lerp(0xFF3A3A4A, accent, a));
            Draw.roundRect(g, swX + 2 + Math.round(a * 16f), swY + 2, 10, 10, 5, 0xFFFFFFFF);

            // przypisany klawisz
            String key = VisualUtilsClient.keyHint(m);
            if (key != null) {
                String label = "[" + key + "]";
                g.drawString(this.font, label, swX - 8 - this.font.width(label),
                        y + (rh - 8) / 2, Theme.withAlpha(accent, 0xFF), false);
            }

            if (click && hover) {
                Config.toggle(m);
                Config.save();
            }
        }

        // stopka: motyw + podpowiedz
        int fy = ry + rows * rowH + 8;
        g.drawString(this.font, "Motyw", px + 14, fy + 3, 0xFF8A8A9A, false);
        for (int i = 0; i < Theme.ACCENTS.length; i++) {
            int sx = px + 58 + i * 20;
            boolean selected = i == Math.floorMod(Config.getTheme(), Theme.ACCENTS.length);
            boolean hover = Draw.inside(mouseX, mouseY, sx - 2, fy - 2, 16, 16);
            if (selected) {
                Draw.roundRect(g, sx - 2, fy - 2, 16, 16, 5, 0xFFFFFFFF);
            } else if (hover) {
                Draw.roundRect(g, sx - 2, fy - 2, 16, 16, 5, 0x80FFFFFF);
            }
            Draw.roundRect(g, sx, fy, 12, 12, 4, Theme.ACCENTS[i]);
            if (click && hover) {
                Config.setTheme(i);
                Config.save();
            }
        }
        g.drawString(this.font, "Klawisze modułów: Opcje > Sterowanie > VisualUtils", px + 14, fy + 20,
                0xFF6E6E7E, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Config.save();
        super.onClose();
    }
}
