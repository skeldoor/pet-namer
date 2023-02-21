package com.example;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.kit.KitType;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.IntStream;

@Slf4j
@PluginDescriptor(
	name = "Example"
)
public class ExamplePlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ExampleConfig config;

	// Bronze Iron Steel Black Mithril Adamant Rune White Dragon Gilded
	int[] rune2hItemId = {1307, 1309, 1311, 1313, 1315, 1317, 1319, 6609, 7158, 20155};

	int superCool2hAnimationIdIdle = 7053;
	int superCool2hAnimationIdIdleRotateLeft = 7044;
	int superCool2hAnimationIdIdleRotateRight = 7044;

	int superCool2hAnimationIdWalking = 7052;
	int superCool2hAnimationIdWalkingRotateLeft = 7048;
	int superCool2hAnimationIdWalkingRotateRight = 4047;

	int superCool2hAnimationIdRunning = 7043;

	@Subscribe(priority = -1000.0f)
	public void onClientTick(ClientTick e)
	{
		Player local = client.getLocalPlayer();
		if (client.getGameState() != GameState.LOGGED_IN) return;
		if (local == null) return;
		PlayerComposition localComp = local.getPlayerComposition();
		if (localComp == null) return;
		int weaponId = localComp.getEquipmentId(KitType.WEAPON);
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "weaponId says " + weaponId, null);
		if (IntStream.of(rune2hItemId).anyMatch(x -> x == weaponId)){
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "weaponId says yes", null);
			swapPlayerAnimation(local);
		} else{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "weaponId says no", null);
		}
	}

//	@Subscribe
//	public void onGameTick(GameTick gameTick){
//		// Replace follower name
//		NPC follower = client.getFollower();
//		if (follower != null &&
//			!Objects.equals(follower.getName(), config.catName()) &&
//			!Objects.equals(config.catName(), "")){
//			NPCComposition followerComp = follower.getComposition();
//			tryReplaceName(followerComp, follower.getName(), config.catName());
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
//	}
//
//	String getPetName(int petId){
//
//		return null;
//	}

	@Provides
	ExampleConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ExampleConfig.class);
	}




	public void tryReplaceName(NPCComposition parent, String find, String replace)
	{
		try {
			String memoryFieldName = getFieldName(parent, find);

			if (memoryFieldName == null) {
				System.out.println("Found memoryFieldName null" );
				log.error("Failed to lookup object name, can't replace field. Is your find object inside the parent?");
				return;
			} else {
				System.out.println("Found memoryFieldName" + memoryFieldName);
			}

			Field field = parent.getClass().getDeclaredField(memoryFieldName);
			field.setAccessible(true);
			field.set(parent, replace);

		} catch (NoSuchFieldException | IllegalAccessException e) {
			e.printStackTrace();
		}
	}


	public String getFieldName(Object parent, Object find)
	{
		for (Field field : parent.getClass().getDeclaredFields())
		{
			field.setAccessible(true);

			try {
				if (Objects.isNull(field.get(parent))){
					continue;
				}
				if (field.get(parent).equals(find)) {
					return field.getName();
				}
			} catch (IllegalAccessException e) {
				return null;
			}
		}

		return null;
	}


}
