package com.skeldoor;

public class PetRenamerPetData {
    public String username;
    public int petId;
    public String petName;
    public String originalPetName;

    public PetRenamerPetData(String username, int petId, String petName, String originalPetName) {
        this.username = username;
        this.petId = petId;
        this.petName = petName;
        this.originalPetName = originalPetName;
    }

    @Override
    public String toString() {
        return "username: "+username+", petId: " + petId + ", petName: " + petName + ", originalPetName: " + originalPetName;
    }
}
