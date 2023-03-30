package com.skeldoor;

public class PetNamerPetData {
    public String username;
    public String displayUsername;
    public int petId;
    public String petName;
    public String originalPetName;

    public PetNamerPetData(String username, String displayUsername, int petId, String petName, String originalPetName) {
        this.username = username;
        this.displayUsername = displayUsername;
        this.petId = petId;
        this.petName = petName;
        this.originalPetName = originalPetName;
    }

    @Override
    public String toString() {
        return "username: " + username + "displayusername: " + displayUsername +", petId: " + petId + ", petName: " + petName + ", originalPetName: " + originalPetName;
    }
}
