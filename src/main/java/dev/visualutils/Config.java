package dev.visualutils;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Properties;

/** Zapamietuje, ktore moduly sa wlaczone i jaki jest motyw (plik config/visualutils.properties). */
public final class Config {

    private static final EnumMap<Module, Boolean> STATE = new EnumMap<>(Module.class);
    private static int theme = 0;

    private Config() {}

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("visualutils.properties");
    }

    public static void load() {
        Properties props = new Properties();
        Path f = file();
        if (Files.exists(f)) {
            try (InputStream in = Files.newInputStream(f)) {
                props.load(in);
            } catch (IOException ignored) {
                // brak pliku albo blad odczytu - zostaja ustawienia domyslne (wszystko wylaczone)
            }
        }
        for (Module m : Module.values()) {
            STATE.put(m, Boolean.parseBoolean(props.getProperty(m.name(), "false")));
        }
        try {
            theme = Integer.parseInt(props.getProperty("THEME", "0").trim());
        } catch (NumberFormatException e) {
            theme = 0;
        }
    }

    public static void save() {
        Properties props = new Properties();
        for (Module m : Module.values()) {
            props.setProperty(m.name(), Boolean.toString(isOn(m)));
        }
        props.setProperty("THEME", Integer.toString(theme));
        try (OutputStream out = Files.newOutputStream(file())) {
            props.store(out, "VisualUtils");
        } catch (IOException ignored) {
            // nie udalo sie zapisac - ustawienia po prostu nie zostana zapamietane
        }
    }

    public static boolean isOn(Module m) {
        return STATE.getOrDefault(m, false);
    }

    public static boolean toggle(Module m) {
        boolean value = !isOn(m);
        STATE.put(m, value);
        return value;
    }

    public static int getTheme() {
        return theme;
    }

    public static void setTheme(int value) {
        theme = value;
    }
}
