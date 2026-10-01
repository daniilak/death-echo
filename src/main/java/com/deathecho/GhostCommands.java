package com.deathecho;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class GhostCommands {
	private GhostCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(Commands.literal("ghosts")
						.executes(GhostCommands::info)
						.then(Commands.literal("graveyard")
								.requires(source -> source.hasPermission(2))
								.executes(GhostCommands::forceGraveyard)))
		);
	}

	private static int info(CommandContext<CommandSourceStack> context) {
		GhostWorldData data = GhostWorldData.get(context.getSource().getLevel());
		String text = "Смертей в мире: " + data.deathCount() + ".";
		if (data.graveyardBuilt()) {
			text += " Кладбище: " + data.graveyardX() + " " + data.graveyardY() + " " + data.graveyardZ()
					+ " (" + data.graveyardDimension() + ").";
		}
		String message = text;
		context.getSource().sendSuccess(() -> Component.literal(message), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int forceGraveyard(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		ServerLevel level = player.serverLevel();
		GhostWorldData data = GhostWorldData.get(level);
		if (data.graveyardBuilt()) {
			context.getSource().sendFailure(Component.literal(
					"Кладбище уже есть: " + data.graveyardX() + " " + data.graveyardY() + " " + data.graveyardZ()
			));
			return 0;
		}
		DeathEvents.raiseGraveyard(level, player.blockPosition(), data);
		return Command.SINGLE_SUCCESS;
	}
}
