package com.deathecho;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.PathfinderMob;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class DeathGhost extends PathfinderMob {
	private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER =
			SynchedEntityData.defineId(DeathGhost.class, EntityDataSerializers.OPTIONAL_UUID);

	private DeathRecording recording;
	private boolean cemetery;
	private int playback;
	private double homeX;
	private double homeY;
	private double homeZ;
	private float baseYaw;
	private int glanceTicks;
	private float glanceYaw;
	private int attackCooldown = 60;
	private int lastSwingFrame = -1;

	public DeathGhost(EntityType<? extends DeathGhost> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
		this.setPersistenceRequired();
		this.xpReward = 12;
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (slot.getType() != EquipmentSlot.Type.ANIMAL_ARMOR) {
				this.setDropChance(slot, 0.0F);
			}
		}
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 30.0)
				.add(Attributes.ATTACK_DAMAGE, 3.0)
				.add(Attributes.MOVEMENT_SPEED, 0.25)
				.add(Attributes.FOLLOW_RANGE, 24.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.8);
	}

	public void applyRecording(DeathRecording recording, boolean cemetery, double x, double y, double z, float yaw) {
		this.recording = recording;
		this.cemetery = cemetery;
		this.homeX = x;
		this.homeY = y;
		this.homeZ = z;
		this.baseYaw = yaw;
		this.entityData.set(DATA_OWNER, Optional.of(recording.ownerId()));
		this.setCustomName(Component.literal(recording.ownerName() + " #" + recording.deathNumber()));
		this.setCustomNameVisible(true);
		this.setHealth(this.getMaxHealth());
		ItemStack hand = cemetery ? recording.mainHand() : ItemStack.EMPTY;
		equip(recording.armor(), recording.offhand(), hand);
		this.moveTo(x, y, z, yaw, 0.0F);
		this.setYHeadRot(yaw);
		this.setYBodyRot(yaw);
	}

	public void glanceAt(DeathGhost other) {
		double dx = other.getX() - this.getX();
		double dz = other.getZ() - this.getZ();
		this.glanceYaw = (float) (Mth.atan2(dz, dx) * (180.0F / Math.PI)) - 90.0F;
		this.glanceTicks = 40;
	}

	public boolean isCemetery() {
		return cemetery;
	}

	public int deathNumber() {
		return recording == null ? 0 : recording.deathNumber();
	}

	public String ownerName() {
		return recording == null ? "" : recording.ownerName();
	}

	public boolean proud() {
		return recording != null && recording.proud();
	}

	public String deathMessage() {
		return recording == null ? "" : recording.deathMessage();
	}

	public UUID ownerId() {
		return this.entityData.get(DATA_OWNER).orElse(null);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_OWNER, Optional.empty());
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide || !this.isAlive()) {
			return;
		}
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		if (this.recording == null) {
			this.discard();
			return;
		}
		this.fallDistance = 0.0F;
		if (this.cemetery) {
			tickCemetery(level);
		} else {
			tickReplay(level);
		}
	}

	private void tickReplay(ServerLevel level) {
		List<MotionFrame> frames = this.recording.frames();
		if (frames.size() < 2) {
			return;
		}
		int span = frames.size() * DeathEchoMod.SAMPLE_INTERVAL_TICKS;
		int time = Math.floorMod(this.playback, span);
		this.playback++;
		int index = time / DeathEchoMod.SAMPLE_INTERVAL_TICKS;
		float partial = (time % DeathEchoMod.SAMPLE_INTERVAL_TICKS) / (float) DeathEchoMod.SAMPLE_INTERVAL_TICKS;
		int nextIndex = index + 1;
		if (nextIndex >= frames.size()) {
			nextIndex = index;
			partial = 0.0F;
		}
		MotionFrame from = frames.get(index);
		MotionFrame to = frames.get(nextIndex);
		double x = from.x() + (to.x() - from.x()) * partial;
		double y = from.y() + (to.y() - from.y()) * partial;
		double z = from.z() + (to.z() - from.z()) * partial;
		float yaw = Mth.rotLerp(partial, from.yaw(), to.yaw());
		float pitch = Mth.lerp(partial, from.pitch(), to.pitch());
		this.setDeltaMovement(x - this.getX(), y - this.getY(), z - this.getZ());
		this.moveTo(x, y, z, yaw, pitch);
		this.setYBodyRot(yaw);
		if (this.glanceTicks > 0) {
			this.setYHeadRot(Mth.rotLerp(0.45F, this.getYHeadRot(), this.glanceYaw));
			this.glanceTicks--;
		} else {
			this.setYHeadRot(yaw);
		}
		this.setShiftKeyDown(from.sneaking());
		this.setSprinting(from.sprinting());
		this.setPose(from.sneaking() ? Pose.CROUCHING : Pose.STANDING);
		if (!ItemStack.matches(this.getMainHandItem(), from.hand())) {
			this.setItemSlot(EquipmentSlot.MAINHAND, from.hand().copy());
		}
		if (from.swinging() && index != this.lastSwingFrame) {
			this.swing(InteractionHand.MAIN_HAND);
			this.lastSwingFrame = index;
		}
		if ((this.tickCount + this.getId()) % 80 == 0) {
			GhostTalk.tryNotice(this, level);
		}
		if (this.tickCount % 12 == 0) {
			level.sendParticles(ParticleTypes.SOUL, this.getX(), this.getY() + 1.0, this.getZ(), 1, 0.15, 0.3, 0.15, 0.01);
		}
		tryAttack(level);
	}

	private void tickCemetery(ServerLevel level) {
		this.moveTo(this.homeX, this.homeY, this.homeZ, this.getYRot(), 0.0F);
		List<DeathGhost> others = level.getEntitiesOfClass(
				DeathGhost.class,
				this.getBoundingBox().inflate(8.0),
				other -> other != this && other.isAlive() && other.isCemetery()
		);
		float target = this.baseYaw;
		if (!others.isEmpty() && (this.tickCount / 60) % 2 == 0) {
			DeathGhost other = others.get((this.tickCount / 60) % others.size());
			double dx = other.getX() - this.getX();
			double dz = other.getZ() - this.getZ();
			target = (float) (Mth.atan2(dz, dx) * (180.0F / Math.PI)) - 90.0F;
		}
		float yaw = Mth.rotLerp(0.12F, this.getYRot(), target);
		this.setYRot(yaw);
		this.setYHeadRot(yaw);
		this.setYBodyRot(yaw);
		if (this.tickCount % 20 == 0) {
			GhostTalk.tryCemetery(this, level);
		}
		if (this.tickCount % 15 == 0) {
			level.sendParticles(ParticleTypes.SOUL, this.getX(), this.getY() + 1.1, this.getZ(), 1, 0.2, 0.35, 0.2, 0.005);
		}
	}

	private void tryAttack(ServerLevel level) {
		if (this.attackCooldown > 0) {
			this.attackCooldown--;
			return;
		}
		UUID owner = this.ownerId();
		if (owner == null) {
			return;
		}
		Player player = level.getPlayerByUUID(owner);
		if (player == null || !player.isAlive() || player.isSpectator() || player.isCreative()) {
			return;
		}
		if (player.distanceToSqr(this) > 2.8 * 2.8) {
			return;
		}
		this.attackCooldown = 16;
		this.swing(InteractionHand.MAIN_HAND);
		this.doHurtTarget(player);
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (this.cemetery) {
			return false;
		}
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return super.hurt(source, amount);
		}
		if (!(source.getEntity() instanceof Player player)) {
			return false;
		}
		UUID owner = this.ownerId();
		if (owner != null && !owner.equals(player.getUUID())) {
			return false;
		}
		return super.hurt(source, amount);
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
		if (this.recording == null || this.cemetery) {
			return;
		}
		List<ItemStack> stacks = new ArrayList<>(this.recording.loot());
		this.recording.loot().clear();
		for (ItemStack stack : stacks) {
			this.spawnAtLocation(stack);
		}
	}

	@Override
	public boolean removeWhenFarAway(double distanceSquared) {
		return false;
	}

	@Override
	public void checkDespawn() {
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putBoolean("Cemetery", this.cemetery);
		tag.putInt("Playback", this.playback);
		tag.putDouble("HomeX", this.homeX);
		tag.putDouble("HomeY", this.homeY);
		tag.putDouble("HomeZ", this.homeZ);
		tag.putFloat("BaseYaw", this.baseYaw);
		if (this.recording != null) {
			tag.put("Recording", this.recording.save(this.registryAccess()));
		}
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		this.cemetery = tag.getBoolean("Cemetery");
		this.playback = tag.getInt("Playback");
		this.homeX = tag.getDouble("HomeX");
		this.homeY = tag.getDouble("HomeY");
		this.homeZ = tag.getDouble("HomeZ");
		this.baseYaw = tag.getFloat("BaseYaw");
		if (tag.contains("Recording", Tag.TAG_COMPOUND)) {
			this.recording = DeathRecording.load(tag.getCompound("Recording"), this.registryAccess());
			this.entityData.set(DATA_OWNER, Optional.of(this.recording.ownerId()));
		}
	}

	private void equip(ItemStack[] armor, ItemStack offhand, ItemStack mainHand) {
		this.setItemSlot(EquipmentSlot.FEET, armor[0].copy());
		this.setItemSlot(EquipmentSlot.LEGS, armor[1].copy());
		this.setItemSlot(EquipmentSlot.CHEST, armor[2].copy());
		this.setItemSlot(EquipmentSlot.HEAD, armor[3].copy());
		this.setItemSlot(EquipmentSlot.OFFHAND, offhand.copy());
		if (!mainHand.isEmpty()) {
			this.setItemSlot(EquipmentSlot.MAINHAND, mainHand.copy());
		}
	}
}
