package dev.zombie;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.zombie.hack.Hack;
import dev.zombie.hack.HackManager;
import dev.zombie.setting.BoolSetting;
import dev.zombie.setting.ModeSetting;
import dev.zombie.setting.NumberSetting;
import dev.zombie.setting.Setting;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

/** Saves module settings to config/zombieclient.json so they survive restarts. */
public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private Config() {}

    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("zombieclient.json"); }

    public static void load(HackManager manager) {
        Path f = file();
        if (!Files.exists(f)) return;
        try (Reader r = Files.newBufferedReader(f)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            for (Hack h : manager.all()) {
                for (Setting s : h.settings()) {
                    String key = h.name + "." + s.name;
                    if (!root.has(key)) continue;
                    if (s instanceof NumberSetting n) n.set(root.get(key).getAsDouble());
                    else if (s instanceof BoolSetting b) b.set(root.get(key).getAsBoolean());
                    else if (s instanceof ModeSetting m) m.set(root.get(key).getAsString());
                }
            }
        } catch (Exception ignored) {
            // a broken config file just means defaults are used
        }
    }

    public static void save(HackManager manager) {
        try {
            JsonObject root = new JsonObject();
            for (Hack h : manager.all()) {
                for (Setting s : h.settings()) {
                    String key = h.name + "." + s.name;
                    if (s instanceof NumberSetting n) root.addProperty(key, n.get());
                    else if (s instanceof BoolSetting b) root.addProperty(key, b.get());
                    else if (s instanceof ModeSetting m) root.addProperty(key, m.get());
                }
            }
            Path f = file();
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(root));
        } catch (Exception ignored) {
        }
    }
}
