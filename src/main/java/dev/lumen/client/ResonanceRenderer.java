package dev.lumen.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

final class ResonanceRenderer {
    static void init() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(ResonanceRenderer::render);
    }

    private static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || context.matrixStack() == null || context.consumers() == null
                || (HighlightStore.blocks().isEmpty() && HighlightStore.entities().isEmpty())) {
            return;
        }
        long now = client.world.getTime();
        MatrixStack matrices = context.matrixStack();
        Camera camera = context.camera();
        VertexConsumerProvider consumers = context.consumers();
        matrices.push();
        matrices.translate(-camera.getPos().x, -camera.getPos().y, -camera.getPos().z);

        VertexConsumer fill = consumers.getBuffer(LumenRenderLayers.SIGNAL_FILL);
        drawSignals(context, matrices, fill, client, now, true);
        if (consumers instanceof VertexConsumerProvider.Immediate immediate) {
            immediate.draw(LumenRenderLayers.SIGNAL_FILL);
        }
        VertexConsumer lines = consumers.getBuffer(LumenRenderLayers.SIGNAL_LINES);
        drawSignals(context, matrices, lines, client, now, false);
        if (consumers instanceof VertexConsumerProvider.Immediate immediate) {
            immediate.draw(LumenRenderLayers.SIGNAL_LINES);
        }
        matrices.pop();
    }

    private static void drawSignals(WorldRenderContext context, MatrixStack matrices, VertexConsumer consumer,
                                    MinecraftClient client, long now, boolean fill) {
        for (HighlightStore.BlockVisual visual : HighlightStore.blocks()) {
            float alpha = HighlightStore.alpha(now, visual.created(), visual.duration());
            if (alpha <= 0) {
                continue;
            }
            BlockPos pos = visual.signal().pos();
            Box box = new Box(pos).expand(0.025);
            if (context.frustum() != null && !context.frustum().isVisible(box)) {
                continue;
            }
            drawBox(matrices, consumer, box, visual.signal().color(), alpha, fill);
        }
        for (HighlightStore.EntityVisual visual : HighlightStore.entities()) {
            Entity entity = client.world.getEntityById(visual.signal().entityId());
            if (entity == null) {
                continue;
            }
            float alpha = HighlightStore.alpha(now, visual.created(), visual.duration());
            Box box = entity.getBoundingBox().expand(0.08);
            if (context.frustum() != null && !context.frustum().isVisible(box)) {
                continue;
            }
            drawBox(matrices, consumer, box, visual.signal().color(), alpha, fill);
        }
    }

    private static void drawBox(MatrixStack matrices, VertexConsumer consumer, Box box, int color,
                                float fade, boolean fill) {
        float red = (color >> 16 & 255) / 255.0f;
        float green = (color >> 8 & 255) / 255.0f;
        float blue = (color & 255) / 255.0f;
        if (fill) {
            WorldRenderer.renderFilledBox(matrices, consumer, box.minX, box.minY, box.minZ,
                    box.maxX, box.maxY, box.maxZ, red, green, blue, fade * 0.065f);
        } else {
            WorldRenderer.drawBox(matrices, consumer, box, red, green, blue, fade * 0.88f);
        }
    }
}
