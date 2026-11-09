package dev.resonance.client;

import dev.resonance.Resonance;
import dev.resonance.ability.Mode;
import dev.resonance.item.GauntletItem;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

final class ResonanceHud {
    private static final int PANEL_WIDTH = 178;
    private static final int PANEL_HEIGHT = 68;
    private static final int PANEL = 0xE5091116;
    private static final int EDGE = 0xFF26363D;
    private static final int MUTED = 0xFF91A2A8;
    private static final int WHITE = 0xFFE5F0F2;

    private ResonanceHud() {
    }

    static void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.options.hudHidden
                || client.getDebugHud().shouldShowDebugHud() || client.player.isSpectator()) {
            return;
        }

        ItemStack held = client.player.getMainHandStack();
        if (!held.isOf(Resonance.GAUNTLET)) {
            return;
        }

        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();
        if (screenWidth < PANEL_WIDTH + 8 || screenHeight < PANEL_HEIGHT + 8) {
            return;
        }

        int x = Math.min(8, screenWidth - PANEL_WIDTH);
        int y = Math.min(8, screenHeight - PANEL_HEIGHT);
        ResonanceClient.ServerState state = ResonanceClient.latestState();
        Mode mode = state == null ? GauntletItem.getMode(held) : Mode.values()[state.mode()];
        int energy = state == null ? GauntletItem.getEnergy(held) : state.energy();
        int cooldownTicks = state == null ? 0 : state.cooldownTicks();
        int wellTicks = state == null ? 0 : state.wellTicks();
        int accent = opaque(mode.color());

        context.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, PANEL);
        context.fill(x, y, x + PANEL_WIDTH, y + 1, EDGE);
        context.fill(x, y + PANEL_HEIGHT - 1, x + PANEL_WIDTH, y + PANEL_HEIGHT, EDGE);
        context.fill(x, y, x + 2, y + PANEL_HEIGHT, accent);
        context.fill(x + PANEL_WIDTH - 1, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, EDGE);

        drawGlyph(context, mode, x + 8, y + 6, 12, accent);
        drawText(context, client, Text.translatable("hud.resonance.brand"), x + 27, y + 7, MUTED);
        drawText(context, client, Text.translatable(mode.translationKey()), x + 8, y + 20, accent);
        drawRight(context, client,
                Text.translatable("hud.resonance.cost", mode.cost()), x + PANEL_WIDTH - 8, y + 20, WHITE);

        drawText(context, client, Text.translatable("hud.resonance.energy_label"), x + 8, y + 33, MUTED);
        drawRight(context, client,
                Text.translatable("hud.resonance.energy_value", energy, GauntletItem.MAX_ENERGY),
                x + PANEL_WIDTH - 8, y + 33, WHITE);

        int barX = x + 8;
        int barY = y + 45;
        int barWidth = PANEL_WIDTH - 16;
        context.fill(barX, barY, barX + barWidth, barY + 3, 0xFF1D2A30);
        int fillWidth = barWidth * Math.max(0, Math.min(energy, GauntletItem.MAX_ENERGY))
                / GauntletItem.MAX_ENERGY;
        if (fillWidth > 0) {
            context.fill(barX, barY, barX + fillWidth, barY + 3, accent);
        }

        Text cooldown = cooldownTicks > 0
                ? Text.translatable("hud.resonance.cooldown_short", seconds(cooldownTicks))
                : Text.translatable("hud.resonance.ready");
        Text field = wellTicks > 0
                ? Text.translatable("hud.resonance.well_short", seconds(wellTicks))
                : Text.translatable("hud.resonance.inactive");
        drawText(context, client, cooldown, x + 8, y + 54, MUTED);
        drawRight(context, client, field, x + PANEL_WIDTH - 8, y + 54, wellTicks > 0 ? accent : MUTED);
    }

    private static String seconds(int ticks) {
        return String.format(Locale.ROOT, "%.1f", ticks / 20.0);
    }

    private static int opaque(int color) {
        return 0xFF000000 | color & 0x00FFFFFF;
    }

    private static void drawText(DrawContext context, MinecraftClient client, Text text, int x, int y, int color) {
        context.drawText(client.textRenderer, text, x, y, color, false);
    }

    private static void drawRight(DrawContext context, MinecraftClient client, Text text,
                                  int right, int y, int color) {
        drawText(context, client, text, right - client.textRenderer.getWidth(text), y, color);
    }

    static void drawGlyph(DrawContext context, Mode mode, int x, int y, int size, int color) {
        int unit = Math.max(1, size / 10);
        int center = size / 2;
        switch (mode) {
            case WELL -> {
                context.fill(x + center - unit, y + unit, x + center + unit, y + size - unit, color);
                context.fill(x + unit, y + center - unit, x + size - unit, y + center + unit, color);
                context.fill(x + center - 2 * unit, y + center - 2 * unit,
                        x + center + 2 * unit, y + center + 2 * unit, color);
                context.fill(x + unit, y + unit, x + 2 * unit, y + 2 * unit, color);
                context.fill(x + size - 2 * unit, y + size - 2 * unit,
                        x + size - unit, y + size - unit, color);
            }
            case PULSE -> {
                context.fill(x + center - unit, y + unit, x + center + unit, y + size - unit, color);
                context.fill(x + unit, y + center - unit, x + size - unit, y + center + unit, color);
                context.fill(x + center - 2 * unit, y + center - 2 * unit,
                        x + center + 2 * unit, y + center + 2 * unit, color);
            }
            case STEP -> {
                context.fill(x + unit, y + size - 3 * unit, x + 4 * unit, y + size - unit, color);
                context.fill(x + center - unit, y + center - unit,
                        x + center + 2 * unit, y + center + unit, color);
                context.fill(x + size - 4 * unit, y + unit, x + size - unit, y + 3 * unit, color);
            }
        }
    }
}
