package com.karpandsmeargle.editor.client;

import com.karpandsmeargle.editor.client.MonumentaFetcher.RequestFailException;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class Commands {
    public final LiteralArgumentBuilder<FabricClientCommandSource> getAllInfoCommand;

    public Commands(MonumentaFetcher fetcher) {
        this.getAllInfoCommand = 
            ClientCommandManager
                .literal("get_all_info")
                .executes(context -> {
                    Main.LOGGER.info("Fetching all tag information...");
                    context.getSource().sendFeedback(Text.literal("Fetching all tag information..."));

                    fetcher.requestAllInfo()
                        .whenComplete((result, ex) -> {
                            if (ex != null) {
                                Main.LOGGER.error("Failed to fetch tag information.");
                                MinecraftClient.getInstance().execute(() -> context.getSource().sendError(Text.literal("Failed to fetch tag information.")));
                                
                                Main.LOGGER.error(ex);
                                if (ex instanceof RequestFailException rfe) {
                                    MinecraftClient.getInstance().execute(() -> context.getSource().sendError(Text.literal(rfe.reason())));
                                } else {
                                    MinecraftClient.getInstance().execute(() -> context.getSource().sendError(Text.literal("Check your client logs for an exception stack trace.")));
                                }

                                return;
                            }

                            Main.LOGGER.info("Received " + result.size() + " tags.");
                            MinecraftClient.getInstance().execute(() -> context.getSource().sendFeedback(Text.literal("Received " + result.size() + " tags.")));
                            for (var entry : result.entrySet()) {
                                MinecraftClient.getInstance().execute(() ->
                                    context.getSource()
                                        .sendFeedback(Text.literal(
                                            "[" + entry.getKey().name() + "] "
                                            + entry.getKey().description()
                                            + (entry.getKey().deprecated() ? "" : " (DEPRECATED)")
                                        )));
                                for (var param : entry.getValue()) {
                                    MinecraftClient.getInstance().execute(() ->
                                        context.getSource()
                                            .sendFeedback(Text.literal(
                                                "- " + param.name() + " (" + param.type() + "): "
                                                + param.description()
                                                + (param.deprecated() ? "" : " (DEPRECATED)")
                                            )));
                                }
                            }
                        });

                    return 1;
                });
    }

        
}
