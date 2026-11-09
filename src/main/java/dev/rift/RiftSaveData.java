package dev.rift;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class RiftSaveData extends PersistentState {
    private static final Type<RiftSaveData> TYPE = new Type<>(RiftSaveData::new, RiftSaveData::read, null);
    private final Map<UUID, PowerState> players = new HashMap<>();

    public static RiftSaveData get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "rift_power");
    }

    public PowerState player(UUID uuid) {
        return players.computeIfAbsent(uuid, ignored -> {
            markDirty();
            return new PowerState();
        });
    }

    private static RiftSaveData read(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        RiftSaveData data = new RiftSaveData();
        NbtList list = nbt.getList("players", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound entry = list.getCompound(i);
            if (!entry.containsUuid("uuid")) continue;
            PowerState state = new PowerState();
            state.restore(entry.getFloat("energy"), entry.getInt("mode"),
                    entry.getIntArray("cooldowns"), entry.getInt("rechargeDelay"));
            data.players.put(entry.getUuid("uuid"), state);
        }
        return data;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        NbtList list = new NbtList();
        players.forEach((uuid, state) -> {
            NbtCompound entry = new NbtCompound();
            entry.putUuid("uuid", uuid);
            entry.putFloat("energy", state.energy());
            entry.putInt("mode", state.mode().ordinal());
            entry.putIntArray("cooldowns", java.util.Arrays.stream(AbilityMode.values())
                    .mapToInt(state::cooldown).toArray());
            entry.putInt("rechargeDelay", state.rechargeDelay());
            list.add(entry);
        });
        nbt.put("players", list);
        return nbt;
    }
}
