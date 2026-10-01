package com.deathecho;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MotionRecorder {
	private static final Map<UUID, Deque<MotionFrame>> BUFFERS = new HashMap<>();
	private static final Map<UUID, DeathRecording> PENDING = new HashMap<>();

	private MotionRecorder() {
	}

	public static void register() {
		ServerTickEvents.END_WORLD_TICK.register(MotionRecorder::onWorldTick);
	}

	public static void clear() {
		BUFFERS.clear();
		PENDING.clear();
	}

	public static void remember(ServerPlayer player) {
		DeathRecording recording = capture(player);
		if (recording != null) {
			PENDING.put(player.getUUID(), recording);
		}
	}

	public static DeathRecording takePending(ServerPlayer player) {
		BUFFERS.remove(player.getUUID());
		return PENDING.remove(player.getUUID());
	}

	private static void onWorldTick(ServerLevel level) {
		for (ServerPlayer player : level.players()) {
			if (player.isAlive() && !player.isDeadOrDying()) {
				PENDING.remove(player.getUUID());
			}
			if (!player.isAlive() || player.isSpectator() || player.isDeadOrDying()) {
				continue;
			}
			if (player.tickCount % DeathEchoMod.SAMPLE_INTERVAL_TICKS != 0) {
				continue;
			}
			push(player);
		}
	}

	private static void push(ServerPlayer player) {
		Deque<MotionFrame> buffer = BUFFERS.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
		buffer.addLast(MotionFrame.capture(player));
		while (buffer.size() > DeathEchoMod.SAMPLE_COUNT) {
			buffer.removeFirst();
		}
	}

	private static DeathRecording capture(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return null;
		}
		Deque<MotionFrame> buffer = BUFFERS.get(player.getUUID());
		List<MotionFrame> frames = new ArrayList<>();
		if (buffer != null) {
			frames.addAll(buffer);
		}
		frames.add(MotionFrame.capture(player));
		frames = trim(frames);
		frames = liftOutOfVoid(level, frames);

		boolean keepItems = player.isCreative()
				|| player.isSpectator()
				|| level.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);
		Inventory inventory = player.getInventory();
		List<ItemStack> loot = new ArrayList<>();
		if (!keepItems) {
			collectLoot(inventory.items, loot);
			collectLoot(inventory.armor, loot);
			collectLoot(inventory.offhand, loot);
		}

		ItemStack[] armor = new ItemStack[4];
		for (int i = 0; i < armor.length; i++) {
			armor[i] = inventory.armor.get(i).copy();
		}
		ItemStack hand = frames.get(frames.size() - 1).hand();
		return new DeathRecording(
				player.getUUID(),
				player.getGameProfile().getName(),
				frames,
				loot,
				armor,
				inventory.offhand.get(0).copy(),
				hand,
				0,
				false,
				""
		);
	}

	private static List<MotionFrame> trim(List<MotionFrame> frames) {
		int extra = frames.size() - DeathEchoMod.SAMPLE_COUNT;
		if (extra > 0) {
			frames = new ArrayList<>(frames.subList(extra, frames.size()));
		}
		if (frames.size() == 1) {
			frames.add(frames.get(0));
		}
		return frames;
	}

	private static List<MotionFrame> liftOutOfVoid(ServerLevel level, List<MotionFrame> frames) {
		double floor = level.getMinBuildHeight() + 1.0;
		List<MotionFrame> safe = new ArrayList<>();
		for (MotionFrame frame : frames) {
			if (frame.y() >= floor) {
				safe.add(frame);
			}
		}
		if (safe.size() >= 2) {
			return safe;
		}
		MotionFrame best = frames.get(0);
		for (MotionFrame frame : frames) {
			if (frame.y() > best.y()) {
				best = frame;
			}
		}
		MotionFrame clamped = best.withY(Math.max(best.y(), floor));
		return List.of(clamped, clamped);
	}

	private static void collectLoot(List<ItemStack> source, List<ItemStack> loot) {
		for (ItemStack stack : source) {
			if (stack.isEmpty() || hasVanishingCurse(stack)) {
				continue;
			}
			loot.add(stack.copy());
		}
	}

	private static boolean hasVanishingCurse(ItemStack stack) {
		if (!stack.isEnchanted()) {
			return false;
		}
		for (Holder<Enchantment> holder : stack.getEnchantments().keySet()) {
			if (holder.is(Enchantments.VANISHING_CURSE)) {
				return true;
			}
		}
		return false;
	}

	static boolean keepsInventory(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return true;
		}
		return player.isCreative()
				|| player.isSpectator()
				|| level.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);
	}
}
