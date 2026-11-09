package dev.lumen;

import net.minecraft.block.Block;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;

public final class LumenTags {
    public static final TagKey<Block> RESONANT_ORES = TagKey.of(RegistryKeys.BLOCK, Lumen.id("resonant_ores"));
    public static final TagKey<Block> RESONANT_CACHES = TagKey.of(RegistryKeys.BLOCK, Lumen.id("resonant_caches"));

    private LumenTags() {
    }
}
