package com.skeldoor;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Objects;

@Slf4j
public class PetNamerPetDataManager {

    HashMap<String, PetNamerPetData> playerPetNames;
    HashMap<Integer, String> originalPetNames;

    private static final String GROUP_NAME = "Pet Namer";
    private static final String LOCAL_CONFIG = "Pet Namer Local Config";

    public PetNamerPetDataManager(){
        this.playerPetNames = new HashMap<>();
        this.originalPetNames = new HashMap<>();
    }

    public void updatePlayerPetData(PetNamerPetData petData){
        putOriginalName(petData.petId, petData.originalPetName);
        String key = createUserPetKey(petData.username, petData.originalPetName);
        PetNamerPetData existingPetData = getPlayerPetName(key);
        if (existingPetData == null){
            playerPetNames.put(key, petData);
        } else {
            playerPetNames.replace(key, petData);
        }
    }

    public String getOriginalName(int petNPCId, String npcDefinitionName){
        String originalName = originalPetNames.get(petNPCId);
        if (originalName == null){
            return npcDefinitionName;
        } else {
            return originalName;
        }
    }

    public void putOriginalName(int petNPCId, String petName){
        originalPetNames.putIfAbsent(petNPCId, petName);
    }

    PetNamerPetData getPlayerPetName(String username, String originalPetName){
        return getPlayerPetName(createUserPetKey(username, originalPetName));
    }

    PetNamerPetData getPlayerPetName(String key){
        return playerPetNames.get(key);
    }

    public void loadNamesFromConfig(ConfigManager configManager){
        String localPetNames = configManager.getConfiguration(GROUP_NAME, LOCAL_CONFIG);
        log.info("localPetNames " + localPetNames);
        if (localPetNames == null) return;
        if (!Objects.equals(localPetNames, "")){
            for (String petBlob : localPetNames.split(";")){
                log.info("petBlob " + petBlob);
                PetNamerPetData parsedPet = parsePetData(petBlob);
                String key = createUserPetKey(parsedPet.username, parsedPet.originalPetName);
                putOriginalName(parsedPet.petId, parsedPet.originalPetName);
                playerPetNames.put(key, parsedPet);
            }
        }
    }

    public void storeNamesToConfig(ConfigManager configManager){
        StringBuilder configEntry = new StringBuilder();
        for (PetNamerPetData petData : playerPetNames.values()){
            log.info("petData storing... " + petData.petName);
            String serialised = serialisePetData(petData);
            configEntry.append(serialised);
        }

        configManager.setConfiguration(GROUP_NAME, LOCAL_CONFIG, configEntry.toString());
        log.info("stored... " + configEntry);
    }

    public PetNamerPetData parsePetData(String petBlob){
        String[] fields = petBlob.split(",");
        String username = fields[0];
        String petName = fields[1];
        int petId = Integer.parseInt(fields[2]);
        String originalPetName = fields[3];
        return new PetNamerPetData(username, petId, petName, originalPetName);
    }

    public String serialisePetData(PetNamerPetData petData){
        return petData.username + "," + petData.petName + "," + petData.petId + ";";
    }

    public String createUserPetKey(String username, String originalPetName){
        return username.toLowerCase() + ":" + originalPetName;
    }
}
