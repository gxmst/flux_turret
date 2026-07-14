package com.mymod.flux_turret.client;

import com.mymod.flux_turret.FluxTurretMod;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Small camera-only impact effect. It adjusts the rendered camera angles and
 * never mutates player rotation, which keeps input, aiming and server state
 * untouched.
 */
@Mod.EventBusSubscriber(modid = FluxTurretMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientImpactEffects {
    private static Level activeLevel;
    private static long startTick = Long.MIN_VALUE;
    private static int durationTicks;
    private static float amplitude;
    private static long phaseSeed;

    private ClientImpactEffects() {
    }

    public static void addExplosionShake(Level level, Vec3 impactPos, float radius) {
        double setting = TurretClientConfig.screenShakeStrength();
        if (setting <= 0.0 || level == null || !level.isClientSide) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != level) {
            return;
        }

        Vec3 cameraPos = minecraft.gameRenderer.getMainCamera().getPosition();
        double reach = Math.max(12.0, radius * 10.0);
        double distance = cameraPos.distanceTo(impactPos);
        if (distance >= reach) {
            return;
        }

        float attenuation = (float) (1.0 - distance / reach);
        float newAmplitude = (float) (setting * attenuation * Math.min(1.35, Math.max(0.35, radius / 5.0)));
        if (newAmplitude <= 0.001f) {
            return;
        }

        long now = level.getGameTime();
        if (activeLevel == level && now - startTick <= durationTicks) {
            float remaining = 1.0f - Math.min(1.0f, (now - startTick) / (float) Math.max(1, durationTicks));
            float remainingAmplitude = amplitude * remaining * remaining;
            amplitude = Math.min(2.5f, Math.max(remainingAmplitude, newAmplitude) + newAmplitude * 0.35f);
        } else {
            activeLevel = level;
            amplitude = Math.min(2.5f, newAmplitude);
        }
        startTick = now;
        durationTicks = 5 + Math.round(Math.min(8.0f, radius));
        phaseSeed = Double.doubleToLongBits(impactPos.x * 31.0 + impactPos.y * 17.0 + impactPos.z * 13.0);
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (activeLevel == null || minecraft.level != activeLevel || durationTicks <= 0) {
            clear();
            return;
        }

        double age = activeLevel.getGameTime() - startTick + event.getPartialTick();
        if (age < 0.0 || age >= durationTicks) {
            clear();
            return;
        }

        double remaining = 1.0 - age / durationTicks;
        double envelope = remaining * remaining;
        double phase = age + (phaseSeed & 1023L) * (1.0 / 1024.0);
        float strength = (float) (amplitude * envelope);

        event.setPitch(event.getPitch() + (float) Math.sin(phase * 8.7) * strength * 0.85f);
        event.setYaw(event.getYaw() + (float) Math.cos(phase * 7.1) * strength * 0.55f);
        event.setRoll(event.getRoll() + (float) Math.sin(phase * 10.9 + 0.7) * strength * 0.75f);
    }

    private static void clear() {
        activeLevel = null;
        startTick = Long.MIN_VALUE;
        durationTicks = 0;
        amplitude = 0.0f;
    }
}
