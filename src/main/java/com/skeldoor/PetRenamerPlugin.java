package com.skeldoor;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.widgets.Widget;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import org.apache.commons.lang3.ArrayUtils;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@PluginDescriptor(
	name = "Pet Renamer"
)
public class PetRenamerPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private PetRenamerConfig config;

	@Inject
	private PetNamerDataManager petNamerDataManager;

	@Inject
	private ChatMessageManager chatMessageManager;

	private static final HashMap<String, PetRenamerPetData> playerPetNames = new HashMap<String, PetRenamerPetData>();

	private final int populateInterval = 15; // Seconds
	private final int getPopulateIntervalTicks = Math.round(populateInterval * 0.6f);

	@Subscribe
	public void onGameTick(GameTick gameTick){
		if (client.getGameState() == GameState.LOGGED_IN){
			if (client.getTickCount() == 1 || client.getTickCount() % getPopulateIntervalTicks == 0){
				populatePlayerPets();
			}
		}
//		// Replace follower name
//		NPC follower = client.getFollower();
//		if (follower != null &&
//			!Objects.equals(follower.getName(), config.catName()) &&
//			!Objects.equals(config.catName(), "")){
//			NPCComposition followerComp = follower.getComposition();
//			PetNamerUtils.tryReplaceName(followerComp, follower.getName(), config.catName());
//		}
//
//		// Replace chat box name
//		int MyChatboxText = 14221318;
//		int PetChatboxName = 15138820;
//
//		// Replace cat specific conversation
//		Widget MyChatboxWidget = client.getWidget(MyChatboxText);
//		if (MyChatboxWidget != null && Objects.equals(MyChatboxWidget.getText(), "Hey cat. What's up?")){
//			MyChatboxWidget.setText("Hey " + config.catName() + ". What's up?");
//		}
//
//		// Replace pet's chat box name
//		// Override a cat's in-game name too so that you can have a name longer than 6 characters
//		Widget PetChatboxWidget = client.getWidget(PetChatboxName);
//		if (PetChatboxWidget != null && !Objects.equals(PetChatboxWidget.getText(), config.catName())){
//			PetChatboxWidget.setText(config.catName());
//		}
	}

	@Subscribe
	public void onCommandExecuted(CommandExecuted commandExecuted){
		String command = commandExecuted.getCommand();
		String[] arguments = commandExecuted.getArguments();
		if (arguments == null) return;
		if (command.equals("renamepet")){
			if (client.getFollower() == null){
				sendHighlightedChatMessage("Drop the pet that you'd like to rename!");
				return;
			}
			String newPetName = String.join(" ", arguments);
			String originalPetName = client.getFollower().getName();
			int petId = client.getFollower().getId();
			String username = client.getLocalPlayer().getName().toLowerCase();
			PetRenamerPetData newPetRenamerPetData = new PetRenamerPetData(username, petId, newPetName, originalPetName);
			petNamerDataManager.updatePetName(newPetRenamerPetData);
			updatePlayerPetData(newPetRenamerPetData);
		}
	}

	@Subscribe
	public void onMenuOpened(MenuOpened menuOpened){
		Tile hoveredTile = client.getSelectedSceneTile();
		if (hoveredTile == null) return;
		List<NPC> nearbyNPCs = client.getNpcs();
		for (NPC npc : nearbyNPCs){
			if (npc.getWorldLocation().distanceTo(hoveredTile.getWorldLocation()) == 0){
				if (npc.getComposition().isFollower()){
					MenuEntry[] currentEntries = menuOpened.getMenuEntries();
					if (npc.getInteracting() == null || npc.getInteracting().getName() == null) continue;
					String username = npc.getInteracting().getName();
					String key = createUserPetKey(username, npc.getComposition().getId());
					PetRenamerPetData existingPetData = getPlayerPetName(key);
					System.out.println("playerPetNames size " + playerPetNames.size());
					for (String i : playerPetNames.keySet()){
						System.out.println("key "+ i);
					}
					MenuEntry newEntry;
					if (existingPetData == null) {
						System.out.println("existing data is null");
						newEntry = createPetEntry(username, npc.getComposition().getName());
					} else {
						System.out.println("existing: " + existingPetData.username + " " + existingPetData.petName);
						newEntry = createPetEntry(username, existingPetData.petName);
					}
					System.out.println("Before");
					for (int i = 0; i < menuOpened.getMenuEntries().length; i++){
						System.out.println("Entry "+ i + " is " + menuOpened.getMenuEntries()[i]);
					}
					menuOpened.setMenuEntries(ArrayUtils.insert(0, currentEntries, newEntry));
					System.out.println("After");
					for (int i = 0; i < menuOpened.getMenuEntries().length; i++){
						System.out.println("Entry "+ i + " is " + menuOpened.getMenuEntries()[i]);
					}
				}
			}
		}
	}

	@Provides
	PetRenamerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PetRenamerConfig.class);
	}

	private MenuEntry createPetEntry (String ownerName, String petName){
		MenuEntry entry = client.createMenuEntry(-1);
		entry.setParam0(10);
		entry.setParam1(1);
		entry.setOption(ownerName + "'s");
		entry.setTarget("<col=ffff00>" + petName);
		entry.setDeprioritized(true);
		entry.setType(MenuAction.of(MenuAction.RUNELITE.getId()));
		return entry;
	}

	private void populatePlayerPets() {
		List<NPC> nearbyNPCs = client.getNpcs();
		List<NPC> nearbyPets = nearbyNPCs.stream().filter(npc -> npc.getComposition().isFollower()).collect(Collectors.toList());
		List<NPC> undefinedPetDatas = new ArrayList<>();
		for (NPC pet : nearbyPets) {
			if (pet.getInteracting() == null || pet.getInteracting().getName() == null) continue;
			String username = pet.getInteracting().getName();
			int petId = pet.getId();
			String key = createUserPetKey(username, petId);
			if (getPlayerPetName(key) == null){
				undefinedPetDatas.add(pet);
			}
		}
		petNamerDataManager.populatePlayerPets(undefinedPetDatas);
	}

	void updatePlayerPetData(PetRenamerPetData petData){
		String key = createUserPetKey(petData.username, petData.petId);
		PetRenamerPetData existingPetData = getPlayerPetName(key);
		if (existingPetData == null){
			playerPetNames.put(key, petData);
		} else {
			playerPetNames.replace(key, petData);
		}
	}

	PetRenamerPetData getPlayerPetName(String key){
		return playerPetNames.get(key);
	}

	public String createUserPetKey(String username, int petId){
		return username.toLowerCase() + ":" + petId;
	}

	private void sendHighlightedChatMessage(String message) {
		ChatMessageBuilder msg = new ChatMessageBuilder()
				.append(ChatColorType.HIGHLIGHT)
				.append(message);

		chatMessageManager.queue(QueuedMessage.builder()
				.type(ChatMessageType.CONSOLE)
				.runeLiteFormattedMessage(msg.build())
				.build());
	}

}
