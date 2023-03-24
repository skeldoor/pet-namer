package com.skeldoor;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.poh.PohIcons;
import net.runelite.client.task.Schedule;
import org.apache.commons.lang3.ArrayUtils;

import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

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
	private PetNamerConfig config;

	@Inject
	private EventBus eventBus;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private ConfigManager configManager;

	@Inject
	private PetNamerServerDataManager petNamerServerDataManager;

	@Inject
	private PetNamerPetDataManager petNamerPetDataManager;

	@Inject
	private PetNamerPOH playerOwnedHouse;

	private final int populateInterval = 5; // Seconds

	private PetNamerPetData currentFollower;

	@Override
	protected void startUp() {
		eventBus.register(playerOwnedHouse);
	}

	@Override
	protected void shutDown(){
		eventBus.unregister(playerOwnedHouse);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged){
		if (!config.onlineMode() && gameStateChanged.getGameState() == GameState.LOGGED_IN){
			petNamerPetDataManager.loadNamesFromConfig(configManager);
		}
	}

	@Schedule(
			period = populateInterval,
			unit = ChronoUnit.SECONDS,
			asynchronous = true
	)
	public void getPlayerConfigs() {
		if (config.onlineMode() && client.getGameState() == GameState.LOGGED_IN){
			// Batch fetch the nearby players pets
			List<NPC> nearbyPetsWithValidPlayers =
					client.getNpcs().stream().filter(
							npc -> npc != null && npc.getName() != null && npc.getComposition().isFollower()
					).filter(
							npc -> npc.getInteracting() != null && npc.getInteracting().getName() != null
					).collect(Collectors.toList());
			petNamerServerDataManager.populatePlayerPets(nearbyPetsWithValidPlayers, petNamerPetDataManager);

			if (playerOwnedHouse.inAHouse){
				List<NPC> wanderingPets =
						client.getNpcs().stream().filter(
								npc -> npc != null && npc.getName() != null && npc.getInteracting() == null
						).collect(Collectors.toList());
				petNamerServerDataManager.populateWanderingPets(playerOwnedHouse.houseOwner, wanderingPets, petNamerPetDataManager);
			}
		}

		if (!config.onlineMode() && client.getGameState() == GameState.LOGGED_IN){
			petNamerPetDataManager.storeNamesToConfig(configManager);
		}
	}

	@Subscribe
	public void onBeforeRender(BeforeRender ignored){
		// Replace pet name if following player
		nameFollower();

		// Replace pet name if in a house
		nameHousePets();


		// Replace pet's chat box name
		// Override a cat's in-game name too so that you can have a name longer than 6 characters
		Widget petChatboxWidget = client.getWidget(WidgetInfo.DIALOG_NPC_NAME);
		if (petChatboxWidget != null && currentFollower != null){
			petChatboxWidget.setText(currentFollower.petName);
		}
	}

	@Subscribe
	public void onCommandExecuted(CommandExecuted commandExecuted){
		String command = commandExecuted.getCommand();
		String[] arguments = commandExecuted.getArguments();
		if (arguments == null) return;
		if (command.equals("renamepet")){
			if (client.getFollower() == null){
				PetNamerUtils.sendHighlightedChatMessage("Drop the pet that you'd like to rename!", chatMessageManager);
				return;
			}
			String newPetName = String.join(" ", arguments);
			newPetName = PetNamerUtils.limitString(newPetName, chatMessageManager);
			int petId = client.getFollower().getId();
			String originalPetName = petNamerPetDataManager.getOriginalName(petId, client.getNpcDefinition(petId).getName());
			if (client.getLocalPlayer().getName() == null) return;
			String username = client.getLocalPlayer().getName().toLowerCase();
			PetNamerPetData newPetNamerPetData = new PetNamerPetData(username, petId, newPetName, originalPetName);

			if (config.onlineMode()){
				petNamerServerDataManager.updatePetName(newPetNamerPetData, petNamerPetDataManager);
			}
			petNamerPetDataManager.updatePlayerPetData(newPetNamerPetData);
			setOverheadText(newPetName);
		}
	}

	@Subscribe
	public void onMenuOpened(MenuOpened menuOpened){
		if (!config.rightClickPets()) return;
		Tile hoveredTile = client.getSelectedSceneTile();
		if (hoveredTile == null) return;
		List<NPC> nearbyNPCs = client.getNpcs();
		for (NPC npc : nearbyNPCs){
			if (npc.getWorldLocation().distanceTo(hoveredTile.getWorldLocation()) == 0){
				if (npc.getComposition().isFollower()){
					if (npc.getInteracting() == null || npc.getInteracting().getName() == null) continue;
					String username = npc.getInteracting().getName();
					String originalPetName = petNamerPetDataManager.getOriginalName(npc.getId(), client.getNpcDefinition(npc.getId()).getName());
					PetNamerPetData existingPetData = petNamerPetDataManager.getPlayerPetName(username, originalPetName);
					MenuEntry newEntry;
					MenuEntry[] currentEntries = menuOpened.getMenuEntries();
					if (existingPetData == null || !config.onlineMode()) {
						newEntry = createPetEntry(username, originalPetName);
					} else {
						newEntry = createPetEntry(username, existingPetData.petName);
					}
					menuOpened.setMenuEntries(ArrayUtils.insert(0, currentEntries, newEntry));
				}
			}
		}
	}

	private void nameFollower(){
		// Replace follower name
		NPC npc = client.getFollower();
		if (npc == null || npc.getInteracting() == null || npc.getInteracting().getName() == null) return;

		String username = npc.getInteracting().getName();
		String originalPetName = petNamerPetDataManager.getOriginalName(npc.getId(), client.getNpcDefinition(npc.getId()).getName());
		PetNamerPetData existingPetData = petNamerPetDataManager.getPlayerPetName(username, originalPetName);

		if (existingPetData != null) {
			NPCComposition existingComposition = npc.getComposition();
			PetNamerUtils.tryReplaceName(existingComposition, npc.getComposition().getName(), existingPetData.petName);
			currentFollower = existingPetData;
		}
	}

	private void nameHousePets(){
		if (playerOwnedHouse.inAHouse){
			List<NPC> nearbyNPCs = client.getNpcs();
			List<NPC> wanderingPets =
					nearbyNPCs.stream()
							.filter(npc -> npc.getInteracting() == null)
							.collect(Collectors.toList());
			for (NPC npc : wanderingPets) {
				// Wandering pets (no interacting) belong to the owner of the house
				String username = playerOwnedHouse.houseOwner;
				String originalPetName = petNamerPetDataManager.getOriginalName(npc.getId(), client.getNpcDefinition(npc.getId()).getName());
				PetNamerPetData existingPetData = petNamerPetDataManager.getPlayerPetName(username, originalPetName);

				if (existingPetData != null) {
					NPCComposition existingComposition = npc.getComposition();
					PetNamerUtils.tryReplaceName(existingComposition, npc.getComposition().getName(), existingPetData.petName);
				}
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
		int secondsToDisplay = 5;
		local.setOverheadCycle(cyclesPerSecond * secondsToDisplay);
	}

	@Provides
	PetNamerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PetNamerConfig.class);
	}
}
