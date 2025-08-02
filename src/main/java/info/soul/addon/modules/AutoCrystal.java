package info.soul.addon.modules;

import info.soul.addon.SoulAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public class AutoCrystal extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Range to detect and break crystals")
        .defaultValue(5.0)
        .min(1.0)
        .max(6.0)
        .sliderMin(1.0)
        .sliderMax(6.0)
        .build()
    );

    private EndCrystalEntity lastPlacedCrystal = null;
    private int ticksSincePlace = 0;

    public AutoCrystal() {
        super(SoulAddon.CATEGORY, "auto-crystal", "Auto-break crystals on place and when right-clicked.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        double r = range.get();
        double rSquared = r * r;
        Vec3d playerPos = mc.player.getPos();

        // 1) Right-click attack on crystal targeted by crosshair
        if (mc.options.useKey.isPressed()) {
            HitResult hit = mc.crosshairTarget;
            if (hit != null && hit.getType() == HitResult.Type.ENTITY) {
                if (hit instanceof EntityHitResult entityHit) {
                    if (entityHit.getEntity() instanceof EndCrystalEntity crystal) {
                        if (crystal.squaredDistanceTo(mc.player) <= rSquared) {
                            mc.interactionManager.attackEntity(mc.player, crystal);
                            mc.player.swingHand(Hand.MAIN_HAND);

                            // Reset last placed, because you attacked manually
                            lastPlacedCrystal = null;
                            ticksSincePlace = 0;
                            return;
                        }
                    }
                }
            }
        }

        // 2) Detect if you just placed a crystal this tick by checking if you hold crystal in hand and just used right-click
        // We'll detect this by tracking the crystal entity that appeared closest after you placed it
        if (mc.player.getMainHandStack().getItem() == Items.END_CRYSTAL) {
            if (mc.options.useKey.wasPressed()) {
                // You just right-clicked with crystal in hand, so track crystals nearby to find the newly placed one
                List<EndCrystalEntity> crystals = mc.world.getEntitiesByClass(EndCrystalEntity.class,
                    new Box(playerPos.x - r, playerPos.y - r, playerPos.z - r,
                            playerPos.x + r, playerPos.y + r, playerPos.z + r),
                    crystal -> crystal.squaredDistanceTo(mc.player) <= rSquared);

                EndCrystalEntity closest = null;
                double closestDistSq = Double.MAX_VALUE;

                for (EndCrystalEntity crystal : crystals) {
                    double distSq = crystal.squaredDistanceTo(mc.player);
                    if (distSq < closestDistSq) {
                        closestDistSq = distSq;
                        closest = crystal;
                    }
                }

                if (closest != null) {
                    lastPlacedCrystal = closest;
                    ticksSincePlace = 0;
                }
            }
        }

        // 3) If we tracked a recently placed crystal, break it ASAP
        if (lastPlacedCrystal != null) {
            // Check if crystal still exists and is in range
            if (lastPlacedCrystal.isAlive() && lastPlacedCrystal.squaredDistanceTo(mc.player) <= rSquared) {
                mc.interactionManager.attackEntity(mc.player, lastPlacedCrystal);
                mc.player.swingHand(Hand.MAIN_HAND);
                lastPlacedCrystal = null; // Reset after break
            } else {
                // Crystal gone or out of range
                lastPlacedCrystal = null;
            }
        }
    }
}
