package dev.dynamicdock;

import java.io.IOException;
import java.nio.file.*;
import java.util.Locale;
import java.util.Properties;
import java.util.function.Consumer;

/** Independent, versioned preferences. Invalid fields recover without discarding valid ones. */
public final class DockConfig {
    public boolean enabled = true, shelf = true, frames = true, autoHide = false;
    public boolean edges = true, shadow = true;
    public float scale = .7f, magnification = .8f, falloff = 1.5f, hideDelay = 20;
    public float opacity = 224f / 255, blur = 1, refraction = 1, cornerRadius = 11;
    public float edgeIntensity = 1, shadowStrength = 1, tintIntensity = 0;
    public int tintColor = 0x7DAFFF;

    public void validate() {
        scale = finite(scale, .7f, .5f, 2);
        magnification = finite(magnification, .8f, 0, 2.5f);
        falloff = finite(falloff, 1.5f, .5f, 8);
        hideDelay = finite(hideDelay, 20, 1, 120);
        opacity = finite(opacity, 224f / 255, 0, 1);
        blur = finite(blur, 1, 0, 2);
        refraction = finite(refraction, 1, 0, 2);
        cornerRadius = finite(cornerRadius, 11, 0, 11);
        edgeIntensity = finite(edgeIntensity, 1, 0, 2);
        shadowStrength = finite(shadowStrength, 1, 0, 2);
        tintIntensity = finite(tintIntensity, 0, 0, 1);
        tintColor &= 0xFFFFFF;
    }
    private static float finite(float v, float fallback, float min, float max) {
        return Math.clamp(Float.isFinite(v) ? v : fallback, min, max);
    }
    public static DockConfig load(Path file, Consumer<String> warning) {
        DockConfig c = new DockConfig();
        if (!Files.exists(file)) return c;
        Properties p = new Properties();
        try (var r = Files.newBufferedReader(file)) { p.load(r); }
        catch (IOException | IllegalArgumentException e) {
            warning.accept("Cannot read Dynamic Dock config; using defaults: " + e.getMessage());
            return c;
        }
        for (var field : DockConfig.class.getFields()) {
            String value = p.getProperty(field.getName());
            if (value == null) continue;
            try {
                if (field.getType() == boolean.class) {
                    if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) throw new IllegalArgumentException();
                    field.setBoolean(c, Boolean.parseBoolean(value));
                } else if (field.getType() == float.class) field.setFloat(c, Float.parseFloat(value));
                else if (field.getName().equals("tintColor")) {
                    if (!value.matches("[0-9a-fA-F]{6}")) throw new IllegalArgumentException();
                    field.setInt(c, Integer.parseInt(value, 16));
                }
            } catch (ReflectiveOperationException | IllegalArgumentException e) {
                warning.accept("Invalid Dynamic Dock setting: " + field.getName() + "; using default");
            }
        }
        c.validate();
        return c;
    }
    public void save(Path file, Consumer<String> warning) {
        validate();
        Properties p = new Properties();
        p.setProperty("configVersion", "1");
        for (var field : DockConfig.class.getFields()) {
            try { p.setProperty(field.getName(), field.getName().equals("tintColor")
                    ? String.format(Locale.ROOT, "%06X", tintColor) : field.get(this).toString()); }
            catch (IllegalAccessException e) { throw new IllegalStateException(e); }
        }
        Path temp = null;
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            temp = Files.createTempFile(file.toAbsolutePath().getParent(), "dynamicdock-", ".tmp");
            try (var w = Files.newBufferedWriter(temp)) { p.store(w, "Dynamic Dock preferences"); }
            try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException e) { warning.accept("Cannot save Dynamic Dock config: " + e.getMessage()); }
        finally { if (temp != null) try { Files.deleteIfExists(temp); } catch (IOException ignored) { } }
    }
}
