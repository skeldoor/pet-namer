package com.skeldoor;

import net.runelite.api.NPCComposition;

import java.lang.reflect.Field;
import java.util.Objects;

public class PetNamerUtils {

    public static void tryReplaceName(NPCComposition parent, String find, String replace)
    {
        try {
            String memoryFieldName = getFieldName(parent, find);

            if (memoryFieldName == null) {
                return;
            }

            Field field = parent.getClass().getDeclaredField(memoryFieldName);
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
}
