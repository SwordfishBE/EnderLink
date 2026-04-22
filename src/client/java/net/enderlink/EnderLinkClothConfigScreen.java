package net.enderlink;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.enderlink.config.CommandAccessMode;
import net.enderlink.config.EnderLinkConfig;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class EnderLinkClothConfigScreen {
    private EnderLinkClothConfigScreen() {
    }

    static Screen create(Screen parent) {
        EnderLinkConfig config = EnderLinkMod.loadConfigForEditing();

        ConfigBuilder builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Component.literal("EnderLink Config"))
            .setSavingRunnable(() -> EnderLinkMod.applyEditedConfig(config));

        ConfigEntryBuilder entries = builder.entryBuilder();

        ConfigCategory general = builder.getOrCreateCategory(Component.literal("General"));
        general.addEntry(entries.startStrField(Component.literal("Teleport Blocks"), String.join(", ", config.teleportBlocks))
            .setDefaultValue("minecraft:gold_block, minecraft:lapis_block")
            .setTooltip(Component.literal("Comma-separated block IDs that act as EnderLink pads. Carpets can cover them without disabling the pad."))
            .setSaveConsumer(value -> config.teleportBlocks = java.util.Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .distinct()
                .toList())
            .build());
        general.addEntry(entries.startIntField(Component.literal("Charge Ticks"), config.chargeTicks)
            .setDefaultValue(60)
            .setMin(0)
            .setTooltip(Component.literal("Warmup time before teleporting. 20 ticks equals 1 second."))
            .setSaveConsumer(value -> config.chargeTicks = value)
            .build());
        general.addEntry(entries.startBooleanToggle(Component.literal("Safety Enabled"), config.safetyEnabled)
            .setDefaultValue(true)
            .setTooltip(Component.literal("Reject unsafe destinations, fluids, blocked spaces, and invalid arrivals."))
            .setSaveConsumer(value -> config.safetyEnabled = value)
            .build());
        general.addEntry(entries.startBooleanToggle(Component.literal("Particles Enabled"), config.particlesEnabled)
            .setDefaultValue(true)
            .setTooltip(Component.literal("Show the configured particle during the warmup and again on arrival."))
            .setSaveConsumer(value -> config.particlesEnabled = value)
            .build());
        general.addEntry(entries.startStrField(Component.literal("Particle Type"), config.particleType)
            .setDefaultValue("minecraft:witch")
            .setTooltip(Component.literal("Simple particle ID used during warmup and on arrival."))
            .setSaveConsumer(value -> config.particleType = value)
            .build());
        general.addEntry(entries.startIntField(Component.literal("Warmup Particle Count"), config.warmupParticleCount)
            .setDefaultValue(8)
            .setMin(0)
            .setTooltip(Component.literal("Number of particles to spawn while the teleport is charging."))
            .setSaveConsumer(value -> config.warmupParticleCount = value)
            .build());
        general.addEntry(entries.startIntField(Component.literal("Arrival Burst Particle Count"), config.arrivalBurstParticleCount)
            .setDefaultValue(24)
            .setMin(0)
            .setTooltip(Component.literal("Number of particles to spawn immediately when the teleport completes."))
            .setSaveConsumer(value -> config.arrivalBurstParticleCount = value)
            .build());
        general.addEntry(entries.startIntField(Component.literal("Arrival Trail Particle Count"), config.arrivalTrailParticleCount)
            .setDefaultValue(6)
            .setMin(0)
            .setTooltip(Component.literal("Number of particles to spawn each tick during the short arrival trail effect."))
            .setSaveConsumer(value -> config.arrivalTrailParticleCount = value)
            .build());
        general.addEntry(entries.startIntField(Component.literal("Arrival Particle Duration Ticks"), config.arrivalParticleDurationTicks)
            .setDefaultValue(60)
            .setMin(0)
            .setTooltip(Component.literal("How long arrival particles continue after teleporting. 20 ticks equals 1 second."))
            .setSaveConsumer(value -> config.arrivalParticleDurationTicks = value)
            .build());
        general.addEntry(entries.startBooleanToggle(Component.literal("Sound Enabled"), config.soundEnabled)
            .setDefaultValue(true)
            .setTooltip(Component.literal("Play the configured sound at the destination."))
            .setSaveConsumer(value -> config.soundEnabled = value)
            .build());
        general.addEntry(entries.startStrField(Component.literal("Sound Event"), config.soundEvent)
            .setDefaultValue("minecraft:entity.illusioner.mirror_move")
            .setTooltip(Component.literal("Sound event ID to play when teleporting finishes."))
            .setSaveConsumer(value -> config.soundEvent = value)
            .build());
        general.addEntry(entries.startFloatField(Component.literal("Sound Volume"), config.soundVolume)
            .setDefaultValue(0.8F)
            .setMin(0.0F)
            .setTooltip(Component.literal("Volume for the arrival sound effect."))
            .setSaveConsumer(value -> config.soundVolume = value)
            .build());
        general.addEntry(entries.startFloatField(Component.literal("Sound Pitch"), config.soundPitch)
            .setDefaultValue(1.0F)
            .setMin(0.0F)
            .setTooltip(Component.literal("Pitch for the arrival sound effect."))
            .setSaveConsumer(value -> config.soundPitch = value)
            .build());
        general.addEntry(entries.startStrField(Component.literal("Allowed Dimensions"), String.join(", ", config.allowedDimensions))
            .setDefaultValue("minecraft:overworld, minecraft:the_nether, minecraft:the_end")
            .setTooltip(Component.literal("Comma-separated dimension IDs allowed to participate in EnderLink teleports."))
            .setSaveConsumer(value -> config.allowedDimensions = java.util.Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .distinct()
                .toList())
            .build());
        general.addEntry(entries.startBooleanToggle(Component.literal("LuckPerms Enabled"), config.luckPerms)
            .setDefaultValue(false)
            .setTooltip(Component.literal("Use LuckPerms permission nodes when the LuckPerms mod is installed."))
            .setSaveConsumer(value -> config.luckPerms = value)
            .build());
        general.addEntry(entries.startIntField(Component.literal("Max Pads Per Player"), config.maxPadsPerPlayer)
            .setDefaultValue(10)
            .setMin(0)
            .setTooltip(Component.literal("Default pad limit when no higher permission override exists."))
            .setSaveConsumer(value -> config.maxPadsPerPlayer = value)
            .build());
        general.addEntry(entries.startIntField(Component.literal("Max Links Per Player"), config.maxLinksPerPlayer)
            .setDefaultValue(5)
            .setMin(0)
            .setTooltip(Component.literal("Default link limit when no higher permission override exists."))
            .setSaveConsumer(value -> config.maxLinksPerPlayer = value)
            .build());

        ConfigCategory access = builder.getOrCreateCategory(Component.literal("Command Access"));
        access.addEntry(entries.startStrField(Component.literal("Add Access"), config.addAccess.name())
            .setDefaultValue(CommandAccessMode.OPS_ONLY.name())
            .setTooltip(Component.literal("EVERYONE or OPS_ONLY when LuckPerms is inactive."))
            .setSaveConsumer(value -> config.addAccess = CommandAccessMode.parseOrDefault(value, CommandAccessMode.OPS_ONLY))
            .build());
        access.addEntry(entries.startStrField(Component.literal("Link Access"), config.linkAccess.name())
            .setDefaultValue(CommandAccessMode.OPS_ONLY.name())
            .setTooltip(Component.literal("EVERYONE or OPS_ONLY when LuckPerms is inactive."))
            .setSaveConsumer(value -> config.linkAccess = CommandAccessMode.parseOrDefault(value, CommandAccessMode.OPS_ONLY))
            .build());
        access.addEntry(entries.startStrField(Component.literal("Unlink Access"), config.unlinkAccess.name())
            .setDefaultValue(CommandAccessMode.OPS_ONLY.name())
            .setTooltip(Component.literal("EVERYONE or OPS_ONLY when LuckPerms is inactive."))
            .setSaveConsumer(value -> config.unlinkAccess = CommandAccessMode.parseOrDefault(value, CommandAccessMode.OPS_ONLY))
            .build());
        access.addEntry(entries.startStrField(Component.literal("Remove Access"), config.removeAccess.name())
            .setDefaultValue(CommandAccessMode.OPS_ONLY.name())
            .setTooltip(Component.literal("EVERYONE or OPS_ONLY when LuckPerms is inactive."))
            .setSaveConsumer(value -> config.removeAccess = CommandAccessMode.parseOrDefault(value, CommandAccessMode.OPS_ONLY))
            .build());
        access.addEntry(entries.startStrField(Component.literal("Rename Access"), config.renameAccess.name())
            .setDefaultValue(CommandAccessMode.OPS_ONLY.name())
            .setTooltip(Component.literal("EVERYONE or OPS_ONLY when LuckPerms is inactive."))
            .setSaveConsumer(value -> config.renameAccess = CommandAccessMode.parseOrDefault(value, CommandAccessMode.OPS_ONLY))
            .build());
        access.addEntry(entries.startStrField(Component.literal("List Access"), config.listAccess.name())
            .setDefaultValue(CommandAccessMode.EVERYONE.name())
            .setTooltip(Component.literal("EVERYONE or OPS_ONLY when LuckPerms is inactive."))
            .setSaveConsumer(value -> config.listAccess = CommandAccessMode.parseOrDefault(value, CommandAccessMode.EVERYONE))
            .build());
        access.addEntry(entries.startStrField(Component.literal("Info Access"), config.infoAccess.name())
            .setDefaultValue(CommandAccessMode.EVERYONE.name())
            .setTooltip(Component.literal("EVERYONE or OPS_ONLY when LuckPerms is inactive."))
            .setSaveConsumer(value -> config.infoAccess = CommandAccessMode.parseOrDefault(value, CommandAccessMode.EVERYONE))
            .build());

        return builder.build();
    }
}
