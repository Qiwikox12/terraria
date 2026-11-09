package dev.lumen.client;

import dev.lumen.item.ResonanceMode;
import dev.lumen.item.ResonatorItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

final class ResonanceHud {
    private static final int BACKGROUND = 0xC90A1118;
    private static final int TEXT = 0xE8F4F2;
    private static final int MUTED = 0x829A9E;

    static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden || client.currentScreen != null) {
            return;
        }
        ItemStack stack = ResonatorItem.getHeldStack(client.player);
        if (stack.isEmpty()) {
            return;
        }
        ResonanceMode mode = ResonatorItem.getMode(stack);
        int accent = 0xFF000000 | mode.color();
        int width = 174;
        int height = 70;
        int x = context.getScaledWindowWidth() - width - 12;
        int y = context.getScaledWindowHeight() - height - 12;

        context.fill(x + 2, y, x + width - 2, y + height, BACKGROUND);
        context.fill(x, y + 2, x + width, y + height - 2, BACKGROUND);
        context.fill(x + 2, y, x + width - 2, y + 2, accent);
        context.fill(x, y + 8, x + 2, y + height - 8, withAlpha(mode.color(), 0.45f));
        context.drawItem(stack, x + 9, y + 9);
        context.drawTextWithShadow(client.textRenderer, Text.translatable("hud.lumen.title"),
                x + 32, y + 8, TEXT);
        context.drawTextWithShadow(client.textRenderer, mode.displayName(), x + 32, y + 20, accent);

        int charge = ResonatorItem.getCharge(stack);
        int capacity = ResonatorItem.getCapacity(stack);
        String chargeText = charge + " / " + capacity;
        int chargeWidth = client.textRenderer.getWidth(chargeText);
        context.drawTextWithShadow(client.textRenderer, chargeText,
                x + width - chargeWidth - 10, y + 20, MUTED);
        context.fill(x + 10, y + 34, x + width - 10, y + 38, 0xFF233238);
        int filled = Math.round((width - 20) * (float) charge / capacity);
        context.fill(x + 10, y + 34, x + 10 + filled, y + 38, accent);

        long now = client.world.getTime();
        int signals = HighlightStore.lastSignals(now);
        String signalLine = Text.translatable("hud.lumen.signals", signals).getString()
                + (HighlightStore.lastDeep() ? "  ·  " + Text.translatable("hud.lumen.deep").getString() : "");
        context.drawTextWithShadow(client.textRenderer, signalLine, x + 10, y + 44, signals > 0 ? TEXT : MUTED);
        HighlightStore.Nearest nearest = HighlightStore.nearest(client);
        if (nearest != null) {
            String distance = String.format(java.util.Locale.ROOT, "%.1fm", nearest.distance());
            context.drawTextWithShadow(client.textRenderer, distance,
                    x + width - client.textRenderer.getWidth(distance) - 10, y + 44,
                    0xFF000000 | nearest.color());
        }
        context.drawTextWithShadow(client.textRenderer, Text.translatable("hud.lumen.controls"),
                x + 10, y + 57, MUTED);
    }

    private static int withAlpha(int color, float alpha) {
        return Math.round(alpha * 255) << 24 | color;
    }
}
