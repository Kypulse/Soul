package info.soul.addon.modules;

import info.soul.addon.SoulAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import java.util.List;
import java.util.Locale;

public class AutoAHSniper extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Item> item = sgGeneral.add(new ItemSetting.Builder()
        .name("item")
        .description("Item to snipe on AH.")
        .defaultValue(Items.DIAMOND)
        .onChanged(val -> {
            if (val == null) {
                this.item.set(Items.DIAMOND);
            }
        })
        .build()
    );

    private final Setting<Double> maxPrice = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-price")
        .description("Maximum price to buy.")
        .defaultValue(100000)
        .min(1)
        .sliderMin(1)
        .sliderMax(10000000)
        .build()
    );

    private final Setting<Double> refreshDelay = sgGeneral.add(new DoubleSetting.Builder()
        .name("refresh-delay-seconds")
        .description("Delay between AH refreshes (seconds).")
        .defaultValue(0.5)
        .min(0.1)
        .sliderMin(0.1)
        .sliderMax(10)
        .build()
    );

    private final Setting<Boolean> allItems = sgGeneral.add(new BoolSetting.Builder()
        .name("all-items")
        .description("Snipe any item if price is below max price.")
        .defaultValue(false)
        .build()
    );

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private long lastActionTime = 0;
    private boolean waitingForGui = false;
    private boolean waitingForConfirm = false;
    private int anvilClicks = 0;
    private long lastAnvilClickTime = 0;
    private boolean anvilPause = false;
    private long anvilPauseStart = 0;
    private boolean postBuyPause = false;
    private long postBuyPauseStart = 0;
    private boolean inventoryFullNotified = false;
    private boolean shouldClickAnvilAfterBuy = false;

    public AutoAHSniper() {
        super(SoulAddon.CATEGORY, "Auto AH Sniper", "Automatically snipes selected item on AH for max price.");
    }

    @Override
    public void onActivate() {
        lastActionTime = 0;
        waitingForGui = false;
        waitingForConfirm = false;
        anvilClicks = 0;
        lastAnvilClickTime = 0;
        anvilPause = false;
        anvilPauseStart = 0;
        postBuyPause = false;
        postBuyPauseStart = 0;
        inventoryFullNotified = false;
        shouldClickAnvilAfterBuy = false;
    }

    @Override
    public void onDeactivate() {
        waitingForGui = false;
        waitingForConfirm = false;
        anvilClicks = 0;
        anvilPause = false;
        postBuyPause = false;
        inventoryFullNotified = false;
        shouldClickAnvilAfterBuy = false;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null || item.get() == null || item.get() == Items.AIR) return;

        // Sprawdzanie pełnego ekwipunku
        if (isInventoryFull()) {
            if (!inventoryFullNotified) {
                ChatUtils.warning("[AH Sniper] Inventory full! Stopping purchases.");
                inventoryFullNotified = true;
            }
            return;
        } else {
            inventoryFullNotified = false;
        }

        long now = System.currentTimeMillis();

        // Kliknij kowadło po zakupie, jeśli trzeba
        if (shouldClickAnvilAfterBuy && mc.currentScreen instanceof GenericContainerScreen screen) {
            ScreenHandler handler = screen.getScreenHandler();
            for (Slot s : handler.slots) {
                if (s.inventory == mc.player.getInventory()) continue;
                ItemStack anvilStack = s.getStack();
                if (!anvilStack.isEmpty() && isAnvil(anvilStack)) {
                    mc.interactionManager.clickSlot(handler.syncId, s.id, 0, SlotActionType.PICKUP, mc.player);
                    shouldClickAnvilAfterBuy = false;
                    break;
                }
            }
        }

        // Przerwa po kupieniu przedmiotu
        if (postBuyPause) {
            if (now - postBuyPauseStart >= 1490) {
                postBuyPause = false;
            } else {
                return;
            }
        }

        // Przerwa po klikaniu kowadła
        if (anvilPause) {
            if (now - anvilPauseStart >= 1890) {
                anvilPause = false;
                anvilClicks = 0;
            } else {
                return;
            }
        }

        // Czekamy na GUI potwierdzenia zakupu
        if (waitingForConfirm && mc.currentScreen instanceof GenericContainerScreen screen) {
            ScreenHandler handler = screen.getScreenHandler();
            for (Slot slot : handler.slots) {
                if (slot.inventory == mc.player.getInventory()) continue;
                ItemStack stack = slot.getStack();
                if (!stack.isEmpty() && (stack.getItem() == Items.GREEN_STAINED_GLASS_PANE || stack.getItem() == Items.LIME_STAINED_GLASS_PANE)) {
                    mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
                    ChatUtils.info("[AH Sniper] Bought " + item.get().getName().getString() + " for <= $" + maxPrice.get().intValue());
                    waitingForConfirm = false;
                    lastActionTime = now;
                    shouldClickAnvilAfterBuy = true;
                    postBuyPause = true;
                    postBuyPauseStart = now;
                    return;
                }
            }
            return;
        }

        // Klikanie kowadła 10 razy z delayem (z jednoczesnym skanowaniem)
        if (waitingForGui && mc.currentScreen instanceof GenericContainerScreen screen) {
            ScreenHandler handler = screen.getScreenHandler();

            // 1. Skanuj w poszukiwaniu przedmiotu, nawet podczas klikania kowadła
            for (Slot slot : handler.slots) {
                if (slot.inventory == mc.player.getInventory()) continue;
                ItemStack stack = slot.getStack();
                if (!stack.isEmpty() && (allItems.get() || stack.getItem() == item.get())) {
                    String tooltip = getTooltip(stack);
                    double price = parsePrice(tooltip);
                    if (price > 0 && price <= maxPrice.get()) {
                        // Znaleziono! Przerwij cykl i kup.
                        mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
                        waitingForConfirm = true;
                        lastActionTime = now;
                        shouldClickAnvilAfterBuy = true;

                        // Zresetuj stan klikania kowadła
                        waitingForGui = false;
                        anvilClicks = 0;
                        anvilPause = false;
                        return;
                    }
                }
            }

            // 2. Jeśli nic nie znaleziono, kontynuuj klikanie kowadła
            for (Slot slot : handler.slots) {
                if (slot.inventory == mc.player.getInventory()) continue;
                ItemStack stack = slot.getStack();
                if (!stack.isEmpty() && isAnvil(stack)) {
                    if (anvilClicks < 10 && now - lastAnvilClickTime >= (long) (refreshDelay.get() * 1000)) {
                        mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
                        anvilClicks++;
                        lastAnvilClickTime = now;
                        if (anvilClicks >= 10) {
                            anvilPause = true;
                            anvilPauseStart = now;
                            waitingForGui = false;
                        }
                    }
                    return;
                }
            }
            return;
        }

        // Skanowanie aukcji (poza cyklem klikania)
        if (mc.currentScreen instanceof GenericContainerScreen screen && !waitingForGui && !waitingForConfirm && !anvilPause && !postBuyPause) {
            ScreenHandler handler = screen.getScreenHandler();
            for (Slot slot : handler.slots) {
                if (slot.inventory == mc.player.getInventory()) continue;
                ItemStack stack = slot.getStack();
                if (!stack.isEmpty() && (allItems.get() || stack.getItem() == item.get())) {
                    String tooltip = getTooltip(stack);
                    double price = parsePrice(tooltip);
                    if (price > 0 && price <= maxPrice.get()) {
                        mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
                        waitingForConfirm = true;
                        lastActionTime = now;
                        shouldClickAnvilAfterBuy = true;
                        return;
                    }
                }
            }
        }

        // Odświeżanie AH
        if (now - lastActionTime >= (long) (refreshDelay.get() * 1000) && !waitingForGui && !waitingForConfirm && !anvilPause && !postBuyPause) {
            String itemName = Registries.ITEM.getId(item.get()).getPath().replace('_', ' ');
            String cmd = "/ah " + itemName;
            ChatUtils.sendPlayerMsg(cmd);
            waitingForGui = true;
            lastActionTime = now;
        }
    }

    private boolean isAnvil(ItemStack stack) {
        return stack.getItem() == Items.ANVIL;
    }

    private String getTooltip(ItemStack stack) {
        if (mc.player == null) return "";
        List<Text> tooltipLines = stack.getTooltip(Item.TooltipContext.DEFAULT, mc.player, TooltipType.BASIC);
        if (tooltipLines.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        for (Text t : tooltipLines) {
            sb.append(t.getString()).append("\n");
        }
        return sb.toString();
    }

    private double parsePrice(String tooltip) {
        for (String line : tooltip.split("\n")) {
            if (line.toLowerCase(Locale.ROOT).contains("price")) {
                String[] parts = line.split("\\$");
                if (parts.length > 1) {
                    // Clean the string: remove anything that is not a digit, a dot, or a suffix char (k, m, b)
                    String priceStr = parts[1].toLowerCase(Locale.ROOT).replaceAll("[^\\d.kmb]", "");

                    if (priceStr.isEmpty()) continue;

                    try {
                        char suffix = ' ';
                        // Check if last character is one of the suffixes
                        char lastChar = priceStr.charAt(priceStr.length() - 1);
                        if (lastChar == 'k' || lastChar == 'm' || lastChar == 'b') {
                             suffix = lastChar;
                             priceStr = priceStr.substring(0, priceStr.length() - 1);
                        }

                        double value = Double.parseDouble(priceStr);

                        switch (suffix) {
                            case 'k':
                                value *= 1_000;
                                break;
                            case 'm':
                                value *= 1_000_000;
                                break;
                            case 'b':
                                value *= 1_000_000_000L;
                                break;
                        }

                        return value;
                    } catch (NumberFormatException e) {
                        // ignore and continue to next line
                    }
                }
            }
        }
        return -1;
    }

    private boolean isInventoryFull() {
        // Sprawdza sloty 9-35 (główna część ekwipunku)
        for (int i = 9; i <= 35; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) return false;
        }
        return true;
    }
} 