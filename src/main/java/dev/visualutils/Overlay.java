package dev.visualutils;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Rysuje tracery, ramki ESP, nametagi oraz panele HUD (info, FPS, klawisze, pancerz...).
 * Pozycje w swiecie sa przeliczane na ekran, wiec nie potrzeba dodatkowych mixinow.
 */
public final class Overlay {

    private static final int COLOR_PLAYER = 0xFFFF5555;
    private static final int COLOR_MONSTER = 0xFFFFAA00;
    private static final int COLOR_MOB = 0xFF55FF55;
    private static final int PANEL_BG = 0x90101018;
    private static final int LABEL_COLOR = 0xFF9A9AAA;
    private static final double MAX_DISTANCE = 128.0;
    private static final int MAX_TARGETS = 40;
    private static final DateTimeFormatter CLOCK_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private Overlay() {}

    /** Dane kamery gracza potrzebne do rzutowania punktow 3D na ekran. */
    private record Cam(Vec3 eye, Vec3 fwd, Vec3 right, Vec3 up, double scale, double cx, double cy) {}

    public static void render(GuiGraphics g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.options.hideGui) return;

        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();

        drawHud(g, mc, player, width, height);

        boolean worldModules = Config.isOn(Module.TRACERS) || Config.isOn(Module.ESP)
                || Config.isOn(Module.NAMETAGS);
        // rzutowanie dziala poprawnie tylko w widoku z pierwszej osoby
        if (!worldModules || !mc.options.getCameraType().isFirstPerson()) return;

        float pt = deltaTracker.getGameTimeDeltaPartialTick(false);

        double yaw = Math.toRadians(player.getViewYRot(pt));
        double pitch = Math.toRadians(player.getViewXRot(pt));
        Vec3 fwd = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        Vec3 right = new Vec3(-Math.cos(yaw), 0.0, -Math.sin(yaw));
        Vec3 up = right.cross(fwd);

        double fov = mc.options.fov().get();
        double scale = (height / 2.0) / Math.tan(Math.toRadians(fov) / 2.0);
        Cam cam = new Cam(player.getEyePosition(pt), fwd, right, up, scale, width / 2.0, height / 2.0);

        boolean mobs = Config.isOn(Module.MOBS);
        int count = 0;

