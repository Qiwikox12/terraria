package dev.lumen.network;

import dev.lumen.Lumen;
import dev.lumen.item.ResonanceMode;
import dev.lumen.item.ResonatorItem;
import io.netty.handler.codec.DecoderException;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public final class LumenNetworking {
    public static void init() {
        PayloadTypeRegistry.playC2S().register(CycleModePayload.ID, CycleModePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ScanResultPayload.ID, ScanResultPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PulsePayload.ID, PulsePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(CycleModePayload.ID, (payload, context) ->
                context.server().execute(() -> ResonatorItem.setModeFromNetwork(context.player(), payload.mode())));
    }

    private static int readCount(RegistryByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > 256) {
            throw new DecoderException("Invalid LUMEN signal count: " + count);
        }
        return count;
    }

    public record CycleModePayload(int mode) implements CustomPayload {
        public static final CustomPayload.Id<CycleModePayload> ID = new CustomPayload.Id<>(Lumen.id("cycle_mode"));
        public static final PacketCodec<RegistryByteBuf, CycleModePayload> CODEC = PacketCodec.of(
                (value, buf) -> buf.writeVarInt(value.mode),
                buf -> new CycleModePayload(buf.readVarInt()));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record BlockSignal(long position, int color) {
        public BlockPos pos() {
            return BlockPos.fromLong(position);
        }
    }

    public record EntitySignal(int entityId, int color) {
    }

    public record ScanResultPayload(int mode, boolean deep, int radius, int duration, List<BlockSignal> blocks,
                                    List<EntitySignal> entities) implements CustomPayload {
        public static final CustomPayload.Id<ScanResultPayload> ID = new CustomPayload.Id<>(Lumen.id("scan_result"));
        public static final PacketCodec<RegistryByteBuf, ScanResultPayload> CODEC = PacketCodec.of(
                ScanResultPayload::write, ScanResultPayload::read);

        public ScanResultPayload {
            blocks = List.copyOf(blocks);
            entities = List.copyOf(entities);
        }

        private static void write(ScanResultPayload value, RegistryByteBuf buf) {
            buf.writeVarInt(value.mode);
            buf.writeBoolean(value.deep);
            buf.writeVarInt(value.radius);
            buf.writeVarInt(value.duration);
            buf.writeVarInt(value.blocks.size());
            for (BlockSignal signal : value.blocks) {
                buf.writeLong(signal.position);
                buf.writeInt(signal.color);
            }
            buf.writeVarInt(value.entities.size());
            for (EntitySignal signal : value.entities) {
                buf.writeVarInt(signal.entityId);
                buf.writeInt(signal.color);
            }
        }

        private static ScanResultPayload read(RegistryByteBuf buf) {
            int mode = buf.readVarInt();
            boolean deep = buf.readBoolean();
            int radius = buf.readVarInt();
            int duration = buf.readVarInt();
            int blockCount = readCount(buf);
            List<BlockSignal> blocks = new ArrayList<>(blockCount);
            for (int i = 0; i < blockCount; i++) {
                blocks.add(new BlockSignal(buf.readLong(), buf.readInt()));
            }
            int entityCount = readCount(buf);
            List<EntitySignal> entities = new ArrayList<>(entityCount);
            for (int i = 0; i < entityCount; i++) {
                entities.add(new EntitySignal(buf.readVarInt(), buf.readInt()));
            }
            return new ScanResultPayload(mode, deep, radius, duration, blocks, entities);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record PulsePayload(double x, double y, double z, int radius, int mode,
                               boolean deep) implements CustomPayload {
        public static final CustomPayload.Id<PulsePayload> ID = new CustomPayload.Id<>(Lumen.id("pulse"));
        public static final PacketCodec<RegistryByteBuf, PulsePayload> CODEC = PacketCodec.of(
                (value, buf) -> {
                    buf.writeDouble(value.x);
                    buf.writeDouble(value.y);
                    buf.writeDouble(value.z);
                    buf.writeVarInt(value.radius);
                    buf.writeVarInt(value.mode);
                    buf.writeBoolean(value.deep);
                }, buf -> new PulsePayload(buf.readDouble(), buf.readDouble(), buf.readDouble(),
                        buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));

        public ResonanceMode resonanceMode() {
            return ResonanceMode.fromId(mode);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
