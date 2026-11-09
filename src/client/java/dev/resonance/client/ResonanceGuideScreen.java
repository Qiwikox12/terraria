package dev.resonance.client;

import dev.resonance.Resonance;
import dev.resonance.ability.Mode;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class ResonanceGuideScreen extends Screen {
    private static final String[] TAB_KEYS = {
            "screen.resonance.tab.overview",
            "screen.resonance.tab.abilities",
            "screen.resonance.tab.crafting",
            "screen.resonance.tab.notes"
    };
    private static final int PANEL = 0xF00C1318;
    private static final int CARD = 0xFF121C22;
    private static final int EDGE = 0xFF2B3A41;
    private static final int WHITE = 0xFFE3ECEE;
    private static final int MUTED = 0xFF9AABB1;
    private int page;

    public ResonanceGuideScreen() {
        super(Text.translatable("screen.resonance.guide.title"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        Layout layout = layout();
        context.fill(0, 0, width, height, 0xB9000508);
        if (layout.width() < 180 || layout.height() < 140) {
            super.render(context, mouseX, mouseY, delta);
            return;
        }

        context.fill(layout.x(), layout.y(), layout.x() + layout.width(), layout.y() + layout.height(), PANEL);
        context.fill(layout.x(), layout.y(), layout.x() + layout.width(), layout.y() + 1, EDGE);
        context.fill(layout.x(), layout.y() + layout.height() - 1,
                layout.x() + layout.width(), layout.y() + layout.height(), EDGE);
        context.fill(layout.x(), layout.y(), layout.x() + 1, layout.y() + layout.height(), EDGE);
        context.fill(layout.x() + layout.width() - 1, layout.y(),
                layout.x() + layout.width(), layout.y() + layout.height(), EDGE);
        context.fill(layout.x() + 1, layout.y() + 1,
                layout.x() + layout.width() - 1, layout.y() + 3, 0xFF45C9C1);

        ResonanceHud.drawGlyph(context, Mode.WELL, layout.x() + 12, layout.y() + 8, 14, 0xFF45C9C1);
        context.drawText(textRenderer, Text.translatable("screen.resonance.guide.title"),
                layout.x() + 32, layout.y() + 10, WHITE, false);
        drawCloseButton(context, layout, mouseX, mouseY);
        drawTabs(context, layout, mouseX, mouseY);

        switch (page) {
            case 0 -> renderOverview(context, layout);
            case 1 -> renderAbilities(context, layout);
            case 2 -> renderCrafting(context, layout);
            case 3 -> renderNotes(context, layout);
            default -> page = 0;
        }
        drawFooter(context, layout, mouseX, mouseY);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            page = Math.floorMod(page - 1, TAB_KEYS.length);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            page = (page + 1) % TAB_KEYS.length;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        Layout layout = layout();
        if (inside(mouseX, mouseY, layout.closeX(), layout.closeY(), 18, 18)) {
            close();
            return true;
        }
        for (int i = 0; i < TAB_KEYS.length; i++) {
            int tabX = layout.tabsX() + i * (layout.tabWidth() + layout.tabGap());
            if (inside(mouseX, mouseY, tabX, layout.tabsY(), layout.tabWidth(), 19)) {
                page = i;
                return true;
            }
        }
        if (inside(mouseX, mouseY, layout.backX(), layout.navY(), 24, 15)) {
            page = Math.floorMod(page - 1, TAB_KEYS.length);
            return true;
        }
        if (inside(mouseX, mouseY, layout.nextX(), layout.navY(), 24, 15)) {
            page = (page + 1) % TAB_KEYS.length;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void renderOverview(DrawContext context, Layout layout) {
        int x = layout.contentX();
        int y = layout.contentY();
        context.fill(x, y, x + layout.contentWidth(), y + 43, CARD);
        ResonanceHud.drawGlyph(context, Mode.WELL, x + 7, y + 5, 32, 0xFF45C9C1);
        drawText(context, Text.translatable("screen.resonance.overview.heading"), x + 48, y + 5, WHITE);
        drawText(context, Text.translatable("screen.resonance.overview.summary"), x + 48, y + 18, MUTED);
        drawText(context, Text.translatable("screen.resonance.overview.controls"), x + 48, y + 30, 0xFFB9C9CD);

        drawInfoLine(context, x + 4, y + 51, "screen.resonance.overview.energy", 0xFF45C9C1);
        drawInfoLine(context, x + 4, y + 69, "screen.resonance.overview.range", 0xFF45C9C1);
        drawInfoLine(context, x + 4, y + 87, "screen.resonance.overview.field", 0xFF45C9C1);
        drawInfoLine(context, x + 4, y + 105, "screen.resonance.overview.safety", 0xFFF2B660);
    }

    private void renderAbilities(DrawContext context, Layout layout) {
        Mode[] modes = Mode.values();
        String[][] descriptions = {
                {"screen.resonance.ability.well.line1", "screen.resonance.ability.well.line2"},
                {"screen.resonance.ability.pulse.line1", "screen.resonance.ability.pulse.line2"},
                {"screen.resonance.ability.step.line1", "screen.resonance.ability.step.line2"}
        };
        for (int i = 0; i < modes.length; i++) {
            Mode mode = modes[i];
            int x = layout.contentX();
            int y = layout.contentY() + i * 45;
            int accent = opaque(mode.color());
            context.fill(x, y, x + layout.contentWidth(), y + 41, CARD);
            context.fill(x, y, x + 2, y + 41, accent);
            ResonanceHud.drawGlyph(context, mode, x + 8, y + 10, 20, accent);
            drawText(context, Text.translatable(mode.translationKey()), x + 36, y + 4, accent);
            drawRight(context, Text.translatable("hud.resonance.cost", mode.cost()),
                    x + layout.contentWidth() - 8, y + 4, WHITE);
            drawText(context, Text.translatable(descriptions[i][0]), x + 36, y + 16, MUTED);
            drawText(context, Text.translatable(descriptions[i][1]), x + 36, y + 27, MUTED);
        }
    }

    private void renderCrafting(DrawContext context, Layout layout) {
        renderRecipe(context, layout, 0, "screen.resonance.recipe.core.title",
                new String[]{" A ", "AEA", " A "},
                "screen.resonance.recipe.core.materials", Resonance.CORE,
                "screen.resonance.recipe.core.output");
        renderRecipe(context, layout, 1, "screen.resonance.recipe.gauntlet.title",
                new String[]{"ICI", "EDE", " I "},
                "screen.resonance.recipe.gauntlet.materials", Resonance.GAUNTLET,
                "screen.resonance.recipe.gauntlet.output");
        renderRecipe(context, layout, 2, "screen.resonance.recipe.guide.title",
                new String[]{"B A"},
                "screen.resonance.recipe.guide.materials", Resonance.FIELD_GUIDE,
                "screen.resonance.recipe.guide.output");
    }

    private void renderRecipe(DrawContext context, Layout layout, int row, String titleKey,
                              String[] pattern, String materialsKey, net.minecraft.item.Item output,
                              String outputKey) {
        int x = layout.contentX();
        int y = layout.contentY() + row * 43;
        context.fill(x, y, x + layout.contentWidth(), y + 40, CARD);
        drawText(context, Text.translatable(titleKey), x + 7, y + 9, WHITE);
        drawRecipeGrid(context, x + 84, y + 3, pattern);
        drawArrow(context, x + 121, y + 14, 0xFF8DA2A9);
        context.drawItem(new ItemStack(output), x + 138, y + 8);
        drawText(context, Text.translatable(outputKey), x + 158, y + 11, MUTED);
        drawText(context, Text.translatable(materialsKey), x + 7, y + 29, MUTED);
    }

    private void renderNotes(DrawContext context, Layout layout) {
        String[] keys = {
                "screen.resonance.notes.server",
                "screen.resonance.notes.energy",
                "screen.resonance.notes.field",
                "screen.resonance.notes.safety",
                "screen.resonance.notes.step",
                "screen.resonance.notes.fallback"
        };
        for (int i = 0; i < keys.length; i++) {
            int x = layout.contentX();
            int y = layout.contentY() + i * 23;
            context.fill(x, y, x + layout.contentWidth(), y + 20, CARD);
            context.fill(x, y, x + 2, y + 20, i == 3 ? 0xFFF2B660 : 0xFF45C9C1);
            drawText(context, Text.translatable(keys[i]), x + 9, y + 6, i == 3 ? WHITE : MUTED);
        }
    }

    private void drawTabs(DrawContext context, Layout layout, int mouseX, int mouseY) {
        for (int i = 0; i < TAB_KEYS.length; i++) {
            int x = layout.tabsX() + i * (layout.tabWidth() + layout.tabGap());
            boolean selected = page == i;
            boolean hovered = inside(mouseX, mouseY, x, layout.tabsY(), layout.tabWidth(), 19);
            context.fill(x, layout.tabsY(), x + layout.tabWidth(), layout.tabsY() + 19,
                    selected ? 0xFF1C2D33 : hovered ? 0xFF162229 : 0xFF10191E);
            if (selected) {
                context.fill(x, layout.tabsY() + 17, x + layout.tabWidth(), layout.tabsY() + 19, 0xFF45C9C1);
            }
            int color = selected ? WHITE : hovered ? 0xFFD0DDDF : MUTED;
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable(TAB_KEYS[i]),
                    x + layout.tabWidth() / 2, layout.tabsY() + 5, color);
        }
    }

    private void drawCloseButton(DrawContext context, Layout layout, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, layout.closeX(), layout.closeY(), 18, 18);
        context.fill(layout.closeX(), layout.closeY(), layout.closeX() + 18, layout.closeY() + 18,
                hovered ? 0xFF3C2527 : 0xFF192328);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("×"),
                layout.closeX() + 9, layout.closeY() + 4, hovered ? 0xFFFFB2A7 : MUTED);
    }

    private void drawFooter(DrawContext context, Layout layout, int mouseX, int mouseY) {
        drawNavButton(context, layout.backX(), layout.navY(), true,
                inside(mouseX, mouseY, layout.backX(), layout.navY(), 24, 15));
        drawNavButton(context, layout.nextX(), layout.navY(), false,
                inside(mouseX, mouseY, layout.nextX(), layout.navY(), 24, 15));
        context.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("screen.resonance.footer.hint"),
                layout.x() + layout.width() / 2, layout.navY() + 3, MUTED);
    }

    private void drawNavButton(DrawContext context, int x, int y, boolean left, boolean hovered) {
        context.fill(x, y, x + 24, y + 15, hovered ? 0xFF24373E : 0xFF162127);
        int color = hovered ? WHITE : MUTED;
        int cy = y + 7;
        if (left) {
            context.fill(x + 8, cy - 1, x + 16, cy + 1, color);
            context.fill(x + 7, cy - 3, x + 10, cy + 3, color);
        } else {
            context.fill(x + 8, cy - 1, x + 16, cy + 1, color);
            context.fill(x + 14, cy - 3, x + 17, cy + 3, color);
        }
    }

    private void drawRecipeGrid(DrawContext context, int x, int y, String[] pattern) {
        int cell = 8;
        for (int row = 0; row < pattern.length; row++) {
            String line = pattern[row];
            for (int column = 0; column < line.length(); column++) {
                int cellX = x + column * (cell + 1);
                int cellY = y + row * (cell + 1);
                char symbol = line.charAt(column);
                context.fill(cellX, cellY, cellX + cell, cellY + cell, 0xFF26363D);
                if (symbol != ' ') {
                    context.fill(cellX + 1, cellY + 1, cellX + cell - 1, cellY + cell - 1, ingredientColor(symbol));
                    context.drawCenteredTextWithShadow(textRenderer, Text.literal(Character.toString(symbol)),
                            cellX + cell / 2, cellY + 1, 0xFFFFFFFF);
                }
            }
        }
    }

    private void drawInfoLine(DrawContext context, int x, int y, String key, int color) {
        context.fill(x, y + 3, x + 3, y + 7, color);
        drawText(context, Text.translatable(key), x + 10, y, MUTED);
    }

    private void drawArrow(DrawContext context, int x, int y, int color) {
        context.fill(x, y + 3, x + 8, y + 5, color);
        context.fill(x + 6, y + 1, x + 9, y + 3, color);
        context.fill(x + 6, y + 5, x + 9, y + 7, color);
    }

    private void drawText(DrawContext context, Text text, int x, int y, int color) {
        context.drawText(textRenderer, text, x, y, color, false);
    }

    private void drawRight(DrawContext context, Text text, int right, int y, int color) {
        drawText(context, text, right - textRenderer.getWidth(text), y, color);
    }

    private int ingredientColor(char symbol) {
        return switch (symbol) {
            case 'A' -> 0xFF9B78D4;
            case 'E' -> 0xFF45C9C1;
            case 'I' -> 0xFFB4C0C3;
            case 'C' -> 0xFF46D4D0;
            case 'D' -> 0xFF78C8F0;
            case 'B' -> 0xFFC9A978;
            default -> 0xFF65767C;
        };
    }

    private int opaque(int color) {
        return 0xFF000000 | color & 0x00FFFFFF;
    }

    private Layout layout() {
        int panelWidth = Math.min(440, Math.max(0, width - 16));
        int panelHeight = Math.min(280, Math.max(0, height - 16));
        int x = (width - panelWidth) / 2;
        int y = (height - panelHeight) / 2;
        int tabsWidth = Math.max(0, panelWidth - 20);
        int tabGap = 3;
        int tabWidth = Math.max(0, (tabsWidth - tabGap * (TAB_KEYS.length - 1)) / TAB_KEYS.length);
        int navY = y + panelHeight - 22;
        return new Layout(x, y, panelWidth, panelHeight,
                x + 10, y + 27, tabWidth, tabGap,
                x + 12, y + 54, Math.max(0, panelWidth - 24), Math.max(0, panelHeight - 82),
                navY, x + 10, x + panelWidth - 34, x + panelWidth - 25, y + 7);
    }

    private boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private record Layout(int x, int y, int width, int height,
                          int tabsX, int tabsY, int tabWidth, int tabGap,
                          int contentX, int contentY, int contentWidth, int contentHeight,
                          int navY, int backX, int nextX, int closeX, int closeY) {
    }
}
