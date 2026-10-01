package com.deathecho;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

public final class GhostWorldData extends SavedData {
	private static final String STORAGE_ID = "deathecho";
	private static final int MAX_MEMORIES = 128;

	public static final Factory<GhostWorldData> FACTORY = new Factory<>(
			GhostWorldData::new,
			GhostWorldData::load,
			DataFixTypes.LEVEL
	);

	private int deathCount;
	private boolean graveyardBuilt;
	private String graveyardDimension = "";
	private int graveyardX;
	private int graveyardY;
	private int graveyardZ;
	private final List<EchoMemory> memories = new ArrayList<>();

	public static GhostWorldData get(ServerLevel level) {
		return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, STORAGE_ID);
	}

	public int deathCount() {
		return deathCount;
	}

	public int nextDeath() {
		deathCount++;
		setDirty();
		return deathCount;
	}

	public boolean graveyardBuilt() {
		return graveyardBuilt;
	}

	public String graveyardDimension() {
		return graveyardDimension;
	}

	public int graveyardX() {
		return graveyardX;
	}

	public int graveyardY() {
		return graveyardY;
	}

	public int graveyardZ() {
		return graveyardZ;
	}

	public void markGraveyard(String dimension, int x, int y, int z) {
		graveyardBuilt = true;
		graveyardDimension = dimension;
		graveyardX = x;
		graveyardY = y;
		graveyardZ = z;
		setDirty();
	}

	public void addMemory(EchoMemory memory) {
		memories.add(memory);
		while (memories.size() > MAX_MEMORIES) {
			memories.remove(0);
		}
		setDirty();
	}

	public List<EchoMemory> sample(int count) {
		if (memories.isEmpty() || count <= 0) {
			return List.of();
		}
		if (memories.size() <= count) {
			return List.copyOf(memories);
		}
		List<EchoMemory> picked = new ArrayList<>();
		double step = (memories.size() - 1) / (double) (count - 1);
		for (int i = 0; i < count; i++) {
			picked.add(memories.get((int) Math.round(i * step)));
		}
		return picked;
	}

	public static GhostWorldData load(CompoundTag tag, HolderLookup.Provider provider) {
		GhostWorldData data = new GhostWorldData();
		data.deathCount = tag.getInt("DeathCount");
		data.graveyardBuilt = tag.getBoolean("GraveyardBuilt");
		data.graveyardDimension = tag.getString("GraveyardDim");
		data.graveyardX = tag.getInt("GraveyardX");
		data.graveyardY = tag.getInt("GraveyardY");
		data.graveyardZ = tag.getInt("GraveyardZ");
		ListTag memories = tag.getList("Memories", Tag.TAG_COMPOUND);
		for (int i = 0; i < memories.size(); i++) {
			data.memories.add(EchoMemory.load(memories.getCompound(i), provider));
		}
		return data;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
		tag.putInt("DeathCount", deathCount);
		tag.putBoolean("GraveyardBuilt", graveyardBuilt);
		tag.putString("GraveyardDim", graveyardDimension);
		tag.putInt("GraveyardX", graveyardX);
		tag.putInt("GraveyardY", graveyardY);
		tag.putInt("GraveyardZ", graveyardZ);
		ListTag memories = new ListTag();
		for (EchoMemory memory : this.memories) {
			memories.add(memories.size(), memory.save(provider));
		}
		tag.put("Memories", memories);
		return tag;
	}
}
