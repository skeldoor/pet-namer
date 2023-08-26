package com.skeldoor;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.*;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.task.Schedule;
import org.apache.commons.lang3.ArrayUtils;

import java.awt.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/*
Currently supports:
Your pets following you
Others pets following them
Your pets wandering in your house
Others pets wandering in their house
*/

@Slf4j
@PluginDescriptor(
	name = "Pet Namer",
	description = "Type \"::namepet name\" in chat to name your pet"
)
public class PetNamerPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private EventBus eventBus;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private ConfigManager configManager;

	@Inject
	private PetNamerConfig config;

	@Inject
	private PetNamerServerDataManager petNamerServerDataManager;

	@Inject
	private PetNamerPetDataManager petNamerPetDataManager;

	@Inject
	private PetNamerPOH playerOwnedHouse;

	private final int populateInterval = 5; // Seconds

	@Override
	protected void startUp() {
		eventBus.register(playerOwnedHouse);
	}

	@Override
	protected void shutDown(){
		eventBus.unregister(playerOwnedHouse);
	}

	@Schedule(
			period = populateInterval,
			unit = ChronoUnit.SECONDS,
			asynchronous = true
	)
	public void getPlayerConfigs() {
		if (client.getGameState() == GameState.LOGGED_IN){
			// Batch fetch the nearby players pets
			List<NPC> nearbyPetsWithValidPlayers =
					client.getNpcs().stream().filter(
							npc -> npc != null && npc.getName() != null && npc.getComposition().isFollower()
					).filter(
							npc -> npc.getInteracting() != null && npc.getInteracting().getName() != null
					).collect(Collectors.toList());
			petNamerServerDataManager.populatePlayerPets(nearbyPetsWithValidPlayers, petNamerPetDataManager);

			if (playerOwnedHouse.isInAHouse()){
				List<NPC> wanderingPets =
						client.getNpcs().stream().filter(
								npc -> npc != null && npc.getName() != null && npc.getInteracting() == null
						).collect(Collectors.toList());
				petNamerServerDataManager.populateWanderingPets(playerOwnedHouse.getHouseOwner(), wanderingPets, petNamerPetDataManager);
			}
			if (client.getLocalPlayer() != null && client.getLocalPlayer().getName() != null){
				String username = client.getLocalPlayer().getName().toLowerCase();
				PetNamerLocalStorage.storeNamesToConfig(username, configManager, petNamerPetDataManager);
			}
		}
	}

	@Subscribe
	public void onCommandExecuted(CommandExecuted commandExecuted){
		String command = commandExecuted.getCommand();
		String[] arguments = commandExecuted.getArguments();
		if (arguments == null) return;
		if (command.equals("namepet")){
			if (client.getFollower() == null){
				PetNamerUtils.sendHighlightedChatMessage("Drop the pet that you'd like to name!", chatMessageManager);
				return;
			}
			String newPetName = String.join(" ", arguments);
			if (newPetName.length() == 0){
				PetNamerUtils.sendHighlightedChatMessage("Try again like this ::namepet petname", chatMessageManager);
				return;
			}
			newPetName = PetNamerUtils.limitString(newPetName, chatMessageManager);
			int petId = client.getFollower().getId();
			String originalPetName = petNamerPetDataManager.getOriginalName(petId, client.getNpcDefinition(petId).getName());
			if (client.getLocalPlayer().getName() == null) return;

			String username = client.getLocalPlayer().getName();
			String lowerUsername = username.toLowerCase();
			PetNamerPetData newPetNamerPetData = new PetNamerPetData(lowerUsername, username, petId, newPetName, originalPetName);
			petNamerServerDataManager.updatePetName(newPetNamerPetData, petNamerPetDataManager);
			petNamerPetDataManager.updatePlayerPetData(newPetNamerPetData);
			setOverheadText(newPetName);
		}
	}

	@Subscribe
	public void onBeforeRender(BeforeRender ignored){
		// Change the hover text of pets that are hoverable. my pets AND others pets in their house
		MenuEntry[] menuEntries = client.getMenuEntries();
		if (menuEntries.length == 0) return;
		MenuEntry firstEntry = menuEntries[menuEntries.length - 1];

		NPC entryNPC = firstEntry.getNpc();

		if (entryNPC != null){
			String username;
			String lowerUsername;
			if (entryNPC.getInteracting() == null && playerOwnedHouse.isInAHouse()) { // Wandering pet in a house
				username = playerOwnedHouse.getHouseOwner();
				lowerUsername = username;
			} else if (entryNPC.getInteracting() != null && entryNPC.getInteracting().getName() != null) { // Pet following me
				username = entryNPC.getInteracting().getName();
				lowerUsername = username.toLowerCase();
			} else { // Unknown pet
				return; // investigate
			}
			PetNamerPetData petData = petNamerPetDataManager.getPetData(username, lowerUsername, entryNPC.getName());
			// If localmode is enabled, only create menu entries for your own pets
			if (config.localMode() && client.getLocalPlayer() != null ){
				if (!Objects.equals(petData.username, client.getLocalPlayer().getName().toLowerCase())){
					return;
				}
			}
			if (firstEntry.getTarget().contains(Objects.requireNonNull(entryNPC.getName()))) {
				firstEntry.setTarget("<col=ffff00>" + petData.petName);
			}
		}

		// Replace pet's chat box name
		// Override a cat's in-game name too so that you can have a name longer than 6 characters
		Widget petChatboxWidget = client.getWidget(WidgetInfo.DIALOG_NPC_NAME);
		NPC currentFollower = client.getFollower();
		if (petChatboxWidget != null && currentFollower != null){
			petChatboxWidget.setText(petNamerPetDataManager.getPetName(client.getLocalPlayer().getName(), currentFollower.getName()));
		}
	}

	@Subscribe
	public void onMenuOpened(MenuOpened menuOpened){
		// For my followers, others followers, my pets in my house, and others pets in their houses
		List<NPC> nearbyNPCs = client.getNpcs();
		for (NPC npc : nearbyNPCs){
			LocalPoint lp = npc.getLocalLocation();
			Shape clickbox = Perspective.getClickbox(client, npc.getModel(), npc.getCurrentOrientation(), lp.getX(), lp.getY(),	Perspective.getTileHeight(client, lp, npc.getWorldLocation().getPlane()));
			if (clickbox != null && clickbox.contains(client.getMouseCanvasPosition().getX(), client.getMouseCanvasPosition().getY())) {
				String username;
				String displayUsername;
				if (npc.getComposition().isFollower() && npc.getInteracting() != null && npc.getInteracting().getName() != null){
					// Pets following players
					displayUsername = npc.getInteracting().getName();
					username = displayUsername.toLowerCase();
				} else if (!npc.getComposition().isFollower() && playerOwnedHouse.isInAHouse()){
					// Pets in peoples houses aren't followers so we assume they're owned by the house owner
					displayUsername = playerOwnedHouse.getHouseOwner();
					username = displayUsername.toLowerCase();
				} else {
					// If it's not a follower and we're not in someones house, it's probably just any other NPC
					continue;
				}
				PetNamerPetData petData = petNamerPetDataManager.getPetData(username, displayUsername, npc.getName());

				// If localmode is enabled, only create menu entries for your own pets
				if (config.localMode() && client.getLocalPlayer() != null ){
					if (!Objects.equals(petData.username, client.getLocalPlayer().getName().toLowerCase())){
						continue;
					}
				}
				MenuEntry[] currentEntries = menuOpened.getMenuEntries();

				for (MenuEntry entry : currentEntries){
					if (entry.getTarget().contains(Objects.requireNonNull(npc.getName()))) {
						entry.setTarget("<col=ffff00>" + petData.petName);
					}
				}

				MenuEntry newEntry;
				/* TODO
				    Currently only named pets will have a correctly formatted username when they're roaming in \
				    another players house this is because a username capitalisation cannot be guarenteed as we're \
				    only trusting the input from the player typing the friend's name they wish to visit.
				    Named pets will look like: "Skeldoor's NamedPet"
				    But unnamed pets will be formatted however the user typed the house name: "SkElDoOr's Beaver"
				*/
				newEntry = createPetEntry(petData.displayUsername, petData.petName);
				menuOpened.setMenuEntries(ArrayUtils.insert(0, currentEntries, newEntry));
			}
		}
	}

	private MenuEntry createPetEntry(String ownerName, String petName){
		MenuEntry entry = client.createMenuEntry(1);
		entry.setParam0(0);
		entry.setParam1(0);
		entry.setOption(ownerName + "'s");
		entry.setTarget("<col=ffff00>" + petName);
		entry.setType(MenuAction.of(MenuAction.RUNELITE.getId()));
		return entry;
	}

	void setOverheadText(String petName){
		Random rand = new Random();
		String overheadText = "";
		switch(rand.nextInt(25)){
			case 0 : overheadText = "And I shall name you " + petName + "!"; break;
			case 1 : overheadText = "Well aren't you just the cutest little " + petName + "." ; break;
			case 2 : overheadText = "You look like a " + petName + " to me."; break;
			case 3 : overheadText = "I think I'll call you " + petName + "." ; break;
			case 4 : overheadText = "Oh really? Your real name is " + petName + "?"; break;
			case 5 : overheadText = "So you're " + petName + "?"; break;
			case 6 : overheadText = "Nice to meet you, " + petName + "." ; break;
			case 7 : overheadText = petName + "! Welcome to the family, " + petName + "." ; break;
			case 8 : overheadText = petName + " I would never trade you for 200 death runes."; break;
			case 9 : overheadText = petName + " what an amazing name you have."; break;
			case 10 : overheadText = "How about we call you " +  petName + "?"; break;
			case 11 : overheadText = "You look like a " + petName + " to me. What do you think?"; break;
			case 12 : overheadText = "I've always loved the name " + petName + ". What do you think?"; break;
			case 13 : overheadText = "You're so full of energy. Let's name you " + petName + "." ; break;
			case 14 : overheadText = "Hi there, little one! I think I'm going to call you " + petName + "." ; break;
			case 15 : overheadText = "I've been thinking about what to call you. You're now officially " + petName + "!"; break;
			case 16 : overheadText = "You're such a little bundle of joy! I'm going to call you " + petName  + "."; break;
			case 17 : overheadText = "I've been waiting for the perfect name, I've finally found it. You're now " + petName + "."; break;
			case 18 : overheadText = "I think you're going to be a great companion, I'm going to name you " + petName + "."; break;
			case 19 : overheadText = "Would you rather be called " + petName + "?"; break;
			case 20 : overheadText = "Let's find you a cozy bed to sleep in, " + petName + "."; break;
			case 21 : overheadText = "Are you hungry, " + petName + "? Let's get you some food and water."; break;
			case 22 : overheadText = "Let's go for a walk, " + petName + "! You'll love getting some fresh air."; break;
			case 23 : overheadText = petName + " you're going into the POH for the rest of your days."; break;
			case 24 : overheadText = "I love you, " + petName + "! You're the best pet ever."; break;
		}
		Player local = client.getLocalPlayer();
		local.setOverheadText(overheadText);
		int cyclesPerSecond = 50;
		int secondsToDisplay = 6;
		local.setOverheadCycle(cyclesPerSecond * secondsToDisplay);
	}

	@Provides
	PetNamerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PetNamerConfig.class);
	}
}
