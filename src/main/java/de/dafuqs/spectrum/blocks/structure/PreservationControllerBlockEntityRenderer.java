package de.dafuqs.spectrum.blocks.structure;

import de.dafuqs.spectrum.*;
import net.minecraft.client.*;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.entity.*;
import net.minecraft.client.render.entity.*;
import net.minecraft.client.util.math.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import org.joml.*;

import java.lang.*;
import java.lang.Math;

public class PreservationControllerBlockEntityRenderer implements BlockEntityRenderer<PreservationControllerBlockEntity> {

	private static final Identifier AETHER_CORE = SpectrumCommon.locate("textures/block/preservation_controller_aether.png");
	protected static EntityRenderDispatcher dispatcher;

	public PreservationControllerBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {
		dispatcher = MinecraftClient.getInstance().getEntityRenderDispatcher();
	}

	@Override
	public void render(PreservationControllerBlockEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
		matrices.push();
		matrices.translate(0.5, 0.5, 0.5);
		matrices.multiply(dispatcher.camera.getRotation());
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F));
		var buffer = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(AETHER_CORE));

		float time = entity.getWorld().getTime() % 24000 + tickDelta;

		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(time / 1.5F));
		float pulse = (float) (Math.sin((time / 19)));
		float scale = pulse * 0.2F + 0.8F;
		matrices.scale(scale, scale, scale);
		renderRing(matrices, buffer, 15F, pulse * 0.25F + 0.75F, overlay, Direction.UP);
		matrices.pop();
	}

	//These should probably get merged into one thing at some point
	private void renderRing(MatrixStack matrices, VertexConsumer vertices, float scale, float alpha, int overlay, Direction facing) {
		var size = scale / 16F;
		matrices.translate(-size / 2F, -size / 2F, 0);

		var peek = matrices.peek();
		var model = peek.getPositionMatrix();
		var normals = peek.getNormalMatrix();
		var transform = normals.transform(new Vector3f(facing.getUnitVector()));
		renderSide(model, normals, vertices, alpha, scale, scale, 0, size, 0, size, transform.x, transform.y, transform.z, overlay);
	}

	private void renderSide(Matrix4f model, Matrix3f normals, VertexConsumer vertices, float alpha, float u, float v, float x1, float x2, float z1, float z2, float n1, float n2, float n3, int overlay) {
		float u1 = 0, v1 = 0;
		float u2 = u1 + u / 16F, v2 = v1 + v / 16F;

		vertices.vertex(model, x1, z2, 0).color(1F, 1F, 1F, alpha).texture(u1, v1).overlay(overlay).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0, 1, 0).next();
		vertices.vertex(model, x2, z2, 0).color(1F, 1F, 1F, alpha).texture(u2, v1).overlay(overlay).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0, 1, 0).next();
		vertices.vertex(model, x2, z1, 0).color(1F, 1F, 1F, alpha).texture(u2, v2).overlay(overlay).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0, 1, 0).next();
		vertices.vertex(model, x1, z1, 0).color(1F, 1F, 1F, alpha).texture(u1, v2).overlay(overlay).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0, 1, 0).next();
	}
}
