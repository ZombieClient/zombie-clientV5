package dev.zombie.gui;

import dev.zombie.Config;
import dev.zombie.ZombieClient;
import dev.zombie.hack.Hack;
import dev.zombie.setting.BoolSetting;
import dev.zombie.setting.ModeSetting;
import dev.zombie.setting.NumberSetting;
import dev.zombie.setting.Setting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Zombie-themed menu. Three columns: categories | modules | settings of the selected module.
 * Left-click a module to toggle it, right-click to only open its settings.
 * Mouse input is polled in render() so this doesn't depend on Screen input method signatures.
 */
public class ZombieScreen extends Screen {
    private static final int W = 360, H = 250;

    private static final int C_DIM_BG = 0xB0040A04;
    private static final int C_BORDER = 0xFF3F8F32;
    private static final int C_PANEL = 0xF0101A10;
    private static final int C_TITLE = 0xFF1B2D17;
    private static final int C_COL = 0xFF142016;
    private static final int C_ROW = 0xFF1E2E1F;
    private static final int C_ROW_HOVER = 0xFF2B4228;
    private static final int C_ROW_SEL = 0xFF34562C;
    private static final int C_ACCENT = 0xFF7BE05C;
    private static final int C_TEXT = 0xFFE6F2DF;
    private static final int C_DIM = 0xFF8FA886;
    private static final int C_ON = 0xFF6BDB4F;
    private static final int C_OFF = 0xFF8E2A24;
    private static final int C_BLOOD = 0xFF8B1A1A;

    // 8x8 pixel zombie head
    private static final String[] FACE = {
            "GGGGGGGG",
            "GgGGGGgG",
            "GEEGGEEG",
            "GERGGREG",
            "GGGnnGGG",
            "GGGGGGGG",
            "GMMMMMMG",
            "GMTMMTMG",
    };

    // remembered between openings
    private static Hack.Category tab = Hack.Category.COMBAT;
    private static Hack selected;

    private boolean wasLeft, wasRight, click, rightClick;
    private int mx, my;
    private NumberSetting dragging;
    private int page;
    private Hack lastSelected;

    public ZombieScreen() { super(Text.literal("Zombie Client")); }

    @Override public boolean shouldPause() { return false; }

    @Override public void removed() {
        Config.save(ZombieClient.hacks());
        super.removed();
    }

    // ---------------------------------------------------------------- render

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        mx = mouseX;
        my = mouseY;
        long win = client.getWindow().getHandle();
        boolean left = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean right = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        click = left && !wasLeft;
        rightClick = right && !wasRight;
        wasLeft = left;
        wasRight = right;
        if (!left) dragging = null;

        List<Hack> all = ZombieClient.hacks().all();
        if (selected == null || selected.category != tab) selected = firstOf(all, tab);
        if (selected != lastSelected) { page = 0; lastSelected = selected; }

        int x0 = (width - W) / 2, y0 = (height - H) / 2;

        // backdrop + window frame
        ctx.fill(0, 0, width, height, C_DIM_BG);
        ctx.fill(x0 - 2, y0 - 2, x0 + W + 2, y0 + H + 2, C_BORDER);
        ctx.fill(x0, y0, x0 + W, y0 + H, C_PANEL);

        // title bar
        ctx.fill(x0, y0, x0 + W, y0 + 26, C_TITLE);
        ctx.fill(x0, y0 + 26, x0 + W, y0 + 28, C_BORDER);
        drawFace(ctx, x0 + 8, y0 + 5);
        text(ctx, "ZOMBIE CLIENT", x0 + 30, y0 + 5, C_ACCENT);
        text(ctx, "Right Shift opens this menu", x0 + 30, y0 + 15, C_DIM);
        int[][] drips = {{44, 5}, {112, 3}, {178, 8}, {252, 4}, {318, 6}};
        for (int[] d : drips) {
            ctx.fill(x0 + d[0], y0 + 28, x0 + d[0] + 2, y0 + 28 + d[1], C_BLOOD);
            ctx.fill(x0 + d[0] - 1, y0 + 28 + d[1], x0 + d[0] + 3, y0 + 29 + d[1] + 1, C_BLOOD);
        }
        if (button(ctx, x0 + W - 20, y0 + 6, 14, 14, "x", 0xFF3A1A1A, C_BLOOD, C_TEXT)) {
            close();
            return;
        }

        // column backgrounds
        ctx.fill(x0 + 4, y0 + 32, x0 + 92, y0 + H - 4, C_COL);
        ctx.fill(x0 + 96, y0 + 32, x0 + 222, y0 + H - 4, C_COL);
        ctx.fill(x0 + 226, y0 + 32, x0 + W - 4, y0 + H - 4, C_COL);

