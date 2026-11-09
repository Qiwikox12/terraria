package dev.eventhorizon.client;

import dev.eventhorizon.core.Ability;
import dev.eventhorizon.core.ReactorState;
import dev.eventhorizon.item.ModItems;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.Locale;

public final class ReactorHud {
    public static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden || client.currentScreen != null
            || !ModItems.holdingGauntlet(client.player)) return;
        var state = EventHorizonClient.REACTOR;
        var font = client.textRenderer;
        int x = 10, y = 10, width = 196;
        int accent = 0xFF000000 | state.mode.rgb();
        context.fill(x + 2, y + 2, x + width + 2, y + 91, 0x40000000);
        context.fill(x, y, x + width, y + 89, 0xD8101723);
        context.fill(x, y, x + 2, y + 89, accent);
        context.fill(x + 2, y, x + width, y + 1, 0xFF344153);
        context.drawText(font, Text.literal("E V E N T  H O R I Z O N"), x + 10, y + 7, 0xFF93A4B8, false);
        context.drawItem(new ItemStack(ModItems.GRAVITY_GAUNTLET), x + 9, y + 22);
        context.drawTextWithShadow(font, Text.translatable(state.mode.translationKey()), x + 32, y + 23, accent);
        Text status = state.cooldown > 0
            ? Text.translatable("hud.eventhorizon.cooldown", String.format(Locale.ROOT, "%.1f", state.cooldown / 20.0))
            : Text.translatable("hud.eventhorizon.ready");
        if (state.cooldown == 0 && (state.energy < state.mode.cost() || state.mode == Ability.SINGULARITY && state.resonance < 3)) {
            status = Text.literal("—");
        }
        context.drawText(font, status, x + width - 8 - font.getWidth(status), y + 34,
            state.cooldown > 0 ? 0xFFADB9C8 : accent, false);
        context.drawText(font, Text.translatable("hud.eventhorizon.energy"), x + 10, y + 44, 0xFFC2CDDA, false);
        String energy = state.energy + " / " + ReactorState.MAX_ENERGY;
        context.drawText(font, energy, x + width - 8 - font.getWidth(energy), y + 44, 0xFFE7F1F7, false);
        int barWidth = width - 18;
        context.fill(x + 10, y + 56, x + width - 8, y + 60, 0xFF2A3546);
        context.fill(x + 10, y + 56, x + 10 + Math.round(barWidth * state.smoothEnergy / 100f), y + 60,
            state.energy < state.mode.cost() ? 0xFFFF8F7B : accent);
        for (int i = 1; i < 10; i++) context.fill(x + 10 + barWidth * i / 10, y + 56, x + 11 + barWidth * i / 10, y + 60, 0xA0101723);
        for (int i = 0; i < 3; i++) diamond(context, x + 14 + i * 13, y + 70,
            i < state.resonance ? 0xFFF279CF : 0xFF364157);
        if (state.resonance > 0) context.fill(x + 10, y + 77, x + 10 + 35 * state.comboTicks / ReactorState.COMBO_WINDOW, y + 78, 0xFFF279CF);
        Text controls = Text.literal("[").append(EventHorizonClient.cycleKey.getBoundKeyLocalizedText()).append("]  ")
            .append(Text.translatable("hud.eventhorizon.mode")).append("   [")
            .append(EventHorizonClient.guideKey.getBoundKeyLocalizedText()).append("]  ")
            .append(Text.translatable("hud.eventhorizon.guide"));
        context.drawText(font, controls, x + 53, y + 67, 0xFF93A4B8, false);
    }

    private static void diamond(DrawContext context, int x, int y, int color) {
        for (int row = -3; row <= 3; row++) {
            int half = 3 - Math.abs(row);
            context.fill(x - half, y + row, x + half + 1, y + row + 1, color);
        }
    }
}
