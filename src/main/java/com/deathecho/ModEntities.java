package com.deathecho;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
	public static final EntityType<DeathGhost> DEATH_GHOST = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			DeathEchoMod.id("death_ghost"),
			EntityType.Builder.of(DeathGhost::new, MobCategory.MISC)
					.sized(0.6F, 1.8F)
					.eyeHeight(1.62F)
					.clientTrackingRange(80)
					.updateInterval(1)
					.fireImmune()
					.build(DeathEchoMod.MOD_ID + ":death_ghost")
	);

	private ModEntities() {
	}

	public static void registerAttributes() {
		FabricDefaultAttributeRegistry.register(DEATH_GHOST, DeathGhost.createAttributes());
	}
}
