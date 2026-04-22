package net.enderlink.permission;

import me.lucko.fabric.api.permissions.v0.Permissions;
import net.enderlink.config.CommandAccessMode;
import net.enderlink.config.EnderLinkConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

public final class PermissionService {
    private static final int LIMIT_SCAN_MAX = 256;

    public boolean canUse(ServerPlayer player) {
        if (!isLuckPermsActive()) {
            return true;
        }

        return hasAdmin(player) || Permissions.check(player, "enderlink.use", false);
    }

    public boolean canRun(CommandSourceStack source, ManagedAction action) {
        if (action == ManagedAction.RELOAD) {
            if (isLuckPermsActive()) {
                return hasAdmin(source) || Permissions.check(source, "enderlink.reload", false);
            }
            return hasAdmin(source);
        }

        if (isLuckPermsActive()) {
            return hasAdmin(source) || Permissions.check(source, action.permissionNode(), false);
        }

        return switch (action) {
            case ADD -> hasAccessMode(source, EnderLinkConfig.get().addAccess);
            case LINK -> hasAccessMode(source, EnderLinkConfig.get().linkAccess);
            case UNLINK -> hasAccessMode(source, EnderLinkConfig.get().unlinkAccess);
            case REMOVE -> hasAccessMode(source, EnderLinkConfig.get().removeAccess);
            case RENAME -> hasAccessMode(source, EnderLinkConfig.get().renameAccess);
            case LIST -> hasAccessMode(source, EnderLinkConfig.get().listAccess);
            case INFO -> hasAccessMode(source, EnderLinkConfig.get().infoAccess);
            case RELOAD -> hasAdmin(source);
        };
    }

    public boolean hasAdmin(ServerPlayer player) {
        if (isLuckPermsActive()) {
            return Permissions.check(player, "enderlink.admin", false);
        }
        return player.createCommandSourceStack().permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
    }

    public boolean hasAdmin(CommandSourceStack source) {
        if (isLuckPermsActive()) {
            return Permissions.check(source, "enderlink.admin", false);
        }
        return source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
    }

    public int getPadLimit(ServerPlayer player) {
        return resolveLimit(player, EnderLinkConfig.get().maxPadsPerPlayer, "enderlink.limit.blocks.");
    }

    public int getLinkLimit(ServerPlayer player) {
        return resolveLimit(player, EnderLinkConfig.get().maxLinksPerPlayer, "enderlink.limit.links.");
    }

    public boolean isLuckPermsActive() {
        return EnderLinkConfig.get().luckPerms && FabricLoader.getInstance().isModLoaded("luckperms");
    }

    private boolean hasAccessMode(CommandSourceStack source, CommandAccessMode accessMode) {
        return accessMode == CommandAccessMode.EVERYONE || hasAdmin(source);
    }

    private int resolveLimit(ServerPlayer player, int fallbackLimit, String prefix) {
        if (!isLuckPermsActive()) {
            return fallbackLimit;
        }

        if (hasAdmin(player) || Permissions.check(player, prefix + "unlimited", false)) {
            return Integer.MAX_VALUE;
        }

        int effectiveLimit = fallbackLimit;
        int maxToScan = Math.max(fallbackLimit, LIMIT_SCAN_MAX);
        for (int current = maxToScan; current > fallbackLimit; current--) {
            if (Permissions.check(player, prefix + current, false)) {
                effectiveLimit = current;
                break;
            }
        }

        return effectiveLimit;
    }

    public enum ManagedAction {
        ADD("enderlink.add"),
        LINK("enderlink.link"),
        UNLINK("enderlink.unlink"),
        REMOVE("enderlink.remove"),
        RENAME("enderlink.rename"),
        LIST("enderlink.list"),
        INFO("enderlink.info"),
        RELOAD("enderlink.reload");

        private final String permissionNode;

        ManagedAction(String permissionNode) {
            this.permissionNode = permissionNode;
        }

        public String permissionNode() {
            return permissionNode;
        }
    }
}
