package com.deathecho;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
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
	private boolean provoked;
	private boolean awakened;
	private int strayTicks;
	private long bornAt;
	private long expireAt;
	private boolean focusSet;
	private double focusX;
	private double focusY;
	private double focusZ;

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
		this.xpReward = cemetery ? 0 : 12;
		ItemStack hand = cemetery ? recording.mainHand() : ItemStack.EMPTY;
		equip(recording.armor(), recording.offhand(), hand);
		this.moveTo(x, y, z, yaw, 0.0F);
		this.setYHeadRot(yaw);
		this.setYBodyRot(yaw);
	}

	public void setGraveyardCenter(double x, double y, double z) {
		this.focusX = x;
		this.focusY = y;
		this.focusZ = z;
		this.focusSet = true;
	}

	public void noteSpawn(ServerLevel level) {
		this.bornAt = level.getGameTime();
		if (!this.cemetery && !this.holdsLoot()) {
			this.expireAt = this.bornAt + DeathEchoMod.EMPTY_REPLAY_TICKS;
			trimEmpty(level, this.ownerId());
		}
	}

	public boolean holdsLoot() {
		return this.recording != null && this.recording.hasLoot();
	}

	public boolean isAwake() {
		return this.awakened;
	}

	public long bornAt() {
		return this.bornAt;
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
		if (fadeIfEmpty(level)) {
			return;
		}
		if (this.cemetery) {
			if (this.awakened) {
				tickCemeteryFight(level);
			} else {
				tickCemetery(level);
			}
		} else if (this.provoked) {
			tickFight(level);
		} else {
			tickReplay(level);
		}
	}

	private boolean fadeIfEmpty(ServerLevel level) {
		if (this.cemetery || this.holdsLoot() || this.provoked) {
			return false;
		}
		if (this.expireAt == 0L) {
			this.expireAt = level.getGameTime() + DeathEchoMod.EMPTY_REPLAY_TICKS;
		}
		if ((this.tickCount + this.getId()) % 40 == 0) {
			trimEmpty(level, this.ownerId());
		}
		if (level.getGameTime() < this.expireAt) {
			return false;
		}
		level.sendParticles(ParticleTypes.SOUL, this.getX(), this.getY() + 1.0, this.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
		this.discard();
		return true;
	}

	private static void trimEmpty(ServerLevel level, UUID owner) {
		if (owner == null) {
			return;
		}
		List<DeathGhost> empty = new ArrayList<>();
		level.getEntities(
				ModEntities.DEATH_GHOST,
				ghost -> ghost.isAlive()
						&& !ghost.isCemetery()
						&& !ghost.holdsLoot()
						&& !ghost.provoked
						&& owner.equals(ghost.ownerId()),
				empty
		);
		if (empty.size() <= DeathEchoMod.MAX_EMPTY_REPLAYS) {
			return;
		}
		empty.sort(Comparator.comparingLong(DeathGhost::bornAt));
		long soon = level.getGameTime() + DeathEchoMod.EMPTY_OVERFLOW_TICKS;
		int overflow = empty.size() - DeathEchoMod.MAX_EMPTY_REPLAYS;
		for (int i = 0; i < overflow; i++) {
			DeathGhost old = empty.get(i);
			if (old.expireAt == 0L || old.expireAt > soon) {
				old.expireAt = soon;
			}
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

	private void tickFight(ServerLevel level) {
		Player player = livingOwner(level);
		if (player == null || player.distanceToSqr(this) > DeathEchoMod.FIGHT_LEASH * DeathEchoMod.FIGHT_LEASH) {
			this.provoked = false;
			this.setDeltaMovement(Vec3.ZERO);
			return;
		}
		chase(player);
		tryAttack(level);
	}

	private void tickCemetery(ServerLevel level) {
		if (tryWake(level)) {
			tickCemeteryFight(level);
			return;
		}
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

	private boolean tryWake(ServerLevel level) {
		Player player = livingOwner(level);
		if (player == null || !onCenterPad(player)) {
			return false;
		}
		AABB box = new AABB(this.focusX, this.focusY, this.focusZ, this.focusX, this.focusY, this.focusZ).inflate(12.0);
		List<DeathGhost> circle = level.getEntitiesOfClass(
				DeathGhost.class,
				box,
				other -> other.isAlive() && other.isCemetery() && player.getUUID().equals(other.ownerId())
		);
		boolean woke = false;
		for (DeathGhost ghost : circle) {
			if (!ghost.awakened) {
				ghost.awakened = true;
				ghost.strayTicks = 0;
				woke = true;
			}
		}
		if (woke) {
			level.playSound(
					null,
					BlockPos.containing(this.focusX, this.focusY, this.focusZ),
					SoundEvents.SOUL_ESCAPE.value(),
					SoundSource.HOSTILE,
					1.0F,
					0.5F
			);
			GhostTalk.announceWake(this, level);
		}
		return this.awakened;
	}

	private void tickCemeteryFight(ServerLevel level) {
		Player player = livingOwner(level);
		if (player == null || player.distanceToSqr(this) > DeathEchoMod.GRAVEYARD_LEASH * DeathEchoMod.GRAVEYARD_LEASH) {
			this.strayTicks++;
			if (player == null || this.strayTicks > 60) {
				sleepAgain();
			}
			return;
		}
		this.strayTicks = 0;
		chase(player);
		tryAttack(level);
	}

	private void sleepAgain() {
		this.awakened = false;
		this.strayTicks = 0;
		this.setDeltaMovement(Vec3.ZERO);
		this.setHealth(this.getMaxHealth());
		this.moveTo(this.homeX, this.homeY, this.homeZ, this.baseYaw, 0.0F);
		this.setYRot(this.baseYaw);
		this.setYHeadRot(this.baseYaw);
		this.setYBodyRot(this.baseYaw);
	}

	private void chase(Player player) {
		double dx = player.getX() - this.getX();
		double dy = player.getY() - this.getY();
		double dz = player.getZ() - this.getZ();
		double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (dist > 1.6) {
			double speed = 0.18 / dist;
			this.setDeltaMovement(dx * speed, dy * speed, dz * speed);
			this.move(MoverType.SELF, this.getDeltaMovement());
		}
		this.setDeltaMovement(Vec3.ZERO);
		this.lookAt(player, 30.0F, 30.0F);
		this.setYBodyRot(this.getYHeadRot());
	}

	private boolean onCenterPad(Player player) {
		ensureFocus();
		double dx = player.getX() - this.focusX;
		double dz = player.getZ() - this.focusZ;
		if (dx * dx + dz * dz > DeathEchoMod.GRAVEYARD_PAD * DeathEchoMod.GRAVEYARD_PAD) {
			return false;
		}
		if (Math.abs(player.getY() - this.focusY) > 1.5) {
			return false;
		}
		BlockPos ground = BlockPos.containing(player.getX(), player.getY() - 0.2, player.getZ());
		return this.level().getBlockState(ground).is(Blocks.SOUL_SAND);
	}

	private void ensureFocus() {
		if (this.focusSet) {
			return;
		}
		float yawRad = this.baseYaw * ((float) Math.PI / 180.0F);
		this.focusX = this.homeX - Mth.sin(yawRad) * 3.0;
		this.focusY = this.homeY;
		this.focusZ = this.homeZ + Mth.cos(yawRad) * 3.0;
		this.focusSet = true;
	}

	private Player livingOwner(ServerLevel level) {
		UUID owner = this.ownerId();
		if (owner == null) {
			return null;
		}
		Player player = level.getPlayerByUUID(owner);
		if (player == null || !player.isAlive() || player.isSpectator() || player.isCreative()) {
			return null;
		}
		return player;
	}

	private void tryAttack(ServerLevel level) {
		if (this.attackCooldown > 0) {
			this.attackCooldown--;
			return;
		}
		Player player = livingOwner(level);
		if (player == null) {
			return;
		}
		if (player.distanceToSqr(this) > DeathEchoMod.REPLAY_HIT_RANGE * DeathEchoMod.REPLAY_HIT_RANGE) {
			return;
		}
		this.attackCooldown = 16;
		this.swing(InteractionHand.MAIN_HAND);
		this.doHurtTarget(player);
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
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
		if (this.cemetery && !this.awakened) {
			return false;
		}
		boolean hit = super.hurt(source, amount);
		if (hit && !this.cemetery) {
			this.provoked = true;
		}
		return hit;
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
		tag.putBoolean("Provoked", this.provoked);
		tag.putBoolean("Awake", this.awakened);
		tag.putInt("Playback", this.playback);
		tag.putLong("BornAt", this.bornAt);
		tag.putLong("ExpireAt", this.expireAt);
		tag.putDouble("HomeX", this.homeX);
		tag.putDouble("HomeY", this.homeY);
		tag.putDouble("HomeZ", this.homeZ);
		tag.putFloat("BaseYaw", this.baseYaw);
		if (this.focusSet) {
			tag.putDouble("FocusX", this.focusX);
			tag.putDouble("FocusY", this.focusY);
			tag.putDouble("FocusZ", this.focusZ);
		}
		if (this.recording != null) {
			tag.put("Recording", this.recording.save(this.registryAccess()));
		}
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		this.cemetery = tag.getBoolean("Cemetery");
		this.provoked = tag.getBoolean("Provoked");
		this.awakened = tag.getBoolean("Awake");
		this.playback = tag.getInt("Playback");
		this.bornAt = tag.getLong("BornAt");
		this.expireAt = tag.getLong("ExpireAt");
		this.homeX = tag.getDouble("HomeX");
		this.homeY = tag.getDouble("HomeY");
		this.homeZ = tag.getDouble("HomeZ");
		this.baseYaw = tag.getFloat("BaseYaw");
		this.focusSet = tag.contains("FocusX");
		if (this.focusSet) {
			this.focusX = tag.getDouble("FocusX");
			this.focusY = tag.getDouble("FocusY");
			this.focusZ = tag.getDouble("FocusZ");
		}
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
