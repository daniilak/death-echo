package com.deathecho;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DeathRecording {
	private final UUID ownerId;
	private final String ownerName;
	private final List<MotionFrame> frames;
	private final List<ItemStack> loot;
	private final ItemStack[] armor;
	private final ItemStack offhand;
	private final ItemStack mainHand;
	private int deathNumber;
	private boolean proud;
	private String deathMessage;

	public DeathRecording(
			UUID ownerId,
			String ownerName,
			List<MotionFrame> frames,
			List<ItemStack> loot,
			ItemStack[] armor,
			ItemStack offhand,
			ItemStack mainHand,
			int deathNumber,
			boolean proud,
			String deathMessage
	) {
		this.ownerId = ownerId;
		this.ownerName = ownerName == null ? "" : ownerName;
		this.frames = List.copyOf(frames);
		this.loot = new ArrayList<>();
		for (ItemStack stack : loot) {
			if (stack != null && !stack.isEmpty()) {
				this.loot.add(stack.copy());
			}
		}
		this.armor = EchoMemory.copyArmor(armor);
		this.offhand = offhand == null ? ItemStack.EMPTY : offhand.copy();
		this.mainHand = mainHand == null ? ItemStack.EMPTY : mainHand.copy();
		this.deathNumber = deathNumber;
		this.proud = proud;
		this.deathMessage = deathMessage == null ? "" : deathMessage;
	}

	public UUID ownerId() {
		return ownerId;
	}

	public String ownerName() {
		return ownerName;
	}

	public List<MotionFrame> frames() {
		return frames;
	}

	public List<ItemStack> loot() {
		return loot;
	}

	public ItemStack[] armor() {
		return armor;
	}

	public ItemStack offhand() {
		return offhand;
	}

	public ItemStack mainHand() {
		return mainHand;
	}

	public int deathNumber() {
		return deathNumber;
	}

	public boolean proud() {
		return proud;
	}

	public String deathMessage() {
		return deathMessage;
	}

	public void assign(int number, String message) {
		this.deathNumber = number;
		this.proud = number % 2 == 0;
		this.deathMessage = message == null ? "" : message;
	}

	public boolean hasLoot() {
		return !loot.isEmpty();
	}

	public static DeathRecording fromMemory(EchoMemory memory) {
		return new DeathRecording(
				memory.ownerId(),
				memory.ownerName(),
				List.of(),
				List.of(),
				memory.armor(),
				memory.offhand(),
				memory.mainHand(),
				memory.deathNumber(),
				memory.proud(),
				memory.deathMessage()
		);
	}

	public EchoMemory toMemory() {
		return new EchoMemory(ownerId, ownerName, deathNumber, proud, deathMessage, armor, offhand, mainHand);
	}

	public CompoundTag save(HolderLookup.Provider provider) {
		CompoundTag tag = new CompoundTag();
		tag.putUUID("Owner", ownerId);
		tag.putString("Name", ownerName);
		tag.putInt("Number", deathNumber);
		tag.putBoolean("Proud", proud);
		tag.putString("Message", deathMessage);
		ListTag frameTags = new ListTag();
		for (MotionFrame frame : frames) {
			frameTags.add(frameTags.size(), frame.save(provider));
		}
		tag.put("Frames", frameTags);
		ListTag lootTags = new ListTag();
		for (ItemStack stack : loot) {
			Tag saved = stack.save(provider);
			if (saved instanceof CompoundTag compound) {
				lootTags.add(lootTags.size(), compound);
			}
		}
		tag.put("Loot", lootTags);
		for (int i = 0; i < armor.length; i++) {
			putItem(tag, "Armor" + i, armor[i], provider);
		}
		putItem(tag, "Offhand", offhand, provider);
		putItem(tag, "Hand", mainHand, provider);
		return tag;
	}

	public static DeathRecording load(CompoundTag tag, HolderLookup.Provider provider) {
		List<MotionFrame> frames = new ArrayList<>();
		ListTag frameTags = tag.getList("Frames", Tag.TAG_COMPOUND);
		for (int i = 0; i < frameTags.size(); i++) {
			frames.add(MotionFrame.load(frameTags.getCompound(i), provider));
		}
		List<ItemStack> loot = new ArrayList<>();
		ListTag lootTags = tag.getList("Loot", Tag.TAG_COMPOUND);
		for (int i = 0; i < lootTags.size(); i++) {
			ItemStack stack = ItemStack.parseOptional(provider, lootTags.getCompound(i));
			if (!stack.isEmpty()) {
				loot.add(stack);
			}
		}
		ItemStack[] armor = new ItemStack[4];
		for (int i = 0; i < armor.length; i++) {
			armor[i] = EchoMemory.readItem(tag, "Armor" + i, provider);
		}
		return new DeathRecording(
				tag.getUUID("Owner"),
				tag.getString("Name"),
				frames,
				loot,
				armor,
				EchoMemory.readItem(tag, "Offhand", provider),
				EchoMemory.readItem(tag, "Hand", provider),
				tag.getInt("Number"),
				tag.getBoolean("Proud"),
				tag.getString("Message")
		);
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
}
