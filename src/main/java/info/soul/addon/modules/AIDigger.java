package info.soul.addon;

import info.soul.addon.SoulAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PickaxeItem;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.FluidState;

public class AIDigger extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSafety = settings.createGroup("Safety");
//aidigger
    private final Setting<Integer> scanDepth = sgGeneral.add(new IntSetting.Builder()
        .name("hazard-detection")
        .description("How many blocks below to detect for hazards.")
        .defaultValue(6)
        .range(4, 10)
        .sliderRange(4, 10)
        .build()
    );

    private final Setting<Integer> targetYLevel = sgGeneral.add(new IntSetting.Builder()
        .name("target-y-level")
        .description("Y level to stop digging at.")
        .defaultValue(-64)
        .range(-64, 0)
        .sliderRange(-64, 0)
        .build()
    );

    private final Setting<Boolean> chatFeedback = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-feedback")
        .description("Show info and warning messages in chat.")
        .defaultValue(true)
        .build()
    );

    // Safety settings (kept for hazard-related functionality)
    private final Setting<Integer> safetyMargin = sgSafety.add(new IntSetting.Builder()
        .name("How many blocks to stop before Hazard")
        .description("Blocks before hazard to stop digging.")
        .defaultValue(2)
        .range(1, 4)
        .sliderRange(1, 4)
        .build()
    );

    private final Setting<Integer> scanFrequency = sgSafety.add(new IntSetting.Builder()
        .name("checkDangerTicks")
        .description("How often to scan for hazards while digging (in ticks).")
        .defaultValue(5)
        .range(1, 20)
        .sliderRange(1, 20)
        .build()
    );

    // Module components
    private MiningState currentState = MiningState.IDLE;
    private PathScanner pathScanner;
    private BlockPos miningBlock = null;
    private int blocksMined = 0;
    private int scanTicks = 0;
    private int ticksSinceActivation = 0;
    private boolean packetSentThisTick = false;

    public AIDigger() {
        super(SoulAddon.CATEGORY, "AIDigger", "Automatically digs a hole downward, avoiding water and lava.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null || !mc.player.isAlive() || mc.player.networkHandler == null || !mc.player.networkHandler.getConnection().isOpen()) {
            sendError("Invalid player or server state! Cannot activate module.");
            toggle();
            return;
        }

        if (mc.player.getY() < targetYLevel.get()) {
            sendError("Already below target Y=" + targetYLevel.get() + "! Current Y: " + Math.round(mc.player.getY()));
            toggle();
            return;
        }

        try {
            pathScanner = new PathScanner();
        } catch (Exception e) {
            sendError("Failed to initialize components: " + e.getMessage());
            toggle();
            return;
        }

        resetMovement();
        currentState = MiningState.IDLE;
        blocksMined = 0;
        scanTicks = 0;
        ticksSinceActivation = 0;
        packetSentThisTick = false;
        miningBlock = null;

        sendInfo("AIDigger activated at Y=" + Math.round(mc.player.getY()) + ". Waiting for initialization...");
    }

    @Override
    public void onDeactivate() {
        resetMovement();
        currentState = MiningState.IDLE;
        miningBlock = null;
        sendInfo("AIDigger deactivated.");
    }

    private void resetMovement() {
        if (mc.options != null) {
            mc.options.attackKey.setPressed(false);
            mc.options.forwardKey.setPressed(false);
            mc.options.backKey.setPressed(false);
            mc.options.leftKey.setPressed(false);
            mc.options.rightKey.setPressed(false);
            mc.options.sneakKey.setPressed(false);
        }
        if (mc.player != null) {
            mc.player.setVelocity(0, mc.player.getVelocity().y, 0);
        }
        if (mc.interactionManager != null && miningBlock != null) {
            mc.interactionManager.cancelBlockBreaking();
            miningBlock = null;
        }
    }

    private void sendInfo(String message) {
        if (chatFeedback.get()) {
            info(message);
        }
    }

    private void sendWarning(String message) {
        if (chatFeedback.get()) {
            warning(message);
        }
    }

    private void sendError(String message) {
        if (chatFeedback.get()) {
            error(message);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null || !mc.player.isAlive() || mc.player.networkHandler == null || !mc.player.networkHandler.getConnection().isOpen()) {
            sendError("Invalid player or server state! Disabling module.");
            toggle();
            return;
        }

        if (mc.currentScreen != null) {
            resetMovement();
            return;
        }

        ticksSinceActivation++;
        packetSentThisTick = false;

        if (mc.player.getY() <= targetYLevel.get()) {
            sendInfo("Reached target Y=" + targetYLevel.get() + ". Stopping.");
            toggle();
            return;
        }

        if (currentState == MiningState.IDLE && ticksSinceActivation >= 20) {
            currentState = MiningState.SCANNING;
            resetMovement();
            sendInfo("Starting scanning phase.");
        }

        FindItemResult pickaxe = InvUtils.findInHotbar(this::isTool);
        if (!pickaxe.found()) {
            sendError("No pickaxe found in hotbar!");
            toggle();
            return;
        }

        if (!pickaxe.isMainHand()) {
            if (!packetSentThisTick) {
                InvUtils.swap(pickaxe.slot(), false);
                packetSentThisTick = true;
                sendInfo("Swapped to pickaxe, delaying next action.");
            }
            return;
        }

        // Set player to look downward
        if (mc.player.getPitch() != 90) {
            mc.player.setPitch(90);
            packetSentThisTick = true;
            sendInfo("Adjusting pitch to look downward.");
            return;
        }

        try {
            switch (currentState) {
                case SCANNING -> handleScanning();
                case DIGGING -> handleDigging();
                case HAZARD_DETECTED -> handleHazardDetected();
                case STOPPED -> toggle();
            }
        } catch (Exception e) {
            sendError("Error in state machine: " + e.getMessage());
            toggle();
        }
    }

    private boolean isTool(ItemStack itemStack) {
        return itemStack.getItem() instanceof PickaxeItem;
    }

    private void handleScanning() {
        BlockPos playerPos = mc.player.getBlockPos();
        PathScanner.ScanResult result = pathScanner.scanDownward(playerPos, scanDepth.get());

        if (result.isSafe()) {
            currentState = MiningState.DIGGING;
            blocksMined = 0;
            scanTicks = 0;
            startDigging();
            sendInfo("Scan complete, starting digging downward.");
        } else {
            String hazardName = result.getHazardType().toString();
            sendWarning(hazardName + " detected at Y=" + (playerPos.getY() - result.getHazardDistance()) + ".");
            currentState = MiningState.HAZARD_DETECTED;
            resetMovement();
        }
    }

    private void startDigging() {
        mc.options.attackKey.setPressed(true);
        mc.options.sneakKey.setPressed(true); // Prevent falling
        miningBlock = mc.player.getBlockPos().down();
    }

    private void handleDigging() {
        scanTicks++;
        if (scanTicks >= scanFrequency.get()) {
            scanTicks = 0;
            BlockPos playerPos = mc.player.getBlockPos();
            PathScanner.ScanResult result = pathScanner.scanDownward(playerPos, scanDepth.get());

            if (!result.isSafe() && result.getHazardDistance() <= safetyMargin.get()) {
                String hazardName = result.getHazardType().toString();
                sendWarning(hazardName + " detected at Y=" + (playerPos.getY() - result.getHazardDistance()) + ".");
                resetMovement();
                currentState = MiningState.HAZARD_DETECTED;
                return;
            }
        }

        if (mc.player.getY() <= targetYLevel.get()) {
            sendInfo("Reached target Y=" + targetYLevel.get() + ". Stopping.");
            resetMovement();
            toggle();
            return;
        }

        BlockPos below = mc.player.getBlockPos().down();
        BlockState state = mc.world.getBlockState(below);
        if (state.isAir() || state.getFluidState().getFluid() != net.minecraft.fluid.Fluids.EMPTY) {
            blocksMined++;
            resetMovement();
            startDigging();
            sendInfo("Dug block at Y=" + below.getY() + ", total blocks: " + blocksMined);
        }
    }

    private void handleHazardDetected() {
        resetMovement();
        sendError("Hazard detected, stopping digging.");
        currentState = MiningState.STOPPED;
    }

    public enum MiningState {
        IDLE,
        SCANNING,
        DIGGING,
        HAZARD_DETECTED,
        STOPPED
    }

    private class PathScanner {
        public static class ScanResult {
            private final boolean safe;
            private final HazardType hazardType;
            private final int hazardDistance;

            public ScanResult(boolean safe, HazardType hazardType, int hazardDistance) {
                this.safe = safe;
                this.hazardType = hazardType;
                this.hazardDistance = hazardDistance;
            }

            public boolean isSafe() { return safe; }
            public HazardType getHazardType() { return hazardType; }
            public int getHazardDistance() { return hazardDistance; }
        }

        public enum HazardType {
            NONE,
            LAVA,
            WATER,
            FALLING_BLOCK,
            DANGEROUS_BLOCK
        }

        public ScanResult scanDownward(BlockPos start, int depth) {
            for (int y = 1; y <= depth; y++) {
                BlockPos checkPos = start.down(y);
                BlockState state = mc.world.getBlockState(checkPos);

                HazardType fluidHazard = checkForFluids(state);
                if (fluidHazard != HazardType.NONE) {
                    return new ScanResult(false, fluidHazard, y);
                }

                HazardType hazard = checkBlock(checkPos, true);
                if (hazard != HazardType.NONE) {
                    return new ScanResult(false, hazard, y);
                }
            }
            return new ScanResult(true, HazardType.NONE, -1);
        }

        private HazardType checkForFluids(BlockState state) {
            FluidState fluid = state.getFluidState();
            if (fluid.getFluid() == net.minecraft.fluid.Fluids.LAVA || fluid.getFluid() == net.minecraft.fluid.Fluids.FLOWING_LAVA) {
                return HazardType.LAVA;
            }
            if (fluid.getFluid() == net.minecraft.fluid.Fluids.WATER || fluid.getFluid() == net.minecraft.fluid.Fluids.FLOWING_WATER) {
                return HazardType.WATER;
            }
            return HazardType.NONE;
        }

        private HazardType checkBlock(BlockPos pos, boolean checkFalling) {
            BlockState state = mc.world.getBlockState(pos);
            net.minecraft.block.Block block = state.getBlock();

            if (checkFalling && isFallingBlock(block)) {
                return HazardType.FALLING_BLOCK;
            }

            if (isDangerousBlock(block)) {
                return HazardType.DANGEROUS_BLOCK;
            }

            return HazardType.NONE;
        }

        private boolean isFallingBlock(net.minecraft.block.Block block) {
            return block == net.minecraft.block.Blocks.SAND ||
                   block == net.minecraft.block.Blocks.RED_SAND ||
                   block == net.minecraft.block.Blocks.GRAVEL ||
                   block == net.minecraft.block.Blocks.POINTED_DRIPSTONE;
        }

        private boolean isDangerousBlock(net.minecraft.block.Block block) {
            return block == net.minecraft.block.Blocks.LAVA ||
                   block == net.minecraft.block.Blocks.MAGMA_BLOCK ||
                   block == net.minecraft.block.Blocks.POINTED_DRIPSTONE ||
                   block == net.minecraft.block.Blocks.POWDER_SNOW;
        }
    }
}