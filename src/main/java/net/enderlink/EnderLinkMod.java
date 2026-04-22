package net.enderlink;

import net.enderlink.command.EnderLinkCommand;
import net.enderlink.compat.ElevatorCompat;
import net.enderlink.config.EnderLinkConfig;
import net.enderlink.data.TeleportPadStore;
import net.enderlink.permission.PermissionService;
import net.enderlink.service.TeleportService;
import net.enderlink.util.ModrinthUpdateChecker;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public final class EnderLinkMod implements ModInitializer {
    public static final String MOD_ID = "enderlink";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final String FALLBACK_MOD_NAME = "EnderLink";
    private static final TeleportPadStore PAD_STORE = new TeleportPadStore();
    private static final PermissionService PERMISSION_SERVICE = new PermissionService();
    private static final TeleportService TELEPORT_SERVICE = new TeleportService(PAD_STORE, PERMISSION_SERVICE);

    private static volatile MinecraftServer currentServer;

    @Override
    public void onInitialize() {
        EnderLinkConfig.load();
        PAD_STORE.load();

        LOGGER.info("{} Mod initialized. Version: {}", logPrefix(), modVersion());

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            EnderLinkCommand.register(dispatcher)
        );

        ServerTickEvents.END_SERVER_TICK.register(TELEPORT_SERVICE::onServerTick);
        ServerPlayConnectionEvents.DISCONNECT.register((networkHandler, server) ->
            TELEPORT_SERVICE.removePlayer(networkHandler.player.getUUID())
        );

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            currentServer = server;
            PAD_STORE.validateConfiguredPads(server);
            ElevatorCompat.warnIfConflictingBlocks();
            ModrinthUpdateChecker.checkOnceAsync();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> currentServer = null);
    }

    public static TeleportPadStore padStore() {
        return PAD_STORE;
    }

    public static PermissionService permissions() {
        return PERMISSION_SERVICE;
    }

    public static TeleportService teleportService() {
        return TELEPORT_SERVICE;
    }

    public static EnderLinkConfig loadConfigForEditing() {
        return EnderLinkConfig.loadForEditing();
    }

    public static void applyEditedConfig(EnderLinkConfig editedConfig) {
        EnderLinkConfig.applyEditedConfig(editedConfig);
    }

    public static void reloadConfig() {
        EnderLinkConfig.load();
        scheduleRuntimeRefresh();
    }

    public static void scheduleRuntimeRefresh() {
        MinecraftServer server = currentServer;
        if (server == null) {
            return;
        }

        server.execute(() -> {
            PAD_STORE.validateConfiguredPads(server);
            ElevatorCompat.warnIfConflictingBlocks();
        });
    }

    public static String modName() {
        return modMetadata().map(ModMetadata::getName).orElse(FALLBACK_MOD_NAME);
    }

    public static String modVersion() {
        return modMetadata()
            .map(metadata -> metadata.getVersion().getFriendlyString())
            .orElse("unknown");
    }

    public static String logPrefix() {
        return "[" + modName() + "]";
    }

    private static Optional<ModMetadata> modMetadata() {
        return FabricLoader.getInstance()
            .getModContainer(MOD_ID)
            .map(container -> container.getMetadata());
    }
}
