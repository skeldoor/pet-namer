package com.skeldoor;

public class PetNamerPetData {
    public String username;
    public int petId;
    public String petName;

    public PetNamerPetData(String username, int petId, String petName) {
        this.username = username;
        this.petId = petId;
        this.petName = petName;
    }

    @Override
    public String toString() {
        return "username: "+username+", petId: " + petId + ", petName: " + petName;
    }
}
