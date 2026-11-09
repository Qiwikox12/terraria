package dev.eventhorizon.client;

import dev.eventhorizon.core.Ability;
import dev.eventhorizon.item.ModItems;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

public final class FieldGuideScreen extends Screen {
    private int page;
    private int scroll;
    private int maxScroll;
    private int left, top, panelWidth, panelHeight;
    private ButtonWidget previous, next;

    public FieldGuideScreen() { super(Text.translatable("guide.eventhorizon.title")); }

    @Override
    protected void init() {
        panelWidth = Math.min(350, width - 16);
        panelHeight = Math.min(238, height - 16);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        previous = addDrawableChild(ButtonWidget.builder(Text.literal("←"), button -> changePage(-1))
            .dimensions(left + 12, top + panelHeight - 29, 32, 20).build());
        next = addDrawableChild(ButtonWidget.builder(Text.literal("→"), button -> changePage(1))
            .dimensions(left + 48, top + panelHeight - 29, 32, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("guide.eventhorizon.close"), button -> close())
            .dimensions(left + panelWidth - 96, top + panelHeight - 29, 84, 20).build());
        updateButtons();
    }

    private void changePage(int delta) {
        page = Math.clamp(page + delta, 0, 5);
        scroll = 0;
        updateButtons();
    }

    private void updateButtons() {
        previous.active = page > 0;
        next.active = page < 5;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        Ability ability = page >= 1 && page <= 4 ? Ability.values()[page - 1] : null;
        int accent = ability == null ? 0xFF56E7F4 : 0xFF000000 | ability.rgb();
        context.fill(left + 3, top + 3, left + panelWidth + 3, top + panelHeight + 3, 0x70000000);
        context.fill(left, top, left + panelWidth, top + panelHeight, 0xF5101723);
        context.fill(left, top, left + panelWidth, top + 2, accent);
        context.fill(left, top + 2, left + 1, top + panelHeight, 0xFF354356);
        context.fill(left + panelWidth - 1, top + 2, left + panelWidth, top + panelHeight, 0xFF354356);
        context.drawText(textRenderer, Text.translatable("guide.eventhorizon.subtitle"), left + 14, top + 12, 0xFF8FA4B8, false);
        context.drawTextWithShadow(textRenderer, title, left + 14, top + 28, 0xFFF1F6FC);
        context.drawItem(new ItemStack(ModItems.GRAVITY_GAUNTLET), left + panelWidth - 34, top + 20);
        context.fill(left + 14, top + 46, left + panelWidth - 14, top + 47, 0xFF304052);
        Text heading = ability == null
            ? Text.translatable(page == 0 ? "guide.eventhorizon.overview" : "guide.eventhorizon.safety_title")
            : Text.translatable(ability.translationKey());
        context.drawTextWithShadow(textRenderer, heading, left + 14, top + 56, accent);
        String number = String.format(Locale.ROOT, "%02d / 06", page + 1);
        context.drawText(textRenderer, number, left + panelWidth - 14 - textRenderer.getWidth(number), top + 56, 0xFF8FA4B8, false);
        int bodyTop = top + 75;
        int bodyBottom = top + panelHeight - 39;
        context.enableScissor(left + 13, bodyTop, left + panelWidth - 13, bodyBottom);
        int y = bodyTop - scroll;
        if (ability != null) {
            y = paragraph(context, Text.translatable("guide.eventhorizon.cost", ability.cost()).append("     ")
                .append(Text.translatable("guide.eventhorizon.cooldown", String.format(Locale.ROOT, "%.1f", ability.cooldownTicks() / 20.0))), y, accent);
            y = paragraph(context, Text.translatable("guide.eventhorizon." + ability.name().toLowerCase(Locale.ROOT)), y + 6, 0xFFD1DCE8);
            y = paragraph(context, Text.translatable("ability.eventhorizon." + ability.name().toLowerCase(Locale.ROOT) + ".description"), y + 4, 0xFF8FA4B8);
        } else if (page == 0) {
            y = paragraph(context, Text.translatable("guide.eventhorizon.basics"), y, 0xFFD1DCE8);
            y = paragraph(context, Text.translatable("guide.eventhorizon.combo_title"), y + 5, 0xFFF279CF);
            y = paragraph(context, Text.translatable("guide.eventhorizon.combo"), y, 0xFFD1DCE8);
        } else {
            y = paragraph(context, Text.translatable("guide.eventhorizon.safety"), y, 0xFFD1DCE8);
        }
        maxScroll = Math.max(0, y + scroll - bodyBottom);
        context.disableScissor();
        if (maxScroll > 0) {
            int trackHeight = bodyBottom - bodyTop;
            context.fill(left + panelWidth - 7, bodyTop, left + panelWidth - 5, bodyBottom, 0xFF304052);
            int thumbY = bodyTop + (trackHeight - 14) * scroll / maxScroll;
            context.fill(left + panelWidth - 7, thumbY, left + panelWidth - 5, thumbY + 14, accent);
        }
        for (int i = 0; i < 6; i++) context.fill(left + 94 + i * 8, top + panelHeight - 20,
            left + 98 + i * 8, top + panelHeight - 16, i == page ? accent : 0xFF344256);
    }

    private int paragraph(DrawContext context, Text text, int y, int color) {
        for (var line : textRenderer.wrapLines(text, panelWidth - 32)) {
            context.drawTextWithShadow(textRenderer, line, left + 14, y, color);
            y += 12;
        }
        return y + 4;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scroll = Math.clamp(scroll - (int) (vertical * 18), 0, maxScroll);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_RIGHT) { changePage(1); return true; }
        if (keyCode == GLFW.GLFW_KEY_LEFT) { changePage(-1); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() { return false; }
}
