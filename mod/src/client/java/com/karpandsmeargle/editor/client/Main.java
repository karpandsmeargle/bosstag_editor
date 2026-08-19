package com.karpandsmeargle.editor.client;

import net.fabricmc.api.ClientModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Main implements ClientModInitializer {
	private MonumentaFetcher fetcher;
	public static final String MOD_IDENTIFIER = "bosstag_editor";
	public static final Logger LOGGER = LogManager.getLogger(MOD_IDENTIFIER);

	@Override
	public void onInitializeClient() {
		fetcher = new MonumentaFetcher();
	}

}