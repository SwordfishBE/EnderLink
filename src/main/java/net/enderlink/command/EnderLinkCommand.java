package net.enderlink.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.enderlink.EnderLinkMod;
import net.enderlink.compat.ElevatorCompat;
import net.enderlink.data.TeleportPadStore;
import net.enderlink.data.TeleportPadStore.TeleportPadData;
import net.enderlink.permission.PermissionService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class EnderLinkCommand {
    private static final SuggestionProvider<CommandSourceStack> PAD_NAME_SUGGESTIONS = EnderLinkCommand::suggestPadNames;

    private EnderLinkCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("enderlink")
                .then(Commands.literal("add")
                    .executes(context -> add(context.getSource()))
                )
                .then(Commands.literal("link")
                    .then(Commands.argument("first", StringArgumentType.word()).suggests(PAD_NAME_SUGGESTIONS)
                        .then(Commands.argument("second", StringArgumentType.word()).suggests(PAD_NAME_SUGGESTIONS)
                            .executes(context -> link(
                                context.getSource(),
                                StringArgumentType.getString(context, "first"),
                                StringArgumentType.getString(context, "second")
                            ))
                        )
                    )
                )
                .then(Commands.literal("unlink")
                    .then(Commands.argument("first", StringArgumentType.word()).suggests(PAD_NAME_SUGGESTIONS)
                        .then(Commands.argument("second", StringArgumentType.word()).suggests(PAD_NAME_SUGGESTIONS)
                            .executes(context -> unlink(
                                context.getSource(),
                                StringArgumentType.getString(context, "first"),
                                StringArgumentType.getString(context, "second")
                            ))
                        )
                    )
                )
                .then(Commands.literal("remove")
                    .then(Commands.argument("name", StringArgumentType.word()).suggests(PAD_NAME_SUGGESTIONS)
                        .executes(context -> remove(
                            context.getSource(),
                            StringArgumentType.getString(context, "name")
                        ))
                    )
                )
                .then(Commands.literal("rename")
                    .then(Commands.argument("name", StringArgumentType.word()).suggests(PAD_NAME_SUGGESTIONS)
                        .then(Commands.argument("newName", StringArgumentType.word())
                            .executes(context -> rename(
                                context.getSource(),
                                StringArgumentType.getString(context, "name"),
                                StringArgumentType.getString(context, "newName")
                            ))
                        )
                    )
                )
                .then(Commands.literal("list")
                    .executes(context -> list(context.getSource()))
                )
                .then(Commands.literal("info")
                    .executes(context -> info(context.getSource(), null))
                    .then(Commands.argument("name", StringArgumentType.word()).suggests(PAD_NAME_SUGGESTIONS)
                        .executes(context -> info(
                            context.getSource(),
                            StringArgumentType.getString(context, "name")
                        ))
                    )
                )
                .then(Commands.literal("reload")
                    .executes(context -> reload(context.getSource()))
                )
        );
    }

    private static int add(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!EnderLinkMod.permissions().canRun(source, PermissionService.ManagedAction.ADD)) {
            source.sendFailure(Component.literal(EnderLinkMod.logPrefix() + " You do not have permission to use /enderlink add."));
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        TeleportPadStore store = EnderLinkMod.padStore();
        TeleportPadData existingPad = store.getPadAt(player.level().dimension().identifier().toString(), net.enderlink.service.TeleportService.getPadBlockPos(player));
        if (existingPad != null) {
            source.sendFailure(Component.literal(EnderLinkMod.logPrefix() + " This teleport block is already registered as " + existingPad.name + "."));
            return 0;
        }

        if (ElevatorCompat.isBlockReservedByElevator(player.level().getBlockState(net.enderlink.service.TeleportService.getPadBlockPos(player)).getBlock())) {
            source.sendFailure(Component.literal(EnderLinkMod.logPrefix() + " This block is configured for Elevator too, so EnderLink cannot use it. Remove the overlap from one of the configs first."));
            return 0;
        }

        if (!EnderLinkMod.teleportService().isConfiguredTeleportBlock(player.level().getBlockState(net.enderlink.service.TeleportService.getPadBlockPos(player)).getBlock())) {
            source.sendFailure(Component.literal(EnderLinkMod.logPrefix() + " Stand on a configured EnderLink block first."));
            return 0;
        }

        if (!EnderLinkMod.permissions().hasAdmin(player)) {
            int padLimit = EnderLinkMod.permissions().getPadLimit(player);
            int currentPadCount = store.countPadsOwnedBy(player.getUUID());
            if (currentPadCount >= padLimit) {
                source.sendFailure(Component.literal(EnderLinkMod.logPrefix() + " You have reached your teleport pad limit of " + padLimit + "."));
                return 0;
            }
        }

        TeleportPadData createdPad = store.createPad(
            player.getUUID(),
            player.level().dimension().identifier().toString(),
            net.enderlink.service.TeleportService.getPadBlockPos(player)
        );
        source.sendSuccess(() -> prefixed("Added ").append(formatPadComponent(createdPad)), false);
        return 1;
    }

    private static int link(CommandSourceStack source, String firstName, String secondName) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!EnderLinkMod.permissions().canRun(source, PermissionService.ManagedAction.LINK)) {
            source.sendFailure(Component.literal(EnderLinkMod.logPrefix() + " You do not have permission to use /enderlink link."));
            return 0;
        }

        String firstKey = TeleportPadStore.normalizePadName(firstName);
        String secondKey = TeleportPadStore.normalizePadName(secondName);
        if (firstKey.equals(secondKey)) {
            source.sendFailure(Component.literal(EnderLinkMod.logPrefix() + " Link two different teleport pads."));
            return 0;
        }

        TeleportPadStore store = EnderLinkMod.padStore();
        TeleportPadData firstPad = store.getPadByName(firstKey);
        TeleportPadData secondPad = store.getPadByName(secondKey);
        if (firstPad == null || secondPad == null) {
            source.sendFailure(Component.literal(EnderLinkMod.logPrefix() + " One or both teleport pads do not exist."));
            return 0;
        }

        ServerPlayer player = source.getPlayer();
        if (player != null && !EnderLinkMod.permissions().hasAdmin(player)) {
            if (!store.playerOwnsPad(player.getUUID(), firstPad.name) || !store.playerOwnsPad(player.getUUID(), secondPad.name)) {
                source.sendFailure(Component.literal(EnderLinkMod.logPrefix() + " You may only link teleport pads you own."));
                return 0;
            }

            int linkLimit = EnderLinkMod.permissions().getLinkLimit(player);
            int currentLinkCount = store.countLinksOwnedBy(player.getUUID());
            if (currentLinkCount >= linkLimit) {
                source.sendFailure(Component.literal(EnderLinkMod.logPrefix() + " You have reached your link limit of " + linkLimit + "."));
                return 0;
            }
        }

        if (firstPad.linkedPadName != null || secondPad.linkedPadName != null) {
            source.sendFailure(Component.literal(EnderLinkMod.logPrefix() + " Unlink the existing connection first."));
            return 0;
        }

        store.linkPads(firstPad.name, secondPad.name);
        source.sendSuccess(() -> prefixed("Linked ")
            .append(padNameComponent(firstPad.name))
            .append(Component.literal(" <-> "))
            .append(padNameComponent(secondPad.name)), true);
        return 1;
    }

    private static int unlink(CommandSourceStack source, String firstName, String secondName) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!EnderLinkMod.permissions().canRun(source, PermissionService.ManagedAction.UNLINK)) {
            source.sendFailure(Component.literal(EnderLinkMod.logPrefix() + " You do not have permission to use /enderlink unlink."));
            return 0;
        }

        TeleportPadStore store = EnderLinkMod.padStore();
        TeleportPadData firstPad = store.getPadByName(firstName);
        TeleportPadData secondPad = store.getPadByName(secondName);
        if (firstPad == null || secondPad == null) {
            source.sendFailure(Component.literal(EnderLinkMod.logPrefix() + " One or both teleport pads do not exist."));
            return 0;
        }

        ServerPlayer player = source.getPlayer();
        if (player != null && !EnderLinkMod.permissions().hasAdmin(player)) {
            if (!store.playerOwnsPad(player.getUUID(), firstPad.name) || !store.playerOwnsPad(player.getUUID(), secondPad.name)) {
                source.sendFailure(Component.literal(EnderLinkMod.logPrefix() + " You may only unlink teleport pads you own."));
                return 0;
            }
        }

        if (!secondPad.name.equals(firstPad.linkedPadName) || !firstPad.name.equals(secondPad.linkedPadName)) {
            source.sendFailure(Component.literal(EnderLinkMod.logPrefix() + " Those teleport pads are not linked together."));
            return 0;
        }

        store.unlinkPads(firstPad.name, secondPad.name);
        source.sendSuccess(() -> prefixed("Unlinked ")
            .append(padNameComponent(firstPad.name))
            .append(Component.literal(" and "))
            .append(padNameComponent(secondPad.name)), true);
        return 1;
    }

    private static int remove(CommandSourceStack source, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!EnderLinkMod.permissions().canRun(source, PermissionService.ManagedAction.REMOVE)) {
            source.sendFailure(Component.literal(EnderLinkMod.logPrefix() + " You do not have permission to use /enderlink remove."));
            return 0;
        }

        TeleportPadStore store = EnderLinkMod.padStore();
        TeleportPadData pad = store.getPadByName(name);
        if (pad == null) {
            source.sendFailure(Component.literal(EnderLinkMod.logPrefix() + " Unknown teleport pad."));
            return 0;
        }

        ServerPlayer player = source.getPlayer();
        if (player != null && !EnderLinkMod.permissions().hasAdmin(player) && !store.playerOwnsPad(player.getUUID(), pad.name)) {
            source.sendFailure(Component.literal(EnderLinkMod.logPrefix() + " You may only remove teleport pads you own."));
            return 0;
        }

        store.removePad(pad.name);
        source.sendSuccess(() -> prefixed("Removed ")
            .append(padNameComponent(pad.name))
            .append(Component.literal(".")), true);
        return 1;
    }

    private static int rename(CommandSourceStack source, String name, String newName) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!EnderLinkMod.permissions().canRun(source, PermissionService.ManagedAction.RENAME)) {
            source.sendFailure(Component.literal(EnderLinkMod.logPrefix() + " You do not have permission to use /enderlink rename."));
            return 0;
        }

        String normalizedNewName = TeleportPadStore.normalizePadName(newName);
        if (!TeleportPadStore.isValidPadName(normalizedNewName)) {
            source.sendFailure(Component.literal(EnderLinkMod.logPrefix() + " Teleport pad names may only use letters, numbers, underscores, and dashes."));
            return 0;
        }

        TeleportPadStore store = EnderLinkMod.padStore();
        TeleportPadData pad = store.getPadByName(name);
        if (pad == null) {
            source.sendFailure(Component.literal(EnderLinkMod.logPrefix() + " Unknown teleport pad."));
            return 0;
        }

        ServerPlayer player = source.getPlayer();
        if (player != null && !EnderLinkMod.permissions().hasAdmin(player) && !store.playerOwnsPad(player.getUUID(), pad.name)) {
            source.sendFailure(Component.literal(EnderLinkMod.logPrefix() + " You may only rename teleport pads you own."));
            return 0;
        }

        if (!store.renamePad(pad.name, normalizedNewName)) {
            source.sendFailure(Component.literal(EnderLinkMod.logPrefix() + " That teleport pad name is already taken."));
            return 0;
        }

        source.sendSuccess(() -> prefixed("Renamed ")
            .append(padNameComponent(pad.name))
            .append(Component.literal(" to "))
            .append(padNameComponent(normalizedNewName))
            .append(Component.literal(".")), true);
        return 1;
    }

    private static int list(CommandSourceStack source) {
        if (!EnderLinkMod.permissions().canRun(source, PermissionService.ManagedAction.LIST)) {
            source.sendFailure(Component.literal(EnderLinkMod.logPrefix() + " You do not have permission to use /enderlink list."));
            return 0;
        }

        List<TeleportPadData> pads = EnderLinkMod.padStore().getAllPads();
        if (pads.isEmpty()) {
            source.sendSuccess(() -> Component.literal(EnderLinkMod.logPrefix() + " No teleport pads have been added yet."), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal(EnderLinkMod.logPrefix() + " Registered teleport pads:"), false);

        Set<String> listedPairs = new HashSet<>();
        boolean foundLinkedPair = false;
        for (TeleportPadData pad : pads) {
            if (pad.linkedPadName == null) {
                continue;
            }

            String pairKey = pairKey(pad.name, pad.linkedPadName);
            if (!listedPairs.add(pairKey)) {
                continue;
            }

            TeleportPadData linkedPad = EnderLinkMod.padStore().getPadByName(pad.linkedPadName);
            if (linkedPad == null) {
                continue;
            }

            foundLinkedPair = true;
            source.sendSuccess(() -> Component.literal(" - ")
                .append(formatPadComponent(pad))
                .append(Component.literal(" <-> "))
                .append(formatPadComponent(linkedPad)), false);
        }

        boolean foundUnlinkedPad = false;
        for (TeleportPadData pad : pads) {
            if (pad.linkedPadName != null) {
                continue;
            }

            foundUnlinkedPad = true;
            source.sendSuccess(() -> Component.literal(" - ")
                .append(formatPadComponent(pad))
                .append(Component.literal(" [unlinked]").withStyle(ChatFormatting.RED)), false);
        }

        if (!foundLinkedPair && !foundUnlinkedPad) {
            source.sendSuccess(() -> Component.literal(" - No valid teleport entries found."), false);
        }

        return 1;
    }

    private static int info(CommandSourceStack source, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!EnderLinkMod.permissions().canRun(source, PermissionService.ManagedAction.INFO)) {
            source.sendFailure(Component.literal(EnderLinkMod.logPrefix() + " You do not have permission to use /enderlink info."));
            return 0;
        }

        TeleportPadData pad;
        if (name == null) {
            ServerPlayer player = source.getPlayerOrException();
            pad = EnderLinkMod.padStore().getPadAt(
                player.level().dimension().identifier().toString(),
                net.enderlink.service.TeleportService.getPadBlockPos(player)
            );
            if (pad == null) {
                source.sendFailure(Component.literal(EnderLinkMod.logPrefix() + " You are not standing on a registered teleport pad."));
                return 0;
            }
        } else {
            pad = EnderLinkMod.padStore().getPadByName(name);
            if (pad == null) {
                source.sendFailure(Component.literal(EnderLinkMod.logPrefix() + " Unknown teleport pad."));
                return 0;
            }
        }

        source.sendSuccess(() -> prefixed("").append(formatPadComponent(pad)), false);
        if (pad.linkedPadName != null) {
            TeleportPadData linkedPad = EnderLinkMod.padStore().getPadByName(pad.linkedPadName);
            if (linkedPad != null) {
                source.sendSuccess(() -> Component.literal("   Linked to ").withStyle(ChatFormatting.GREEN).append(formatPadComponent(linkedPad)), false);
            }
        }
        return 1;
    }

    private static int reload(CommandSourceStack source) {
        if (!EnderLinkMod.permissions().canRun(source, PermissionService.ManagedAction.RELOAD)) {
            source.sendFailure(Component.literal(EnderLinkMod.logPrefix() + " You do not have permission to use /enderlink reload."));
            return 0;
        }

        EnderLinkMod.reloadConfig();
        source.sendSuccess(() -> Component.literal(EnderLinkMod.logPrefix() + " Config reloaded."), true);
        return 1;
    }

    private static MutableComponent formatPadComponent(TeleportPadData pad) {
        return padNameComponent(pad.name)
            .append(Component.literal(" (" + pad.x + ", " + pad.y + ", " + pad.z + " in " + shortDimensionName(pad.dimension) + ")"));
    }

    private static MutableComponent padNameComponent(String name) {
        return Component.literal(name).withStyle(ChatFormatting.AQUA);
    }

    private static MutableComponent prefixed(String message) {
        return Component.literal(EnderLinkMod.logPrefix() + " " + message);
    }

    private static CompletableFuture<Suggestions> suggestPadNames(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        for (TeleportPadData pad : EnderLinkMod.padStore().getAllPads()) {
            builder.suggest(pad.name);
        }
        return builder.buildFuture();
    }

    private static String shortDimensionName(String dimensionId) {
        return dimensionId.startsWith("minecraft:") ? dimensionId.substring("minecraft:".length()) : dimensionId;
    }

    private static String pairKey(String first, String second) {
        return first.compareTo(second) <= 0 ? first + "|" + second : second + "|" + first;
    }
}
