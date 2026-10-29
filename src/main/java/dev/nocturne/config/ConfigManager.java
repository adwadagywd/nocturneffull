package dev.nocturne.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.nocturne.module.Module;
import dev.nocturne.module.ModuleManager;
import dev.nocturne.setting.Setting;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<Map<String, Map<String, Object>>>() {}.getType();

    private ConfigManager() {}

    private static Path mainFile() { return FabricLoader.getInstance().getConfigDir().resolve("nocturne.json"); }

    public static Path dir() {
        Path d = FabricLoader.getInstance().getConfigDir().resolve("nocturne").resolve("configs");
        try { Files.createDirectories(d); } catch (Exception ignored) {}
        return d;
    }

    public static String clean(String n) {
        String c = n == null ? "" : n.replaceAll("[^A-Za-z0-9_\\-]", "");
        return c.length() > 24 ? c.substring(0, 24) : c;
    }

    private static Map<String, Map<String, Object>> snapshot() {
        Map<String, Map<String, Object>> root = new LinkedHashMap<>();
        for (Module m : ModuleManager.ALL) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("_enabled", m.isEnabled());
            o.put("_key", m.key);
            for (Setting<?> s : m.settings) o.put(s.name, s.save());
            root.put(m.name, o);
        }
        return root;
    }

    private static void apply(Map<String, Map<String, Object>> root) {
        boolean old = dev.nocturne.module.impl.Notifications.suppress;
        dev.nocturne.module.impl.Notifications.suppress = true;
        try { applyInner(root); } finally { dev.nocturne.module.impl.Notifications.suppress = old; }
    }

    private static void applyInner(Map<String, Map<String, Object>> root) {
        for (Module m : ModuleManager.ALL) {
            Map<String, Object> o = root.get(m.name);
            if (o == null) continue;
            for (Setting<?> s : m.settings)
                if (o.containsKey(s.name)) { try { s.load(o.get(s.name)); } catch (Exception ignored) {} }
            if (o.get("_key") instanceof Number kn) m.key = kn.intValue();
            if (m.persistEnabled() && o.get("_enabled") instanceof Boolean b) m.setEnabled(b);
        }
    }

    private static boolean write(Path f, Map<String, Map<String, Object>> data) {
        try { Files.writeString(f, GSON.toJson(data)); return true; } catch (Exception e) { return false; }
    }

    private static Map<String, Map<String, Object>> read(Path f) {
        if (!Files.exists(f)) return null;
        try (Reader r = Files.newBufferedReader(f)) { return GSON.fromJson(r, TYPE); } catch (Exception e) { return null; }
    }

    // ---- automatic main config ----
    public static void save() { write(mainFile(), snapshot()); }

    public static void load() {
        Map<String, Map<String, Object>> r = read(mainFile());
        if (r != null) apply(r);
    }

    // ---- named configs ----
    public static boolean saveAs(String name) {
        String n = clean(name);
        return !n.isEmpty() && write(dir().resolve(n + ".json"), snapshot());
    }

    public static boolean loadFrom(String name) {
        Map<String, Map<String, Object>> r = read(dir().resolve(clean(name) + ".json"));
        if (r == null) return false;
        apply(r);
        save();
        return true;
    }

    public static boolean delete(String name) {
        try { return Files.deleteIfExists(dir().resolve(clean(name) + ".json")); } catch (Exception e) { return false; }
    }

    public static List<String> list() {
        List<String> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(dir())) {
            s.forEach(p -> {
                String f = p.getFileName().toString();
                if (f.endsWith(".json")) out.add(f.substring(0, f.length() - 5));
            });
        } catch (Exception ignored) {}
        Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        return out;
    }
}
