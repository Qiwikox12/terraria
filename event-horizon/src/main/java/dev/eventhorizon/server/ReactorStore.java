package dev.eventhorizon.server;

import dev.eventhorizon.core.ReactorState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ReactorStore extends PersistentState {
    private static final Type<ReactorStore> TYPE = new Type<>(ReactorStore::new, ReactorStore::read, null);
    private final Map<UUID, ReactorState> reactors = new HashMap<>();

    public static ReactorStore get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "eventhorizon_reactors");
    }

    public ReactorState find(UUID id) { return reactors.get(id); }

    public ReactorState reactor(UUID id) {
        return reactors.computeIfAbsent(id, ignored -> {
            markDirty();
            return new ReactorState();
        });
    }

    public static ReactorStore read(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        ReactorStore store = new ReactorStore();
        NbtList players = nbt.getList("players", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < players.size(); i++) {
            NbtCompound entry = players.getCompound(i);
            if (!entry.containsUuid("id")) continue;
            store.reactors.put(entry.getUuid("id"), ReactorState.restore(new ReactorState.Snapshot(
                entry.getInt("energy"), entry.getInt("mode"), entry.getInt("resonance"), entry.getInt("lastAbility"),
                entry.getLong("comboExpiresAt"), entry.getLong("globalReadyAt"), entry.getLongArray("readyAt"))));
        }
        return store;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtList players = new NbtList();
        reactors.forEach((id, reactor) -> {
            ReactorState.Snapshot state = reactor.snapshot();
            NbtCompound entry = new NbtCompound();
            entry.putUuid("id", id);
            entry.putInt("energy", state.energy());
            entry.putInt("mode", state.mode());
            entry.putInt("resonance", state.resonance());
            entry.putInt("lastAbility", state.lastAbility());
            entry.putLong("comboExpiresAt", state.comboExpiresAt());
            entry.putLong("globalReadyAt", state.globalReadyAt());
            entry.putLongArray("readyAt", state.readyAt());
            players.add(entry);
        });
        nbt.putInt("version", 1);
        nbt.put("players", players);
        return nbt;
    }
}
