package info.soul.addon.modules;

import info.soul.addon.SoulAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

public class AutoSpawnerDeliver extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    private final Setting<Integer> delayBetweenCycles = settings.createGroup("Delay").add(new IntSetting.Builder()
        .name("cycle-delay-ms")
        .description("Time in milliseconds to wait between delivery cycles.")
        .defaultValue(5000)
        .min(1000)
        .sliderMax(10000)
        .build()
    );

    private boolean spawnerOpened = false;
    private boolean clickedSlotOne = false;
    private boolean chestMoved = false;
    private boolean bonesMoved = false;
    private boolean done = false;

    private long spawnerOpenTime = 0;
    private long guiClosedTime = 0;
    private boolean commandSent = false;
    private boolean clickedOrderBone = false;
    private long orderGuiOpenedAt = 0;
    private long lastInsertTime = 0;
    private static final long INSERT_DELAY_MS = 100;
    private boolean deliveryDone = false;
    private boolean deliveryConfirmed = false;
    private long confirmGuiOpenedAt = 0;
    private long greenGlassClickedAt = 0;
    private boolean awaitingRepeat = false;
    private long cycleRestartTime = 0;
    private boolean shouldCloseSpawnerGui = false;

    public AutoSpawnerDeliver() {
        super(SoulAddon.CATEGORY, "AutoSpawnerDeliver", "Automates spawner bone collection and delivery.");
    }

    @Override
    public void onActivate() {
        resetCycle();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        long now = System.currentTimeMillis();

        if (cycleRestartTime > 0 && now >= cycleRestartTime) {
            resetCycle();
            cycleRestartTime = 0;
        }

        if (!done && (isMainInventoryFullForBones() || shouldCloseSpawnerGui)) {
            if (mc.currentScreen instanceof GenericContainerScreen) {
                mc.player.closeHandledScreen();
            }
            done = true;
            guiClosedTime = now;
            return;
        }

        if (!spawnerOpened && mc.currentScreen == null && mc.crosshairTarget instanceof BlockHitResult hit) {
            BlockPos pos = hit.getBlockPos();
            if (mc.world.getBlockState(pos).getBlock() == Blocks.SPAWNER) {
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                spawnerOpened = true;
                spawnerOpenTime = now;
                return;
            }
        }

        if (mc.currentScreen instanceof GenericContainerScreen screen) {
            ScreenHandler handler = screen.getScreenHandler();

            if (!clickedSlotOne && now - spawnerOpenTime >= 1000) {
                if (handler.slots.size() > 1) {
                    mc.interactionManager.clickSlot(handler.syncId, 1, 0, SlotActionType.PICKUP, mc.player);
                    clickedSlotOne = true;
                    return;
                }
            }

            if (clickedSlotOne && !chestMoved) {
                for (Slot slot : handler.slots) {
                    ItemStack stack = slot.getStack();
                    if (stack.getItem() == Items.CHEST) {
                        mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.QUICK_MOVE, mc.player);
                        chestMoved = true;
                        return;
                    }
                }
                chestMoved = true;
            }

            if (chestMoved && !bonesMoved && now - spawnerOpenTime >= 1200) {
                boolean movedAny = false;
                boolean boneFound = false;
                for (Slot slot : handler.slots) {
                    ItemStack stack = slot.getStack();
                    if (stack.getItem() == Items.BONE) {
                        boneFound = true;
                        if (!isMainInventoryFullForBones()) {
                            mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.QUICK_MOVE, mc.player);
                            movedAny = true;
                        }
                    }
                }

                if (!boneFound || isMainInventoryFullForBones()) {
                    mc.player.closeHandledScreen();
                    bonesMoved = true;
                    done = true;
                    guiClosedTime = now;
                    return;
                }
            }
        } else if (clickedSlotOne && !done) {
            done = true;
            guiClosedTime = now;
        }

        if (done && !commandSent && mc.currentScreen == null && now - guiClosedTime >= 1000) {
            ChatUtils.sendPlayerMsg("/order bones");
            commandSent = true;
        }

        if (commandSent && !clickedOrderBone) {
            if (orderGuiOpenedAt == 0 && mc.currentScreen instanceof GenericContainerScreen) {
                orderGuiOpenedAt = now;
            }

            if (orderGuiOpenedAt > 0 && now - orderGuiOpenedAt >= 1000) {
                if (mc.currentScreen instanceof GenericContainerScreen screen) {
                    ScreenHandler handler = screen.getScreenHandler();
                    for (Slot slot : handler.slots) {
                        ItemStack stack = slot.getStack();
                        if (stack.getItem() == Items.BONE) {
                            mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
                            clickedOrderBone = true;
                            break;
                        }
                    }
                }
            }
        }

        if (clickedOrderBone && mc.currentScreen instanceof GenericContainerScreen screen) {
            String title = screen.getTitle().getString().toLowerCase();
            if (title.contains("deliver items")) {
                if (now - lastInsertTime >= INSERT_DELAY_MS) {
                    ScreenHandler handler = screen.getScreenHandler();
                    boolean bonesLeft = false;
                    for (int i = 9; i < 36; i++) {
                        ItemStack stack = mc.player.getInventory().getStack(i);
                        if (stack.getItem() == Items.BONE) {
                            bonesLeft = true;
                            for (Slot slot : handler.slots) {
                                if (slot.inventory != mc.player.getInventory() && slot.getStack().isEmpty()) {
                                    int invSlot = 36 + i - 9;
                                    mc.interactionManager.clickSlot(handler.syncId, invSlot, 0, SlotActionType.QUICK_MOVE, mc.player);
                                    lastInsertTime = now;
                                    return;
                                }
                            }
                        }
                    }
                    if (!bonesLeft) {
                        mc.player.closeHandledScreen();
                        deliveryDone = true;
                    }
                }
            }
        }

        if (!deliveryConfirmed && mc.currentScreen instanceof GenericContainerScreen screen) {
            String title = screen.getTitle().getString().toLowerCase();
            if (title.contains("confirm delivery")) {
                if (confirmGuiOpenedAt == 0) confirmGuiOpenedAt = now;

                if (now - confirmGuiOpenedAt >= 1000) {
                    ScreenHandler handler = screen.getScreenHandler();
                    for (Slot slot : handler.slots) {
                        ItemStack stack = slot.getStack();
                        if (!stack.isEmpty() && stack.getItem() == Items.LIME_STAINED_GLASS_PANE) {
                            mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
                            greenGlassClickedAt = now;
                            deliveryConfirmed = true;
                            awaitingRepeat = true;
                            break;
                        }
                    }
                }
            } else {
                confirmGuiOpenedAt = 0;
            }
        }

        if (awaitingRepeat && deliveryConfirmed && now - greenGlassClickedAt >= 2000) {
            if (mc.currentScreen instanceof GenericContainerScreen) {
                mc.player.closeHandledScreen();
            }
            awaitingRepeat = false;
            deliveryConfirmed = false;
            greenGlassClickedAt = 0;

            cycleRestartTime = now + delayBetweenCycles.get();
        }
    }

    private void resetCycle() {
        clickedSlotOne = false;
        spawnerOpened = false;
        chestMoved = false;
        bonesMoved = false;
        clickedOrderBone = false;
        commandSent = false;
        orderGuiOpenedAt = 0;
        deliveryDone = false;
        confirmGuiOpenedAt = 0;
        greenGlassClickedAt = 0;
        awaitingRepeat = false;
        deliveryConfirmed = false;
        done = false;
        shouldCloseSpawnerGui = false;
    }

    private boolean isMainInventoryFullForBones() {
        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) return false;
            if (stack.getItem() == Items.BONE && stack.getCount() < stack.getMaxCount()) return false;
        }
        return true;
    }

    private boolean hasBonesInInventory() {
        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.BONE) return true;
        }
        return false;
    }
}
