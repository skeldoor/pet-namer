package com.skeldoor;

import net.runelite.api.ChatMessageType;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;

public class PetNamerUtils {

    public static String limitString(String input, ChatMessageManager chatMessageManager) {
        String regex = "[^a-zA-Z0-9\\s-]";
        String output = input.replaceAll(regex, "");
        output = output.substring(0, Math.min(output.length(), 35));
        if (output.length() < input.length()) {
            sendHighlightedChatMessage("Names are limited to 35 alphanumerical characters + spaces and hyphens", chatMessageManager);
        }
        return output;
    }

    public static void sendHighlightedChatMessage(String message, ChatMessageManager chatMessageManager) {
        ChatMessageBuilder msg = new ChatMessageBuilder()
                .append(ChatColorType.HIGHLIGHT)
                .append(message);

        chatMessageManager.queue(QueuedMessage.builder()
                .type(ChatMessageType.CONSOLE)
                .runeLiteFormattedMessage(msg.build())
                .build());
    }


}
