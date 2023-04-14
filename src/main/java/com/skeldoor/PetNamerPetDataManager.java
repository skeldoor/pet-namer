package com.skeldoor;

import lombok.extern.slf4j.Slf4j;
import java.util.HashMap;

@Slf4j
public class PetNamerPetDataManager {

    HashMap<String, PetNamerPetData> playerPetNames;
    HashMap<Integer, String> originalPetNames;

    public String getPetName(String username, String petName){
        PetNamerPetData petData = playerPetNames.get(createUserPetKey(username, petName));
        if (petData == null){
            return petName;
        } else{
            return petData.petName;
        }
    }

    public PetNamerPetData getPetData(String username, String displayUsername, String petName){
        return playerPetNames.getOrDefault(createUserPetKey(username, petName), new PetNamerPetData(username, displayUsername, -1, petName, petName));
    }

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

    public String createUserPetKey(String username, String originalPetName){
        return username.toLowerCase() + ":" + originalPetName;
    }
}
