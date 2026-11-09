package dev.chronoweave.registry;

import dev.chronoweave.ChronoWeaveMod;
import dev.chronoweave.block.entity.ChronoAnchorBlockEntity;
import dev.chronoweave.block.entity.ChrononSingularityCoreBlockEntity;
import dev.chronoweave.block.entity.TemporalAcceleratorBlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModBlockEntities {
    public static final BlockEntityType<ChrononSingularityCoreBlockEntity> CHRONON_SINGULARITY_CORE =
            Registry.register(
                    Registries.BLOCK_ENTITY_TYPE,
                    Identifier.of(ChronoWeaveMod.MOD_ID, "chronon_singularity_core"),
                    BlockEntityType.Builder.create(ChrononSingularityCoreBlockEntity::new, ModBlocks.CHRONON_SINGULARITY_CORE).build(null)
            );

    public static final BlockEntityType<TemporalAcceleratorBlockEntity> TEMPORAL_ACCELERATOR =
            Registry.register(
                    Registries.BLOCK_ENTITY_TYPE,
                    Identifier.of(ChronoWeaveMod.MOD_ID, "temporal_accelerator"),
                    BlockEntityType.Builder.create(TemporalAcceleratorBlockEntity::new, ModBlocks.TEMPORAL_ACCELERATOR).build(null)
            );

    public static final BlockEntityType<ChronoAnchorBlockEntity> CHRONO_ANCHOR =
            Registry.register(
                    Registries.BLOCK_ENTITY_TYPE,
                    Identifier.of(ChronoWeaveMod.MOD_ID, "chrono_anchor"),
                    BlockEntityType.Builder.create(ChronoAnchorBlockEntity::new, ModBlocks.CHRONO_ANCHOR).build(null)
            );

    private ModBlockEntities() {
    }

    public static void init() {
        // Static field initialization registers all BlockEntityTypes
    }
}