        for (Entity e : mc.level.entitiesForRendering()) {
            if (e == player || !e.isAlive()) continue;
            boolean isPlayer = e instanceof Player;
            if (!isPlayer && !(mobs && e instanceof Mob)) continue;

            double dist = e.distanceTo(player);
            if (dist > MAX_DISTANCE) continue;
            if (++count > MAX_TARGETS) break;

            int color = isPlayer ? COLOR_PLAYER : (e instanceof Monster ? COLOR_MONSTER : COLOR_MOB);
            drawTarget(g, mc, cam, e, pt, dist, color, width, height);
        }
    }

    // ---------- HUD ----------

    private static void drawHud(GuiGraphics g, Minecraft mc, LocalPlayer player, int width, int height) {
        int accent = Theme.accent();
        int y = 4;

        if (Config.isOn(Module.INFO)) {
            List<String> lines = new ArrayList<>();
            lines.add(String.format(Locale.ROOT, "XYZ  %.1f / %.1f / %.1f",
                    player.getX(), player.getY(), player.getZ()));
            lines.add("Kierunek  " + directionName(player.getDirection().getSerializedName()));
            lines.add(String.format(Locale.ROOT, "Prędkość  %.1f bl/s", Stats.speed()));
            y += drawBox(g, mc, 4, y, lines, accent) + 4;
        }

        List<String> stats = new ArrayList<>();
        if (Config.isOn(Module.STATS)) {
            stats.add("FPS  " + mc.getFps());
            stats.add("Ping  " + pingText(mc, player));
        }
        if (Config.isOn(Module.CPS)) {
            stats.add("CPS  " + Stats.leftCps() + " | " + Stats.rightCps());
        }
        if (Config.isOn(Module.CLOCK)) {
            stats.add("Czas  " + LocalTime.now().format(CLOCK_FORMAT));
        }
        if (!stats.isEmpty()) {
            drawBox(g, mc, 4, y, stats, accent);
        }

        if (Config.isOn(Module.KEYSTROKES)) {
            drawKeystrokes(g, mc, 6, height - 86, accent);
        }
        if (Config.isOn(Module.ARMOR)) {
            drawArmor(g, mc, player, width, height, accent);
        }
        if (Config.isOn(Module.POTIONS)) {
            drawPotions(g, mc, player, width, accent);
        }
    }

    private static String directionName(String serialized) {
        return switch (serialized) {
            case "north" -> "Północ (-Z)";
            case "south" -> "Południe (+Z)";
            case "east" -> "Wschód (+X)";
            case "west" -> "Zachód (-X)";
            default -> serialized;
        };
    }

    private static String pingText(Minecraft mc, LocalPlayer player) {
        ClientPacketListener conn = mc.getConnection();
        if (conn == null) return "-";
        PlayerInfo info = conn.getPlayerInfo(player.getUUID());
        if (info == null) return "-";
        return info.getLatency() + " ms";
    }

    /** Panel z liniami "nazwa  wartosc" (nazwa szara, wartosc biala). Zwraca wysokosc panelu. */
    private static int drawBox(GuiGraphics g, Minecraft mc, int x, int y, List<String> lines, int accent) {
        int lineH = 11;
        int w = 0;
        for (String line : lines) {
            int idx = line.indexOf("  ");
            String label = idx >= 0 ? line.substring(0, idx) : line;
            String value = idx >= 0 ? line.substring(idx + 2) : "";
            w = Math.max(w, mc.font.width(label) + 6 + mc.font.width(value));
        }
        int boxW = w + 16;
        int boxH = lines.size() * lineH + 6;
        Draw.roundRect(g, x, y, boxW, boxH, 4, PANEL_BG);
        g.fill(x, y + 3, x + 2, y + boxH - 3, accent);

        int ty = y + 4;
        for (String line : lines) {
            int idx = line.indexOf("  ");
            String label = idx >= 0 ? line.substring(0, idx) : line;
            String value = idx >= 0 ? line.substring(idx + 2) : "";
            g.drawString(mc.font, label, x + 8, ty, LABEL_COLOR, false);
            g.drawString(mc.font, value, x + 8 + mc.font.width(label) + 6, ty, 0xFFFFFFFF, false);
            ty += lineH;
        }
        return boxH;
    }

    private static void drawKeystrokes(GuiGraphics g, Minecraft mc, int x, int y, int accent) {
        int s = 20;
        int gap = 2;
        drawKey(g, mc, x + s + gap, y, s, s, "W", mc.options.keyUp.isDown(), accent);
        int row2 = y + s + gap;
        drawKey(g, mc, x, row2, s, s, "A", mc.options.keyLeft.isDown(), accent);
        drawKey(g, mc, x + s + gap, row2, s, s, "S", mc.options.keyDown.isDown(), accent);
        drawKey(g, mc, x + 2 * (s + gap), row2, s, s, "D", mc.options.keyRight.isDown(), accent);
        int row3 = row2 + s + gap;
        int total = 3 * s + 2 * gap;
        drawKey(g, mc, x, row3, total, 12, "SPACJA", mc.options.keyJump.isDown(), accent);
        int row4 = row3 + 12 + gap;
        int half = (total - gap) / 2;
        drawKey(g, mc, x, row4, half, 16, "LPM", mc.options.keyAttack.isDown(), accent);
        drawKey(g, mc, x + half + gap, row4, half, 16, "PPM", mc.options.keyUse.isDown(), accent);
    }

    private static void drawKey(GuiGraphics g, Minecraft mc, int x, int y, int w, int h,
                                String label, boolean pressed, int accent) {
        Draw.roundRect(g, x, y, w, h, 3, pressed ? Theme.withAlpha(accent, 0xE0) : PANEL_BG);
        g.drawCenteredString(mc.font, label, x + w / 2, y + (h - 8) / 2,
                pressed ? 0xFFFFFFFF : 0xFFC4C4D0);
    }

    private static void drawArmor(GuiGraphics g, Minecraft mc, LocalPlayer player,
                                  int width, int height, int accent) {
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        List<ItemStack> stacks = new ArrayList<>();
        for (EquipmentSlot slot : slots) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!stack.isEmpty()) stacks.add(stack);
        }
        if (stacks.isEmpty()) return;

        int rowH = 20;
        int boxW = 56;
        int boxH = stacks.size() * rowH + 4;
        int bx = width - boxW - 4;
        int by = height / 2 - boxH / 2;
        Draw.roundRect(g, bx, by, boxW, boxH, 4, PANEL_BG);
        g.fill(bx + boxW - 2, by + 3, bx + boxW, by + boxH - 3, accent);

        int y = by + 2;
        for (ItemStack stack : stacks) {
            g.renderItem(stack, bx + boxW - 24, y + 1);
            g.renderItemDecorations(mc.font, stack, bx + boxW - 24, y + 1);
            if (stack.isDamageableItem()) {
                int max = stack.getMaxDamage();
                int left = max - stack.getDamageValue();
                float ratio = max > 0 ? (float) left / max : 1f;
                int color = Theme.lerp(0xFFFF5555, 0xFF55FF55, ratio);
                String text = Integer.toString(left);
                g.drawString(mc.font, text, bx + boxW - 28 - mc.font.width(text), y + 6, color, false);
            }
            y += rowH;
        }
    }

    private static void drawPotions(GuiGraphics g, Minecraft mc, LocalPlayer player, int width, int accent) {
        int y = 4;
        for (MobEffectInstance eff : player.getActiveEffects()) {
            String name = eff.getEffect().value().getDisplayName().getString();
            String level = eff.getAmplifier() > 0 ? " " + (eff.getAmplifier() + 1) : "";
            String time;
            if (eff.isInfiniteDuration()) {
                time = "∞";
            } else {
                int secs = eff.getDuration() / 20;
                time = String.format(Locale.ROOT, "%d:%02d", secs / 60, secs % 60);
            }
            String left = name + level;
            int w = mc.font.width(left) + 8 + mc.font.width(time) + 14;
            int x = width - w - 4;
            Draw.roundRect(g, x, y, w, 14, 4, PANEL_BG);
            g.fill(x + w - 2, y + 3, x + w, y + 11, accent);
            g.drawString(mc.font, left, x + 6, y + 3, 0xFFFFFFFF, false);
            g.drawString(mc.font, time, x + 6 + mc.font.width(left) + 8, y + 3, LABEL_COLOR, false);
            y += 16;
        }
    }

    // ---------- swiat (tracery, ESP, nametagi) ----------

    private static void drawTarget(GuiGraphics g, Minecraft mc, Cam cam, Entity e, float pt,
                                   double dist, int color, int width, int height) {
        Vec3 pos = e.getPosition(pt);
        double halfW = e.getBbWidth() / 2.0;
        double h = e.getBbHeight();

        // ramka: rzutujemy 8 rogow bryly kolizji
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        boolean allFront = true;
        for (int i = 0; i < 8; i++) {
            double px = pos.x + ((i & 1) == 0 ? -halfW : halfW);
            double py = pos.y + ((i & 2) == 0 ? 0.0 : h);
            double pz = pos.z + ((i & 4) == 0 ? -halfW : halfW);
            double[] s = project(cam, new Vec3(px, py, pz));
            if (s[2] <= 0.05) {
                allFront = false;
                break;
            }
            minX = Math.min(minX, s[0]);
            maxX = Math.max(maxX, s[0]);
            minY = Math.min(minY, s[1]);
            maxY = Math.max(maxY, s[1]);
        }
        if (allFront && (maxX - minX > width * 4 || maxY - minY > height * 4)) {
            allFront = false;
        }

        double[] center = project(cam, pos.add(0.0, h / 2.0, 0.0));
        boolean centerFront = center[2] > 0.05;

        if (Config.isOn(Module.ESP) && allFront) {
            drawRect(g, (int) minX, (int) minY, (int) maxX, (int) maxY, color);
        }

        if (Config.isOn(Module.TRACERS)) {
            double tx;
            double ty;
            if (centerFront) {
                tx = clamp(center[0], 0, width);
                ty = clamp(center[1], 0, height);
            } else {
                // cel jest za plecami - linia wskazuje kierunek do krawedzi ekranu
                double dx = center[3];
                double dy = -center[4];
                double len = Math.hypot(dx, dy);
                if (len < 1e-4) {
                    dx = 0.0;
                    dy = 1.0;
                    len = 1.0;
                }
                dx /= len;
                dy /= len;
                double kx = dx != 0.0 ? (width / 2.0) / Math.abs(dx) : Double.MAX_VALUE;
                double ky = dy != 0.0 ? (height / 2.0) / Math.abs(dy) : Double.MAX_VALUE;
                double k = Math.min(kx, ky);
                tx = width / 2.0 + dx * k;
                ty = height / 2.0 + dy * k;
            }
            drawLine(g, width / 2.0, height, tx, ty, color);
        }

        if (Config.isOn(Module.NAMETAGS)) {
            double tagX;
            double tagY;
            if (allFront) {
                tagX = (minX + maxX) / 2.0;
                tagY = minY - 14;
            } else if (centerFront) {
                tagX = center[0];
                tagY = center[1] - 14;
            } else {
                return;
            }

            StringBuilder text = new StringBuilder(e.getName().getString());
            if (e instanceof LivingEntity living) {
                text.append("  ").append((int) Math.ceil(living.getHealth()))
                        .append("/").append((int) Math.ceil(living.getMaxHealth())).append(" HP");
            }
            text.append("  ").append((int) dist).append("m");

            String s = text.toString();
            int tw = mc.font.width(s);
            int x = (int) tagX - tw / 2;
            int y = (int) tagY;
            Draw.roundRect(g, x - 4, y - 2, tw + 8, 12, 3, 0xA0101018);
            g.fill(x - 4, y + 10, x + tw + 4, y + 11, Theme.withAlpha(color, 0xC0));
            g.drawString(mc.font, s, x, y, 0xFFFFFFFF, false);
        }
    }

    // ---------- pomocnicze ----------

    /** Zwraca {ekranX, ekranY, glebokosc, prawo, gora}. Glebokosc <= 0 oznacza punkt za plecami. */
    private static double[] project(Cam c, Vec3 p) {
        Vec3 d = p.subtract(c.eye());
        double depth = d.dot(c.fwd());
        double x = d.dot(c.right());
        double y = d.dot(c.up());
        double sx = c.cx();
        double sy = c.cy();
        if (depth > 0.05) {
            sx = c.cx() + x / depth * c.scale();
            sy = c.cy() - y / depth * c.scale();
        }
        return new double[]{sx, sy, depth, x, y};
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static void drawRect(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        g.fill(x0, y0, x1, y0 + 1, color);
        g.fill(x0, y1 - 1, x1, y1, color);
        g.fill(x0, y0, x0 + 1, y1, color);
        g.fill(x1 - 1, y0, x1, y1, color);
    }

    /** Linia z malych kwadratow 2x2 (GuiGraphics nie ma wbudowanego rysowania linii). */
    private static void drawLine(GuiGraphics g, double x0, double y0, double x1, double y1, int color) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        int steps = (int) Math.min(2000.0, Math.ceil(Math.max(Math.abs(dx), Math.abs(dy)) / 2.0));
        if (steps <= 0) return;
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            int x = (int) (x0 + dx * t);
            int y = (int) (y0 + dy * t);
            g.fill(x, y, x + 2, y + 2, color);
        }
    }
}
