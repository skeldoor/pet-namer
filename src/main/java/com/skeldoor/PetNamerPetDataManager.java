package com.skeldoor;

import net.runelite.client.config.ConfigManager;
import java.util.HashMap;
import java.util.Objects;

public class PetNamerPetDataManager {

    HashMap<String, PetNamerPetData> playerPetNames;

    private static final String GROUP_NAME = "Pet Namer";
    private static final String LOCAL_CONFIG = "Pet Namer Local Config";

    public PetNamerPetDataManager(){
        this.playerPetNames = new HashMap<>();
    }

    public void updatePlayerPetData(PetNamerPetData petData){
        String key = createUserPetKey(petData.username, petData.petId);
        PetNamerPetData existingPetData = getPlayerPetName(key);
        if (existingPetData == null){
            playerPetNames.put(key, petData);
        } else {
            playerPetNames.replace(key, petData);
        }
    }

    PetNamerPetData getPlayerPetName(String username, int npcId){
        return getPlayerPetName(createUserPetKey(username, npcId));
    }

    PetNamerPetData getPlayerPetName(String key){
        return playerPetNames.get(key);
    }

    public void loadNamesFromConfig(ConfigManager configManager, String username){
        String localPetNames = configManager.getConfiguration(GROUP_NAME, LOCAL_CONFIG);
        if (localPetNames == null) return;
        if (!Objects.equals(localPetNames, "")){
            for (String petBlob : localPetNames.split(";")){
                PetNamerPetData parsedPet = parsePetData(petBlob, username);
                int petId = parsedPet.petId;
                String key = createUserPetKey(username, petId);
                playerPetNames.put(key, parsedPet);
            }
        }
    }

    public void storeNamesToConfig(ConfigManager configManager){
        StringBuilder configEntry = new StringBuilder();
        for (PetNamerPetData petData : playerPetNames.values()){
            String serialised = serialisePetData(petData);
            configEntry.append(serialised);
        }
        configManager.setConfiguration(GROUP_NAME, LOCAL_CONFIG, configEntry.toString());
    }

    public PetNamerPetData parsePetData(String petBlob, String username){
        String[] fields = petBlob.split(",");
        int petId = Integer.parseInt(fields[0]);
        String petName = fields[1];
        return new PetNamerPetData(username, petId, petName);
    }

    public String serialisePetData(PetNamerPetData petData){
        return petData.username + "," + petData.petName + "," + petData.petId + ";";
    }

    public String createUserPetKey(String username, int petId){
        return username.toLowerCase() + ":" + petId;
    }
}
