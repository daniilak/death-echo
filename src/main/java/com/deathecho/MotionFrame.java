package com.deathecho;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public record MotionFrame(
		double x,
		double y,
		double z,
		float yaw,
		float pitch,
		boolean sneaking,
		boolean sprinting,
		boolean swinging,
		ItemStack hand
) {
	public MotionFrame {
		hand = hand == null ? ItemStack.EMPTY : hand.copy();
	}

	public static MotionFrame capture(Player player) {
		return new MotionFrame(
				player.getX(),
				player.getY(),
				player.getZ(),
				player.getYRot(),
				player.getXRot(),
				player.isShiftKeyDown(),
				player.isSprinting(),
				player.swinging,
				player.getMainHandItem()
		);
	}

	public MotionFrame withY(double newY) {
		return new MotionFrame(x, newY, z, yaw, pitch, sneaking, sprinting, swinging, hand);
	}

	public CompoundTag save(HolderLookup.Provider provider) {
		CompoundTag tag = new CompoundTag();
		tag.putDouble("X", x);
		tag.putDouble("Y", y);
		tag.putDouble("Z", z);
		tag.putFloat("Yaw", yaw);
		tag.putFloat("Pitch", pitch);
		tag.putBoolean("Sneak", sneaking);
		tag.putBoolean("Sprint", sprinting);
		tag.putBoolean("Swing", swinging);
		if (!hand.isEmpty()) {
			Tag saved = hand.save(provider);
			if (saved instanceof CompoundTag compound) {
				tag.put("Hand", compound);
			}
		}
		return tag;
	}

	public static MotionFrame load(CompoundTag tag, HolderLookup.Provider provider) {
		ItemStack hand = ItemStack.EMPTY;
		if (tag.contains("Hand", Tag.TAG_COMPOUND)) {
			hand = ItemStack.parseOptional(provider, tag.getCompound("Hand"));
		}
		return new MotionFrame(
				tag.getDouble("X"),
				tag.getDouble("Y"),
				tag.getDouble("Z"),
				tag.getFloat("Yaw"),
				tag.getFloat("Pitch"),
				tag.getBoolean("Sneak"),
				tag.getBoolean("Sprint"),
				tag.getBoolean("Swing"),
				hand
		);
	}
}
