package com.skeldoor;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.NPCComposition;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import java.lang.reflect.Field;
import java.util.Objects;

@Slf4j
public class PetNamerUtils {

    public static void tryReplaceName(NPCComposition parent, String find, String replace)
    {
        try {
            Field field = parent.getClass().getDeclaredField("av");
            field.setAccessible(true);
            field.set(parent, replace);

        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }


    public static String getFieldName(Object parent, Object find)
    {
        for (Field field : parent.getClass().getDeclaredFields())
        {
            field.setAccessible(true);

            try {
                if (Objects.isNull(field.get(parent))){
                    continue;
                }
                if (field.get(parent).equals(find)) {
                    return field.getName();
                }
            } catch (IllegalAccessException e) {
                return null;
            }
        }

        return null;
    }

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
