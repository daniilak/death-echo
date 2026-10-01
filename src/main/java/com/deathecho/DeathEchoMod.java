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
	/** Сколько смертей в мире нужно, чтобы само собралось кладбище. В чат это не пишется. */
	public static final int GRAVEYARD_DEATHS = 12;
	/** Пустой призрак живёт полторы минуты, потом тает. С вещами не тает. */
	public static final int EMPTY_REPLAY_TICKS = 20 * 90;
	/** Сколько пустых повторов одного игрока держать сразу. Лишние тают быстрее. */
	public static final int MAX_EMPTY_REPLAYS = 4;
	public static final int EMPTY_OVERFLOW_TICKS = 20 * 5;
	public static final double REPLAY_HIT_RANGE = 2.8;
	public static final double FIGHT_LEASH = 24.0;
	public static final double GRAVEYARD_LEASH = 16.0;
	public static final double GRAVEYARD_PAD = 2.4;

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
