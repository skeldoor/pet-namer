package com.example;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import java.lang.reflect.Field;
import java.util.Objects;

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

	@Override
	protected void startUp() throws Exception
	{
		log.info("Example started!");
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("Example stopped!");
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() == GameState.LOGGED_IN) {
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Example says " + config.catName(), null);
		}
	}

	@Subscribe
	public void onGameTick(GameTick gameTick){
		// Replace follower name
		NPC follower = client.getFollower();
		if (follower != null &&
			!Objects.equals(follower.getName(), config.catName()) &&
			!Objects.equals(config.catName(), "")){
			NPCComposition followerComp = follower.getComposition();
			tryReplaceName(followerComp, follower.getName(), config.catName());
		}

		// Replace chatbox name


	}

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
