package net.enderlink.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.enderlink.EnderLinkMod;
import net.enderlink.config.EnderLinkConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

public final class ElevatorCompat {
    private static final Pattern COMMENT_PATTERN = Pattern.compile("(?m)^\\s*//.*(?:\\R|$)");

    private ElevatorCompat() {
    }

    public static void warnIfConflictingBlocks() {
        Set<String> elevatorBlocks = loadElevatorBlocks();
        if (elevatorBlocks.isEmpty()) {
            return;
        }

        Set<String> overlappingBlocks = new LinkedHashSet<>();
        for (String blockId : EnderLinkConfig.get().teleportBlocks) {
            String normalizedBlockId = normalizeBlockId(blockId);
            if (normalizedBlockId != null) {
                overlappingBlocks.add(normalizedBlockId);
            }
        }
        overlappingBlocks.retainAll(elevatorBlocks);

        if (!overlappingBlocks.isEmpty()) {
            EnderLinkMod.LOGGER.warn(
                "{} Elevator and EnderLink share teleport blocks: {}",
                EnderLinkMod.logPrefix(),
                String.join(", ", overlappingBlocks)
            );
        }
    }

    public static boolean isBlockReservedByElevator(Block block) {
        if (block == null || block == Blocks.AIR) {
            return false;
        }

        for (String elevatorBlockId : loadElevatorBlocks()) {
            try {
                Block elevatorBlock = BuiltInRegistries.BLOCK.getValue(Identifier.parse(elevatorBlockId));
                if (elevatorBlock != Blocks.AIR && elevatorBlock == block) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }

        return false;
    }

    public static boolean isBlockIdReservedByElevator(String blockId) {
        String normalizedBlockId = normalizeBlockId(blockId);
        if (normalizedBlockId == null) {
            return false;
        }

        return loadElevatorBlocks().contains(normalizedBlockId);
    }

    public static String elevatorBlockCacheKey() {
        Set<String> elevatorBlocks = loadElevatorBlocks();
        return elevatorBlocks.isEmpty() ? "" : String.join("|", elevatorBlocks);
    }

    private static Set<String> loadElevatorBlocks() {
        Path elevatorConfigPath = FabricLoader.getInstance().getConfigDir().resolve("elevator.json");
        if (!FabricLoader.getInstance().isModLoaded("elevator") || !Files.exists(elevatorConfigPath)) {
            return Set.of();
        }

        try {
            JsonElement root = JsonParser.parseString(stripComments(Files.readString(elevatorConfigPath)));
            if (!root.isJsonObject()) {
                return Set.of();
            }

            JsonObject configObject = root.getAsJsonObject();
            LinkedHashSet<String> blocks = new LinkedHashSet<>();
            if (configObject.has("elevatorBlocks") && configObject.get("elevatorBlocks").isJsonArray()) {
                JsonArray blockArray = configObject.getAsJsonArray("elevatorBlocks");
                for (JsonElement blockElement : blockArray) {
                    if (blockElement.isJsonPrimitive()) {
                        String normalizedBlockId = normalizeBlockId(blockElement.getAsString());
                        if (normalizedBlockId != null) {
                            blocks.add(normalizedBlockId);
                        }
                    }
                }
            }

            if (configObject.has("elevatorBlock") && configObject.get("elevatorBlock").isJsonPrimitive()) {
                String normalizedBlockId = normalizeBlockId(configObject.get("elevatorBlock").getAsString());
                if (normalizedBlockId != null) {
                    blocks.add(normalizedBlockId);
                }
            }

            return blocks;
        } catch (Exception exception) {
            EnderLinkMod.LOGGER.debug("{} Failed to inspect Elevator config.", EnderLinkMod.logPrefix(), exception);
            return Set.of();
        }
    }

    private static String stripComments(String rawJson) {
        return COMMENT_PATTERN.matcher(rawJson).replaceAll("");
    }

    private static String normalizeBlockId(String blockId) {
        if (blockId == null) {
            return null;
        }

        String trimmed = blockId.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        return trimmed.contains(":") ? trimmed : "minecraft:" + trimmed;
    }
}
