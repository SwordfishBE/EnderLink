package net.enderlink.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.enderlink.EnderLinkMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public final class EnderLinkConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern COMMENT_PATTERN = Pattern.compile("(?m)^\\s*//.*(?:\\R|$)");
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve(EnderLinkMod.MOD_ID);
    private static final Path CONFIG_PATH = CONFIG_DIR.resolve("enderlink.json");

    private static EnderLinkConfig instance;

    public List<String> teleportBlocks = new ArrayList<>(List.of(
        "minecraft:gold_block",
        "minecraft:lapis_block"
    ));
    public int chargeTicks = 60;
    public boolean safetyEnabled = true;
    public boolean particlesEnabled = true;
    public String particleType = "minecraft:witch";
    public int warmupParticleCount = 8;
    public int arrivalBurstParticleCount = 24;
    public int arrivalTrailParticleCount = 6;
    public int arrivalParticleDurationTicks = 60;
    public boolean soundEnabled = true;
    public String soundEvent = "minecraft:entity.illusioner.mirror_move";
    public float soundVolume = 0.8F;
    public float soundPitch = 1.0F;
    public List<String> allowedDimensions = new ArrayList<>(List.of(
        "minecraft:overworld",
        "minecraft:the_nether",
        "minecraft:the_end"
    ));
    public boolean luckPerms = false;
    public int maxPadsPerPlayer = 10;
    public int maxLinksPerPlayer = 5;
    public CommandAccessMode addAccess = CommandAccessMode.OPS_ONLY;
    public CommandAccessMode linkAccess = CommandAccessMode.OPS_ONLY;
    public CommandAccessMode unlinkAccess = CommandAccessMode.OPS_ONLY;
    public CommandAccessMode removeAccess = CommandAccessMode.OPS_ONLY;
    public CommandAccessMode renameAccess = CommandAccessMode.OPS_ONLY;
    public CommandAccessMode listAccess = CommandAccessMode.EVERYONE;
    public CommandAccessMode infoAccess = CommandAccessMode.EVERYONE;

    private EnderLinkConfig() {
    }

    public static EnderLinkConfig load() {
        LoadResult loadResult = readOrCreateConfig();
        instance = loadResult.config();
        if (loadResult.shouldSave()) {
            instance.save();
        }
        return instance;
    }

    public static EnderLinkConfig loadForEditing() {
        return readOrCreateConfig().config();
    }

    public static void applyEditedConfig(EnderLinkConfig editedConfig) {
        instance = editedConfig == null ? new EnderLinkConfig() : editedConfig;
        instance.normalize();
        instance.save();
        EnderLinkMod.scheduleRuntimeRefresh();
    }

    public static EnderLinkConfig get() {
        return instance == null ? load() : instance;
    }

    private static LoadResult readOrCreateConfig() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                String rawJson = Files.readString(CONFIG_PATH);
                EnderLinkConfig loadedConfig = GSON.fromJson(stripComments(rawJson), EnderLinkConfig.class);
                if (loadedConfig == null) {
                    loadedConfig = new EnderLinkConfig();
                }
                loadedConfig.normalize();
                return new LoadResult(loadedConfig, true);
            } catch (Exception exception) {
                EnderLinkMod.LOGGER.warn(
                    "{} Failed to load config, using defaults without overwriting '{}': {}",
                    EnderLinkMod.logPrefix(),
                    CONFIG_PATH.getFileName(),
                    exception.getMessage()
                );
            }
        }

        EnderLinkConfig defaultConfig = new EnderLinkConfig();
        defaultConfig.normalize();
        return new LoadResult(defaultConfig, !Files.exists(CONFIG_PATH));
    }

    private void save() {
        try {
            Files.createDirectories(CONFIG_DIR);
            Files.writeString(CONFIG_PATH, toCommentedJson());
        } catch (IOException exception) {
            EnderLinkMod.LOGGER.warn("{} Failed to save config: {}", EnderLinkMod.logPrefix(), exception.getMessage());
        }
    }

    private void normalize() {
        teleportBlocks = normalizeResourceIdentifiers(teleportBlocks, List.of("minecraft:gold_block", "minecraft:lapis_block"));
        allowedDimensions = normalizeResourceIdentifiers(allowedDimensions, List.of(
            "minecraft:overworld",
            "minecraft:the_nether",
            "minecraft:the_end"
        ));

        chargeTicks = Math.max(0, chargeTicks);
        warmupParticleCount = Math.max(0, warmupParticleCount);
        arrivalBurstParticleCount = Math.max(0, arrivalBurstParticleCount);
        arrivalTrailParticleCount = Math.max(0, arrivalTrailParticleCount);
        arrivalParticleDurationTicks = Math.max(0, arrivalParticleDurationTicks);
        maxPadsPerPlayer = Math.max(0, maxPadsPerPlayer);
        maxLinksPerPlayer = Math.max(0, maxLinksPerPlayer);
        soundVolume = Math.max(0.0F, soundVolume);
        soundPitch = Math.max(0.0F, soundPitch);

        particleType = Objects.requireNonNullElse(normalizeResourceIdentifier(particleType), "minecraft:witch");
        soundEvent = Objects.requireNonNullElse(normalizeResourceIdentifier(soundEvent), "minecraft:entity.illusioner.mirror_move");

        addAccess = CommandAccessMode.normalize(addAccess, CommandAccessMode.OPS_ONLY);
        linkAccess = CommandAccessMode.normalize(linkAccess, CommandAccessMode.OPS_ONLY);
        unlinkAccess = CommandAccessMode.normalize(unlinkAccess, CommandAccessMode.OPS_ONLY);
        removeAccess = CommandAccessMode.normalize(removeAccess, CommandAccessMode.OPS_ONLY);
        renameAccess = CommandAccessMode.normalize(renameAccess, CommandAccessMode.OPS_ONLY);
        listAccess = CommandAccessMode.normalize(listAccess, CommandAccessMode.EVERYONE);
        infoAccess = CommandAccessMode.normalize(infoAccess, CommandAccessMode.EVERYONE);
    }

    private static List<String> normalizeResourceIdentifiers(List<String> values, List<String> fallback) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                String normalizedValue = normalizeResourceIdentifier(value);
                if (normalizedValue != null) {
                    normalized.add(normalizedValue);
                }
            }
        }

        if (normalized.isEmpty()) {
            normalized.addAll(fallback);
        }

        return new ArrayList<>(normalized);
    }

    private static String normalizeResourceIdentifier(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        return trimmed.contains(":") ? trimmed : "minecraft:" + trimmed;
    }

    private static String stripComments(String rawJson) {
        return COMMENT_PATTERN.matcher(rawJson).replaceAll("");
    }

    private String toCommentedJson() {
        String newline = System.lineSeparator();
        return "{" + newline
            + "  // Block IDs that can act as EnderLink pads. Players may hide these with carpets and they still work." + newline
            + "  \"teleportBlocks\": " + GSON.toJson(teleportBlocks) + "," + newline
            + newline
            + "  // Warmup time before teleporting. 20 ticks equals 1 second." + newline
            + "  \"chargeTicks\": " + chargeTicks + "," + newline
            + newline
            + "  // When true, destination checks require enough safe space, reject fluids, and block unsafe arrivals." + newline
            + "  \"safetyEnabled\": " + safetyEnabled + "," + newline
            + newline
            + "  // Spawn the configured particle during the warmup and again on arrival." + newline
            + "  \"particlesEnabled\": " + particlesEnabled + "," + newline
            + newline
            + "  // Simple particle ID to use for warmup and arrival. Simple particles work best." + newline
            + "  \"particleType\": " + GSON.toJson(particleType) + "," + newline
            + newline
            + "  // Number of particles to spawn around the player while the teleport is charging." + newline
            + "  \"warmupParticleCount\": " + warmupParticleCount + "," + newline
            + newline
            + "  // Number of particles to spawn immediately when the teleport completes." + newline
            + "  \"arrivalBurstParticleCount\": " + arrivalBurstParticleCount + "," + newline
            + newline
            + "  // Number of particles to spawn each tick during the short arrival trail effect." + newline
            + "  \"arrivalTrailParticleCount\": " + arrivalTrailParticleCount + "," + newline
            + newline
            + "  // How long arrival particles should continue after teleporting. 20 ticks equals 1 second." + newline
            + "  \"arrivalParticleDurationTicks\": " + arrivalParticleDurationTicks + "," + newline
            + newline
            + "  // Play the configured sound at the destination when the teleport completes." + newline
            + "  \"soundEnabled\": " + soundEnabled + "," + newline
            + newline
            + "  // Sound event ID to play on arrival." + newline
            + "  \"soundEvent\": " + GSON.toJson(soundEvent) + "," + newline
            + newline
            + "  // Sound volume for the arrival sound." + newline
            + "  \"soundVolume\": " + soundVolume + "," + newline
            + newline
            + "  // Sound pitch for the arrival sound." + newline
            + "  \"soundPitch\": " + soundPitch + "," + newline
            + newline
            + "  // Dimensions where EnderLink pads are allowed to participate in teleports." + newline
            + "  \"allowedDimensions\": " + GSON.toJson(allowedDimensions) + "," + newline
            + newline
            + "  // Enable LuckPerms checks through fabric-permissions-api when the LuckPerms mod is installed." + newline
            + "  \"luckPerms\": " + luckPerms + "," + newline
            + newline
            + "  // Default per-player pad limit when no higher permission override exists." + newline
            + "  \"maxPadsPerPlayer\": " + maxPadsPerPlayer + "," + newline
            + newline
            + "  // Default per-player link limit when no higher permission override exists." + newline
            + "  \"maxLinksPerPlayer\": " + maxLinksPerPlayer + "," + newline
            + newline
            + "  // Access modes: EVERYONE or OPS_ONLY. These are used when LuckPerms is disabled or not installed." + newline
            + "  \"addAccess\": " + GSON.toJson(addAccess.name()) + "," + newline
            + "  \"linkAccess\": " + GSON.toJson(linkAccess.name()) + "," + newline
            + "  \"unlinkAccess\": " + GSON.toJson(unlinkAccess.name()) + "," + newline
            + "  \"removeAccess\": " + GSON.toJson(removeAccess.name()) + "," + newline
            + "  \"renameAccess\": " + GSON.toJson(renameAccess.name()) + "," + newline
            + "  \"listAccess\": " + GSON.toJson(listAccess.name()) + "," + newline
            + "  \"infoAccess\": " + GSON.toJson(infoAccess.name()) + newline
            + "}" + newline;
    }

    private record LoadResult(EnderLinkConfig config, boolean shouldSave) {
    }
}
