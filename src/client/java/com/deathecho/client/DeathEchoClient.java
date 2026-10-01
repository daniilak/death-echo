package com.deathecho.client;

import com.deathecho.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public final class DeathEchoClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(ModEntities.DEATH_GHOST, DeathGhostRenderer::new);
	}
}
