package info.soul.addon.modules;

import info.soul.addon.SoulAddon;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.text.Text;
import meteordevelopment.meteorclient.utils.player.ChatUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GambleBot extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> winChance = sgGeneral.add(new IntSetting.Builder()
        .name("win-chance")
        .description("Chance to win in percent.")
        .defaultValue(100)
        .min(0)
        .max(100)
        .sliderMax(100)
        .build()
    );

    private final Setting<List<String>> winMessages = sgGeneral.add(new StringListSetting.Builder()
        .name("win-messages")
        .description("Messages sent when player wins. One will be chosen at random.")
        .defaultValue(List.of(
            "Congrats, you won!",
            "You crushed it!",
            "Victory is yours!",
            "Well played, champ!"
        ))
        .build()
    );

    private final Setting<List<String>> loseMessages = sgGeneral.add(new StringListSetting.Builder()
        .name("lose-messages")
        .description("Messages sent when player loses. One will be chosen at random.")
        .defaultValue(List.of(
            "Sorry, you lost!",
            "Better luck next time!",
            "Keep trying!",
            "Unlucky this round."
        ))
        .build()
    );

    private final Setting<String> webhookUrl = sgGeneral.add(new StringSetting.Builder()
        .name("discord-webhook")
        .description("Discord webhook URL to send gamble logs.")
        .defaultValue("")
        .build()
    );

    private static final Pattern PAY_PATTERN = Pattern.compile("(\\w+) paid you \\$(\\d+(k)?)");

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Random random = new Random();

    public GambleBot() {
        super(SoulAddon.CATEGORY, "Gamble Bot", "Auto gamble bot with customizable messages and Discord webhook logging");
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof GameMessageS2CPacket) {
            GameMessageS2CPacket packet = (GameMessageS2CPacket) event.packet;
            Text text = packet.content();
            String msg = text.getString();

            Matcher matcher = PAY_PATTERN.matcher(msg);
            if (matcher.find()) {
                String player = matcher.group(1);
                int bet = parseAmount(matcher.group(2));
                int chance = winChance.get();

                boolean win = Math.random() * 100 < chance;

                if (win) {
                    int payout = bet * 2;
                    ChatUtils.info("[GambleBot] Bet: " + bet + ", Payout: " + payout);

                    String winMsg = getRandomFromList(winMessages.get());
                    ChatUtils.sendPlayerMsg("/msg " + player + " " + winMsg);

                    new Thread(() -> {
                        try {
                            Thread.sleep(250); 
                        } catch (InterruptedException ignored) {}

                        ChatUtils.sendPlayerMsg("/pay " + player + " " + payout);
                        ChatUtils.info("[GambleBot] Paid out " + payout + " to " + player);

                        sendWebhookEmbed(player, bet, true, payout);
                    }).start();

                } else {
                    String loseMsg = getRandomFromList(loseMessages.get());
                    ChatUtils.sendPlayerMsg("/msg " + player + " " + loseMsg);
                    ChatUtils.info("[GambleBot] Lost! Sent lose message to " + player);

                    new Thread(() -> sendWebhookEmbed(player, bet, false, 0)).start();
                }
            }
        }
    }

    private int parseAmount(String raw) {
        raw = raw.toLowerCase();
        if (raw.endsWith("k")) {
            try {
                int base = Integer.parseInt(raw.substring(0, raw.length() - 1));
                return base * 1000;
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void sendWebhookEmbed(String player, int bet, boolean won, int payout) {
        String url = webhookUrl.get().trim();
        if (url.isEmpty()) return;

        String title = "🎲 Gamble Result🎲";
        String color = won ? "65280" : "16711680"; 

        StringBuilder fields = new StringBuilder();
        fields.append(String.format("""
            {"name": "Player", "value": "%s", "inline": true},
            {"name": "Bet", "value": "$%s", "inline": true},
            {"name": "Result", "value": "%s", "inline": true}""",
            player, formatAmount(bet), won ? "Won 🎉" : "Lost ❌"
        ));

        if (won) {
            fields.append(String.format(",{\"name\": \"Payout\", \"value\": \"$%s\", \"inline\": true}", formatAmount(payout)));
        }

        String json = String.format("""
            {
              "embeds": [{
                "title": "%s",
                "color": %s,
                "fields": [%s]
              }]
            }
            """, title, color, fields.toString()
        );

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();

        try {
            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            ChatUtils.info("[GambleBot] Failed to send webhook: " + e.getMessage());
        }
    }

    private String formatAmount(int amount) {
        if (amount >= 1000) return (amount / 1000) + "k";
        return Integer.toString(amount);
    }

    private String getRandomFromList(List<String> list) {
        if (list == null || list.isEmpty()) return "";
        return list.get(random.nextInt(list.size()));
    }
}
