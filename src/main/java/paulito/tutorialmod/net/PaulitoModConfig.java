package paulito.tutorialmod.net;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.fabricmc.loader.api.FabricLoader;

public class PaulitoModConfig {
    private static final Pattern FIELD_PATTERN = Pattern.compile("\"([a-zA-Z0-9_]+)\"\\s*:\\s*(true|false|-?\\d+)");

    public boolean enabled = true;
    public boolean debugMode = false;

    public boolean killFeedEnabled = true;
    public boolean showWeaponName = true;
    public boolean showDimension = true;
    public boolean showSurvivalInfo = true;

    public boolean streaksEnabled = true;
    public int normalStreakThreshold = 50;
    public int grandStreakThreshold = 500;
    public boolean resetOnDimensionChange = true;
    public boolean resetOnRespawn = true;

    public boolean normalRewardEnabled = true;
    public int normalRewardXp = 25;
    public boolean grandRewardEnabled = true;
    public int grandRewardXp = 200;

    public boolean damageTrackingEnabled = true;
    public boolean dimensionTrackingEnabled = true;
    public boolean healthTrackingEnabled = true;
    public boolean joinStateRefreshEnabled = true;
    public boolean respawnStateRefreshEnabled = true;

    public static PaulitoModConfig load() {
        Path configFile = FabricLoader.getInstance().getConfigDir().resolve("paulitomod.json");

        try {
            Files.createDirectories(configFile.getParent());

            if (Files.notExists(configFile)) {
                PaulitoModConfig config = new PaulitoModConfig();
                save(config);
                return config;
            }

            String content = Files.readString(configFile, StandardCharsets.UTF_8);
            PaulitoModConfig config = parse(content);
            save(config);
            return config;
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo cargar la configuración del mod.", e);
        }
    }

    public static void save(PaulitoModConfig config) throws IOException {
        Path configFile = FabricLoader.getInstance().getConfigDir().resolve("paulitomod.json");
        Files.createDirectories(configFile.getParent());

        String json = "{\n"
                + "  \"general\": {\n"
                + "    \"enabled\": " + config.enabled + ",\n"
                + "    \"debugMode\": " + config.debugMode + "\n"
                + "  },\n"
                + "  \"killFeed\": {\n"
                + "    \"enabled\": " + config.killFeedEnabled + ",\n"
                + "    \"showWeaponName\": " + config.showWeaponName + ",\n"
                + "    \"showDimension\": " + config.showDimension + ",\n"
                + "    \"showSurvivalInfo\": " + config.showSurvivalInfo + "\n"
                + "  },\n"
                + "  \"streaks\": {\n"
                + "    \"enabled\": " + config.streaksEnabled + ",\n"
                + "    \"normalThreshold\": " + config.normalStreakThreshold + ",\n"
                + "    \"grandThreshold\": " + config.grandStreakThreshold + ",\n"
                + "    \"resetOnDimensionChange\": " + config.resetOnDimensionChange + ",\n"
                + "    \"resetOnRespawn\": " + config.resetOnRespawn + "\n"
                + "  },\n"
                + "  \"rewards\": {\n"
                + "    \"normalRewardEnabled\": " + config.normalRewardEnabled + ",\n"
                + "    \"normalRewardXp\": " + config.normalRewardXp + ",\n"
                + "    \"grandRewardEnabled\": " + config.grandRewardEnabled + ",\n"
                + "    \"grandRewardXp\": " + config.grandRewardXp + "\n"
                + "  },\n"
                + "  \"tracking\": {\n"
                + "    \"damageTrackingEnabled\": " + config.damageTrackingEnabled + ",\n"
                + "    \"dimensionTrackingEnabled\": " + config.dimensionTrackingEnabled + ",\n"
                + "    \"healthTrackingEnabled\": " + config.healthTrackingEnabled + ",\n"
                + "    \"joinStateRefreshEnabled\": " + config.joinStateRefreshEnabled + ",\n"
                + "    \"respawnStateRefreshEnabled\": " + config.respawnStateRefreshEnabled + "\n"
                + "  }\n"
                + "}\n";

        Files.writeString(configFile, json, StandardCharsets.UTF_8);
    }

    private static PaulitoModConfig parse(String content) {
        PaulitoModConfig config = new PaulitoModConfig();
        Matcher matcher = FIELD_PATTERN.matcher(content);

        while (matcher.find()) {
            String key = matcher.group(1);
            String value = matcher.group(2);
            applyValue(config, key, value);
        }

        return config;
    }

    private static void applyValue(PaulitoModConfig config, String key, String value) {
        switch (key) {
            case "enabled" -> config.enabled = Boolean.parseBoolean(value);
            case "debugMode" -> config.debugMode = Boolean.parseBoolean(value);
            case "killFeedEnabled" -> config.killFeedEnabled = Boolean.parseBoolean(value);
            case "showWeaponName" -> config.showWeaponName = Boolean.parseBoolean(value);
            case "showDimension" -> config.showDimension = Boolean.parseBoolean(value);
            case "showSurvivalInfo" -> config.showSurvivalInfo = Boolean.parseBoolean(value);
            case "streaksEnabled" -> config.streaksEnabled = Boolean.parseBoolean(value);
            case "normalThreshold" -> config.normalStreakThreshold = Integer.parseInt(value);
            case "grandThreshold" -> config.grandStreakThreshold = Integer.parseInt(value);
            case "resetOnDimensionChange" -> config.resetOnDimensionChange = Boolean.parseBoolean(value);
            case "resetOnRespawn" -> config.resetOnRespawn = Boolean.parseBoolean(value);
            case "normalRewardEnabled" -> config.normalRewardEnabled = Boolean.parseBoolean(value);
            case "normalRewardXp" -> config.normalRewardXp = Integer.parseInt(value);
            case "grandRewardEnabled" -> config.grandRewardEnabled = Boolean.parseBoolean(value);
            case "grandRewardXp" -> config.grandRewardXp = Integer.parseInt(value);
            case "damageTrackingEnabled" -> config.damageTrackingEnabled = Boolean.parseBoolean(value);
            case "dimensionTrackingEnabled" -> config.dimensionTrackingEnabled = Boolean.parseBoolean(value);
            case "healthTrackingEnabled" -> config.healthTrackingEnabled = Boolean.parseBoolean(value);
            case "joinStateRefreshEnabled" -> config.joinStateRefreshEnabled = Boolean.parseBoolean(value);
            case "respawnStateRefreshEnabled" -> config.respawnStateRefreshEnabled = Boolean.parseBoolean(value);
            default -> {
            }
        }
    }
}
