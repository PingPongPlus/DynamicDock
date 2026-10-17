package dev.dynamicdock;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;

/** Dependency-free checks for persisted input and the full HUD footprint. */
public final class DockChecks {
    private static int checks;
    private static void check(boolean condition, String detail) {
        checks++;
        if (!condition) throw new AssertionError(detail);
    }
    public static void main(String[] args) throws Exception {
        var dir = Files.createTempDirectory("dynamicdock-check-");
        var file = dir.resolve("dynamicdock.properties");
        var warnings = new ArrayList<String>();
        try {
            DockConfig c = DockConfig.load(file, warnings::add);
            check(c.enabled && c.scale == .7f && c.cornerRadius == 11, "reference defaults");
            c.enabled = false; c.magnification = 0; c.tintColor = 0x12AB34; c.blur = 1.75f;
            c.save(file, warnings::add);
            DockConfig loaded = DockConfig.load(file, warnings::add);
            check(!loaded.enabled && loaded.magnification == 0 && loaded.tintColor == 0x12AB34 && loaded.blur == 1.75f, "round trip");
            c.save(file, warnings::add);
            try (var files = Files.list(dir)) { check(files.count() == 1, "atomic save removes temporary files"); }
            Files.writeString(file, "scale=NaN\nmagnification=Infinity\nfalloff=-100\nopacity=100\nenabled=bad\nblur=bad\ntintColor=bad\nhideDelay=500\nshelf=false\n");
            loaded = DockConfig.load(file, warnings::add);
            check(loaded.scale == .7f && loaded.magnification == .8f, "non-finite recovery");
            check(loaded.falloff == .5f && loaded.opacity == 1 && loaded.hideDelay == 120, "range clamping");
            check(loaded.enabled && loaded.blur == 1 && loaded.tintColor == 0x7DAFFF && !loaded.shelf, "individual malformed fields preserve valid fields");
            Files.writeString(file, "scale=\\uZZZZ");
            loaded = DockConfig.load(file, warnings::add);
            check(loaded.scale == .7f && loaded.enabled, "malformed properties recovery");
            check(!warnings.isEmpty(), "invalid configuration is reported");
        } finally { Files.deleteIfExists(file); Files.deleteIfExists(dir); }
        float[] scales = new float[10];
        for (int width : new int[]{160, 240, 320, 427, 640, 1920})
            for (float size : new float[]{.5f, .7f, 1, 2})
                for (float mag : new float[]{0, .8f, 2.5f})
                    for (float falloff : new float[]{.5f, 1.5f, 8})
                        for (int hand = 0; hand < 2; hand++)
                            for (float center = 0; center <= 8; center += .125f) {
                                for (int i = 0; i < 10; i++) scales[i] = mag == 0 ? 1 : Math.min(3.65f, 1 + mag * DockLayout.curve(Math.abs((i < 9 ? i : hand == 0 ? -1 : 9) - center), falloff) + (i == Math.round(center) ? .15f : 0));
                                DockLayout l = new DockLayout(scales, center, true, hand == 0, true, size, width, 80, true, true, 2, 5);
                                check(Math.abs(l.left + l.right) < .001f, "centered shelf");
                                check(l.size > 0 && l.size <= size, "fit never enlarges requested scale");
                                for (int i = 0; i < 9; i++) {
                                    check(Math.abs(l.x[i]) * l.size + 13 * scales[i] * l.size < width / 2f, "slot/frame fits");
                                    if (i > 0) check(l.x[i] > l.x[i - 1], "slot order");
                                }
                                check((Math.abs(l.x[9]) + 18 * scales[9]) * l.size < width / 2f, "offhand fits");
                                check((Math.max(-l.left, l.right) + 26) * l.size < width / 2f, "attack indicator fits");
                                check((l.bottom - l.top) * l.size <= 80.001f, "vertical fit including pickup and shadow");
                            }
        Arrays.fill(scales, 1);
        DockLayout plain = new DockLayout(scales, 4, false, true, false, .7f, 320, 100, false, false, 0, 0);
        for (int i = 1; i < 9; i++) check(plain.x[i] - plain.x[i - 1] == 20, "zero magnification has vanilla slot spacing");
        for (float slotScale : new float[]{1, 1.95f, 3.65f}) {
            Arrays.fill(scales, slotScale);
            for (float dockScale : new float[]{.5f, .7f, 2}) {
                DockLayout framed = new DockLayout(scales, 4, false, true, false, dockScale, 320, 80, true, false, 0, 0);
                float frameBottom = -3 + 4 * slotScale;
                check(framed.bottom >= frameBottom, "complete 24-row frame fits without shelf/shadow padding");
                float screenBottom = 240 - 2 - framed.bottom * framed.size + frameBottom * framed.size;
                check(screenBottom <= 238.001f, "closing black border retains the window inset");
            }
        }
        check(DockLayout.curve(0, 1.5f) == 1 && Math.abs(DockLayout.curve(2, 1.5f)) < .0001f, "falloff endpoints");
        System.out.println("Dynamic Dock checks passed: " + checks);
    }
}