        // --- column 1: categories
        int lx = x0 + 8, ty = y0 + 36;
        for (Hack.Category c : Hack.Category.values()) {
            boolean sel = c == tab;
            if (button(ctx, lx, ty, 80, 24, c.label.toUpperCase(),
                    sel ? C_ROW_SEL : C_ROW, C_ROW_HOVER, sel ? C_ACCENT : C_TEXT)) {
                tab = c;
                selected = firstOf(all, c);
            }
            ty += 28;
        }
        int enabledCount = 0;
        for (Hack h : all) if (h.isEnabled()) enabledCount++;
        text(ctx, enabledCount + " module" + (enabledCount == 1 ? "" : "s") + " on", lx + 2, y0 + H - 54, C_DIM);
        if (button(ctx, lx, y0 + H - 40, 80, 18, "Disable all", 0xFF3A1A1A, C_BLOOD, C_TEXT)) {
            for (Hack h : all) h.setEnabled(false);
        }

        // --- column 2: modules of the current category
        int rx = x0 + 100, ry = y0 + 36;
        for (Hack h : all) {
            if (h.category != tab) continue;
            boolean hover = in(rx, ry, 118, 18);
            boolean sel = h == selected;
            ctx.fill(rx, ry, rx + 118, ry + 18, sel ? C_ROW_SEL : (hover ? C_ROW_HOVER : C_ROW));
            ctx.fill(rx + 5, ry + 6, rx + 11, ry + 12, h.isEnabled() ? C_ON : C_OFF);
            text(ctx, h.name, rx + 16, ry + 5, h.isEnabled() ? C_TEXT : C_DIM);
            if (!h.settings().isEmpty()) text(ctx, "\u00BB", rx + 118 - 10, ry + 5, C_DIM);
            if (hover && click) { h.toggle(); selected = h; }
            if (hover && rightClick) selected = h;
            ry += 20;
        }

        // --- column 3: settings
        if (selected != null) drawSettings(ctx, x0 + 230, y0 + 36, 122, y0 + H - 40);

