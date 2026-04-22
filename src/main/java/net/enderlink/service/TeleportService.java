package net.enderlink.service;

import net.enderlink.EnderLinkMod;
import net.enderlink.compat.ElevatorCompat;
import net.enderlink.config.EnderLinkConfig;
import net.enderlink.data.TeleportPadStore;
import net.enderlink.data.TeleportPadStore.TeleportPadData;
import net.enderlink.permission.PermissionService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class TeleportService {
    private static final int INTEGRITY_SCAN_INTERVAL = 40;

    private final TeleportPadStore padStore;
    private final PermissionService permissions;
    private final Map<UUID, PendingTeleport> pendingTeleports = new HashMap<>();
    private final Map<UUID, String> failedInteractionLocks = new HashMap<>();
    private final Map<UUID, String> arrivalLocks = new HashMap<>();
    private final Map<UUID, ArrivalParticleEffect> arrivalParticleEffects = new HashMap<>();

    private Set<Block> cachedTeleportBlocks = Set.of();
    private String cachedTeleportBlocksKey = "";
    private SimpleParticleType cachedParticleType;
    private String cachedParticleId = "";
    private SoundEvent cachedSoundEvent;
    private String cachedSoundId = "";

    public TeleportService(TeleportPadStore padStore, PermissionService permissions) {
        this.padStore = padStore;
        this.permissions = permissions;
    }

    public void onServerTick(MinecraftServer server) {
        long currentTick = server.getTickCount();

        if (currentTick % INTEGRITY_SCAN_INTERVAL == 0) {
            padStore.validateConfiguredPads(server);
        }

        tickArrivalEffects(server, currentTick);

        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer player : level.getPlayers(ignored -> true)) {
                if (player.isSpectator()) {
                    continue;
                }
                handlePlayer(player, currentTick);
            }
        }
    }

    public void removePlayer(UUID playerUuid) {
        pendingTeleports.remove(playerUuid);
        failedInteractionLocks.remove(playerUuid);
        arrivalLocks.remove(playerUuid);
        arrivalParticleEffects.remove(playerUuid);
    }

    public boolean isConfiguredTeleportBlock(Block block) {
        return getTeleportBlocks(EnderLinkConfig.get()).contains(block);
    }

    public static BlockPos getPadBlockPos(ServerPlayer player) {
        return BlockPos.containing(player.getX(), player.getY() - 0.2D, player.getZ());
    }

    private void handlePlayer(ServerPlayer player, long currentTick) {
        UUID playerUuid = player.getUUID();
        String currentPadName = resolveCurrentPadName(player);
        PendingTeleport pendingTeleport = pendingTeleports.get(playerUuid);

        if (currentPadName == null || !player.isShiftKeyDown()) {
            if (pendingTeleport != null) {
                cancelTeleport(player, "Teleport canceled.");
            }

            if (currentPadName == null || !player.isShiftKeyDown() || !Objects.equals(arrivalLocks.get(playerUuid), currentPadName)) {
                arrivalLocks.remove(playerUuid);
            }
            failedInteractionLocks.remove(playerUuid);
            return;
        }

        if (!Objects.equals(arrivalLocks.get(playerUuid), currentPadName)) {
            arrivalLocks.remove(playerUuid);
        }
        if (!Objects.equals(failedInteractionLocks.get(playerUuid), currentPadName)) {
            failedInteractionLocks.remove(playerUuid);
        }

        if (Objects.equals(arrivalLocks.get(playerUuid), currentPadName)) {
            return;
        }

        if (pendingTeleport != null) {
            tickPendingTeleport(player, currentPadName, currentTick, pendingTeleport);
            return;
        }

        if (Objects.equals(failedInteractionLocks.get(playerUuid), currentPadName)) {
            return;
        }

        if (!permissions.canUse(player)) {
            sendFailure(player, "You do not have permission to use EnderLink teleports.");
            failedInteractionLocks.put(playerUuid, currentPadName);
            return;
        }

        TeleportPadData sourcePad = padStore.getPadByName(currentPadName);
        if (sourcePad == null) {
            failedInteractionLocks.put(playerUuid, currentPadName);
            return;
        }

        if (sourcePad.linkedPadName == null) {
            sendFailure(player, sourcePad.name + " is not linked.");
            failedInteractionLocks.put(playerUuid, currentPadName);
            return;
        }

        TeleportPadData destinationPad = padStore.getPadByName(sourcePad.linkedPadName);
        if (destinationPad == null) {
            sendFailure(player, sourcePad.name + " links to a missing destination.");
            failedInteractionLocks.put(playerUuid, currentPadName);
            return;
        }

        if (!isDimensionAllowed(sourcePad.dimension) || !isDimensionAllowed(destinationPad.dimension)) {
            sendFailure(player, "This teleport is blocked by the allowedDimensions config.");
            failedInteractionLocks.put(playerUuid, currentPadName);
            return;
        }

        PendingTeleport newPendingTeleport = new PendingTeleport(
            sourcePad.name,
            destinationPad.name,
            player.position(),
            currentTick + EnderLinkConfig.get().chargeTicks
        );
        pendingTeleports.put(playerUuid, newPendingTeleport);
        player.sendSystemMessage(Component.literal(EnderLinkMod.logPrefix() + " Charging teleport to " + destinationPad.name + "..."), true);
        spawnChargeParticles(player.level(), player);
    }

    private void tickPendingTeleport(ServerPlayer player, String currentPadName, long currentTick, PendingTeleport pendingTeleport) {
        if (!pendingTeleport.sourcePadName.equals(currentPadName)) {
            cancelTeleport(player, "Teleport canceled.");
            return;
        }

        if (player.position().distanceToSqr(pendingTeleport.startPosition) > 0.01D) {
            cancelTeleport(player, "Teleport canceled because you moved.");
            return;
        }

        long remainingTicks = Math.max(0L, pendingTeleport.completeTick - currentTick);
        double remainingSeconds = remainingTicks / 20.0D;
        player.sendSystemMessage(
            Component.literal(EnderLinkMod.logPrefix() + " Teleporting in " + String.format(java.util.Locale.ROOT, "%.1f", remainingSeconds) + "s"),
            true
        );

        if (remainingTicks > 0L && remainingTicks % 5L == 0L) {
            spawnChargeParticles(player.level(), player);
        }

        if (currentTick < pendingTeleport.completeTick) {
            return;
        }

        pendingTeleports.remove(player.getUUID());

        TeleportPadData sourcePad = padStore.getPadByName(pendingTeleport.sourcePadName);
        TeleportPadData destinationPad = padStore.getPadByName(pendingTeleport.destinationPadName);
        if (sourcePad == null || destinationPad == null) {
            sendFailure(player, "Teleport failed because the link changed.");
            failedInteractionLocks.put(player.getUUID(), currentPadName);
            return;
        }

        if (performTeleport(player, sourcePad, destinationPad, currentTick)) {
            arrivalLocks.put(player.getUUID(), destinationPad.name);
            failedInteractionLocks.remove(player.getUUID());
            player.sendSystemMessage(Component.literal(""), true);
        } else {
            failedInteractionLocks.put(player.getUUID(), currentPadName);
        }
    }

    private boolean performTeleport(ServerPlayer player, TeleportPadData sourcePad, TeleportPadData destinationPad, long currentTick) {
        MinecraftServer server = player.createCommandSourceStack().getServer();
        if (server == null) {
            sendFailure(player, "Teleport failed because the server is unavailable.");
            return false;
        }

        ServerLevel destinationLevel = server.getLevel(net.minecraft.resources.ResourceKey.create(
            net.minecraft.core.registries.Registries.DIMENSION,
            Identifier.parse(destinationPad.dimension)
        ));
        if (destinationLevel == null) {
            sendFailure(player, "Teleport failed because the destination dimension is unavailable.");
            return false;
        }

        if (!sourcePad.name.equals(destinationPad.linkedPadName)) {
            sendFailure(player, "Teleport failed because the link is no longer two-way.");
            return false;
        }

        BlockPos destinationPos = destinationPad.blockPos();
        if (!destinationLevel.getWorldBorder().isWithinBounds(destinationPos)) {
            sendFailure(player, "Teleport failed because the destination is outside the world border.");
            return false;
        }

        if (!isConfiguredTeleportBlock(destinationLevel.getBlockState(destinationPos).getBlock())) {
            sendFailure(player, "Teleport failed because the destination block is no longer valid.");
            return false;
        }

        LandingSpot landingSpot = resolveLandingSpot(player, destinationLevel, destinationPos);
        if (!landingSpot.safe()) {
            sendFailure(player, landingSpot.failureMessage());
            return false;
        }

        player.teleportTo(
            destinationLevel,
            landingSpot.x,
            landingSpot.y,
            landingSpot.z,
            java.util.Set.of(),
            player.getYRot(),
            player.getXRot(),
            true
        );
        player.setDeltaMovement(Vec3.ZERO);

        playArrivalEffects(destinationLevel, landingSpot.x, landingSpot.y, landingSpot.z);
        int durationTicks = EnderLinkConfig.get().arrivalParticleDurationTicks;
        if (durationTicks > 0) {
            arrivalParticleEffects.put(
                player.getUUID(),
                new ArrivalParticleEffect(
                    destinationLevel.dimension().identifier().toString(),
                    landingSpot.x,
                    landingSpot.y,
                    landingSpot.z,
                    currentTick + durationTicks
                )
            );
        }
        return true;
    }

    private LandingSpot resolveLandingSpot(ServerPlayer player, ServerLevel level, BlockPos destinationPos) {
        BlockPos feetBlockPos = destinationPos.above();
        BlockPos headBlockPos = destinationPos.above(2);
        BlockState feetState = level.getBlockState(feetBlockPos);
        boolean carpetCover = feetState.is(BlockTags.WOOL_CARPETS);

        if (!feetState.isAir() && !carpetCover) {
            return LandingSpot.failure("Teleport failed because the destination is blocked.");
        }

        if (!EnderLinkConfig.get().safetyEnabled) {
            double y = destinationPos.getY() + 1.0D + getLandingHeightOffset(level, feetBlockPos);
            return LandingSpot.success(destinationPos.getX() + 0.5D, y, destinationPos.getZ() + 0.5D);
        }

        if (!level.getFluidState(feetBlockPos).isEmpty() || !level.getFluidState(headBlockPos).isEmpty()) {
            return LandingSpot.failure("Teleport failed because the destination is underwater or inside a fluid.");
        }

        if (!level.getFluidState(destinationPos).isEmpty()) {
            return LandingSpot.failure("Teleport failed because the teleport pad is in an unsafe fluid.");
        }

        double y = destinationPos.getY() + 1.0D + getLandingHeightOffset(level, feetBlockPos);
        AABB playerBox = player.getDimensions(Pose.STANDING).makeBoundingBox(destinationPos.getX() + 0.5D, y, destinationPos.getZ() + 0.5D);
        if (!level.noCollision(player, playerBox)) {
            return LandingSpot.failure("Teleport failed because there is not enough space at the destination.");
        }

        if (!level.getWorldBorder().isWithinBounds(playerBox)) {
            return LandingSpot.failure("Teleport failed because the player would arrive outside the world border.");
        }

        return LandingSpot.success(destinationPos.getX() + 0.5D, y, destinationPos.getZ() + 0.5D);
    }

    private void cancelTeleport(ServerPlayer player, String reason) {
        pendingTeleports.remove(player.getUUID());
        player.sendSystemMessage(Component.literal(EnderLinkMod.logPrefix() + " " + reason), true);
    }

    private void playArrivalEffects(ServerLevel level, double x, double y, double z) {
        EnderLinkConfig config = EnderLinkConfig.get();

        if (config.particlesEnabled) {
            SimpleParticleType particleType = getParticleType(config);
            if (particleType != null && config.arrivalBurstParticleCount > 0) {
                level.sendParticles(particleType, x, y + 0.5D, z, config.arrivalBurstParticleCount, 0.25D, 0.6D, 0.25D, 0.02D);
            }
        }

        if (config.soundEnabled) {
            SoundEvent soundEvent = getSoundEvent(config);
            if (soundEvent != null) {
                level.playSound(null, x, y, z, soundEvent, SoundSource.PLAYERS, config.soundVolume, config.soundPitch);
            }
        }
    }

    private void spawnChargeParticles(ServerLevel level, ServerPlayer player) {
        EnderLinkConfig config = EnderLinkConfig.get();
        if (!config.particlesEnabled) {
            return;
        }

        SimpleParticleType particleType = getParticleType(config);
        if (particleType == null) {
            return;
        }

        if (config.warmupParticleCount > 0) {
            level.sendParticles(particleType, player.getX(), player.getY() + 1.0D, player.getZ(), config.warmupParticleCount, 0.25D, 0.4D, 0.25D, 0.02D);
        }
    }

    private void tickArrivalEffects(MinecraftServer server, long currentTick) {
        EnderLinkConfig config = EnderLinkConfig.get();
        if (arrivalParticleEffects.isEmpty() || !config.particlesEnabled) {
            return;
        }

        SimpleParticleType particleType = getParticleType(config);
        if (particleType == null) {
            return;
        }

        java.util.Iterator<Map.Entry<UUID, ArrivalParticleEffect>> iterator = arrivalParticleEffects.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ArrivalParticleEffect> entry = iterator.next();
            ArrivalParticleEffect effect = entry.getValue();
            if (currentTick >= effect.expireTick) {
                iterator.remove();
                continue;
            }

            ServerLevel level = server.getLevel(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                Identifier.parse(effect.dimensionId)
            ));
            if (level == null) {
                iterator.remove();
                continue;
            }

            if (config.arrivalTrailParticleCount > 0) {
                level.sendParticles(particleType, effect.x, effect.y + 0.5D, effect.z, config.arrivalTrailParticleCount, 0.2D, 0.45D, 0.2D, 0.015D);
            }
        }
    }

    private String resolveCurrentPadName(ServerPlayer player) {
        BlockPos padPos = getPadBlockPos(player);
        TeleportPadData pad = padStore.getPadAt(player.level().dimension().identifier().toString(), padPos);
        return pad == null ? null : pad.name;
    }

    private boolean isDimensionAllowed(String dimensionId) {
        for (String allowedDimension : EnderLinkConfig.get().allowedDimensions) {
            if (allowedDimension.equals(dimensionId)) {
                return true;
            }
        }
        return false;
    }

    private Set<Block> getTeleportBlocks(EnderLinkConfig config) {
        String blockCacheKey = String.join("|", config.teleportBlocks) + "||elevator=" + ElevatorCompat.elevatorBlockCacheKey();
        if (!blockCacheKey.equals(cachedTeleportBlocksKey)) {
            java.util.HashSet<Block> resolvedBlocks = new java.util.HashSet<>();
            for (String blockId : config.teleportBlocks) {
                if (ElevatorCompat.isBlockIdReservedByElevator(blockId)) {
                    EnderLinkMod.LOGGER.warn("{} Ignoring EnderLink block because Elevator already uses it: {}", EnderLinkMod.logPrefix(), blockId);
                    continue;
                }

                try {
                    Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(blockId));
                    if (block == Blocks.AIR) {
                        EnderLinkMod.LOGGER.warn("{} Invalid teleport block in config: {}", EnderLinkMod.logPrefix(), blockId);
                    } else {
                        resolvedBlocks.add(block);
                    }
                } catch (Exception ignored) {
                    EnderLinkMod.LOGGER.warn("{} Invalid teleport block in config: {}", EnderLinkMod.logPrefix(), blockId);
                }
            }
            cachedTeleportBlocks = Set.copyOf(resolvedBlocks);
            cachedTeleportBlocksKey = blockCacheKey;
        }
        return cachedTeleportBlocks;
    }

    private SimpleParticleType getParticleType(EnderLinkConfig config) {
        if (!config.particleType.equals(cachedParticleId)) {
            cachedParticleType = resolveSimpleParticle(config.particleType);
            cachedParticleId = config.particleType;
        }
        return cachedParticleType;
    }

    private SoundEvent getSoundEvent(EnderLinkConfig config) {
        if (!config.soundEvent.equals(cachedSoundId)) {
            cachedSoundEvent = resolveSoundEvent(config.soundEvent);
            cachedSoundId = config.soundEvent;
        }
        return cachedSoundEvent;
    }

    private SimpleParticleType resolveSimpleParticle(String particleId) {
        try {
            Object particle = BuiltInRegistries.PARTICLE_TYPE.getValue(Identifier.parse(particleId));
            if (particle instanceof SimpleParticleType simpleParticleType) {
                return simpleParticleType;
            }
        } catch (Exception ignored) {
        }

        EnderLinkMod.LOGGER.warn("{} Invalid or unsupported particle in config: {}", EnderLinkMod.logPrefix(), particleId);
        return null;
    }

    private SoundEvent resolveSoundEvent(String soundId) {
        try {
            SoundEvent soundEvent = BuiltInRegistries.SOUND_EVENT.getValue(Identifier.parse(soundId));
            if (soundEvent != null) {
                return soundEvent;
            }
        } catch (Exception ignored) {
        }

        EnderLinkMod.LOGGER.warn("{} Invalid sound event in config: {}", EnderLinkMod.logPrefix(), soundId);
        return null;
    }

    private double getLandingHeightOffset(ServerLevel level, BlockPos feetBlockPos) {
        return level.getBlockState(feetBlockPos).is(BlockTags.WOOL_CARPETS) ? 0.0625D : 0.0D;
    }

    private void sendFailure(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(EnderLinkMod.logPrefix() + " " + message));
    }

    private record PendingTeleport(String sourcePadName, String destinationPadName, Vec3 startPosition, long completeTick) {
    }

    private record ArrivalParticleEffect(String dimensionId, double x, double y, double z, long expireTick) {
    }

    private record LandingSpot(boolean safe, double x, double y, double z, String failureMessage) {
        private static LandingSpot success(double x, double y, double z) {
            return new LandingSpot(true, x, y, z, "");
        }

        private static LandingSpot failure(String failureMessage) {
            return new LandingSpot(false, 0.0D, 0.0D, 0.0D, failureMessage);
        }
    }
}
