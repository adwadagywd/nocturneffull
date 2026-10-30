package dev.nocturne.gui;

import dev.nocturne.config.ConfigManager;
import dev.nocturne.module.Category;
import dev.nocturne.module.Module;
import dev.nocturne.module.ModuleManager;
import dev.nocturne.module.impl.NoFriendsDamage;
import dev.nocturne.module.impl.Notifications;
import dev.nocturne.module.impl.ThemeModule;
import dev.nocturne.setting.*;
import dev.nocturne.util.Friends;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class ClickGui extends Screen {
    private static final int W = 140, HEAD = 28, ROW = 20, H_SMALL = 17, H_SLIDER = 27, H_TEXT = 34, PAD = 5, TOP = 70;
    private static final String ALNUM = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_";
    private static final String SEARCH_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789 _";

    private enum Tab {
        MODULES("Modules"), CONFIGS("Configs"), FRIENDS("Friends"), THEME("Theme");
        final String label;
        Tab(String l) { label = l; }
    }

    private static Tab tab = Tab.MODULES;
    /** per category: x, y, open(1/0) - static so the layout survives re-opening */
    private static final Map<Category, int[]> STATE = new EnumMap<>(Category.class);

    private interface Click { void on(int button, int mx, int my); }
    private record Hit(int x, int y, int w, int h, Click click) {}

    /** a text box owned by the GUI itself (search / config name / friend name) */
    private final class Field implements Editable {
        private String text = "";
        private final String allowed;
        private final int max;
        private final boolean lower;

        Field(String allowed, int max, boolean lower) { this.allowed = allowed; this.max = max; this.lower = lower; }

        @Override public String text() { return text; }
        @Override public void setText(String s) { text = s; }
        @Override public char filter(char c) {
            char x = lower ? Character.toLowerCase(c) : c;
            return allowed.indexOf(x) >= 0 ? x : 0;
        }
        @Override public int maxLen() { return max; }
    }

    private final List<Hit> hits = new ArrayList<>();
    private final Map<String, Float> anims = new HashMap<>();
    private final List<float[]> particles = new ArrayList<>();
    private final long openNs = System.nanoTime();
    private long lastNs, tabNs = System.nanoTime();
    private float dt = 0.016f, tOpen, master = 1f;
    private boolean closing;
    private long closeNs;
    private float scroll, scrollTarget, contentBottom;
    private float closeP, closeDur = 0.36f;

    private Category dragPanel;
    private int dragOx, dragOy;
    private Slidable dragSlider;
    private int dragX, dragW = 1;
    private Editable edit;
    private Runnable editEnter;
    private Module bindingModule;

    private final Field searchField = new Field(SEARCH_CHARS, 20, true);
    private final Field cfgField = new Field(ALNUM + "-", 24, false);
    private final Field friendField = new Field(ALNUM, 16, false);
    private List<String> cfgList = new ArrayList<>();
    private String status = "";
    private long statusNs;

    public ClickGui() {
        super(Text.literal("Nocturne"));
        refreshConfigs();
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void removed() { ConfigManager.save(); }

    @Override
    public void close() {
        if (closing) return;
        closing = true;
        closeNs = System.nanoTime();
        closeDur = Theme.closeEffect().equals("Fade") ? 0.2f : 0.4f;
        Sounds.click(false);
    }

    // ================= helpers =================
    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
    private static float clamp01(float t) { return t < 0f ? 0f : Math.min(t, 1f); }
    /** ease-out with a little overshoot: panels "bounce" into place */
    private static float easeBack(float t) {
        t = clamp01(t);
        float c1 = 1.70158f, c3 = c1 + 1f, u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }
    private static float ease(float t) { float u = 1f - clamp01(t); return 1f - u * u * u; }

    private static String keyOf(Object key) {
        return key instanceof String s ? s : Integer.toString(System.identityHashCode(key));
    }

    private float anim(Object key, String tag, float target, float speed) {
        String k = keyOf(key) + tag;
        float cur = anims.getOrDefault(k, target);
        cur += (target - cur) * (1f - (float) Math.exp(-speed * Theme.anim() * dt));
        if (Math.abs(target - cur) < 0.002f) cur = target;
        anims.put(k, cur);
        return cur;
    }
    private float peek(Object key, String tag, float def) { return anims.getOrDefault(keyOf(key) + tag, def); }

    private static int rowH(Setting<?> s) {
        if (s instanceof BlockListSetting b) return b.height();
        if (s instanceof StringSetting) return H_TEXT;
        return s instanceof Slidable ? H_SLIDER : H_SMALL;
    }
    private static int settingsH(Module m) {
        int h = 0;
        for (Setting<?> s : m.settings) if (s.visible()) h += rowH(s);
        return h;
    }

    private static String pretty(String id) {
        int i = id.indexOf(':');
        String path = i >= 0 ? id.substring(i + 1) : id;
        String ns = (i >= 0 && !id.startsWith("minecraft:")) ? id.substring(0, i) + ":" : "";
        return ns + path.replace('_', ' ');
    }

    private static String keyName(int key) {
        String s = InputUtil.fromKeyCode(key, 0).getLocalizedText().getString();
        return s.length() > 6 ? s.substring(0, 6) : s;
    }

    private void switchTab(Tab t) {
        if (t == tab) return;
        Sounds.click(true);
        tab = t;
        scroll = 0f;
        scrollTarget = 0f;
        tabNs = System.nanoTime();
        edit = null;
        editEnter = null;
        if (t == Tab.CONFIGS) refreshConfigs();
    }

    private void refreshConfigs() { cfgList = ConfigManager.list(); }

    private void setStatus(String s) {
        status = s;
        statusNs = System.nanoTime();
        Notifications.push("Nocturne", s, true);
    }

    private void drawStatus(DrawContext ctx, TextRenderer tr, int x, int y) {
        if (status.isEmpty()) return;
        float age = (System.nanoTime() - statusNs) / 1.0e9f;
        if (age > 3.5f) return;
        float old = Draw.alpha;
        Draw.alpha = old * (age > 2.8f ? 1f - (age - 2.8f) / 0.7f : 1f);
        Draw.text(ctx, tr, status, x, y, Theme.accent(), false);
        Draw.alpha = old;
    }

    private List<Module> modulesOf(Category c) {
        List<Module> all = ModuleManager.byCategory(c);
        String q = searchField.text().trim();
        if (q.isEmpty()) return all;
        List<Module> out = new ArrayList<>();
        for (Module m : all) if (m.name.toLowerCase(Locale.ROOT).contains(q)) out.add(m);
        return out;
    }

    /** default panel positions: a centred grid that wraps to the window width */
    private int[] state(Category c) {
        return STATE.computeIfAbsent(c, k -> {
            Category[] cats = Category.values();
            int cols = Math.max(1, Math.min(cats.length, (width - 24) / (W + 12)));
            int rows = (cats.length + cols - 1) / cols;
            int[] rowTop = new int[rows + 1];
            rowTop[0] = TOP;
            for (int r = 0; r < rows; r++) {
                int maxH = 0;
                for (Category o : cats)
                    if (o.ordinal() / cols == r) maxH = Math.max(maxH, HEAD + ModuleManager.byCategory(o).size() * ROW + 12);
                rowTop[r + 1] = rowTop[r] + maxH + 16;
            }
            int col = k.ordinal() % cols, row = k.ordinal() / cols;
            int perRow = Math.min(cols, cats.length - row * cols);
            int gridW = perRow * (W + 12) - 12;
            int x0 = Math.max(8, (width - gridW) / 2);
            return new int[]{x0 + col * (W + 12), rowTop[row], 1};
        });
    }

    // ================= render =================
    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        long now = System.nanoTime();
        dt = lastNs == 0 ? 0.016f : Math.min(0.05f, (now - lastNs) / 1.0e9f);
        lastNs = now;
        tOpen = (now - openNs) / 1.0e9f;
        master = 1f;
        closeP = 0f;
        if (closing) {
            closeP = (now - closeNs) / 1.0e9f / closeDur;
            if (closeP >= 1f) { client.setScreen(null); return; }
            boolean fade = Theme.closeEffect().equals("Fade");
            master = 1f - ease(clamp01((closeP - (fade ? 0f : 0.35f)) / (fade ? 1f : 0.65f)));
        }
        hits.clear();
        TextRenderer tr = textRenderer;
        if (dragSlider != null) dragSlider.setFraction((mx - dragX) / (double) dragW);

        float minScroll = -Math.max(0f, contentBottom - height);
        scrollTarget = MathHelper.clamp(scrollTarget, minScroll, 0f);
        scroll += (scrollTarget - scroll) * (1f - (float) Math.exp(-14f * dt));

        drawBackground(ctx, mx, my, delta);

        float tabA = ease((now - tabNs) / 1.0e9f / 0.22f);
        contentBottom = 0f;
        switch (tab) {
            case MODULES -> renderModules(ctx, tr, mx, my, tabA);
            case CONFIGS -> renderConfigs(ctx, tr, mx, my, tabA);
            case FRIENDS -> renderFriends(ctx, tr, mx, my, tabA);
            case THEME -> renderTheme(ctx, tr, mx, my, tabA);
        }

        // glass: a soft light follows the cursor across the panes
        if (Theme.glass() && !closing) {
            Draw.alpha = master * clamp01(tOpen / 0.4f);
            Draw.radial(ctx, mx, my, 110, 110, 0x26FFFFFF, 0x00FFFFFF);
        }

        // scrim so scrolled content slides under the bar instead of over it
        Draw.alpha = master;
        ctx.fillGradient(0, 0, width, TOP - 6, Draw.col(Theme.glass() ? 0x66050912 : 0xCC000000), Draw.col(0x00000000));
        drawTopBar(ctx, tr, mx, my);
        if (tab == Tab.MODULES) {
            Draw.alpha = master * tabA;
            drawField(ctx, tr, searchField, "Search modules...", (width - 220) / 2, 40, 220, 20, mx, my, !closing, null);
        }

        // juicy rings: a soft pulse when opening, a burst when closing
        float ringMax = Math.max(width, height) * 0.62f;
        if (closing && Theme.closeEffect().equals("Burst")) {
            float rp = ease(closeP);
            Draw.alpha = (1f - closeP) * 0.55f;
            Draw.ring(ctx, width / 2.0, height / 2.0, rp * ringMax, 4, Theme.accent(), 1f);
            Draw.ring(ctx, width / 2.0, height / 2.0, rp * ringMax * 0.82f, 2, Theme.accent2(), 1f);
        } else if (!closing && tOpen < 0.6f) {
            float rp = ease(tOpen / 0.6f);
            Draw.alpha = (1f - tOpen / 0.6f) * 0.35f;
            Draw.ring(ctx, width / 2.0, height / 2.0, rp * ringMax * 0.8f, 3, Theme.accent(), 1f);
        }

        Draw.alpha = master * clamp01(tOpen / 0.4f);
        String hint = switch (tab) {
            case MODULES -> "LMB toggle    RMB settings    MMB bind key    drag headers    scroll";
            case CONFIGS -> "Configs are saved in .minecraft/config/nocturne/configs";
            case FRIENDS -> "Friends are shown green and never targeted by combat modules";
            case THEME -> "Changes apply instantly and are stored in your configs";
        };
        Draw.text(ctx, tr, hint, (width - Draw.width(tr, hint)) / 2, height - 14, Theme.dim(), false);
        Draw.alpha = 1f;
    }

    private void drawBackground(DrawContext ctx, int mx, int my, float delta) {
        boolean blur = Theme.blur();
        if (blur) renderBackground(ctx, mx, my, delta);
        Draw.alpha = master * clamp01(tOpen / 0.25f);
        int top = blur ? 0x28000000 : 0xB0000000;
        int bot = Theme.glow() ? Theme.accentA(0x3A) : (blur ? 0x50000000 : 0xD0000000);
        if (Theme.glass()) { top = blur ? 0x40050912 : 0xA0050912; bot = blur ? 0x6A050912 : 0xC0050912; }
        ctx.fillGradient(0, 0, width, height, Draw.col(top), Draw.col(bot));
        if (Theme.glass() && Theme.glassBlobs()) drawBlobs(ctx);

        if (!Theme.particles()) return;
        if (particles.isEmpty()) {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            for (int i = 0; i < 45; i++)
                particles.add(new float[]{r.nextFloat() * width, r.nextFloat() * height,
                        (r.nextFloat() - 0.5f) * 6f, -(4f + r.nextFloat() * 10f), 1f + r.nextFloat() * 2f, 0.25f + r.nextFloat() * 0.5f});
        }
        for (float[] p : particles) {
            p[0] += p[2] * dt;
            p[1] += p[3] * dt;
            if (p[1] < -4f) { p[1] = height + 4f; p[0] = ThreadLocalRandom.current().nextFloat() * width; }
            Draw.round(ctx, (int) p[0], (int) p[1], (int) p[4], (int) p[4], 1, Theme.accentA((int) (p[5] * 110)), false);
        }
    }

    /** big slow colour blobs behind the glass - what makes the panes look like they refract something */
    private void drawBlobs(DrawContext ctx) {
        double t = System.currentTimeMillis() / 1000.0;
        int[] cols = {Theme.accent(), Draw.hueShift(Theme.accent(), 0.33f), Theme.accent2(), Draw.hueShift(Theme.accent(), 0.62f)};
        double[][] pos = {{0.18, 0.30}, {0.82, 0.26}, {0.55, 0.82}, {0.14, 0.86}};
        double rad = Math.max(width, height) * 0.36;
        for (int i = 0; i < 4; i++) {
            double cx = (pos[i][0] + Math.sin(t * 0.17 + i * 1.9) * 0.08) * width;
            double cy = (pos[i][1] + Math.cos(t * 0.13 + i * 2.3) * 0.07) * height;
            Draw.radial(ctx, cx, cy, rad, rad, (0x52 << 24) | (cols[i] & 0xFFFFFF), cols[i] & 0xFFFFFF);
        }
    }

    private void drawTopBar(DrawContext ctx, TextRenderer tr, int mx, int my) {
        float intro = ease(tOpen / 0.3f);
        float outT = closing && !Theme.closeEffect().equals("Fade") ? ease(clamp01(closeP * 1.6f)) : 0f;
        Draw.alpha = master * intro * (1f - outT);
        Tab[] tabs = Tab.values();
        int cell = 72, gap = 4, bh = 26;
        int bw = tabs.length * cell + (tabs.length - 1) * gap + 16;
        int bx = (width - bw) / 2, by = 8 - Math.round((1f - intro) * 10f) - Math.round(outT * 22f);

        Draw.shadow(ctx, bx, by, bw, bh, 10);
        Draw.surface(ctx, bx, by, bw, bh, 10);
        float slide = anim(this, "tabx", tab.ordinal(), 16f);
        int thx = bx + 8 + Math.round(slide * (cell + gap));
        if (Theme.glass()) { // glass thumb, like an iOS segmented control
            Draw.glassFill(ctx, thx, by + 4, cell, bh - 8, (bh - 8) / 2f, 0x5CFFFFFF, 0x26FFFFFF, true, true);
            Draw.glassFill(ctx, thx, by + 4, cell, (bh - 8) / 2, (bh - 8) / 2f, 0x34FFFFFF, 0x00FFFFFF, true, false);
            Draw.glassRim(ctx, thx, by + 4, cell, bh - 8, (bh - 8) / 2f, 0xC0FFFFFF, 0x22FFFFFF, 0x70FFFFFF);
        } else Draw.round(ctx, thx, by + 4, cell, bh - 8, 8, Theme.accentA(0x3C));
        for (Tab t : tabs) {
            int tx = bx + 8 + t.ordinal() * (cell + gap);
            boolean hov = !closing && in(mx, my, tx, by + 3, cell, bh - 6);
            float hv = anim(t, "thov", hov ? 1f : 0f, 18f);
            int color = t == tab ? (Theme.glass() ? Theme.text() : Theme.accent()) : Draw.lerp(Theme.dim(), Theme.text(), hv);
            Draw.text(ctx, tr, t.label, tx + (cell - Draw.width(tr, t.label)) / 2, by + (bh - 8) / 2, color, false);
            if (!closing) hits.add(new Hit(tx, by + 3, cell, bh - 6, (b, cx2, cy2) -> switchTab(t)));
        }
    }

    // ---------------- Modules tab ----------------
    private void renderModules(DrawContext ctx, TextRenderer tr, int mx, int my, float tabA) {
        Module hovered = null;
        float bottom = 0f;
        int sc = Math.round(scroll);
        int idx = 0;
        boolean searching = !searchField.text().trim().isEmpty();

        for (Category c : Category.values()) {
            List<Module> mods = modulesOf(c);
            if (mods.isEmpty()) continue;
            int[] st = state(c);
            float pin = ease((tOpen - idx * 0.06f) / 0.3f) * tabA;
            float inRaw = clamp01((tOpen - idx * 0.06f) / 0.38f);
            float outP = closing && !Theme.closeEffect().equals("Fade") ? ease((closeP * 1.4f - idx * 0.07f) / 0.6f) : 0f;
            idx++;
            float p = pin * (1f - outP);
            Draw.alpha = master * p;
            boolean live = p > 0.7f && !closing;

            final int x = MathHelper.clamp(st[0], 0, Math.max(0, width - W));
            final int y = st[1] + sc + Math.round((1f - easeBack(inRaw)) * 34f) + Math.round(outP * 40f);
            float openA = anim(c, "open", (st[2] == 1 || searching) ? 1f : 0f, 16f);

            int bodyH = 0, on = 0;
            for (Module m : mods) {
                bodyH += ROW;
                float ea = anim(m, "exp", m.expanded ? 1f : 0f, 14f);
                bodyH += Math.round(settingsH(m) * ea);
                if (m.isEnabled()) on++;
            }
            int visBody = Math.round(bodyH * openA);
            int pad = Math.round(PAD * openA);
            int totalH = HEAD + visBody + pad;
            bottom = Math.max(bottom, st[1] + HEAD + bodyH + PAD + 30);

            Draw.shadow(ctx, x, y, W, totalH, 9);
            Draw.surface(ctx, x, y, W, totalH, 9);
            Draw.round(ctx, x + 1, y + 1, W - 2, HEAD - 1, 8, Theme.header());
            if (openA > 0.02f) Draw.rect(ctx, x + 1, y + HEAD - 8, W - 2, 8, Theme.header());
            Draw.bold(ctx, tr, c.label, x + 11, y + 10, Theme.text(), false);
            String cnt = on + "/" + mods.size();
            Draw.text(ctx, tr, cnt, x + W - 11 - Draw.width(tr, cnt), y + 10, on > 0 ? Theme.accent() : Theme.dim(), false);
            Draw.hgrad(ctx, x + 10, y + HEAD - 1, W - 20, 1, Theme.accent(), Theme.accent2());
            int shx = x + 10 + Math.round(((System.currentTimeMillis() + c.ordinal() * 400L) % 2600L) / 2600f * (W - 20 - 28));
            Draw.hgrad(ctx, shx, y + HEAD - 1, 14, 1, 0x00FFFFFF, 0xB0FFFFFF);
            Draw.hgrad(ctx, shx + 14, y + HEAD - 1, 14, 1, 0xB0FFFFFF, 0x00FFFFFF);

            if (live) hits.add(new Hit(x, y, W, HEAD, (b, hx, hy) -> {
                if (b == 0) { dragPanel = c; dragOx = hx - x; dragOy = hy - y; }
                else if (b == 1) st[2] ^= 1;
            }));

            if (visBody > 0) {
                int bodyTop = y + HEAD;
                int bodyEnd = bodyTop + visBody;
                ctx.enableScissor(x, bodyTop, x + W, bodyEnd);
                int cy = bodyTop;
                for (Module m : mods) {
                    int rx = x + PAD, rw = W - 2 * PAD, ry = cy + 1, rh = ROW - 2;
                    boolean hov = live && in(mx, my, rx, cy, rw, ROW) && my < bodyEnd;
                    if (hov) hovered = m;
                    float hv = anim(m, "hov", hov ? 1f : 0f, 18f);
                    float en = anim(m, "en", m.isEnabled() ? 1f : 0f, 14f);

                    float fl = anim(m, "flash", 0f, 5f);
                    if (fl > 0.01f) Draw.round(ctx, rx, ry, rw, rh, 6, Draw.scaleA(0xFFFFFFFF, fl * 0.2f));
                    if (hv > 0.01f) Draw.round(ctx, rx, ry, rw, rh, 6, Draw.scaleA(Theme.rowHover(), hv));
                    if (en > 0.01f) {
                        int fw = Math.max(10, Math.round(rw * en));
                        Draw.round(ctx, rx, ry, fw, rh, 6, Draw.scaleA(Theme.accentA(0x55), en));
                        Draw.round(ctx, rx + 1, ry + 4, 2, rh - 8, 1, Draw.scaleA(Theme.accent(), en));
                        if (Theme.glass()) Draw.round(ctx, rx + 6, ry + 1, Math.max(2, fw - 12), 1, 0.5f, Draw.scaleA(0x70FFFFFF, en));
                    }
                    int tc = Draw.lerp(Theme.dim(), Theme.text(), Math.max(en, hv * 0.7f));
                    Draw.text(ctx, tr, m.name, rx + 9 + Math.round(2 * en), cy + 6, tc, false);
                    if (!m.settings.isEmpty()) {
                        int dc = m.expanded ? Theme.accent() : Theme.dim();
                        for (int i = 0; i < 3; i++) Draw.rect(ctx, rx + rw - 14 + i * 4, cy + 9, 2, 2, dc);
                    }
                    String kl = bindingModule == m ? "[...]" : (m.key >= 0 ? "[" + keyName(m.key) + "]" : null);
                    if (kl != null)
                        Draw.text(ctx, tr, kl, rx + rw - (m.settings.isEmpty() ? 6 : 18) - Draw.width(tr, kl), cy + 6,
                                bindingModule == m ? Theme.accent() : Theme.dim(), false);
                    if (live && cy + ROW <= bodyEnd)
                        hits.add(new Hit(rx, cy, rw, ROW, (b, hx, hy) -> {
                            if (b == 0) { m.toggle(); anims.put(keyOf(m) + "flash", 1f); Sounds.click(m.isEnabled()); }
                            else if (b == 1) m.expanded = !m.expanded;
                            else if (b == 2) bindingModule = m;
                        }));
                    cy += ROW;

                    float ea = peek(m, "exp", 0f);
                    int sh = Math.round(settingsH(m) * ea);
                    if (sh > 0) {
                        ctx.enableScissor(x, cy, x + W, cy + sh);
                        Draw.round(ctx, x + PAD, cy, W - 2 * PAD, sh, 6, Theme.inset());
                        int sy = cy;
                        for (Setting<?> s : m.settings) {
                            if (!s.visible()) continue;
                            boolean act = live && ea > 0.98f && sy + rowH(s) <= bodyEnd;
                            sy = drawSetting(ctx, tr, s, x + PAD + 5, sy, W - 2 * PAD - 10, mx, my, act);
                        }
                        ctx.disableScissor();
                        cy += sh;
                    }
                }
                ctx.disableScissor();
            }
        }
        contentBottom = bottom;
        if (hovered != null && !closing) ctx.drawTooltip(tr, Fonts.text(tr, hovered.description), mx, my);
    }

    // ---------------- Configs tab ----------------
    private void saveConfig() {
        String n = ConfigManager.clean(cfgField.text());
        if (n.isEmpty()) { setStatus("Type a config name first"); return; }
        setStatus(ConfigManager.saveAs(n) ? "Saved " + n : "Could not save " + n);
        refreshConfigs();
    }

    private void renderConfigs(DrawContext ctx, TextRenderer tr, int mx, int my, float tabA) {
        Draw.alpha = master * tabA;
        boolean live = tabA > 0.7f && !closing;
        int cw = Math.min(340, width - 24), cx = (width - cw) / 2;
        int cy = TOP + Math.round(scroll) + Math.round((1f - tabA) * 14f);
        int rows = Math.max(1, cfgList.size());
        int ch = 34 + 36 + rows * 24 + 36;
        card(ctx, tr, cx, cy, cw, ch, "Configs", "save / load every setting");

        int iy = cy + 34 + 8;
        drawField(ctx, tr, cfgField, "Config name...", cx + 10, iy, cw - 20 - 72, 20, mx, my, live, this::saveConfig);
        button(ctx, tr, "Save", cx + cw - 10 - 66, iy, 66, 20, live, mx, my, Theme.accent(), this::saveConfig);

        int ly = iy + 30;
        if (cfgList.isEmpty()) Draw.text(ctx, tr, "No configs yet - type a name and press Save", cx + 14, ly + 7, Theme.dim(), false);
        for (String n : cfgList) {
            boolean hov = live && in(mx, my, cx + 8, ly, cw - 16, 22);
            float hv = anim("cfg:" + n, "row", hov ? 1f : 0f, 18f);
            if (hv > 0.01f) Draw.round(ctx, cx + 8, ly, cw - 16, 22, 6, Draw.scaleA(Theme.rowHover(), hv));
            Draw.text(ctx, tr, n, cx + 16, ly + 7, Theme.text(), false);
            button(ctx, tr, "Delete", cx + cw - 16 - 54, ly + 2, 54, 18, live, mx, my, 0xFFFF5C6C, () -> {
                setStatus(ConfigManager.delete(n) ? "Deleted " + n : "Could not delete " + n);
                refreshConfigs();
            });
            button(ctx, tr, "Load", cx + cw - 16 - 54 - 4 - 46, ly + 2, 46, 18, live, mx, my, Theme.accent(),
                    () -> setStatus(ConfigManager.loadFrom(n) ? "Loaded " + n : "Could not load " + n));
            ly += 24;
        }
        drawStatus(ctx, tr, cx + 14, cy + ch - 22);
        contentBottom = TOP + ch + 30;
    }

    // ---------------- Friends tab ----------------
    private void addFriend() {
        String n = friendField.text().trim();
        if (n.isEmpty()) { setStatus("Type a nickname first"); return; }
        if (Friends.add(n)) { setStatus("Added " + n); friendField.setText(""); }
        else setStatus(n + " is already a friend");
    }

    private void renderFriends(DrawContext ctx, TextRenderer tr, int mx, int my, float tabA) {
        Draw.alpha = master * tabA;
        boolean live = tabA > 0.7f && !closing;
        int cw = Math.min(340, width - 24), cx = (width - cw) / 2;
        int cy = TOP + Math.round(scroll) + Math.round((1f - tabA) * 14f);
        List<String> fl = Friends.all();
        int rows = Math.max(1, fl.size());
        int ch = 34 + 36 + rows * 24 + 36 + 30;
        card(ctx, tr, cx, cy, cw, ch, "Friends", "NoFriendsDamage protects them");

        int iy = cy + 34 + 8;
        drawField(ctx, tr, friendField, "Player nickname...", cx + 10, iy, cw - 20 - 72, 20, mx, my, live, this::addFriend);
        button(ctx, tr, "Add", cx + cw - 10 - 66, iy, 66, 20, live, mx, my, Theme.accent(), this::addFriend);

        int ly = iy + 30;
        if (fl.isEmpty()) Draw.text(ctx, tr, "No friends yet - type a nickname and press Add", cx + 14, ly + 7, Theme.dim(), false);
        for (String n : fl) {
            boolean hov = live && in(mx, my, cx + 8, ly, cw - 16, 22);
            float hv = anim("fr:" + n, "row", hov ? 1f : 0f, 18f);
            if (hv > 0.01f) Draw.round(ctx, cx + 8, ly, cw - 16, 22, 6, Draw.scaleA(Theme.rowHover(), hv));
            Draw.round(ctx, cx + 16, ly + 8, 6, 6, 3, 0xFF3DDC97);
            Draw.text(ctx, tr, n, cx + 28, ly + 7, Theme.text(), false);
            button(ctx, tr, "Remove", cx + cw - 16 - 58, ly + 2, 58, 18, live, mx, my, 0xFFFF5C6C, () -> {
                Friends.remove(n);
                setStatus("Removed " + n);
            });
            ly += 24;
        }
        NoFriendsDamage nf = NoFriendsDamage.INSTANCE;
        if (nf != null) {
            int ty = ly + 6;
            boolean on = nf.isEnabled();
            Draw.text(ctx, tr, "NoFriendsDamage", cx + 14, ty + 5, Theme.soft(), false);
            button(ctx, tr, on ? "ON" : "OFF", cx + cw - 14 - 54, ty, 54, 18, live, mx, my,
                    on ? 0xFF3DDC97 : 0xFFFF5C6C, () -> { nf.toggle(); Sounds.click(nf.isEnabled()); });
        }
        drawStatus(ctx, tr, cx + 14, cy + ch - 22);
        contentBottom = TOP + ch + 30;
    }

    // ---------------- Theme tab ----------------
    private void renderTheme(DrawContext ctx, TextRenderer tr, int mx, int my, float tabA) {
        ThemeModule tm = ThemeModule.INSTANCE;
        if (tm == null) return;
        Draw.alpha = master * tabA;
        boolean live = tabA > 0.7f && !closing;
        int cw = Math.min(340, width - 24), cx = (width - cw) / 2;
        int cy = TOP + Math.round(scroll) + Math.round((1f - tabA) * 14f);

        List<Setting<?>> list = new ArrayList<>();
        int sh = 0;
        for (Setting<?> s : tm.settings) if (s != tm.preset && s.visible()) { list.add(s); sh += rowH(s); }
        String[] modes = tm.preset.modes;
        int swRows = (modes.length + 3) / 4;
        int ch = 34 + 12 + swRows * 30 + 8 + sh + 16;
        card(ctx, tr, cx, cy, cw, ch, "Theme", "colours, corners, animations");

        int sy0 = cy + 34 + 12;
        int sw = (cw - 20 - 3 * 6) / 4;
        for (int i = 0; i < modes.length; i++) {
            final String name = modes[i];
            int sx = cx + 10 + (i % 4) * (sw + 6), sy = sy0 + (i / 4) * 30;
            int col = i < ThemeModule.PRESETS.length ? (0xFF000000 | ThemeModule.PRESETS[i].accent()) : tm.hue1.rgb();
            boolean sel = tm.preset.is(name);
            boolean hov = live && in(mx, my, sx, sy, sw, 24);
            float hv = anim("sw:" + name, "hov", hov ? 1f : 0f, 18f);
            if (sel) Draw.panel(ctx, sx, sy, sw, 24, 8, Theme.accentA(0x30), Theme.accentA(0xB0));
            else Draw.round(ctx, sx, sy, sw, 24, 8, Draw.scaleA(Theme.rowHover(), 0.5f + 0.5f * hv));
            Draw.round(ctx, sx + 8, sy + 8, 8, 8, 4, col);
            Draw.text(ctx, tr, name, sx + 22, sy + 8, sel ? Theme.text() : Theme.soft(), false);
            if (live) hits.add(new Hit(sx, sy, sw, 24, (b, hx, hy) -> tm.preset.set(name)));
        }

        int sy = sy0 + swRows * 30 + 8;
        Draw.round(ctx, cx + 8, sy, cw - 16, Math.max(1, sh), 8, Theme.inset());
        for (Setting<?> s : list) sy = drawSetting(ctx, tr, s, cx + 16, sy, cw - 32, mx, my, live);
        contentBottom = TOP + ch + 30;
    }

    // ---------------- shared widgets ----------------
    private void card(DrawContext ctx, TextRenderer tr, int x, int y, int w, int h, String title, String sub) {
        Draw.shadow(ctx, x, y, w, h, 10);
        Draw.surface(ctx, x, y, w, h, 10);
        Draw.round(ctx, x + 1, y + 1, w - 2, 33, 9, Theme.header());
        Draw.rect(ctx, x + 1, y + 26, w - 2, 8, Theme.header());
        Draw.bold(ctx, tr, title, x + 14, y + 11, Theme.text(), false);
        Draw.text(ctx, tr, sub, x + 14 + Draw.boldWidth(tr, title) + 8, y + 11, Theme.dim(), false);
        Draw.hgrad(ctx, x + 12, y + 33, w - 24, 1, Theme.accent(), Theme.accent2());
    }

    private void drawField(DrawContext ctx, TextRenderer tr, Editable f, String placeholder, int x, int y, int w, int h,
                           int mx, int my, boolean act, Runnable enter) {
        boolean focused = edit == f;
        boolean hov = act && in(mx, my, x, y, w, h);
        Draw.round(ctx, x, y, w, h, 6, focused ? Theme.fieldFocus() : (hov ? Theme.rowHover() : Theme.field()));
        if (focused) Draw.rect(ctx, x + 5, y + h - 2, w - 10, 1, Theme.accent());
        String t = f.text();
        boolean empty = t.isEmpty();
        String shown = empty && !focused ? placeholder
                : t + ((focused && (System.currentTimeMillis() / 450) % 2 == 0) ? "_" : "");
        Draw.text(ctx, tr, (focused && !empty ? Draw.trimTail(tr, shown, w - 12) : Draw.trim(tr, shown, w - 12)), x + 6, y + (h - 8) / 2,
                empty && !focused ? Theme.dim() : Theme.text(), false);
        if (act) hits.add(new Hit(x, y, w, h, (b, hx, hy) -> { edit = f; editEnter = enter; }));
    }

    private void button(DrawContext ctx, TextRenderer tr, String label, int x, int y, int w, int h,
                        boolean act, int mx, int my, int color, Runnable r) {
        boolean hov = act && in(mx, my, x, y, w, h);
        float hv = anim("btn:" + label + ":" + x + ":" + y, "h", hov ? 1f : 0f, 20f);
        Draw.round(ctx, x, y, w, h, 6, Draw.scaleA(color, 0.16f + 0.24f * hv));
        if (Theme.glass()) Draw.round(ctx, x + 5, y + 1, Math.max(2, w - 10), 1, 0.5f, Draw.scaleA(0x70FFFFFF, 0.5f + 0.5f * hv));
        Draw.text(ctx, tr, label, x + (w - Draw.width(tr, label)) / 2, y + (h - 8) / 2, Draw.lerp(color, 0xFFFFFFFF, hv * 0.5f), false);
        if (act) hits.add(new Hit(x, y, w, h, (b, hx, hy) -> { if (b == 0) r.run(); }));
    }

    private int drawSetting(DrawContext ctx, TextRenderer tr, Setting<?> s, int x, int y, int w, int mx, int my, boolean act) {
        int h = rowH(s);
        boolean hov = act && in(mx, my, x, y, w, h);
        int labelCol = hov ? Theme.text() : Theme.soft();

        if (s instanceof BoolSetting b) {
            float t = anim(b, "t", b.get() ? 1f : 0f, 20f);
            Draw.text(ctx, tr, b.name, x + 2, y + 5, labelCol, false);
            if (Theme.glass()) { // iOS-style switch with a floating white knob
                int px = x + w - 25, py = y + 3;
                Draw.round(ctx, px, py, 25, 13, 6.5f, Draw.lerp(Theme.track(), Theme.accent(), t));
                Draw.glassFill(ctx, px, py, 25, 6, 6.5f, 0x44FFFFFF, 0x00FFFFFF, true, false);
                int kx = px + 1 + Math.round(12 * t);
                Draw.round(ctx, kx, py + 2, 11, 11, 5.5f, 0x34000000, false);
                Draw.round(ctx, kx, py + 1, 11, 11, 5.5f, 0xFFFFFFFF);
            } else {
                int px = x + w - 22, py = y + 4;
                Draw.round(ctx, px, py, 20, 10, 5, Draw.lerp(Theme.track(), Theme.accent(), t));
                Draw.round(ctx, px + 1 + Math.round(10 * t), py + 1, 8, 8, 4, Theme.knob());
            }
            if (act) hits.add(new Hit(x, y, w, h, (btn, hx, hy) -> { b.toggle(); Sounds.click(b.get()); }));
        } else if (s instanceof ModeSetting m) {
            Draw.text(ctx, tr, m.name, x + 2, y + 5, labelCol, false);
            String v = m.get();
            int vw = Draw.width(tr, v);
            Draw.round(ctx, x + w - vw - 11, y + 2, vw + 9, 13, 6, Theme.accentA(0x30));
            Draw.text(ctx, tr, v, x + w - vw - 6, y + 5, Theme.accent(), false);
            if (act) hits.add(new Hit(x, y, w, h, (btn, hx, hy) -> { m.cycle(btn == 1 ? -1 : 1); Sounds.click(true); }));
        } else if (s instanceof StringSetting ss) {
            Draw.text(ctx, tr, ss.name, x + 2, y + 3, labelCol, false);
            drawField(ctx, tr, ss, "type here...", x, y + 15, w, 16, mx, my, act, null);
        } else if (s instanceof BlockListSetting bl) {
            int cy = y + 3;
            if (bl.searchable) {
                drawField(ctx, tr, bl, "Search blocks...", x, cy, w, 14, mx, my, act, null);
                cy += 16;
                List<String> res = bl.results();
                int n = Math.min(res.size(), BlockListSetting.MAX_RESULTS);
                for (int i = 0; i < n; i++) {
                    final String id = res.get(i);
                    boolean isSel = bl.has(id);
                    if (act && in(mx, my, x, cy, w, 12)) Draw.round(ctx, x, cy, w, 12, 4, Theme.rowHover());
                    Draw.text(ctx, tr, Draw.trim(tr, (isSel ? "+ " : "  ") + pretty(id), w - 6), x + 4, cy + 2,
                            isSel ? Theme.accent() : Theme.soft(), false);
                    if (act) hits.add(new Hit(x, cy, w, 12, (btn, hx, hy) -> bl.toggle(id)));
                    cy += 12;
                }
                cy += 2;
                int bw = (w - 4) / 2;
                button(ctx, tr, "All ores", x, cy, bw, 14, act, mx, my, Theme.accent(), bl::addAllOres);
                button(ctx, tr, "Clear", x + bw + 4, cy, bw, 14, act, mx, my, 0xFFFF5C6C, bl::clear);
                cy += 18;
            } else {
                Draw.text(ctx, tr, Draw.trim(tr, bl.hint, w - 4), x + 2, cy + 1, Theme.dim(), false);
                cy += 13;
                button(ctx, tr, "Clear selection", x, cy, w, 14, act, mx, my, 0xFFFF5C6C, bl::clear);
                cy += 18;
            }
            List<String> selected = new ArrayList<>(bl.get());
            int shownN = Math.min(selected.size(), BlockListSetting.MAX_SHOWN);
            for (int i = 0; i < shownN; i++) {
                final String id = selected.get(i);
                boolean hv = act && in(mx, my, x, cy, w, 11);
                Draw.text(ctx, tr, Draw.trim(tr, pretty(id), w - 16), x + 4, cy + 1, hv ? Theme.text() : Theme.accent(), false);
                Draw.text(ctx, tr, "x", x + w - 9, cy + 1, hv ? 0xFFFF5555 : Theme.dim(), false);
                if (act) hits.add(new Hit(x, cy, w, 11, (btn, hx, hy) -> bl.toggle(id)));
                cy += 11;
            }
            if (selected.size() > shownN)
                Draw.text(ctx, tr, "+" + (selected.size() - shownN) + " more", x + 4, cy + 1, Theme.dim(), false);
        } else if (s instanceof Slidable sl) {
            float f = anim(s, "f", (float) sl.fraction(), dragSlider == sl ? 60f : 22f);
            Draw.text(ctx, tr, s.name, x + 2, y + 4, labelCol, false);
            String v = s.display();
            if (!v.isEmpty()) Draw.text(ctx, tr, v, x + w - 2 - Draw.width(tr, v), y + 4, Theme.dim(), false);
            final int tx = x + 2, tw = w - 4, ty = y + h - 9;
            if (s instanceof ColorSetting) {
                for (int i = 0; i < tw; i += 2)
                    Draw.rect(ctx, tx + i, ty, Math.min(2, tw - i), 5, 0xFF000000 | MathHelper.hsvToRgb(i / (float) tw, 0.75f, 1f));
                Draw.round(ctx, tx + Math.round(f * (tw - 3)) - 1, ty - 2, 5, 9, 2, 0xFFFFFFFF);
            } else {
                Draw.round(ctx, tx, ty, tw, 5, 2.5f, Theme.track());
                int fw = Math.max(5, Math.round(tw * f));
                Draw.round(ctx, tx, ty, fw, 5, 2.5f, Theme.accent());
                if (dragSlider == sl || hov) Draw.round(ctx, tx + fw - 7, ty - 4, 14, 13, 6.5f, Theme.accentA(0x40));
                if (Theme.glass()) {
                    Draw.round(ctx, tx + 2, ty, Math.max(1, fw - 4), 1, 0.5f, 0x66FFFFFF);
                    Draw.round(ctx, tx + fw - 5, ty + 1, 11, 11, 5.5f, 0x34000000, false);
                    Draw.round(ctx, tx + fw - 5, ty - 3, 11, 11, 5.5f, 0xFFFFFFFF);
                } else Draw.round(ctx, tx + fw - 4, ty - 1, 8, 7, 3.5f, Theme.knob());
            }
            if (act) hits.add(new Hit(x, y, w, h, (btn, hx, hy) -> {
                if (btn != 0) return;
                dragSlider = sl; dragX = tx; dragW = tw;
                sl.setFraction((hx - tx) / (double) tw);
            }));
        }
        return y + h;
    }

    // ================= input =================
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (closing) return true;
        edit = null;
        editEnter = null;
        bindingModule = null;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (in((int) mx, (int) my, h.x, h.y, h.w, h.h)) { h.click.on(button, (int) mx, (int) my); return true; }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragPanel = null; dragSlider = null;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragPanel != null) {
            int[] st = STATE.get(dragPanel);
            st[0] = MathHelper.clamp((int) mx - dragOx, 0, Math.max(0, width - W));
            st[1] = (int) my - dragOy - Math.round(scroll);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        scrollTarget += (float) vAmount * 28f;
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (edit != null) {
            char f = edit.filter(chr);
            if (f != 0 && edit.text().length() < edit.maxLen()) edit.setText(edit.text() + f);
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (bindingModule != null) {
            if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) return true; // reserved for the GUI
            if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) bindingModule.key = -1;
            else if (key != GLFW.GLFW_KEY_ESCAPE) bindingModule.key = key;
            bindingModule = null;
            return true;
        }
        if (edit != null) {
            if (key == GLFW.GLFW_KEY_BACKSPACE && !edit.text().isEmpty()) {
                edit.setText(edit.text().substring(0, edit.text().length() - 1));
            } else if (Screen.isPaste(key)) {
                String clip = client.keyboard.getClipboard();
                StringBuilder sb = new StringBuilder(edit.text());
                for (char c : clip.toCharArray()) {
                    char f = edit.filter(c);
                    if (f != 0 && sb.length() < edit.maxLen()) sb.append(f);
                }
                edit.setText(sb.toString());
            } else if (key == GLFW.GLFW_KEY_ESCAPE) {
                edit = null; editEnter = null;
            } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                if (editEnter != null) editEnter.run(); else edit = null;
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) { close(); return true; }
        return super.keyPressed(key, scan, mods);
    }
}
