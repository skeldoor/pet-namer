package com.skeldoor;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.VarClientStr;
import net.runelite.api.events.*;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import java.util.Objects;

@Slf4j
public class PetNamerPOH
{
    public static final int BUILDING_MODE_VARP = 780;

    public static final int BUILDING_MODE_VARBIT = 2176;

    @Inject
    private Client client;

    private boolean buildingMode = false;
    public boolean inAHouse;
    public String houseOwner = "";

    @Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged){
        if (gameStateChanged.getGameState() == GameState.LOADING || gameStateChanged.getGameState() == GameState.HOPPING){
            inAHouse = false;
            houseOwner = "";
        }
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned obj){
        if (obj.getGameObject().getId() == 4525 && client.isInInstancedRegion()){
            inAHouse = true;
            log.info("In the house of " + houseOwner);
        }
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event)
    {
        if (event.getIndex() == PetNamerPOH.BUILDING_MODE_VARP)
        {
            int varbitValue = client.getVarbitValue(PetNamerPOH.BUILDING_MODE_VARBIT);
            buildingMode = (client.getVarbitValue(PetNamerPOH.BUILDING_MODE_VARBIT) == 1);
            if (varbitValue == 1) {
                log.info("Building mode: " + buildingMode);
            } else if (varbitValue == 0)  {
                log.info("Building mode: " + buildingMode);
            }
        }
    }

    @Subscribe
    public void onVarClientIntChanged(VarClientIntChanged event){
        int magicalNumber = 1112;
        if  (event.getIndex() == magicalNumber && Objects.equals(houseOwner, "")){
            houseOwner = client.getVarcStrValue(VarClientStr.INPUT_TEXT);
            if (Objects.equals(houseOwner, "")) houseOwner = client.getLocalPlayer().getName();
        }
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked menuOptionClicked){
        log.info("menuOptionClicked.getMenuOption() " + menuOptionClicked.getMenuOption());
        log.info("menuOptionClicked.getMenuEntry() " + menuOptionClicked.getMenuEntry());
        log.info("menuOptionClicked.getMenuTarget() " + menuOptionClicked.getMenuTarget());
    }

}