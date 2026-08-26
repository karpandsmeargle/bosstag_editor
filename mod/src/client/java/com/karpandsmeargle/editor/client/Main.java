package com.karpandsmeargle.editor.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Main implements ClientModInitializer {
    private MonumentaFetcher fetcher;
    private Commands commands;
    public static final String MOD_IDENTIFIER = "bosstag_editor";
    public static final Logger LOGGER = LogManager.getLogger(MOD_IDENTIFIER);
    
    @Override
    public void onInitializeClient() {
        fetcher = new MonumentaFetcher();
        commands = new Commands(fetcher);
        
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(commands.getAllInfoCommand);
        });
    }
    
}