        // hint
        String hint = "Left-click: toggle    Right-click: settings    Esc: close";
        text(ctx, hint, x0 + (W - textRenderer.getWidth(hint)) / 2, y0 + H - 13, C_DIM);
    }

    private void drawSettings(DrawContext ctx, int x, int top, int w, int resetY) {
        Hack h = selected;
        int y = top;

        boolean on = h.isEnabled();
        if (button(ctx, x, y, w, 18, h.name + ": " + (on ? "ON" : "OFF"),
                on ? 0xFF2D5A25 : 0xFF4A1E1B, on ? 0xFF3A7430 : 0xFF6A2A25, on ? C_ON : C_TEXT)) {
            h.toggle();
        }
        y += 22;

        String key = h.binding != null ? h.binding.getBoundKeyLocalizedText().getString() : "";
        text(ctx, "Key: " + key, x, y, C_DIM);
        y += 11;

        List<OrderedText> lines = textRenderer.wrapLines(Text.literal(h.description), w);
        for (OrderedText line : lines) {
            ctx.drawTextWithShadow(textRenderer, line, x, y, C_DIM);
            y += 10;
        }
        y += 3;
        ctx.fill(x, y, x + w, y + 1, 0xFF2E6B25);
        y += 7;

        if (h.settings().isEmpty()) {
            text(ctx, "No settings", x, y, C_DIM);
            return;
        }
        List<List<Setting>> pages = paginate(h.settings(), (resetY - 26) - y);
        int count = pages.size();
        page = Math.max(0, Math.min(page, count - 1));
        for (Setting s : pages.get(page)) {
            if (s instanceof NumberSetting ns) y = drawSlider(ctx, ns, x, y, w);
            else if (s instanceof ModeSetting ms) y = drawMode(ctx, ms, x, y, w);
            else if (s instanceof BoolSetting b) y = drawToggle(ctx, b, x, y, w);
        }
        if (count > 1) {
            int py = resetY - 20;
            if (button(ctx, x, py, 18, 14, "<", C_ROW, C_ROW_HOVER, C_TEXT)) page = (page - 1 + count) % count;
            if (button(ctx, x + w - 18, py, 18, 14, ">", C_ROW, C_ROW_HOVER, C_TEXT)) page = (page + 1) % count;
            String label = "Page " + (page + 1) + "/" + count;
            text(ctx, label, x + (w - textRenderer.getWidth(label)) / 2, py + 3, C_DIM);
        }
        if (button(ctx, x, resetY, w, 18, "Reset defaults", 0xFF2A2A1A, 0xFF4A4A24, C_TEXT)) {
            for (Setting s : h.settings()) s.reset();
        }
    }

    private int drawSlider(DrawContext ctx, NumberSetting n, int x, int y, int w) {
        text(ctx, n.name, x, y, C_TEXT);
        String v = n.display();
        text(ctx, v, x + w - textRenderer.getWidth(v), y, C_ACCENT);

        int by = y + 12;
        ctx.fill(x, by, x + w, by + 6, 0xFF0B120A);
        double frac = (n.get() - n.min) / (n.max - n.min);
        int fx = x + (int) Math.round(frac * w);
        ctx.fill(x, by, fx, by + 6, C_BORDER);
        ctx.fill(fx - 1, by - 2, fx + 2, by + 8, C_TEXT);

        if (in(x, by - 3, w, 12) && click) dragging = n;
        if (dragging == n) {
            double t = Math.max(0.0, Math.min(1.0, (mx - x) / (double) w));
            n.set(n.min + t * (n.max - n.min));
        }
        return y + 28;
    }

    private int drawMode(DrawContext ctx, ModeSetting m, int x, int y, int w) {
        text(ctx, m.name, x, y, C_TEXT);
        if (button(ctx, x, y + 10, w, 14, m.get(), C_ROW, C_ROW_HOVER, C_ACCENT)) m.next();
        return y + 28;
    }

    private int drawToggle(DrawContext ctx, BoolSetting b, int x, int y, int w) {
        text(ctx, b.name, x, y + 1, C_TEXT);
        int bx = x + w - 22;
        ctx.fill(bx, y, bx + 22, y + 10, b.get() ? C_BORDER : 0xFF3A1A1A);
        int kx = b.get() ? bx + 13 : bx + 1;
        ctx.fill(kx, y + 1, kx + 8, y + 9, C_TEXT);
        if (in(x, y - 2, w, 14) && click) b.toggle();
        return y + 18;
    }

    // ---------------------------------------------------------------- helpers

    private void drawFace(DrawContext ctx, int x, int y) {
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                ctx.fill(x + c * 2, y + r * 2, x + c * 2 + 2, y + r * 2 + 2, faceColor(FACE[r].charAt(c)));
            }
        }
    }

    private static int faceColor(char c) {
        return switch (c) {
            case 'G' -> 0xFF5E9E45;
            case 'g' -> 0xFF4A8036;
            case 'E' -> 0xFF14210F;
            case 'R' -> 0xFFD93030;
            case 'n' -> 0xFF3F6E2C;
            case 'M' -> 0xFF24150F;
            case 'T' -> 0xFFD2DBC5;
            default -> 0xFF000000;
        };
    }

    /** Splits settings into pages that fit in `budget` pixels of height. */
    private static List<List<Setting>> paginate(List<Setting> list, int budget) {
        List<List<Setting>> pages = new ArrayList<>();
        List<Setting> cur = new ArrayList<>();
        int used = 0;
        for (Setting s : list) {
            int h = (s instanceof NumberSetting || s instanceof ModeSetting) ? 28 : 18;
            if (used + h > budget && !cur.isEmpty()) {
                pages.add(cur);
                cur = new ArrayList<>();
                used = 0;
            }
            cur.add(s);
            used += h;
        }
        if (!cur.isEmpty()) pages.add(cur);
        return pages;
    }

    private static Hack firstOf(List<Hack> all, Hack.Category c) {
        List<Hack> list = new ArrayList<>();
        for (Hack h : all) if (h.category == c) list.add(h);
        return list.isEmpty() ? null : list.get(0);
    }

    private boolean in(int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void text(DrawContext ctx, String s, int x, int y, int color) {
        ctx.drawTextWithShadow(textRenderer, s, x, y, color);
    }

    /** Draws a button and returns true if it was clicked this frame. */
    private boolean button(DrawContext ctx, int x, int y, int w, int h, String label, int bg, int bgHover, int fg) {
        boolean hover = in(x, y, w, h);
        ctx.fill(x, y, x + w, y + h, hover ? bgHover : bg);
        ctx.fill(x, y, x + w, y + 1, 0xFF2E6B25);
        ctx.fill(x, y + h - 1, x + w, y + h, 0xFF2E6B25);
        ctx.fill(x, y, x + 1, y + h, 0xFF2E6B25);
        ctx.fill(x + w - 1, y, x + w, y + h, 0xFF2E6B25);
        text(ctx, label, x + (w - textRenderer.getWidth(label)) / 2, y + (h - 8) / 2, fg);
        return hover && click;
    }
}
