package com.deathecho;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

import java.util.List;

public final class GhostTalk {
	private static long nextNoticeAt;
	private static long nextCemeteryAt;

	private GhostTalk() {
	}

	public static void clear() {
		nextNoticeAt = 0L;
		nextCemeteryAt = 0L;
	}

	public static void tryNotice(DeathGhost ghost, ServerLevel level) {
		if (ghost.isCemetery() || level.getGameTime() < nextNoticeAt) {
			return;
		}
		List<DeathGhost> nearby = level.getEntitiesOfClass(
				DeathGhost.class,
				ghost.getBoundingBox().inflate(12.0),
				other -> other != ghost && other.isAlive() && !other.isCemetery()
		);
		if (nearby.isEmpty()) {
			return;
		}
		DeathGhost other = nearby.get(ghost.getRandom().nextInt(nearby.size()));
		nextNoticeAt = level.getGameTime() + 60L;
		ghost.glanceAt(other);
		other.glanceAt(ghost);
		say(level, ghost, noticeLine(ghost, other), 32.0);
	}

	public static void tryCemetery(DeathGhost ghost, ServerLevel level) {
		if (!ghost.isCemetery() || ghost.isAwake() || level.getGameTime() < nextCemeteryAt) {
			return;
		}
		if (ghost.getRandom().nextFloat() > 0.35F) {
			return;
		}
		List<DeathGhost> others = level.getEntitiesOfClass(
				DeathGhost.class,
				ghost.getBoundingBox().inflate(10.0),
				other -> other != ghost && other.isAlive() && other.isCemetery()
		);
		nextCemeteryAt = level.getGameTime() + 80L;
		DeathGhost other = others.isEmpty() ? null : others.get(ghost.getRandom().nextInt(others.size()));
		if (other != null) {
			ghost.glanceAt(other);
		}
		say(level, ghost, cemeteryLine(ghost, other), 48.0);
	}

	private static String noticeLine(DeathGhost ghost, DeathGhost other) {
		String self = label(ghost);
		int otherNumber = other.deathNumber();
		String[] lines = {
				self + ": О, смерть #" + otherNumber + ". Нас становится много.",
				self + ": Не подходи, #" + otherNumber + ". Я занят вечным падением.",
				self + ": #" + otherNumber + ", ты тоже он? Соболезную нам.",
				self + ": Смотри, #" + otherNumber + " повторяет тот же забег."
		};
		return lines[ghost.getRandom().nextInt(lines.length)];
	}

	private static String cemeteryLine(DeathGhost ghost, DeathGhost other) {
		RandomSource random = ghost.getRandom();
		String name = ghost.ownerName().isEmpty() ? "ты" : ghost.ownerName();
		String self = label(ghost);
		if (!ghost.deathMessage().isEmpty() && random.nextFloat() < 0.25F) {
			return self + ": До сих пор звучит так — " + clip(ghost.deathMessage(), 80);
		}
		if (other != null && random.nextBoolean()) {
			int otherNumber = other.deathNumber();
			if (ghost.proud()) {
				return self + ": Не слушай #" + otherNumber + ". Этот хотя бы умер красиво.";
			}
			return self + ": #" + otherNumber + " прав. " + name + ", это было стыдно.";
		}
		if (ghost.proud()) {
			String[] lines = {
					self + ": Я до сих пор горжусь этим забегом.",
					self + ": Почти получилось. В следующий раз долетишь.",
					self + ": " + name + ", я бы сделал так же. Мы и есть ты.",
					self + ": Смотри, как красиво я падаю. Это ты."
			};
			return lines[random.nextInt(lines.length)];
		}
		String[] lines = {
				self + ": " + name + ", ты идиот. И я это доказал лично.",
				self + ": Я видел яму. Ты тоже её видел.",
				self + ": Опять? Серьёзно, " + name + "?",
				self + ": Кто ставит блок и удивляется, что умер? Мы."
		};
		return lines[random.nextInt(lines.length)];
	}

	public static void announceWake(DeathGhost ghost, ServerLevel level) {
		String name = ghost.ownerName().isEmpty() ? "ты" : ghost.ownerName();
		String self = label(ghost);
		String[] lines = {
				self + ": " + name + " встал в центр. Хватит разговоров.",
				self + ": Ты сам пришёл. Теперь бей.",
				self + ": Могилы помнят. И мы тоже."
		};
		say(level, ghost, lines[ghost.getRandom().nextInt(lines.length)], 32.0);
	}

	private static String label(DeathGhost ghost) {
		int number = ghost.deathNumber();
		return number > 0 ? "Смерть #" + number : "Призрак";
	}

	private static void say(ServerLevel level, Entity speaker, String line, double radius) {
		Component text = Component.literal(line).withStyle(ChatFormatting.GRAY);
		double radiusSqr = radius * radius;
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(speaker) <= radiusSqr) {
				player.sendSystemMessage(text);
			}
		}
	}

	private static String clip(String text, int max) {
		if (text.length() <= max) {
			return text;
		}
		return text.substring(0, max - 1) + "…";
	}
}
