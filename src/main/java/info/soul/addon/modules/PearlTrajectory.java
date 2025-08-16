package info.soul.addon;

import info.soul.addon.SoulAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.List;

public class PearlTrajectory extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
        .name("color")
        .description("Color of the landing spot.")
        .defaultValue(new SettingColor(255, 0, 255, 255))
        .build()
    );

    private final Setting<Boolean> showDistance = sgGeneral.add(new BoolSetting.Builder()
        .name("show-distance")
        .description("Shows the distance to where the pearl will land.")
        .defaultValue(true)
        .build()
    );

    public PearlTrajectory() {
        super(SoulAddon.CATEGORY, "Pearl Trajectory", "Shows where your ender pearl will land.");
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!isModuleActive() || !isHoldingPearl()) return;

        Vec3d landingSpot = calculateLandingSpot();
        if (landingSpot == null) return;

        renderLandingSpot(event, landingSpot);
    }

    private boolean isHoldingPearl() {
        return mc.player.getMainHandStack().isOf(Items.ENDER_PEARL) 
            || mc.player.getOffHandStack().isOf(Items.ENDER_PEARL);
    }

    private Vec3d calculateLandingSpot() {
        if (mc.player == null || mc.world == null) return null;
        
        Vec3d pos = mc.player.getEyePos().add(0, -0.08, 0);
        Vec3d velocity = getThrowVelocity();
        
        for (int tick = 0; tick < 300; tick++) {
            Vec3d nextPos = pos.add(velocity);
            
            HitResult hitResult = raycast(pos, nextPos);
            if (hitResult.getType() == HitResult.Type.BLOCK) {
                return hitResult.getPos();
            }
            
            pos = nextPos;
            velocity = velocity.multiply(0.99);
            velocity = velocity.add(0, -0.03, 0);
            
            if (pos.y < mc.world.getBottomY() - 32 || pos.y > 350) {
                return pos;
            }
        }
        
        return pos;
    }

    private Vec3d getThrowVelocity() {
        Vec3d lookVec = Vec3d.fromPolar(mc.player.getPitch(), mc.player.getYaw());
        return lookVec.multiply(1.5);
    }

    private HitResult raycast(Vec3d from, Vec3d to) {
        return mc.world.raycast(new RaycastContext(
            from, to,
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            mc.player
        ));
    }

    private void renderLandingSpot(Render3DEvent event, Vec3d landingSpot) {
        Box box = new Box(
            landingSpot.x - 0.5, landingSpot.y - 0.05, landingSpot.z - 0.5,
            landingSpot.x + 0.5, landingSpot.y + 0.05, landingSpot.z + 0.5
        );
        
        event.renderer.box(box, color.get(), color.get(), ShapeMode.Both, 0);
        
        // If show distance is enabled, we could log it or display it in chat
        // For now, just render a slightly different box to indicate distance info
        if (showDistance.get()) {
            double distance = mc.player.getPos().distanceTo(landingSpot);
            
            // Render a second smaller box on top to indicate distance is being calculated
            Box distanceBox = new Box(
                landingSpot.x - 0.2, landingSpot.y + 0.1, landingSpot.z - 0.2,
                landingSpot.x + 0.2, landingSpot.y + 0.15, landingSpot.z + 0.2
            );
            
            SettingColor distanceColor = new SettingColor(color.get().r, color.get().g, color.get().b, 150);
            event.renderer.box(distanceBox, distanceColor, distanceColor, ShapeMode.Both, 0);
            
            // Optional: Print distance to action bar (if you want to see the actual number)
            // mc.player.sendMessage(Text.literal(String.format("Pearl Distance: %.1fm", distance)), true);
        }
    }

    private boolean isModuleActive() {
        return this.isActive() && mc.player != null && mc.world != null;
    }
}