package com.deathecho;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/** Короткая память о смерти для кладбища: без траектории и без лута. */
public record EchoMemory(
		UUID ownerId,
		String ownerName,
		int deathNumber,
		boolean proud,
		String deathMessage,
		ItemStack[] armor,
		ItemStack offhand,
		ItemStack mainHand
) {
	public EchoMemory {
		ownerName = ownerName == null ? "" : ownerName;
		deathMessage = deathMessage == null ? "" : deathMessage;
		armor = copyArmor(armor);
		offhand = offhand == null ? ItemStack.EMPTY : offhand.copy();
		mainHand = mainHand == null ? ItemStack.EMPTY : mainHand.copy();
	}

	public CompoundTag save(HolderLookup.Provider provider) {
		CompoundTag tag = new CompoundTag();
		tag.putUUID("Owner", ownerId);
		tag.putString("Name", ownerName);
		tag.putInt("Number", deathNumber);
		tag.putBoolean("Proud", proud);
		tag.putString("Message", deathMessage);
		for (int i = 0; i < armor.length; i++) {
			putItem(tag, "Armor" + i, armor[i], provider);
		}
		putItem(tag, "Offhand", offhand, provider);
		putItem(tag, "Hand", mainHand, provider);
		return tag;
	}

	public static EchoMemory load(CompoundTag tag, HolderLookup.Provider provider) {
		ItemStack[] armor = new ItemStack[4];
		for (int i = 0; i < armor.length; i++) {
			armor[i] = readItem(tag, "Armor" + i, provider);
		}
		return new EchoMemory(
				tag.getUUID("Owner"),
				tag.getString("Name"),
				tag.getInt("Number"),
				tag.getBoolean("Proud"),
				tag.getString("Message"),
				armor,
				readItem(tag, "Offhand", provider),
				readItem(tag, "Hand", provider)
		);
	}

	static ItemStack[] copyArmor(ItemStack[] source) {
		ItemStack[] armor = new ItemStack[4];
		for (int i = 0; i < armor.length; i++) {
			ItemStack stack = source != null && i < source.length ? source[i] : ItemStack.EMPTY;
			armor[i] = stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
		}
		return armor;
	}

	private static void putItem(CompoundTag tag, String key, ItemStack stack, HolderLookup.Provider provider) {
		if (stack.isEmpty()) {
			return;
		}
		Tag saved = stack.save(provider);
		if (saved instanceof CompoundTag compound) {
			tag.put(key, compound);
		}
	}

	static ItemStack readItem(CompoundTag tag, String key, HolderLookup.Provider provider) {
		if (!tag.contains(key, Tag.TAG_COMPOUND)) {
			return ItemStack.EMPTY;
		}
		return ItemStack.parseOptional(provider, tag.getCompound(key));
	}
}
