package com.skeldoor;

import com.google.gson.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.NPC;
import okhttp3.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Singleton
public class PetNamerDataManager {
    private final String baseUrl = "http://64.226.75.168:8080";
    private final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    @Inject
    private PetRenamerPlugin plugin;

    @Inject
    private OkHttpClient okHttpClient;

    @Inject
    private Gson gson;

    protected void updatePetName(PetRenamerPetData data)
    {
        String username = urlifyString(plugin.createUserPetKey(data.username, data.petId));
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

    public void populatePlayerPets(List<NPC> pets) {
        if (pets.size() == 0) return;
        StringBuilder usernameToPetName = new StringBuilder();
        for (NPC pet : pets){
            if (pet == null || pet.getName() == null || pet.getInteracting() == null || pet.getInteracting().getName() == null) continue;
            String usernamePetname = plugin.createUserPetKey(pet.getInteracting().getName().toLowerCase(), pet.getId());
            usernameToPetName.append(usernamePetname).append(";");
        }

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
                            List<PetRenamerPetData> playerpetDatas = parsePetRenamerData(j);
                            for (PetRenamerPetData playerpetData : playerpetDatas){
                                plugin.updatePlayerPetData(playerpetData);
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

    private static List<PetRenamerPetData> parsePetRenamerData(JsonArray j) {
        List<PetRenamerPetData> l = new ArrayList<>();
        for (JsonElement jsonElement : j)
        {
            JsonObject jObj = jsonElement.getAsJsonObject();
            PetRenamerPetData d = new PetRenamerPetData(
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
