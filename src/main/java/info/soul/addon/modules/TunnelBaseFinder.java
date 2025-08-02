package info.soul.addon.modules;

import info.soul.addon.SoulAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.*;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.inventory.Inventory;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;

import java.util.Random;

public class TunnelBaseFinder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> enableSpawners = sgGeneral.add(new BoolSetting.Builder()
        .name("detect-spawners").defaultValue(true).build());

    private final Setting<Boolean> enableStorage = sgGeneral.add(new BoolSetting.Builder()
        .name("detect-storage").defaultValue(true).build());

    private final Setting<Integer> storageThreshold = sgGeneral.add(new IntSetting.Builder()
        .name("storage-items-needed").defaultValue(5).min(1).sliderMax(100).build());

    private final Setting<Boolean> enableDiscord = sgGeneral.add(new BoolSetting.Builder()
        .name("send-webhook").defaultValue(false).build());

    private final Setting<String> webhookUrl = sgGeneral.add(new StringSetting.Builder()
        .name("webhook-url").defaultValue("").build());

    private final Setting<Boolean> selfPing = sgGeneral.add(new BoolSetting.Builder()
        .name("self-ping").defaultValue(false).build());

    private final Setting<String> discordId = sgGeneral.add(new StringSetting.Builder()
        .name("discord-id").defaultValue("").build());

    private final Setting<Integer> delayMin = sgGeneral.add(new IntSetting.Builder()
        .name("min-delay").defaultValue(8).min(4).sliderMax(20).build());

    private final Setting<Integer> delayMax = sgGeneral.add(new IntSetting.Builder()
        .name("max-delay").defaultValue(20).min(10).sliderMax(60).build());

    private final Random random = new Random();
    private int tickDelay;
    private boolean webhookSent;
    private boolean wasHit;
    private float lastHealth;
    private int emergencyWaitTicks;

    private BlockPos miningTarget = null;
    private float targetYaw = 0;
    private float targetPitch = 0;
    private float baseYaw = 0;
    private float basePitch = 0;

    // Added state for left/right mining steps for gravel handling
    private enum MiningPhase {
        NONE,
        LEFT,
        RIGHT
    }

    private MiningPhase phase = MiningPhase.NONE;
    private BlockPos leftPos = null;
    private BlockPos rightPos = null;

    public TunnelBaseFinder() {
        super(SoulAddon.CATEGORY, "TunnelBaseFinder+", "Undetectable tunnel-based base finder");
    }

    @Override
    public void onActivate() {
        tickDelay = getRandomDelay();
        webhookSent = false;
        wasHit = false;
        emergencyWaitTicks = 0;
        phase = MiningPhase.NONE;
        leftPos = null;
        rightPos = null;

        if (mc.player != null) lastHealth = mc.player.getHealth();
    }

    @Override
    public void onDeactivate() {
        mc.options.forwardKey.setPressed(false);
        mc.options.attackKey.setPressed(false);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        if (mc.player.getHealth() < lastHealth && !wasHit) {
            wasHit = true;
            emergencyWaitTicks = 400;
        }

        if (wasHit) {
            mc.options.forwardKey.setPressed(false);
            mc.options.attackKey.setPressed(false);
            if (--emergencyWaitTicks <= 0) {
                disconnect("Emergency Protocol");
                toggle();
            }
            return;
        }

        if (--tickDelay > 0) {
            if (miningTarget != null) smoothAimToTarget();
            return;
        }
        tickDelay = getRandomDelay();

        mc.options.forwardKey.setPressed(true);

        // Handle gravel mining left-right phases first
        if (phase != MiningPhase.NONE) {
            handleGravelMining();
            return;
        }

        // Normal mining ahead
        BlockPos forwardPos = mc.player.getBlockPos().offset(mc.player.getHorizontalFacing());

        if (!mc.world.getBlockState(forwardPos).isAir()) {
            // Check if forward block is gravel, start gravel mining phase if yes
            if (mc.world.getBlockState(forwardPos).isOf(Blocks.GRAVEL)) {
                startGravelMining(forwardPos);
                return;
            }

            if (isSafeToMine(forwardPos)) {
                simulateMining(forwardPos);
            } else {
                mc.options.attackKey.setPressed(false);
                miningTarget = null;
                return;
            }
        } else {
            mc.options.attackKey.setPressed(false);
            miningTarget = null;
        }

        if (miningTarget != null) smoothAimToTarget();

        if (!webhookSent) {
            if (enableSpawners.get()) {
                BlockPos spawner = findSpawner();
                if (spawner != null) {
                    sendWebhook("Spawner Found at " + spawner);
                    disconnect("Spawner Found");
                    toggle();
                    return;
                }
            }

            if (enableStorage.get()) {
                BlockPos storage = findStorage();
                if (storage != null) {
                    sendWebhook("Storage Found at " + storage);
                    disconnect("Storage Found");
                    toggle();
                    return;
                }
            }
        }

        lastHealth = mc.player.getHealth();
    }

    private void startGravelMining(BlockPos forwardPos) {
        phase = MiningPhase.LEFT;

        Direction facing = mc.player.getHorizontalFacing();

        // leftPos = block to the left of forwardPos relative to player's facing
        leftPos = forwardPos.offset(facing.rotateYCounterclockwise());
        rightPos = forwardPos.offset(facing.rotateYClockwise());

        // Safety check: if left or right are air, don't mine them
        if (mc.world.getBlockState(leftPos).isAir()) leftPos = null;
        if (mc.world.getBlockState(rightPos).isAir()) rightPos = null;
    }

    private void handleGravelMining() {
        if (phase == MiningPhase.LEFT) {
            if (leftPos != null && !mc.world.getBlockState(leftPos).isAir()) {
                if (mc.world.getBlockState(leftPos).isOf(Blocks.GRAVEL)) {
                    simulateMining(leftPos);
                    smoothAimToTarget();
                    phase = MiningPhase.RIGHT; // Next tick mine right side
                    return;
                }
            }
            // If no gravel on left, jump to right phase directly
            phase = MiningPhase.RIGHT;
        }

        if (phase == MiningPhase.RIGHT) {
            if (rightPos != null && !mc.world.getBlockState(rightPos).isAir()) {
                if (mc.world.getBlockState(rightPos).isOf(Blocks.GRAVEL)) {
                    simulateMining(rightPos);
                    smoothAimToTarget();
                    phase = MiningPhase.LEFT; // Next tick mine left side again
                    return;
                }
            }
            // If no gravel on right either, done gravel mining phase
            phase = MiningPhase.NONE;
            leftPos = null;
            rightPos = null;
        }
    }

    private void simulateMining(BlockPos pos) {
        miningTarget = pos;

        double dx = pos.getX() + 0.5 - mc.player.getX();
        double dy = pos.getY() + 0.5 - mc.player.getEyeY();
        double dz = pos.getZ() + 0.5 - mc.player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        targetYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        targetPitch = (float) -Math.toDegrees(Math.atan2(dy, dist));

        mc.options.attackKey.setPressed(true);
        mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        mc.interactionManager.updateBlockBreakingProgress(miningTarget, mc.player.getHorizontalFacing());
    }

    private void smoothAimToTarget() {
        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();

        float yawDiffTarget = MathHelper.wrapDegrees(targetYaw - baseYaw);
        float pitchDiffTarget = targetPitch - basePitch;

        float desiredYaw = baseYaw + yawDiffTarget;
        float desiredPitch = basePitch + pitchDiffTarget;

        float yawDiff = MathHelper.wrapDegrees(desiredYaw - currentYaw);
        float pitchDiff = desiredPitch - currentPitch;

        float yawStep = MathHelper.clamp(yawDiff * 0.1f, -2f, 2f);
        float pitchStep = MathHelper.clamp(pitchDiff * 0.1f, -1.5f, 1.5f);

        mc.player.setYaw(currentYaw + yawStep);
        mc.player.setPitch(MathHelper.clamp(currentPitch + pitchStep, -90f, 90f));
    }

    private boolean isSafeToMine(BlockPos pos) {
        var state = mc.world.getBlockState(pos);
        if (state.isAir()) return false;

        if (state.isOf(Blocks.LAVA)) return false;
        if (state.isOf(Blocks.GRAVEL)) {
            BlockPos above = pos.up();
            if (!mc.world.getBlockState(above).isAir()) return false;
        }

        return true;
    }

    private int getRandomDelay() {
        return delayMin.get() + random.nextInt(delayMax.get() - delayMin.get() + 1);
    }

    private BlockPos findSpawner() {
        BlockPos playerPos = mc.player.getBlockPos();
        int radius = 16;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    if (mc.world.getBlockEntity(pos) instanceof MobSpawnerBlockEntity) return pos;
                }
            }
        }
        return null;
    }

    private BlockPos findStorage() {
        BlockPos playerPos = mc.player.getBlockPos();
        int radius = 16;
        int totalItems = 0;
        BlockPos firstFound = null;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    var be = mc.world.getBlockEntity(pos);

                    if (be instanceof Inventory inv) {
                        int count = 0;
                        for (int i = 0; i < inv.size(); i++) {
                            if (!inv.getStack(i).isEmpty()) count++;
                        }

                        totalItems += count;
                        if (count > 0 && firstFound == null) firstFound = pos;
                    }
                }
            }
        }

        return totalItems >= storageThreshold.get() ? firstFound : null;
    }

    private void sendWebhook(String message) {
        if (!enableDiscord.get()) return;
        if (webhookUrl.get().isEmpty()) return;

        System.out.println("[TunnelBaseFinder] Webhook message: " + message);

        if (selfPing.get() && !discordId.get().isEmpty()) {
            System.out.println("Pinging <@" + discordId.get() + ">");
        }

        webhookSent = true;
    }

    private void disconnect(String reason) {
        mc.player.networkHandler.onDisconnect(new DisconnectS2CPacket(Text.of("[TunnelBaseFinder] " + reason)));
    }
}
