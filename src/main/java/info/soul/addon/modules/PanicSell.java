package info.soul.addon.modules;

import info.soul.addon.SoulAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.ItemSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

public class PanicSell extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    
    private final Setting<Item> item = sgGeneral.add(new ItemSetting.Builder()
            .name("item")
            .description("Item to panic sell.")
            .defaultValue(Items.ELYTRA)
            .build());

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private boolean sold = false;
    private int ticksSinceGUI = 0;

    public PanicSell() {
        super(SoulAddon.CATEGORY, "AhPanic", "Auto sells item on ah for 1b using keybinds");
    }

    @Override
    public void onActivate() {
        sold = false;
        ticksSinceGUI = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        if (!sold) {
            
            int inventorySlot = -1;
            for (int i = 9; i < 36; i++) { // 9-35 = main inventory
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (!stack.isEmpty() && stack.getItem() == item.get()) {
                    inventorySlot = i;
                    break;
                }
            }

            if (inventorySlot == -1) {
                ChatUtils.info("Item not found in inventory.");
                toggle();
                return;
            }


            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, inventorySlot, 4, SlotActionType.SWAP, mc.player);
            

            mc.player.getInventory().selectedSlot = 4;
            

            ChatUtils.sendPlayerMsg("/ah sell 1b");
            
            sold = true;
            return;
        }


        if (sold && mc.currentScreen instanceof GenericContainerScreen screen) {
            ticksSinceGUI++;
            
            if (ticksSinceGUI < 5) {
                return;
            }

            ScreenHandler handler = screen.getScreenHandler();
            
            Slot confirmSlot = handler.getSlot(15);
            if (confirmSlot != null) {
                ItemStack stack = confirmSlot.getStack();
                if (!stack.isEmpty() && stack.getItem() == Items.LIME_STAINED_GLASS_PANE) {
                    mc.interactionManager.clickSlot(handler.syncId, 15, 0, SlotActionType.PICKUP, mc.player);
                    ChatUtils.info("PanicSell: Sale confirmed at slot 15");
                    toggle();
                    return;
                }
            }
            
            for (int i = 0; i < handler.slots.size(); i++) {
                Slot slot = handler.getSlot(i);
                if (slot != null) {
                    ItemStack stack = slot.getStack();
                    if (!stack.isEmpty() && stack.getItem() == Items.LIME_STAINED_GLASS_PANE) {
                        mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.PICKUP, mc.player);
                        ChatUtils.info("PanicSell: Sale confirmed at slot " + i);
                        toggle();
                        return;
                    }
                }
            }
            
            if (ticksSinceGUI > 60) { 
                ChatUtils.error("PanicSell: Confirmation timeout.");
                toggle();
            }
        }
    }
}
