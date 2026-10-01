package com.deathecho;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DeathEchoMod implements ModInitializer {
	public static final String MOD_ID = "deathecho";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Кадр раз в 0.25 секунды. */
	public static final int SAMPLE_INTERVAL_TICKS = 5;
	/** 20 кадров — последние 5 секунд. */
	public static final int SAMPLE_COUNT = 20;
	public static final int GRAVEYARD_DEATHS = 50;

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ModEntities.registerAttributes();
		MotionRecorder.register();
		DeathEvents.register();
		GhostCommands.register();
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			MotionRecorder.clear();
			GhostTalk.clear();
		});
		LOGGER.info("Death Echo loaded: deaths will leave a 5 second replay");
	}
}
