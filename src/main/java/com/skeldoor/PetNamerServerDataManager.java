package com.skeldoor;

import com.google.gson.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import okhttp3.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Singleton
public class PetNamerServerDataManager {
    private final String baseUrl = "http://64.226.75.168:8080";
    private final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    @Inject
    private OkHttpClient okHttpClient;

    @Inject
    private Gson gson;

    protected void updatePetName(PetNamerPetData data, PetNamerPetDataManager petNamerPetDataManager)
    {
        String username = urlifyString(petNamerPetDataManager.createUserPetKey(data.username, data.originalPetName));
        String url = baseUrl.concat("/pet-rename/"+username);

        try
        {
            Request r = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(JSON, gson.toJson(data)))
                    .build();

            okHttpClient.newCall(r).enqueue(new Callback()
            {
                @Override
                public void onFailure(Call call, IOException e)
                {
                    log.debug("Error sending post data", e);
                }

                @Override
                public void onResponse(Call call, Response response)
                {
                    if (response.isSuccessful())
                    {
                        log.debug("Successfully sent prop hunt data");
                        response.close();
                    }
                    else
                    {
                        log.debug("Post request unsuccessful");
                        response.close();
                    }
                }
            });
        }
        catch (IllegalArgumentException e)
        {
            log.error("Bad URL given: " + e.getLocalizedMessage());
        }
    }

    public void populateWanderingPets(String houseOwnerUsername, List<NPC> pets, PetNamerPetDataManager petNamerPetDataManager) {
        if (pets.size() == 0) return;
        StringBuilder usernameToPetName = new StringBuilder();
        for (NPC pet : pets){
            String originalPetName = petNamerPetDataManager.getOriginalName(pet.getId(), pet.getName());
            String usernamePetname = petNamerPetDataManager.createUserPetKey(houseOwnerUsername.toLowerCase(), originalPetName);
            usernameToPetName.append(usernamePetname).append(";");
        }
        grabPetsFromServer(usernameToPetName.toString(), petNamerPetDataManager);
    }

    public void populatePlayerPets(List<NPC> pets, PetNamerPetDataManager petNamerPetDataManager) {
        if (pets.size() == 0) return;
        StringBuilder usernameToPetName = new StringBuilder();
        for (NPC pet : pets){
            String originalPetName = petNamerPetDataManager.getOriginalName(pet.getId(), pet.getName());
            String usernamePetname = petNamerPetDataManager.createUserPetKey(Objects.requireNonNull(pet.getInteracting().getName()).toLowerCase(), originalPetName);
            usernameToPetName.append(usernamePetname).append(";");
        }
        grabPetsFromServer(usernameToPetName.toString(), petNamerPetDataManager);
    }

    private void grabPetsFromServer(String usernameToPetName, PetNamerPetDataManager petNamerPetDataManager ){
        try {
            String url = baseUrl.concat("/pet-rename/".concat(usernameToPetName.toString()));
            Request r = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            okHttpClient.newCall(r).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    log.info("Error getting prop hunt data by username", e);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if(response.isSuccessful()) {
                        try
                        {
                            JsonArray j = gson.fromJson(response.body().string(), JsonArray.class);
                            List<PetNamerPetData> playerpetDatas = parsePetRenamerData(j);
                            for (PetNamerPetData playerpetData : playerpetDatas){
                                petNamerPetDataManager.updatePlayerPetData(playerpetData);
                            }
                        }
                        catch (IOException | JsonSyntaxException e)
                        {
                            log.error(e.getMessage());
                        }
                    }

                    response.close();
                }
            });
        }
        catch(IllegalArgumentException e) {
            log.error("Bad URL given: " + e.getLocalizedMessage());
        }
    }

    private static List<PetNamerPetData> parsePetRenamerData(JsonArray j) {
        List<PetNamerPetData> l = new ArrayList<>();
        for (JsonElement jsonElement : j)
        {
            JsonObject jObj = jsonElement.getAsJsonObject();
            PetNamerPetData d = new PetNamerPetData(
                    jObj.get("username").getAsString(),
                    jObj.get("petId").getAsInt(),
                    jObj.get("petName").getAsString(),
                    jObj.get("originalPetName").getAsString());
            l.add(d);
        }
        return l;
    }

    private String urlifyString(String str) {
        return str.trim().replaceAll("\\s", "%20");
    }
}
