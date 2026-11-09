package dev.lumen.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
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
        if (client.world == null || context.matrixStack() == null
                || (HighlightStore.blocks().isEmpty() && HighlightStore.entities().isEmpty())) {
            return;
        }
        long now = client.world.getTime();
        MatrixStack matrices = context.matrixStack();
        Camera camera = context.camera();
        matrices.push();
        matrices.translate(-camera.getPos().x, -camera.getPos().y, -camera.getPos().z);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        BufferBuilder fill = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP,
                VertexFormats.POSITION_COLOR);
        drawSignals(context, matrices, fill, client, now, true);
        BufferRenderer.drawWithGlobalProgram(fill.end());

        RenderSystem.lineWidth(2.0f);
        BufferBuilder lines = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES,
                VertexFormats.POSITION_COLOR);
        drawSignals(context, matrices, lines, client, now, false);
        BufferRenderer.drawWithGlobalProgram(lines.end());

        RenderSystem.lineWidth(1.0f);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        matrices.pop();
    }

    private static void drawSignals(WorldRenderContext context, MatrixStack matrices, BufferBuilder consumer,
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

    private static void drawBox(MatrixStack matrices, BufferBuilder consumer, Box box, int color,
                                float fade, boolean fill) {
        float red = (color >> 16 & 255) / 255.0f;
        float green = (color >> 8 & 255) / 255.0f;
        float blue = (color & 255) / 255.0f;
        if (fill) {
            WorldRenderer.renderFilledBox(matrices, consumer, box.minX, box.minY, box.minZ,
                    box.maxX, box.maxY, box.maxZ, red, green, blue, fade * 0.09f);
        } else {
            WorldRenderer.drawBox(matrices, consumer, box, red, green, blue, fade * 0.88f);
        }
    }
}
