package info.soul.addon.modules;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.decoration.GlowItemFrameEntity;
import net.minecraft.entity.decoration.LeashKnotEntity;
import net.minecraft.entity.decoration.painting.PaintingEntity;
import info.soul.addon.SoulAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import java.util.ArrayList;
import java.util.List;

public class AntiTrap extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> armorStands = sgGeneral.add(new BoolSetting.Builder()
        .name("armor-stands")
        .description("Do not render armor stands.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> itemFrames = sgGeneral.add(new BoolSetting.Builder()
        .name("item-frames")
        .description("Do not render item frames.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> paintings = sgGeneral.add(new BoolSetting.Builder()
        .name("paintings")
        .description("Do not render paintings.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> leashKnots = sgGeneral.add(new BoolSetting.Builder()
        .name("leash-knots")
        .description("Do not render leash knots.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> minecarts = sgGeneral.add(new BoolSetting.Builder()
        .name("minecarts")
        .description("Do not render minecarts.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Range to remove entities from player.")
        .defaultValue(100.0)
        .min(10.0)
        .max(500.0)
        .sliderRange(10.0, 500.0)
        .build()
    );

    public AntiTrap() {
        super(SoulAddon.CATEGORY, "AntiTrap", "Prevents rendering of trap-related entities like armor stands, item frames, etc.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;
        
        List<Entity> entitiesToRemove = new ArrayList<>();
        
        for (Entity entity : mc.world.getEntities()) {
            if (entity == null) continue;
                
            double distance = mc.player.squaredDistanceTo(entity);
            if (distance > range.get() * range.get()) continue;
            
            if (armorStands.get() && entity instanceof ArmorStandEntity) entitiesToRemove.add(entity);
            if (itemFrames.get() && (entity instanceof ItemFrameEntity || entity instanceof GlowItemFrameEntity)) entitiesToRemove.add(entity);
            if (paintings.get() && entity instanceof PaintingEntity) entitiesToRemove.add(entity);
            if (leashKnots.get() && entity instanceof LeashKnotEntity) entitiesToRemove.add(entity);
            if (minecarts.get() && entity instanceof AbstractMinecartEntity) entitiesToRemove.add(entity);
        }
        
        for (Entity entity : entitiesToRemove) {
            if (entity != null && !entity.isRemoved()) {
                entity.remove(Entity.RemovalReason.DISCARDED);
            }
        }
    }
}