package dev.rift.client;

import dev.rift.AbilityMode;
import dev.rift.RiftMod;
import dev.rift.net.PowerPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class RiftHud {
    private static final AbilityMode[] MODES = AbilityMode.values();
    private static final Identifier[] MODE_ICONS = {
            Identifier.of("rift", "textures/gui/vector.png"),
            Identifier.of("rift", "textures/gui/surge.png"),
            Identifier.of("rift", "textures/gui/singularity.png")
    };

    private static final long TICK_NANOS = 50_000_000L;
    private static final int TEAL = 0xFF46D8C5;
    private static final int GOLD = 0xFFE3B766;
    private static final int MUTED = 0xFF9BAEB4;

    private static boolean hasSample;
    private static float targetEnergy;
    private static float maxEnergy;
    private static float displayedEnergy;
    private static int activeMode = -1;
    private static int heldCount;
    private static final int[] cooldownTicks = new int[MODES.length];
    private static long cooldownSampleNanos;
    private static long lastEnergyFrameNanos;

    private RiftHud() {
    }

    static void accept(PowerPayload payload) {
        float nextMax = finiteNonNegative(payload.maxEnergy());
        float nextEnergy = Math.min(nextMax, finiteNonNegative(payload.energy()));
        if (!hasSample) {
            displayedEnergy = nextEnergy;
        }
        targetEnergy = nextEnergy;
        maxEnergy = nextMax;
        hasSample = true;

        int mode = payload.mode();
        activeMode = mode >= 0 && mode < MODES.length ? mode : -1;
        cooldownTicks[0] = Math.max(0, payload.vectorCooldown());
        cooldownTicks[1] = Math.max(0, payload.surgeCooldown());
        cooldownTicks[2] = Math.max(0, payload.singularityCooldown());
        cooldownSampleNanos = System.nanoTime();
        heldCount = Math.max(0, payload.heldCount());
    }

    static void reset() {
        hasSample = false;
        targetEnergy = 0.0f;
        maxEnergy = 0.0f;
        displayedEnergy = 0.0f;
        activeMode = -1;
        heldCount = 0;
        cooldownTicks[0] = 0;
        cooldownTicks[1] = 0;
        cooldownTicks[2] = 0;
        cooldownSampleNanos = 0L;
        lastEnergyFrameNanos = 0L;
    }

    static boolean isGauntletEquipped(ClientPlayerEntity player) {
        return player.getMainHandStack().isOf(RiftMod.GAUNTLET)
                || player.getOffHandStack().isOf(RiftMod.GAUNTLET);
    }

    static void render(DrawContext context, Text cycleKey) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null
                || client.options.hudHidden
                || client.currentScreen != null
                || !isGauntletEquipped(client.player)) {
            return;
        }

        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();
        if (screenWidth < 190 || screenHeight < 180) {
            return;
        }

        long now = System.nanoTime();
        float energy = smoothEnergy(now);
        boolean compact = screenWidth < 232 || screenHeight < 230;
        int panelWidth = compact ? Math.min(204, screenWidth - 12) : 216;
        if (panelWidth < 178) {
            return;
        }

        int panelHeight = compact ? 32 : 49;
        int x = (screenWidth - panelWidth) / 2;
        int y = screenHeight - (compact ? 84 : 103);
        drawPanel(context, x, y, panelWidth, panelHeight, compact);

        if (compact) {
            renderCompact(context, client.textRenderer, cycleKey, x, y, panelWidth, energy, now);
        } else {
            renderFull(context, client.textRenderer, cycleKey, x, y, panelWidth, energy, now);
        }
    }

    private static void renderFull(
            DrawContext context,
            TextRenderer renderer,
            Text cycleKey,
            int x,
            int y,
            int width,
            float energy,
            long now
    ) {
        int slotSize = 18;
        for (int i = 0; i < MODES.length; i++) {
            drawModeSlot(context, renderer, i, x + 8 + i * 21, y + 4, slotSize, i == activeMode, now);
        }

        int titleX = x + 76;
        int holdingWidth = 62;
        int holdingX = x + width - 8 - holdingWidth;
        Text title = hasSample && activeMode >= 0
                ? Text.translatable(MODES[activeMode].translationKey())
                : Text.translatable("hud.rift.waiting");
        drawFittedText(context, renderer, title, titleX, y + 8, holdingX - titleX - 5, 0xFFE8F0EF);

        if (hasSample) {
            Text holding = Text.translatable("hud.rift.holding", heldCount);
            drawFittedText(context, renderer, holding, holdingX, y + 8, holdingWidth, GOLD);
        }

        Text energyLabel = Text.translatable("hud.rift.energy");
        drawFittedText(context, renderer, energyLabel, x + 8, y + 27, 39, MUTED);

        int valueWidth = 50;
        int valueX = x + width - 8 - valueWidth;
        int barX = x + 52;
        int barWidth = valueX - barX - 7;
        drawEnergyBar(context, barX, y + 27, barWidth, 7, energyFraction(energy), 16);

        String amount = hasSample
                ? Math.round(energy) + "/" + Math.round(maxEnergy)
                : "— / —";
        drawFittedText(context, renderer, Text.literal(amount), valueX, y + 26, valueWidth, 0xFFDDE8E7);

        Text controls = Text.translatable("hud.rift.controls", cycleKey);
        Text status = statusText(now);
        int statusWidth = 62;
        int statusX = x + width - 8 - statusWidth;
        drawFittedText(context, renderer, controls, x + 8, y + 39, statusX - x - 13, MUTED);
        drawFittedText(context, renderer, status, statusX, y + 39, statusWidth, statusColor(now));
    }

    private static void renderCompact(
            DrawContext context,
            TextRenderer renderer,
            Text cycleKey,
            int x,
            int y,
            int width,
            float energy,
            long now
    ) {
        int slotSize = 14;
        for (int i = 0; i < MODES.length; i++) {
            drawModeSlot(context, renderer, i, x + 6 + i * 16, y + 4, slotSize, i == activeMode, now);
        }

        int titleX = x + 56;
        int holdingWidth = Math.min(68, width / 3);
        int holdingX = x + width - 6 - holdingWidth;
        Text title = hasSample && activeMode >= 0
                ? Text.translatable(MODES[activeMode].translationKey())
                : Text.translatable("hud.rift.waiting");
        drawScaledFittedText(context, renderer, title, titleX, y + 7, holdingX - titleX - 4, 0xFFE8F0EF, 0.86f);

        if (hasSample) {
            Text holding = Text.translatable("hud.rift.holding", heldCount);
            drawScaledFittedText(context, renderer, holding, holdingX, y + 7, holdingWidth, GOLD, 0.78f);
        }

        int controlX = x + 100;
        int controlWidth = width - 106;
        if (!hasSample || targetEnergy <= 0.001f) {
            Text message = hasSample ? Text.translatable("hud.rift.empty") : Text.translatable("hud.rift.waiting");
            drawScaledFittedText(context, renderer, message, x + 6, y + 20, controlX - x - 11,
                    hasSample ? GOLD : MUTED, 0.78f);
        } else {
            drawScaledFittedText(context, renderer, Text.translatable("hud.rift.energy"),
                    x + 6, y + 20, 26, MUTED, 0.72f);
            drawEnergyBar(context, x + 35, y + 21, 34, 6, energyFraction(energy), 8);
            String percentage = Math.round(energyFraction(energy) * 100.0f) + "%";
            drawScaledFittedText(context, renderer, Text.literal(percentage), x + 72, y + 20, 25,
                    0xFFDDE8E7, 0.76f);
        }

        Text controls = Text.translatable("hud.rift.controls", cycleKey);
        drawScaledFittedText(context, renderer, controls, controlX, y + 20, controlWidth, MUTED, 0.78f);
    }

    private static void drawPanel(DrawContext context, int x, int y, int width, int height, boolean compact) {
        context.fill(x, y, x + width, y + height, 0xD509121A);
        context.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xB80E1A23);
        context.fill(x + 1, y + 1, x + width - 1, y + 2, 0xFF34474D);
        context.fill(x + 5, y + 1, x + 27, y + 2, TEAL);
        context.fill(x + 1, y + 2, x + 2, y + height - 1, 0x8846D8C5);
        context.fill(x + width - 2, y + 2, x + width - 1, y + height - 1, 0x663B555B);

        if (compact) {
            context.fill(x + 6, y + 17, x + width - 6, y + 18, 0x564F6269);
        } else {
            context.fill(x + 8, y + 22, x + width - 8, y + 23, 0x564F6269);
            context.fill(x + 8, y + 35, x + width - 8, y + 36, 0x564F6269);
        }
    }

    private static void drawModeSlot(
            DrawContext context,
            TextRenderer renderer,
            int mode,
            int x,
            int y,
            int size,
            boolean active,
            long now
    ) {
        int accent = 0xFF000000 | (MODES[mode].color() & 0x00FFFFFF);
        int frame = active ? accent : 0xFF35444A;
        context.fill(x, y, x + size, y + size, active ? withAlpha(accent, 0x48) : 0xA90B141B);
        context.fill(x, y, x + size, y + 1, frame);
        context.fill(x, y + size - 1, x + size, y + size, frame);
        context.fill(x, y, x + 1, y + size, frame);
        context.fill(x + size - 1, y, x + size, y + size, frame);

        int iconX = x + 1;
        int iconY = y + 1;
        int iconSize = size - 2;
        context.drawTexture(MODE_ICONS[mode], iconX, iconY, iconSize, iconSize, 0.0f, 0.0f, 16, 16, 16, 16);

        float remaining = cooldownLeft(mode, now);
        if (remaining > 0.0f) {
            context.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, 0xC90A1118);
            Text countdown = Text.translatable("hud.rift.cooldown", formatSeconds(remaining));
            drawCenteredText(context, renderer, countdown, iconX, iconY, iconSize, iconSize, 0xFFFFFFFF);
        }
    }

    private static void drawEnergyBar(
            DrawContext context,
            int x,
            int y,
            int width,
            int height,
            float fraction,
            int segmentCount
    ) {
        context.fill(x, y, x + width, y + height, 0xFF091219);
        context.fill(x, y, x + width, y + 1, 0xFF45555B);

        int innerX = x + 1;
        int innerY = y + 1;
        int innerWidth = Math.max(1, width - 2);
        int innerHeight = Math.max(1, height - 2);
        int gap = 1;
        int segments = Math.max(1, Math.min(segmentCount, innerWidth));
        int segmentWidth = Math.max(1, (innerWidth - gap * (segments - 1)) / segments);
        float filled = clamp(fraction, 0.0f, 1.0f) * segments;
        int fillColor = blend(GOLD, TEAL, clamp(fraction, 0.0f, 1.0f));

        for (int i = 0; i < segments; i++) {
            int segmentX = innerX + i * (segmentWidth + gap);
            if (segmentX >= innerX + innerWidth) {
                break;
            }
            int cellWidth = Math.min(segmentWidth, innerX + innerWidth - segmentX);
            context.fill(segmentX, innerY, segmentX + cellWidth, innerY + innerHeight, 0xFF26343A);
            float portion = clamp(filled - i, 0.0f, 1.0f);
            int filledWidth = Math.round(cellWidth * portion);
            if (filledWidth > 0) {
                context.fill(segmentX, innerY, segmentX + filledWidth, innerY + innerHeight, fillColor);
            }
        }
    }

    private static void drawFittedText(
            DrawContext context,
            TextRenderer renderer,
            Text text,
            int x,
            int y,
            int maxWidth,
            int color
    ) {
        if (maxWidth <= 0) {
            return;
        }
        String value = fit(renderer, text.getString(), maxWidth);
        if (!value.isEmpty()) {
            context.drawTextWithShadow(renderer, value, x, y, color);
        }
    }

    private static void drawScaledFittedText(
            DrawContext context,
            TextRenderer renderer,
            Text text,
            int x,
            int y,
            int maxWidth,
            int color,
            float preferredScale
    ) {
        if (maxWidth <= 0) {
            return;
        }
        String value = text.getString();
        int naturalWidth = renderer.getWidth(value);
        if (naturalWidth <= 0) {
            return;
        }

        float scale = Math.min(preferredScale, (float) maxWidth / naturalWidth);
        scale = Math.max(0.52f, scale);
        value = fit(renderer, value, Math.max(1, (int) (maxWidth / scale)));

        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0.0);
        context.getMatrices().scale(scale, scale, 1.0f);
        context.drawTextWithShadow(renderer, value, 0, 0, color);
        context.getMatrices().pop();
    }

    private static void drawCenteredText(
            DrawContext context,
            TextRenderer renderer,
            Text text,
            int x,
            int y,
            int width,
            int height,
            int color
    ) {
        String value = text.getString();
        int naturalWidth = renderer.getWidth(value);
        if (naturalWidth <= 0) {
            return;
        }

        float scale = Math.min(0.9f, Math.min((float) (width - 1) / naturalWidth, (float) (height - 1) / 9.0f));
        scale = Math.max(0.48f, scale);
        value = fit(renderer, value, Math.max(1, (int) ((width - 1) / scale)));
        float renderedWidth = renderer.getWidth(value) * scale;
        float renderedHeight = 9.0f * scale;

        context.getMatrices().push();
        context.getMatrices().translate(x + (width - renderedWidth) / 2.0f, y + (height - renderedHeight) / 2.0f, 0.0);
        context.getMatrices().scale(scale, scale, 1.0f);
        context.drawTextWithShadow(renderer, value, 0, 0, color);
        context.getMatrices().pop();
    }

    private static String fit(TextRenderer renderer, String value, int maxWidth) {
        if (renderer.getWidth(value) <= maxWidth) {
            return value;
        }
        String ellipsis = "…";
        int textWidth = Math.max(0, maxWidth - renderer.getWidth(ellipsis));
        return renderer.trimToWidth(value, textWidth) + ellipsis;
    }

    private static float smoothEnergy(long now) {
        if (!hasSample) {
            return 0.0f;
        }
        if (lastEnergyFrameNanos == 0L) {
            lastEnergyFrameNanos = now;
            return displayedEnergy;
        }

        float elapsed = clamp((now - lastEnergyFrameNanos) / 1_000_000_000.0f, 0.0f, 0.1f);
        float blend = 1.0f - (float) Math.exp(-10.0f * elapsed);
        displayedEnergy += (targetEnergy - displayedEnergy) * blend;
        if (Math.abs(targetEnergy - displayedEnergy) < 0.01f) {
            displayedEnergy = targetEnergy;
        }
        lastEnergyFrameNanos = now;
        return clamp(displayedEnergy, 0.0f, maxEnergy);
    }

    private static float energyFraction(float energy) {
        return hasSample && maxEnergy > 0.0f ? clamp(energy / maxEnergy, 0.0f, 1.0f) : 0.0f;
    }

    private static float cooldownLeft(int mode, long now) {
        if (!hasSample || mode < 0 || mode >= cooldownTicks.length) {
            return 0.0f;
        }
        float elapsed = Math.max(0.0f, (now - cooldownSampleNanos) / (float) TICK_NANOS);
        return Math.max(0.0f, cooldownTicks[mode] - elapsed);
    }

    private static Text statusText(long now) {
        if (!hasSample) {
            return Text.translatable("hud.rift.waiting");
        }
        if (targetEnergy <= 0.001f) {
            return Text.translatable("hud.rift.empty");
        }
        float cooldown = cooldownLeft(activeMode, now);
        if (cooldown > 0.0f) {
            return Text.translatable("hud.rift.cooldown", formatSeconds(cooldown));
        }
        return Text.translatable("hud.rift.ready");
    }

    private static int statusColor(long now) {
        if (!hasSample) {
            return MUTED;
        }
        if (targetEnergy <= 0.001f) {
            return GOLD;
        }
        return cooldownLeft(activeMode, now) > 0.0f ? MUTED : TEAL;
    }

    private static String formatSeconds(float ticks) {
        int tenths = Math.max(0, (int) Math.ceil(ticks / 2.0f));
        return tenths / 10 + "." + tenths % 10;
    }

    private static float finiteNonNegative(float value) {
        return Float.isFinite(value) ? Math.max(0.0f, value) : 0.0f;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static int blend(int from, int to, float progress) {
        float amount = clamp(progress, 0.0f, 1.0f);
        int red = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * amount);
        int green = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * amount);
        int blue = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * amount);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }
}
