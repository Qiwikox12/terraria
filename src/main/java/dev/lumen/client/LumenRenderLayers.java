package dev.lumen.client;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;

final class LumenRenderLayers extends RenderPhase {
    static final RenderLayer SIGNAL_FILL = RenderLayer.of("lumen_signal_fill", VertexFormats.POSITION_COLOR,
            VertexFormat.DrawMode.TRIANGLE_STRIP, 1536, false, true,
            RenderLayer.MultiPhaseParameters.builder().program(COLOR_PROGRAM)
                    .layering(VIEW_OFFSET_Z_LAYERING).transparency(TRANSLUCENT_TRANSPARENCY)
                    .depthTest(ALWAYS_DEPTH_TEST).writeMaskState(COLOR_MASK).build(false));

    static final RenderLayer SIGNAL_LINES = RenderLayer.of("lumen_signal_lines", VertexFormats.POSITION_COLOR,
            VertexFormat.DrawMode.LINES, 1536, false, true,
            RenderLayer.MultiPhaseParameters.builder().program(COLOR_PROGRAM).lineWidth(FULL_LINE_WIDTH)
                    .transparency(TRANSLUCENT_TRANSPARENCY).depthTest(ALWAYS_DEPTH_TEST)
                    .writeMaskState(COLOR_MASK).cull(DISABLE_CULLING).build(false));

    private LumenRenderLayers() {
        super("lumen", () -> {
        }, () -> {
        });
    }
}
