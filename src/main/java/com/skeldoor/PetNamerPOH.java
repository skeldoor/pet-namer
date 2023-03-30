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
    private boolean enteringHouse = false;
    public boolean inAHouse = false;
    public String houseOwner = "";

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
    public void onMenuOptionClicked(MenuOptionClicked menuOptionClicked){
        if (Objects.equals(menuOptionClicked.getMenuOption(), "Home") && menuOptionClicked.getMenuTarget().contains("Portal")){
            enteringHouse = true;
            houseOwner = client.getLocalPlayer().getName();
        }
        if (Objects.equals(menuOptionClicked.getMenuOption(), "Continue") && Objects.requireNonNull(menuOptionClicked.getWidget()).getText().contains("Go to your house")){
            enteringHouse = true;
            houseOwner = client.getLocalPlayer().getName();
        }
        if (Objects.equals(menuOptionClicked.getMenuOption(), "Continue") && Objects.requireNonNull(menuOptionClicked.getWidget()).getText().contains("Go to a friend's house")){
            enteringHouse = true;
        }
        if (Objects.equals(menuOptionClicked.getMenuOption(), "Friend's house") && menuOptionClicked.getMenuTarget().contains("Portal")){
            enteringHouse = true;
        }

        if (Objects.equals(menuOptionClicked.getMenuOption(), "Break") && menuOptionClicked.getMenuTarget().contains("Teleport to house")){
            enteringHouse = true;
            houseOwner = client.getLocalPlayer().getName();
        }

        if (Objects.equals(menuOptionClicked.getMenuOption(), "Inside") && menuOptionClicked.getMenuTarget().contains("Teleport to house")){
            enteringHouse = true;
            houseOwner = client.getLocalPlayer().getName();
        }


        log.info("menuOptionClicked.getMenuOption(): " + menuOptionClicked.getMenuOption());
        log.info("menuOptionClicked.getMenuTarget(): " + menuOptionClicked.getMenuTarget());
        log.info("Objects.requireNonNull(menuOptionClicked.getWidget()).getText(): " + Objects.requireNonNull(menuOptionClicked.getWidget()).getText());

    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged){
        log.info("gameStateChanged enteringHouse: " + enteringHouse);
        if (enteringHouse) return;
        if (gameStateChanged.getGameState() == GameState.LOADING || gameStateChanged.getGameState() == GameState.HOPPING){
            log.info("gameStateChanged: " + houseOwner);
            inAHouse = false;
            enteringHouse = false;
            houseOwner = "";
            log.info("gameStateChanged: " + houseOwner);
        }
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned obj){
        if (obj.getGameObject().getId() == 4525 && enteringHouse){
            enteringHouse = false;
            inAHouse = true;
            log.info("Portal is loaded, client int should have changed by now, I think we're in the house of " + houseOwner);
        }
    }

    @Subscribe
    public void onVarClientIntChanged(VarClientIntChanged event){
        int magicalNumber = 1112;
        if  (event.getIndex() == magicalNumber && enteringHouse && Objects.equals(houseOwner, "")){
            log.info("var int 1112 before changed: " + houseOwner);
            houseOwner = client.getVarcStrValue(VarClientStr.INPUT_TEXT);
            log.info("var int 1112 after changed: " + houseOwner);
        }
    }

}