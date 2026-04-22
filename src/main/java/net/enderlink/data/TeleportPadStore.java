package net.enderlink.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.enderlink.EnderLinkMod;
import net.enderlink.compat.ElevatorCompat;
import net.enderlink.config.EnderLinkConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public final class TeleportPadStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DATA_DIR = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve(EnderLinkMod.MOD_ID);
    private static final Path DATA_PATH = DATA_DIR.resolve("teleports.json");
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_-]+");

    private final Map<String, TeleportPadData> padsByName = new LinkedHashMap<>();
    private int nextId = 1;

    public synchronized void load() {
        padsByName.clear();
        nextId = 1;

        if (!Files.exists(DATA_PATH)) {
            save();
            return;
        }

        try {
            StoreFile storeFile = GSON.fromJson(Files.readString(DATA_PATH), StoreFile.class);
            if (storeFile == null) {
                save();
                return;
            }

            nextId = Math.max(1, storeFile.nextId);
            if (storeFile.pads != null) {
                for (TeleportPadData pad : storeFile.pads) {
                    if (pad == null || !isValidPadName(pad.name) || pad.ownerUuid == null || pad.dimension == null) {
                        continue;
                    }

                    pad.name = normalizePadName(pad.name);
                    if (pad.linkedPadName != null && !pad.linkedPadName.isBlank()) {
                        pad.linkedPadName = normalizePadName(pad.linkedPadName);
                    } else {
                        pad.linkedPadName = null;
                    }
                    padsByName.put(pad.name, pad.copy());
                }
            }
        } catch (Exception exception) {
            EnderLinkMod.LOGGER.warn(
                "{} Failed to load teleport data, keeping the current file untouched: {}",
                EnderLinkMod.logPrefix(),
                exception.getMessage()
            );
        }
    }

    public synchronized TeleportPadData getPadByName(String name) {
        TeleportPadData pad = padsByName.get(normalizePadName(name));
        return pad == null ? null : pad.copy();
    }

    public synchronized TeleportPadData getPadAt(String dimension, BlockPos pos) {
        for (TeleportPadData pad : padsByName.values()) {
            if (pad.dimension.equals(dimension) && pad.x == pos.getX() && pad.y == pos.getY() && pad.z == pos.getZ()) {
                return pad.copy();
            }
        }
        return null;
    }

    public synchronized List<TeleportPadData> getAllPads() {
        return padsByName.values().stream()
            .map(TeleportPadData::copy)
            .sorted(Comparator.comparing(pad -> pad.name))
            .toList();
    }

    public synchronized TeleportPadData createPad(UUID ownerUuid, String dimension, BlockPos pos) {
        String name = generateNextPadName();
        TeleportPadData pad = new TeleportPadData();
        pad.name = name;
        pad.ownerUuid = ownerUuid.toString();
        pad.dimension = dimension;
        pad.x = pos.getX();
        pad.y = pos.getY();
        pad.z = pos.getZ();

        padsByName.put(name, pad);
        save();
        return pad.copy();
    }

    public synchronized void linkPads(String firstName, String secondName) {
        TeleportPadData first = padsByName.get(normalizePadName(firstName));
        TeleportPadData second = padsByName.get(normalizePadName(secondName));
        if (first == null || second == null) {
            return;
        }

        first.linkedPadName = second.name;
        second.linkedPadName = first.name;
        save();
    }

    public synchronized void unlinkPads(String firstName, String secondName) {
        TeleportPadData first = padsByName.get(normalizePadName(firstName));
        TeleportPadData second = padsByName.get(normalizePadName(secondName));
        if (first == null || second == null) {
            return;
        }

        if (second.name.equals(first.linkedPadName)) {
            first.linkedPadName = null;
        }
        if (first.name.equals(second.linkedPadName)) {
            second.linkedPadName = null;
        }
        save();
    }

    public synchronized void removePad(String name) {
        String normalizedName = normalizePadName(name);
        TeleportPadData removed = padsByName.remove(normalizedName);
        if (removed == null) {
            return;
        }

        if (removed.linkedPadName != null) {
            TeleportPadData linked = padsByName.get(removed.linkedPadName);
            if (linked != null && normalizedName.equals(linked.linkedPadName)) {
                linked.linkedPadName = null;
            }
        }

        for (TeleportPadData pad : padsByName.values()) {
            if (normalizedName.equals(pad.linkedPadName)) {
                pad.linkedPadName = null;
            }
        }

        save();
    }

    public synchronized boolean renamePad(String oldName, String newName) {
        String normalizedOldName = normalizePadName(oldName);
        String normalizedNewName = normalizePadName(newName);

        if (!isValidPadName(normalizedNewName) || padsByName.containsKey(normalizedNewName)) {
            return false;
        }

        TeleportPadData pad = padsByName.remove(normalizedOldName);
        if (pad == null) {
            return false;
        }

        pad.name = normalizedNewName;
        padsByName.put(normalizedNewName, pad);

        for (TeleportPadData otherPad : padsByName.values()) {
            if (normalizedOldName.equals(otherPad.linkedPadName)) {
                otherPad.linkedPadName = normalizedNewName;
            }
        }

        save();
        return true;
    }

    public synchronized int countPadsOwnedBy(UUID ownerUuid) {
        String ownerKey = ownerUuid.toString();
        int count = 0;
        for (TeleportPadData pad : padsByName.values()) {
            if (ownerKey.equals(pad.ownerUuid)) {
                count++;
            }
        }
        return count;
    }

    public synchronized int countLinksOwnedBy(UUID ownerUuid) {
        String ownerKey = ownerUuid.toString();
        Set<String> countedPairs = new HashSet<>();
        int count = 0;

        for (TeleportPadData pad : padsByName.values()) {
            if (!ownerKey.equals(pad.ownerUuid) || pad.linkedPadName == null) {
                continue;
            }

            String pairKey = pairKey(pad.name, pad.linkedPadName);
            if (countedPairs.add(pairKey)) {
                count++;
            }
        }

        return count;
    }

    public synchronized boolean playerOwnsPad(UUID ownerUuid, String padName) {
        TeleportPadData pad = padsByName.get(normalizePadName(padName));
        return pad != null && ownerUuid.toString().equals(pad.ownerUuid);
    }

    public synchronized boolean isNameTaken(String name) {
        return padsByName.containsKey(normalizePadName(name));
    }

    public synchronized void validateConfiguredPads(MinecraftServer server) {
        EnderLinkConfig config = EnderLinkConfig.get();
        List<String> invalidPads = new ArrayList<>();

        for (TeleportPadData pad : padsByName.values()) {
            ServerLevel level;
            try {
                level = server.getLevel(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION,
                    Identifier.parse(pad.dimension)
                ));
            } catch (Exception exception) {
                invalidPads.add(pad.name);
                continue;
            }

            if (level == null) {
                invalidPads.add(pad.name);
                continue;
            }

            if (!isConfiguredTeleportBlock(level.getBlockState(pad.blockPos()).getBlock(), config)) {
                invalidPads.add(pad.name);
            }
        }

        if (invalidPads.isEmpty()) {
            return;
        }

        for (String invalidPad : invalidPads) {
            EnderLinkMod.LOGGER.debug("{} Removing invalid teleport pad {}", EnderLinkMod.logPrefix(), invalidPad);
            removePad(invalidPad);
        }
    }

    public static String normalizePadName(String name) {
        return name == null ? "" : name.trim().toUpperCase(Locale.ROOT);
    }

    public static boolean isValidPadName(String name) {
        return name != null && !name.isBlank() && VALID_NAME.matcher(name).matches();
    }

    private static boolean isConfiguredTeleportBlock(Block block, EnderLinkConfig config) {
        for (String blockId : config.teleportBlocks) {
            if (ElevatorCompat.isBlockIdReservedByElevator(blockId)) {
                continue;
            }

            try {
                Block configuredBlock = BuiltInRegistries.BLOCK.getValue(Identifier.parse(blockId));
                if (configuredBlock != Blocks.AIR && configuredBlock == block) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private String generateNextPadName() {
        while (padsByName.containsKey("TP" + nextId)) {
            nextId++;
        }

        String generatedName = "TP" + nextId;
        nextId++;
        return generatedName;
    }

    private void save() {
        StoreFile storeFile = new StoreFile();
        storeFile.nextId = nextId;
        storeFile.pads = padsByName.values().stream().map(TeleportPadData::copy).toList();

        try {
            Files.createDirectories(DATA_DIR);
            Files.writeString(DATA_PATH, GSON.toJson(storeFile));
        } catch (IOException exception) {
            EnderLinkMod.LOGGER.warn("{} Failed to save teleport data: {}", EnderLinkMod.logPrefix(), exception.getMessage());
        }
    }

    private static String pairKey(String first, String second) {
        return first.compareTo(second) <= 0 ? first + "|" + second : second + "|" + first;
    }

    private static final class StoreFile {
        int nextId = 1;
        List<TeleportPadData> pads = List.of();
    }

    public static final class TeleportPadData {
        public String name;
        public String ownerUuid;
        public String dimension;
        public int x;
        public int y;
        public int z;
        public String linkedPadName;

        public TeleportPadData copy() {
            TeleportPadData copy = new TeleportPadData();
            copy.name = name;
            copy.ownerUuid = ownerUuid;
            copy.dimension = dimension;
            copy.x = x;
            copy.y = y;
            copy.z = z;
            copy.linkedPadName = linkedPadName;
            return copy;
        }

        public BlockPos blockPos() {
            return new BlockPos(x, y, z);
        }

        public UUID ownerUuid() {
            return UUID.fromString(ownerUuid);
        }
    }
}
