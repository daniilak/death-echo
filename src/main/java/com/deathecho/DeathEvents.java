package com.deathecho;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class DeathEvents {
	private DeathEvents() {
	}

	public static void register() {
		ServerLivingEntityEvents.ALLOW_DEATH.register(DeathEvents::onAllowDeath);
		ServerLivingEntityEvents.AFTER_DEATH.register(DeathEvents::onDeath);
	}

	private static boolean onAllowDeath(net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.damagesource.DamageSource source, float amount) {
		if (entity instanceof ServerPlayer player && !player.isSpectator()) {
			MotionRecorder.remember(player);
		}
		return true;
	}

	private static void onDeath(net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.damagesource.DamageSource source) {
		if (!(entity instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		if (player.isSpectator()) {
			return;
		}

		DeathRecording recording = MotionRecorder.takePending(player);
		if (recording == null || recording.frames().size() < 2) {
			return;
		}

		GhostWorldData data = GhostWorldData.get(level);
		int number = data.nextDeath();
		String cause = player.getCombatTracker().getDeathMessage().getString();
		recording.assign(number, cause);
		data.addMemory(recording.toMemory());

		DeathGhost ghost = spawnReplay(level, recording);
		if (ghost != null && recording.hasLoot()) {
			reclaimDrops(level, player.position(), recording.loot());
		}

		player.sendSystemMessage(Component.literal(deathLine(player, recording, number, data)));

		if (number >= DeathEchoMod.GRAVEYARD_DEATHS && !data.graveyardBuilt()) {
			raiseGraveyard(level, player.blockPosition(), data, true);
		}
	}

	public static BlockPos raiseGraveyard(ServerLevel level, BlockPos deathPos, GhostWorldData data, boolean announceMilestone) {
		List<EchoMemory> memories = data.sample(8);
		BlockPos feet = GraveyardBuilder.build(level, deathPos, memories);
		data.markGraveyard(level.dimension().location().toString(), feet.getX(), feet.getY(), feet.getZ());
		String text = announceMilestone
				? "Смерть номер " + data.deathCount() + ". Кладбище появилось на "
				: "Кладбище собрано на ";
		text += feet.getX() + " " + feet.getY() + " " + feet.getZ() + ". Прошлые ты уже обсуждают тебя.";
		Component message = Component.literal(text);
		for (ServerPlayer player : level.players()) {
			player.sendSystemMessage(message);
		}
		level.playSound(null, feet, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.4F, 0.5F);
		return feet;
	}

	private static DeathGhost spawnReplay(ServerLevel level, DeathRecording recording) {
		DeathGhost ghost = ModEntities.DEATH_GHOST.create(level);
		if (ghost == null) {
			return null;
		}
		MotionFrame start = recording.frames().get(0);
		ghost.applyRecording(recording, false, start.x(), start.y(), start.z(), start.yaw());
		level.addFreshEntity(ghost);
		level.playSound(null, ghost.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 0.8F, 0.7F);
		return ghost;
	}

	private static String deathLine(ServerPlayer player, DeathRecording recording, int number, GhostWorldData data) {
		int left = Math.max(0, DeathEchoMod.GRAVEYARD_DEATHS - number);
		String progress = left == 0
				? "Это была смерть, после которой собирается кладбище."
				: "До кладбища ещё " + left + ".";
		if (MotionRecorder.keepsInventory(player)) {
			return "Смерть #" + number + ". Призрак повторяет последние 5 секунд. Вещи остались у тебя. " + progress;
		}
		if (!recording.hasLoot()) {
			return "Смерть #" + number + ". Призрак повторяет последние 5 секунд. Забирать нечего, но он дерётся. " + progress;
		}
		return "Смерть #" + number + ". Призрак повторяет последние 5 секунд. Убей его, чтобы забрать вещи. " + progress;
	}

	private static void reclaimDrops(ServerLevel level, Vec3 pos, List<ItemStack> loot) {
		List<ItemStack> pending = new ArrayList<>();
		for (ItemStack stack : loot) {
			pending.add(stack.copy());
		}
		AABB box = new AABB(pos, pos).inflate(4.0);
		for (ItemEntity itemEntity : level.getEntitiesOfClass(ItemEntity.class, box, entity -> entity.getAge() <= 1)) {
			ItemStack dropped = itemEntity.getItem();
			for (ItemStack want : pending) {
				if (want.isEmpty() || dropped.isEmpty()) {
					continue;
				}
				if (!ItemStack.isSameItemSameComponents(want, dropped)) {
					continue;
				}
				int take = Math.min(want.getCount(), dropped.getCount());
				want.shrink(take);
				dropped.shrink(take);
				if (dropped.isEmpty()) {
					itemEntity.discard();
				} else {
					itemEntity.setItem(dropped);
				}
				break;
			}
		}
	}
}
