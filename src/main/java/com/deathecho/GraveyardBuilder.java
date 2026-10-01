package com.deathecho;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;

public final class GraveyardBuilder {
	private static final int RADIUS = 7;
	private static final String[][] EPITAPHS = {
			{"почти", "долетел"},
			{"два", "блока"},
			{"опять", "ты"},
			{"яма", "была тут"},
			{"меч", "не спас"},
			{"смотрел", "вниз"},
			{"бежал", "дальше"},
			{"это", "был ты"}
	};

	private GraveyardBuilder() {
	}

	public static BlockPos build(ServerLevel level, BlockPos deathPos, List<EchoMemory> memories) {
		BlockPos feet = findSpot(level, deathPos);
		carve(level, feet);
		placeMarkers(level, feet, memories);
		spawnMemories(level, feet, memories);
		return feet;
	}

	private static BlockPos findSpot(ServerLevel level, BlockPos death) {
		if (level.dimension() == Level.NETHER) {
			int y = Mth.clamp(death.getY(), level.getMinBuildHeight() + 2, level.getMaxBuildHeight() - 8);
			return new BlockPos(death.getX() + 16, y, death.getZ());
		}
		for (int radius = 16; radius <= 64; radius += 4) {
			for (int dx = -radius; dx <= radius; dx += 4) {
				for (int dz = -radius; dz <= radius; dz += 4) {
					if (radius != 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) {
						continue;
					}
					BlockPos column = new BlockPos(death.getX() + dx, death.getY(), death.getZ() + dz);
					BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
					if (surface.getY() <= level.getMinBuildHeight() + 2 || surface.getY() >= level.getMaxBuildHeight() - 6) {
						continue;
					}
					BlockPos groundPos = surface.below();
					BlockState ground = level.getBlockState(groundPos);
					if (!ground.getFluidState().isEmpty() || !ground.isFaceSturdy(level, groundPos, Direction.UP)) {
						continue;
					}
					return surface;
				}
			}
		}
		BlockPos fallback = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, death);
		int y = Mth.clamp(fallback.getY(), level.getMinBuildHeight() + 2, level.getMaxBuildHeight() - 8);
		return new BlockPos(fallback.getX(), y, fallback.getZ());
	}

	private static void carve(ServerLevel level, BlockPos feet) {
		for (int dx = -RADIUS; dx <= RADIUS; dx++) {
			for (int dz = -RADIUS; dz <= RADIUS; dz++) {
				BlockPos ground = feet.offset(dx, -1, dz);
				for (int dy = 0; dy < 5; dy++) {
					replace(level, ground.above(dy + 1), Blocks.AIR.defaultBlockState());
				}
				boolean edge = Math.abs(dx) == RADIUS || Math.abs(dz) == RADIUS;
				boolean center = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
				BlockState floor;
				if (center) {
					floor = Blocks.SOUL_SAND.defaultBlockState();
				} else if (edge) {
					floor = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
				} else if (Math.floorMod(dx + dz, 3) == 0) {
					floor = Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState();
				} else {
					floor = Blocks.DEEPSLATE_TILES.defaultBlockState();
				}
				replace(level, ground, floor);
			}
		}
	}

	private static void placeMarkers(ServerLevel level, BlockPos feet, List<EchoMemory> memories) {
		for (int i = 0; i < 8; i++) {
			double angle = i * Math.PI / 4.0;
			BlockPos stone = onCircle(feet, angle, 5);
			replace(level, stone, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
			replace(level, stone.above(), Blocks.SOUL_LANTERN.defaultBlockState());

			int number = i < memories.size() ? memories.get(i).deathNumber() : i + 1;
			String[] epitaph = EPITAPHS[Math.floorMod(number, EPITAPHS.length)];
			BlockPos signPos = onCircle(feet, angle, 4);
			int rotation = Math.floorMod(Mth.floor((Math.toDegrees(angle) + 180.0) / 22.5), 16);
			BlockState sign = Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, rotation);
			replace(level, signPos, sign);
			if (level.getBlockEntity(signPos) instanceof SignBlockEntity blockEntity) {
				blockEntity.updateText(text -> text
						.setMessage(0, Component.literal("Смерть"))
						.setMessage(1, Component.literal("#" + number))
						.setMessage(2, Component.literal(epitaph[0]))
						.setMessage(3, Component.literal(epitaph[1])), true);
				blockEntity.setChanged();
			}
		}
	}

	private static void spawnMemories(ServerLevel level, BlockPos feet, List<EchoMemory> memories) {
		int count = Math.min(8, memories.size());
		for (int i = 0; i < count; i++) {
			double angle = i * Math.PI / 4.0;
			BlockPos spot = onCircle(feet, angle, 3);
			double x = spot.getX() + 0.5;
			double z = spot.getZ() + 0.5;
			double dx = feet.getX() + 0.5 - x;
			double dz = feet.getZ() + 0.5 - z;
			float yaw = (float) (Mth.atan2(dz, dx) * (180.0F / Math.PI)) - 90.0F;
			DeathGhost ghost = ModEntities.DEATH_GHOST.create(level);
			if (ghost == null) {
				continue;
			}
			ghost.applyRecording(DeathRecording.fromMemory(memories.get(i)), true, x, feet.getY(), z, yaw);
			level.addFreshEntity(ghost);
		}
	}

	private static BlockPos onCircle(BlockPos feet, double angle, int radius) {
		int x = feet.getX() + (int) Math.round(Math.cos(angle) * radius);
		int z = feet.getZ() + (int) Math.round(Math.sin(angle) * radius);
		return new BlockPos(x, feet.getY(), z);
	}

	private static void replace(ServerLevel level, BlockPos pos, BlockState state) {
		BlockState current = level.getBlockState(pos);
		if (current.is(Blocks.BEDROCK)
				|| current.is(Blocks.END_PORTAL)
				|| current.is(Blocks.END_PORTAL_FRAME)
				|| current.is(Blocks.NETHER_PORTAL)
				|| current.hasBlockEntity()) {
			return;
		}
		level.setBlockAndUpdate(pos, state);
	}
}
