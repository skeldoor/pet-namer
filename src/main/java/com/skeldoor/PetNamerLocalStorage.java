package com.skeldoor;

import net.runelite.client.config.ConfigManager;

import javax.inject.Inject;
import java.util.Objects;

public class PetNamerLocalStorage {

    private static final String GROUP_NAME = "PetNamer";
    private static final String LOCAL_CONFIG = "PetNamerLocalStorage";

    public void loadNamesFromConfig(ConfigManager configManager, PetNamerPetDataManager petNamerPetDataManager){
        String localPetNames = configManager.getConfiguration(GROUP_NAME, LOCAL_CONFIG);
        if (localPetNames == null) return;
        if (!Objects.equals(localPetNames, "")){
            for (String petBlob : localPetNames.split(";")){
                petNamerPetDataManager.updatePlayerPetData(parsePetData(petBlob));
            }
        }
    }

    public static void storeNamesToConfig(String username, ConfigManager configManager, PetNamerPetDataManager petNamerPetDataManager){
        StringBuilder configEntry = new StringBuilder();
        for (PetNamerPetData petData : petNamerPetDataManager.getPlayersPets(username)){
            String serialised = serialisePetData(petData);
            configEntry.append(serialised);
        }

        configManager.setConfiguration(GROUP_NAME, LOCAL_CONFIG, configEntry.toString());
    }

    public PetNamerPetData parsePetData(String petBlob){
        String[] fields = petBlob.split(",");
        String username = fields[0];
        String displayUsername = fields[1];
        int petId = Integer.parseInt(fields[2]);
        String petName = fields[3];
        String originalPetName = fields[4];
        return new PetNamerPetData(username, displayUsername, petId, petName, originalPetName);
    }

    public static String serialisePetData(PetNamerPetData petData){
        return  petData.username + "," +
                petData.displayUsername + "," +
                petData.petId + "," +
                petData.petName + "," +
                petData.originalPetName + ";";
    }
}
