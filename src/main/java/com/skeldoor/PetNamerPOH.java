package com.skeldoor;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.VarClientStr;
import net.runelite.api.events.*;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import java.util.Objects;

@Slf4j
public class PetNamerPOH
{
    @Inject
    private Client client;

    private boolean enteringHouse = false;
    private boolean inAHouse = false;
    private String houseOwner = "";

    public boolean isInAHouse(){
        return inAHouse;
    }

    public String getHouseOwner(){
        return houseOwner;
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked menuOptionClicked){
        String option = menuOptionClicked.getMenuOption();
        String target = menuOptionClicked.getMenuTarget();
        String localPlayerName = client.getLocalPlayer().getName();

        // Covers entering homes from a house portal and via teleport tablets
        if (option.equals("Home") && target.contains("Portal")){
            enteringHouse = true;
            houseOwner = localPlayerName;
        } else if (target.contains("Teleport to house") && (option.equals("Break") || option.equals("Inside"))){
            enteringHouse = true;
            houseOwner = client.getLocalPlayer().getName();
        } else if (option.equals("Continue")){
            Widget menuWidget = menuOptionClicked.getWidget();
            if (menuWidget == null) return;
            String menuWidgetText = menuWidget.getText();
            if (menuWidgetText == null) return;
            if (menuWidgetText.contains("Go to your house")) {
                enteringHouse = true;
                houseOwner = localPlayerName;
            } else if (menuWidgetText.contains("Go to a friend's house")){
                enteringHouse = true;
            }
        } else if (option.equals("Friend's house") && target.contains("Portal")) {
            enteringHouse = true;
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged){
        if (enteringHouse) return;
        if (gameStateChanged.getGameState() == GameState.LOADING || gameStateChanged.getGameState() == GameState.HOPPING || gameStateChanged.getGameState() == GameState.LOGIN_SCREEN){
            inAHouse = false;
            enteringHouse = false;
            houseOwner = "";
        }
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned obj){
        int HOUSE_PORTAL_ID = 4525;
        if (obj.getGameObject().getId() == HOUSE_PORTAL_ID && enteringHouse){
            enteringHouse = false;
            inAHouse = true;
        }
    }

    @Subscribe
    public void onVarClientIntChanged(VarClientIntChanged event){
        //If we are entering a house but house owner is empty, we can get the house owner from the input text the user entered
        int enteringHouseVarClientInt = 1112;
        if  (event.getIndex() == enteringHouseVarClientInt && enteringHouse && Objects.equals(houseOwner, "")){
            houseOwner = client.getVarcStrValue(VarClientStr.INPUT_TEXT);
        }
    }
}