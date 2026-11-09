package dev.lumen.client;

import dev.lumen.item.ResonanceMode;
import dev.lumen.network.LumenNetworking.BlockSignal;
import dev.lumen.network.LumenNetworking.EntitySignal;
import dev.lumen.network.LumenNetworking.PulsePayload;
import dev.lumen.network.LumenNetworking.ScanResultPayload;
import dev.lumen.util.ResonanceMath;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

final class HighlightStore {
    private static final List<BlockVisual> BLOCKS = new ArrayList<>();
    private static final List<EntityVisual> ENTITIES = new ArrayList<>();
    private static final List<PulseVisual> PULSES = new ArrayList<>();
    private static ClientWorld world;
    private static int lastSignals;
    private static int lastDuration;
    private static long lastScanTick;
    private static ResonanceMode lastMode = ResonanceMode.ORE;
    private static boolean lastDeep;

    static void accept(ScanResultPayload payload, ClientWorld clientWorld) {
        BLOCKS.clear();
        ENTITIES.clear();
        long now = clientWorld.getTime();
        for (BlockSignal signal : payload.blocks()) {
            BLOCKS.add(new BlockVisual(signal, now, payload.duration()));
        }
        for (EntitySignal signal : payload.entities()) {
            ENTITIES.add(new EntityVisual(signal, now, payload.duration()));
        }
        lastSignals = payload.blocks().size() + payload.entities().size();
        lastDuration = payload.duration();
        lastScanTick = now;
        lastMode = ResonanceMode.fromId(payload.mode());
        lastDeep = payload.deep();
    }

    static void addPulse(PulsePayload payload, ClientWorld clientWorld) {
        PULSES.add(new PulseVisual(payload, clientWorld.getTime()));
    }

    static void tick(MinecraftClient client) {
        if (client.world == null) {
            clear();
            return;
        }
        if (world != client.world) {
            clear();
            world = client.world;
        }
        long now = client.world.getTime();
        BLOCKS.removeIf(signal -> now - signal.created >= signal.duration);
        ENTITIES.removeIf(signal -> now - signal.created >= signal.duration);
        Iterator<PulseVisual> iterator = PULSES.iterator();
        while (iterator.hasNext()) {
            PulseVisual pulse = iterator.next();
            long age = now - pulse.created;
            int duration = pulse.payload.deep() ? 50 : 36;
            if (age > duration) {
                iterator.remove();
                continue;
            }
            if (age % 2 == 0) {
                spawnPulse(client.world, pulse.payload, ResonanceMath.pulseProgress(age, duration));
            }
        }
    }

    private static void spawnPulse(ClientWorld world, PulsePayload pulse, float progress) {
        float radius = Math.max(0.5f, pulse.radius() * progress);
        int points = pulse.deep() ? 32 : 24;
        for (int i = 0; i < points; i++) {
            double angle = Math.TAU * i / points;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            world.addParticle(ParticleTypes.END_ROD, true,
                    pulse.x() + cos * radius, pulse.y(), pulse.z() + sin * radius, 0, 0, 0);
            if (pulse.deep() && i % 2 == 0) {
                world.addParticle(ParticleTypes.END_ROD, true,
                        pulse.x() + cos * radius, pulse.y() + sin * radius, pulse.z(), 0, 0, 0);
            }
        }
    }

    static float alpha(long now, long created, int duration) {
        return ResonanceMath.signalAlpha(now - created, duration);
    }

    static Nearest nearest(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return null;
        }
        Vec3d position = client.player.getPos();
        double best = Double.MAX_VALUE;
        int color = lastMode.color();
        long now = client.world.getTime();
        for (BlockVisual visual : BLOCKS) {
            double distance = Math.sqrt(visual.signal.pos().getSquaredDistance(position));
            if (distance < best) {
                best = distance;
                color = visual.signal.color();
            }
        }
        for (EntityVisual visual : ENTITIES) {
            Entity entity = client.world.getEntityById(visual.signal.entityId());
            if (entity != null) {
                double distance = Math.sqrt(entity.squaredDistanceTo(position));
                if (distance < best) {
                    best = distance;
                    color = visual.signal.color();
                }
            }
        }
        return best == Double.MAX_VALUE || now - lastScanTick > lastDuration ? null : new Nearest(best, color);
    }

    static int lastSignals(long now) {
        return now - lastScanTick <= lastDuration ? lastSignals : 0;
    }

    static boolean lastDeep() {
        return lastDeep;
    }

    static List<BlockVisual> blocks() {
        return BLOCKS;
    }

    static List<EntityVisual> entities() {
        return ENTITIES;
    }

    static void clear() {
        BLOCKS.clear();
        ENTITIES.clear();
        PULSES.clear();
        lastSignals = 0;
        lastDuration = 0;
    }

    record BlockVisual(BlockSignal signal, long created, int duration) {
    }

    record EntityVisual(EntitySignal signal, long created, int duration) {
    }

    record Nearest(double distance, int color) {
    }

    private record PulseVisual(PulsePayload payload, long created) {
    }

    private HighlightStore() {
    }
}
