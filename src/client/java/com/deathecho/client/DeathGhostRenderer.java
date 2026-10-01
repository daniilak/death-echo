package com.deathecho.client;

import com.deathecho.DeathGhost;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

public class DeathGhostRenderer extends LivingEntityRenderer<DeathGhost, PlayerModel<DeathGhost>> {
	/** Около 45% непрозрачности: скин читается, но это уже не живой игрок. */
	private static final int BODY_COLOR = 0x73FFFFFF;

	private final PlayerModel<DeathGhost> wide;
	private final PlayerModel<DeathGhost> slim;

	public DeathGhostRenderer(EntityRendererProvider.Context context) {
		super(context, new GhostPlayerModel(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
		this.wide = this.model;
		this.slim = new GhostPlayerModel(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
		this.addLayer(new HumanoidArmorLayer<>(
				this,
				new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
				new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
				context.getModelManager()
		));
		this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
	}

	@Override
	public void render(DeathGhost entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		this.model = skin(entity).model() == PlayerSkin.Model.SLIM ? this.slim : this.wide;
		super.render(entity, yaw, partialTick, poseStack, buffer, packedLight);
	}

	@Override
	public ResourceLocation getTextureLocation(DeathGhost entity) {
		return skin(entity).texture();
	}

	@Override
	protected RenderType getRenderType(DeathGhost entity, boolean bodyVisible, boolean translucent, boolean glowing) {
		ResourceLocation texture = this.getTextureLocation(entity);
		if (glowing && !bodyVisible && !translucent) {
			return RenderType.outline(texture);
		}
		if (!bodyVisible && !translucent) {
			return null;
		}
		return RenderType.entityTranslucent(texture);
	}

	@Override
	protected void scale(DeathGhost entity, PoseStack poseStack, float partialTick) {
		poseStack.translate(0.0, Math.sin((entity.tickCount + partialTick) * 0.08) * 0.04, 0.0);
	}

	private static PlayerSkin skin(DeathGhost entity) {
		UUID uuid = entity.ownerId();
		Minecraft minecraft = Minecraft.getInstance();
		if (uuid != null && minecraft.level != null) {
			Player player = minecraft.level.getPlayerByUUID(uuid);
			if (player instanceof AbstractClientPlayer clientPlayer) {
				return clientPlayer.getSkin();
			}
			return DefaultPlayerSkin.get(uuid);
		}
		return DefaultPlayerSkin.get(new UUID(0L, 0L));
	}

	private static final class GhostPlayerModel extends PlayerModel<DeathGhost> {
		private GhostPlayerModel(ModelPart root, boolean slim) {
			super(root, slim);
		}

		@Override
		public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
			int alpha = color >>> 24;
			super.renderToBuffer(poseStack, buffer, packedLight, packedOverlay, alpha >= 250 ? BODY_COLOR : color);
		}
	}
}
