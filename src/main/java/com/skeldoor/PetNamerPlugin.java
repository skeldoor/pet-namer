package com.skeldoor;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.*;
import net.runelite.api.geometry.RectangleUnion;
import net.runelite.api.geometry.Shapes;
import net.runelite.api.geometry.SimplePolygon;
import net.runelite.api.model.Jarvis;
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

import static net.runelite.api.Perspective.COSINE;
import static net.runelite.api.Perspective.SINE;

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
			String username = client.getLocalPlayer().getName();
			String lowerUsername = username.toLowerCase();
			PetNamerPetData newPetNamerPetData = new PetNamerPetData(lowerUsername, username, petId, newPetName, originalPetName);

			if (config.onlineMode()){
				petNamerServerDataManager.updatePetName(newPetNamerPetData, petNamerPetDataManager);
			}
			petNamerPetDataManager.updatePlayerPetData(newPetNamerPetData);
			setOverheadText(newPetName);
		}
	}

	@Subscribe
	public void onBeforeRender(BeforeRender ignored){
		// Change the hover text of pets that are hoverable. my pets, others pets in their house
		MenuEntry[] menuEntries = client.getMenuEntries();
		if (menuEntries.length == 0) return;
		MenuEntry firstEntry = menuEntries[menuEntries.length - 1];

		NPC entryNPC = firstEntry.getNpc();

		if (entryNPC != null){
			String username;
			String lowerUsername;
			if (entryNPC.getInteracting() == null && playerOwnedHouse.inAHouse) { // Wandering pet in my house
				username = playerOwnedHouse.houseOwner;
				lowerUsername = username;
			} else if (entryNPC.getInteracting() != null) { // Pet following me
				username = entryNPC.getInteracting().getName();
				lowerUsername = username.toLowerCase();
			} else { // Unknown pet
				return; // investigate
			}
			PetNamerPetData petData = petNamerPetDataManager.getPetData(username, lowerUsername, entryNPC.getName());
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
		if (!config.rightClickPets()) return; // TODO Move this to posiiton that will stop other's pets from being right clickable but not my own
		List<NPC> nearbyNPCs = client.getNpcs();
		for (NPC npc : nearbyNPCs){
			LocalPoint lp = npc.getLocalLocation();
			Shape clickbox = Perspective.getClickbox(client, npc.getModel(), npc.getCurrentOrientation(), lp.getX(), lp.getY(),	Perspective.getTileHeight(client, lp, npc.getWorldLocation().getPlane()));
			if (clickbox != null && clickbox.contains(client.getMouseCanvasPosition().getX(), client.getMouseCanvasPosition().getY())) {
				String username;
				if (npc.getComposition().isFollower() && npc.getInteracting() != null){
					// Pets following players
					username = npc.getInteracting().getName();
				} else if (!npc.getComposition().isFollower() && playerOwnedHouse.inAHouse){
					// Pets in peoples houses arent followers so we assume they're owned by the house owner
					username = playerOwnedHouse.houseOwner;
				} else {
					// If it's not a follower and we're not in someones house, it's probably just any other NPC
					continue;
				}
				PetNamerPetData petData = petNamerPetDataManager.getPetData(username, npc.getName());
				MenuEntry[] currentEntries = menuOpened.getMenuEntries();

				for (MenuEntry entry : currentEntries){
					if (entry.getTarget().contains(Objects.requireNonNull(npc.getName()))) {
						entry.setTarget("<col=ffff00>" + petData.petName);
					}
				}

				MenuEntry newEntry;
				newEntry = createPetEntry(username, petData.petName); // TODO only create the menu if the petdata that was returned was valid, i.e the "pet" we have found isn't another random house npc
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

	public static Shape getClickbox(Client client, Model model, int orientation, int x, int y, int z)
	{
		if (model == null)
		{
			return null;
		}

		SimplePolygon bounds = calculateAABB(client, model, orientation, x, y, z);

		if (bounds == null)
		{
			return null;
		}

		if (model.isClickable())
		{
			return bounds;
		}

		Shapes<SimplePolygon> bounds2d = calculate2DBounds(client, model, orientation, x, y, z);
		if (bounds2d == null)
		{
			return null;
		}

		for (SimplePolygon poly : bounds2d.getShapes())
		{
			poly.intersectWithConvex(bounds);
		}

		return bounds2d;
	}

	private static SimplePolygon calculateAABB(Client client, Model m, int jauOrient, int x, int y, int z)
	{
		AABB aabb = m.getAABB(jauOrient);

		int x1 = aabb.getCenterX();
		int y1 = aabb.getCenterZ();
		int z1 = aabb.getCenterY();

		int ex = aabb.getExtremeX();
		int ey = aabb.getExtremeZ();
		int ez = aabb.getExtremeY();

		int x2 = x1 + ex;
		int y2 = y1 + ey;
		int z2 = z1 + ez;

		x1 -= ex;
		y1 -= ey;
		z1 -= ez;

		int[] xa = new int[]{
				x1, x2, x1, x2,
				x1, x2, x1, x2
		};
		int[] ya = new int[]{
				y1, y1, y2, y2,
				y1, y1, y2, y2
		};
		int[] za = new int[]{
				z1, z1, z1, z1,
				z2, z2, z2, z2
		};

		int[] x2d = new int[8];
		int[] y2d = new int[8];

		modelToCanvasCpu(client, 8, x, y, z, 0, xa, ya, za, x2d, y2d);

		return Jarvis.convexHull(x2d, y2d);
	}

	private static Shapes<SimplePolygon> calculate2DBounds(Client client, Model m, int jauOrient, int x, int y, int z)
	{
		int[] x2d = new int[m.getVerticesCount()];
		int[] y2d = new int[m.getVerticesCount()];
		final int[] faceColors3 = m.getFaceColors3();

		modelToCanvasCpu(client,
				m.getVerticesCount(),
				x, y, z,
				jauOrient,
				m.getVerticesX(), m.getVerticesZ(), m.getVerticesY(),
				x2d, y2d);

		final int radius = 5;

		int[][] tris = new int[][]{
				m.getFaceIndices1(),
				m.getFaceIndices2(),
				m.getFaceIndices3()
		};

		int vpX1 = client.getViewportXOffset();
		int vpY1 = client.getViewportXOffset();
		int vpX2 = vpX1 + client.getViewportWidth();
		int vpY2 = vpY1 + client.getViewportHeight();

		List<RectangleUnion.Rectangle> rects = new ArrayList<>(m.getFaceCount());

		nextTri:
		for (int tri = 0; tri < m.getFaceCount(); tri++)
		{
			if (faceColors3[tri] == -2)
			{
				continue;
			}

			int
					minX = Integer.MAX_VALUE,
					minY = Integer.MAX_VALUE,
					maxX = Integer.MIN_VALUE,
					maxY = Integer.MIN_VALUE;

			for (int[] vertex : tris)
			{
				final int idx = vertex[tri];
				final int xs = x2d[idx];
				final int ys = y2d[idx];

				if (xs == Integer.MIN_VALUE || ys == Integer.MIN_VALUE)
				{
					continue nextTri;
				}

				if (xs < minX)
				{
					minX = xs;
				}
				if (xs > maxX)
				{
					maxX = xs;
				}
				if (ys < minY)
				{
					minY = ys;
				}
				if (ys > maxY)
				{
					maxY = ys;
				}
			}

			minX -= radius;
			minY -= radius;
			maxX += radius;
			maxY += radius;

			if (vpX1 > maxX || vpX2 < minX || vpY1 > maxY || vpY2 < minY)
			{
				continue;
			}

			RectangleUnion.Rectangle r = new RectangleUnion.Rectangle(minX, minY, maxX, maxY);

			rects.add(r);
		}

		return RectangleUnion.union(rects);
	}


	private static void modelToCanvasCpu(Client client, int end, int x3dCenter, int y3dCenter, int z3dCenter, int rotate, int[] x3d, int[] y3d, int[] z3d, int[] x2d, int[] y2d)
	{
		final int
				cameraPitch = client.getCameraPitch(),
				cameraYaw = client.getCameraYaw(),

				pitchSin = SINE[cameraPitch],
				pitchCos = COSINE[cameraPitch],
				yawSin = SINE[cameraYaw],
				yawCos = COSINE[cameraYaw],
				rotateSin = SINE[rotate],
				rotateCos = COSINE[rotate],

				cx = x3dCenter - client.getCameraX(),
				cy = y3dCenter - client.getCameraY(),
				cz = z3dCenter - client.getCameraZ(),

				viewportXMiddle = client.getViewportWidth() / 2,
				viewportYMiddle = client.getViewportHeight() / 2,
				viewportXOffset = client.getViewportXOffset(),
				viewportYOffset = client.getViewportYOffset(),

				zoom3d = client.getScale();

		for (int i = 0; i < end; i++)
		{
			int x = x3d[i];
			int y = y3d[i];
			int z = z3d[i];

			if (rotate != 0)
			{
				int x0 = x;
				x = x0 * rotateCos + y * rotateSin >> 16;
				y = y * rotateCos - x0 * rotateSin >> 16;
			}

			x += cx;
			y += cy;
			z += cz;

			final int
					x1 = x * yawCos + y * yawSin >> 16,
					y1 = y * yawCos - x * yawSin >> 16,
					y2 = z * pitchCos - y1 * pitchSin >> 16,
					z1 = y1 * pitchCos + z * pitchSin >> 16;

			int viewX, viewY;

			if (z1 < 50)
			{
				viewX = Integer.MIN_VALUE;
				viewY = Integer.MIN_VALUE;
			}
			else
			{
				viewX = (viewportXMiddle + x1 * zoom3d / z1) + viewportXOffset;
				viewY = (viewportYMiddle + y2 * zoom3d / z1) + viewportYOffset;
			}

			x2d[i] = viewX;
			y2d[i] = viewY;
		}
	}

	@Provides
	PetNamerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PetNamerConfig.class);
	}


}
