package com.tovkaic.phonon.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.tovkaic.phonon.audio.AudioManager;
import com.tovkaic.phonon.audio.AudioResource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Server commands for managing audio resources.
 * Simple, flat command structure - no nested nonsense.
 */
public class PhononCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("phonon")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("add")
                .then(Commands.argument("name", StringArgumentType.word())
                    .then(Commands.argument("url", StringArgumentType.greedyString())
                        .executes(PhononCommand::addResource)
                    )
                )
            )
            .then(Commands.literal("list")
                .executes(PhononCommand::listResources)
            )
            .then(Commands.literal("remove")
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(PhononCommand::removeResource)
                )
            )
        );
    }

    private static int addResource(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        String url = StringArgumentType.getString(ctx, "url");

        AudioManager manager = AudioManager.getInstance();
        if (manager.getResourceByName(name).isPresent()) {
            ctx.getSource().sendFailure(Component.literal("Resource '" + name + "' already exists"));
            return 0;
        }

        AudioResource resource = new AudioResource(name, url);
        manager.addResource(resource);
        ctx.getSource().sendSuccess(
            () -> Component.literal("Added audio resource: " + name),
            true
        );
        return 1;
    }

    private static int listResources(CommandContext<CommandSourceStack> ctx) {
        AudioManager manager = AudioManager.getInstance();
        List<AudioResource> resources = manager.getAllResources();

        if (resources.isEmpty()) {
            ctx.getSource().sendSuccess(
                () -> Component.literal("No audio resources registered"),
                false
            );
            return 0;
        }

        ctx.getSource().sendSuccess(
            () -> Component.literal("Audio resources (" + resources.size() + "):"),
            false
        );
        for (AudioResource resource : resources) {
            ctx.getSource().sendSuccess(
                () -> Component.literal("  - " + resource.name() + ": " + resource.url()),
                false
            );
        }
        return resources.size();
    }

    private static int removeResource(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        AudioManager manager = AudioManager.getInstance();

        return manager.getResourceByName(name)
            .map(resource -> {
                manager.removeResource(resource.id());
                ctx.getSource().sendSuccess(
                    () -> Component.literal("Removed audio resource: " + name),
                    true
                );
                return 1;
            })
            .orElseGet(() -> {
                ctx.getSource().sendFailure(Component.literal("Resource '" + name + "' not found"));
                return 0;
            });
    }
}
