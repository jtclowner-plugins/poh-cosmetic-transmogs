package com.pohcosmetictransmogs;

import com.pohcosmetictransmogs.PohCosmeticTransmogsConfig.AppearanceOption;
import com.pohcosmetictransmogs.PohCosmeticTransmogsConfig.ArmourAppearance;
import com.pohcosmetictransmogs.PohCosmeticTransmogsConfig.ChestAppearance;
import com.pohcosmetictransmogs.PohCosmeticTransmogsConfig.EntranceAppearance;
import com.pohcosmetictransmogs.PohCosmeticTransmogsConfig.StorageAppearance;
import com.pohcosmetictransmogs.PohCosmeticTransmogsConfig.WardrobeAppearance;
import java.util.HashSet;
import java.util.Set;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PohCosmeticTransmogsTest
{
	@BeforeClass
	public static void loadCatalogue()
	{
		Catalogue.current = Catalogue.loadCatalogue(RuneLiteAPI.GSON, Catalogue.openCatalogueReader());
	}

	@Test
	public void recipesHaveValidModelsAndPlacements()
	{
		Set<String> ids = new HashSet<>();
		assertFalse(Catalogue.current.appearances.values().isEmpty());
		for (Catalogue.Recipe recipe : Catalogue.current.appearances.values())
		{
			assertNotNull(recipe.name);
			assertFalse(recipe.name.trim().isEmpty());
			assertTrue(recipe.name, ids.add(recipe.key));
			for (Catalogue.Definition state : new Catalogue.Definition[]
				{recipe, recipe.closed, recipe.open})
			{
				assertTrue(recipe.name, state.getModelIds().length > 0);
				assertTrue(recipe.name, state.getSizeX() > 0 && state.getSizeY() > 0);
				assertTrue(recipe.name, state.getScaleX() > 0
					&& state.getScaleHeight() > 0 && state.getScaleY() > 0);
				assertNotNull(recipe.name, state.recolours);
			}
			for (Catalogue.Calibration fit : recipe.placements.values())
			{
				assertTrue(recipe.name, fit.getScaleX() > 0 && fit.getScaleHeight() > 0 && fit.getScaleY() > 0);
			}
		}
	}

	@Test
	public void selectablePlacementsHaveExplicitCalibrations()
	{
		for (PohTargetSlot furniture : PohTargetSlot.values())
		{
			for (Catalogue.Recipe recipe : Catalogue.current.appearances.values())
			{
				String id = recipe.key;
				if (isAllowed(furniture, id))
				{
					assertNotNull(recipe.key + ":" + furniture.getTargetKey(),
						recipe.placements.get(furniture.getTargetKey()));
				}
			}
		}
	}

	@Test
	public void chestRecipesCanShareModelsWithoutSharingTheirClosedState()
	{
		Catalogue.Recipe crystal = Catalogue.current.appearances.get("cox_crystal_chest");
		Catalogue.Recipe chest = Catalogue.current.appearances.get("cox_ancient_chest");
		assertEquals("CoX Crystal Chest", crystal.name);
		assertEquals("CoX Chest", chest.name);
		assertEquals(32752, crystal.transitionModelId);
		assertEquals(7506, crystal.transitionAnimationId);
		assertEquals(20, crystal.transitionHandoff);
		assertEquals(-1, chest.transitionModelId);
		assertTrue(Catalogue.current.appearances.get("cox_crystal_bomb").bobbing);
		assertFalse(chest.bobbing);
		assertArrayEquals(crystal.open.getModelIds(), chest.getModelIds());
		assertArrayEquals(new int[] {32752, 32755}, crystal.closed.getModelIds());
		assertArrayEquals(new int[] {32755}, chest.closed.getModelIds());
		assertArrayEquals(new int[] {32756}, chest.open.getModelIds());
		assertNotEquals(crystal.placements.get("fancy_dress_box"),
			chest.placements.get("fancy_dress_box"));
	}

	@Test
	public void containerAndPortalCalibrationsMatchCatalogue()
	{
		Catalogue.Calibration portal = calibration(PohTargetSlot.ENTRANCE_PORTAL, "a_kingdom_divided_portal");
		assertEquals(1024, portal.getRotation());
		assertEquals(232, portal.getScaleX());
		assertEquals(84, portal.getOffsetHeight());

		Catalogue.Calibration chest = calibration(PohTargetSlot.TREASURE_CHEST, "cox_crystal_chest");
		assertEquals(512, chest.getRotation());
		Catalogue.Calibration openChest = calibration(PohTargetSlot.TREASURE_CHEST, "cox_ancient_chest");
		assertEquals(chest.getScaleX(), openChest.getScaleX());
		assertEquals(chest.getScaleHeight(), openChest.getScaleHeight());
		assertEquals(chest.getScaleY(), openChest.getScaleY());
		assertEquals(128, chest.getScaleX());
		assertEquals(192, chest.getScaleY());
		assertEquals(16, chest.getOffsetY());

		Catalogue.Calibration sarcophagus = calibration(PohTargetSlot.TREASURE_CHEST, "toa_sarcophagus");
		assertTrue(sarcophagus.isFlipX());
		assertFalse(calibration(PohTargetSlot.TOY_BOX, "toa_sarcophagus").isFlipX());
		assertEquals(-85, sarcophagus.signedScaleX());
		assertEquals(80, sarcophagus.getScaleY());
		assertEquals(
			calibration(PohTargetSlot.TOY_BOX, "cox_thieving_chest").getRotation() & 2047,
			calibration(PohTargetSlot.TREASURE_CHEST, "cox_thieving_chest").getRotation() & 2047);
		assertEquals(calibration(PohTargetSlot.TOY_BOX, "cox_thieving_chest"),
			calibration(PohTargetSlot.TREASURE_CHEST, "cox_thieving_chest"));
		assertNotEquals(calibration(PohTargetSlot.TOY_BOX, "toa_sarcophagus"), sarcophagus);
		assertNotNull(definition("justiciar_armour"));
		assertArrayEquals(new int[] {35412}, definition("justiciar_armour").getModelIds());
		assertNull(definition("missing_33013"));
		assertEquals(1024, calibration(PohTargetSlot.ARMOUR_CASE, "justiciar_armour").getRotation());
	}

	@Test
	public void dropdownAllowlistsMatchCataloguePlacements()
	{
		assertEquals(34, EntranceAppearance.values().length - 1);
		assertEquals(17, StorageAppearance.values().length - 1);
		assertEquals(19, ArmourAppearance.values().length - 1);
		assertEquals(18, ChestAppearance.values().length - 1);
		assertEquals(15, WardrobeAppearance.values().length - 1);

		assertTrue(isAllowed(PohTargetSlot.TREASURE_CHEST, "tob_monumental_chest"));
		assertFalse(isAllowed(PohTargetSlot.CAPE_RACK, "tob_monumental_chest"));
		assertFalse(isAllowed(PohTargetSlot.ARMOUR_CASE, "deadman_supply_chest"));
		assertFalse(isAllowed(PohTargetSlot.MAGIC_WARDROBE, "clan_hall_portal"));
		assertFalse(isAllowed(PohTargetSlot.MAGIC_WARDROBE, "toa_sarcophagus"));
		assertTrue(isAllowed(PohTargetSlot.MAGIC_WARDROBE, "golem_portal"));
		for (String id : new String[] {"crystal_outcrop"})
		{
			assertTrue(isAllowed(PohTargetSlot.CAPE_RACK, id));
			assertTrue(isAllowed(PohTargetSlot.ARMOUR_CASE, id));
			assertTrue(isAllowed(PohTargetSlot.TOY_BOX, id));
			assertTrue(isAllowed(PohTargetSlot.TREASURE_CHEST, id));
			assertTrue(isAllowed(PohTargetSlot.FANCY_DRESS_BOX, id));
			assertFalse(isAllowed(PohTargetSlot.MAGIC_WARDROBE, id));
			assertFalse(isAllowed(PohTargetSlot.ENTRANCE_PORTAL, id));
		}
		assertTrue(isAllowed(PohTargetSlot.CAPE_RACK, "tob_teleport_crystal"));
		assertFalse(isAllowed(PohTargetSlot.ARMOUR_CASE, "tob_teleport_crystal"));
		assertFalse(isAllowed(PohTargetSlot.TOY_BOX, "tob_teleport_crystal"));
		assertFalse(isAllowed(PohTargetSlot.TREASURE_CHEST, "tob_teleport_crystal"));
		assertFalse(isAllowed(PohTargetSlot.FANCY_DRESS_BOX, "tob_teleport_crystal"));
	}

	@Test
	public void everyDropdownChoiceHasACompiledRecipe()
	{
		assertRecipes(EntranceAppearance.values());
		assertRecipes(StorageAppearance.values());
		assertRecipes(ArmourAppearance.values());
		assertRecipes(ChestAppearance.values());
		assertRecipes(WardrobeAppearance.values());
	}

	@Test
	public void nodeAndCoxRecolourPalettesAreExplicit()
	{
		assertArrayEquals(new short[] {652, 908, 916, 920, 926},
			definition("castle_drakan_portal").colours(Catalogue.ColourChannel.PORTAL));
		assertArrayEquals(new short[] {(short) 38040, (short) 38053, (short) 38309, (short) 38315},
			definition("node_portal").colours(Catalogue.ColourChannel.NODE_TRIM));
		assertEquals(11, definition("node_portal").colours(Catalogue.ColourChannel.NODE_BODY).length);
		assertEquals(4, definition("cox_large_crystal").colours(Catalogue.ColourChannel.CRYSTALS).length);
		assertEquals(4, definition("colourless_crystal").colours(Catalogue.ColourChannel.CRYSTALS).length);
		assertEquals(3, definition("cox_crystal_bomb").colours(Catalogue.ColourChannel.CRYSTALS).length);
		assertEquals(2, definition("cox_crystal_chest").colours(Catalogue.ColourChannel.CRYSTALS).length);
		short[] gauntletChestColours = {
			(short) 32916, (short) 32922, (short) 32926, (short) 32200,
			(short) 29518, (short) 29526, (short) 31192,
			(short) 26776};
		assertArrayEquals(gauntletChestColours,
			definition("gauntlet_reward_chest").colours(Catalogue.ColourChannel.GAUNTLET_CHEST));
		assertArrayEquals(gauntletChestColours,
			definition("gauntlet_reward_chest:open").colours(Catalogue.ColourChannel.GAUNTLET_CHEST));
		assertArrayEquals(new short[] {(short) 55219},
			definition("tob_monumental_chest").colours(Catalogue.ColourChannel.TOB_CHEST));
		assertArrayEquals(definition("tob_monumental_chest").colours(Catalogue.ColourChannel.TOB_CHEST),
			definition("tob_monumental_chest:open").colours(Catalogue.ColourChannel.TOB_CHEST));
		assertArrayEquals(new short[] {(short) 54177},
			definition("crystal_outcrop").colours(Catalogue.ColourChannel.CRYSTALS));
		assertArrayEquals(new short[] {
			(short) 53582, (short) 52403, (short) 52407, (short) 52416,
			(short) 52424, (short) 51515, (short) 51484},
			definition("tob_teleport_crystal").colours(Catalogue.ColourChannel.CRYSTALS));
		assertArrayEquals(new short[] {(short) 960, (short) 794, (short) 914},
			definition("deadman_supply_chest").colours(Catalogue.ColourChannel.DEADMAN_CHEST));
		assertArrayEquals(definition("deadman_supply_chest").colours(Catalogue.ColourChannel.DEADMAN_CHEST),
			definition("deadman_supply_chest:open").colours(Catalogue.ColourChannel.DEADMAN_CHEST));
		assertEquals(23, definition("toa_sarcophagus").colours(Catalogue.ColourChannel.TOA_CONTAINERS).length);
		short[] toaChestColours = definition("toa_chest_1").colours(Catalogue.ColourChannel.TOA_CONTAINERS);
		assertEquals(17, toaChestColours.length);
		assertArrayEquals(toaChestColours,
			definition("toa_chest_1:open").colours(Catalogue.ColourChannel.TOA_CONTAINERS));
		assertArrayEquals(toaChestColours,
			definition("toa_chest_2").colours(Catalogue.ColourChannel.TOA_CONTAINERS));
		assertArrayEquals(toaChestColours,
			definition("toa_chest_2:open").colours(Catalogue.ColourChannel.TOA_CONTAINERS));
		for (int chestColour : new int[] {6315, 6348, 6592, 6674, 6817, 6819,
			6823, 6825, 6827, 6833, 6837, 6839, 6848, 6856, 6864, 6868, 6872})
		{
			assertTrue(contains(toaChestColours, (short) chestColour));
		}
		for (int coinColour : new int[] {7384, 7690, 7349, 7343, 7506, 7500,
			7492, 8123, 7616, 5943, 6986})
		{
			assertFalse(contains(definition("toa_sarcophagus").colours(Catalogue.ColourChannel.TOA_CONTAINERS),
				(short) coinColour));
			assertFalse(contains(toaChestColours, (short) coinColour));
		}
		for (int retainedColour : new int[] {5281, 5293, 6336, 6379, 6976})
		{
			assertFalse(contains(definition("toa_sarcophagus").colours(Catalogue.ColourChannel.TOA_CONTAINERS),
				(short) retainedColour));
		}
		assertEquals(14, PohCosmeticTransmogsConfig.AppearanceColour.GREEN.getHue());
	}

	@Test
	public void crystalModelsUseCalibratedTransforms()
	{
		assertArrayEquals(new int[] {4895}, definition("crystal_outcrop").getModelIds());
		assertEquals("Crystal Outcrop", Catalogue.name("crystal_outcrop"));
		assertNull(definition("missing_4927"));
		for (PohTargetSlot slot : PohTargetSlot.values())
		{
			assertFalse(isAllowed(slot, "missing_4927"));
		}
		assertArrayEquals(new int[] {35449}, definition("tob_teleport_crystal").getModelIds());
		assertEquals(160, calibration(PohTargetSlot.TREASURE_CHEST, "crystal_outcrop").getScaleX());
		assertEquals(96, calibration(PohTargetSlot.TREASURE_CHEST, "crystal_outcrop").getScaleY());
		assertEquals(calibration(PohTargetSlot.TREASURE_CHEST, "crystal_outcrop"),
			calibration(PohTargetSlot.TOY_BOX, "crystal_outcrop"));

		Catalogue.Calibration crystal =
			calibration(PohTargetSlot.CAPE_RACK, "tob_teleport_crystal");
		assertEquals(128, crystal.getScaleX());
		assertEquals(128, crystal.getScaleHeight());
		assertEquals(128, crystal.getScaleY());

		assertEquals(192, calibration(PohTargetSlot.ARMOUR_CASE, "cox_crystal_bomb").getScaleX());
		assertEquals(192, calibration(PohTargetSlot.CAPE_RACK, "cox_crystal_bomb").getScaleX());
		assertEquals(180, calibration(PohTargetSlot.ARMOUR_CASE, "cox_crystal_bomb").getScaleHeight());
		assertEquals(-4, calibration(PohTargetSlot.ARMOUR_CASE, "cox_crystal_bomb").getOffsetHeight());
		assertEquals(180, calibration(PohTargetSlot.CAPE_RACK, "cox_crystal_bomb").getScaleHeight());
		assertEquals(336, calibration(PohTargetSlot.TOY_BOX, "cox_crystal_bomb").getScaleX());
		assertEquals(210, calibration(PohTargetSlot.TOY_BOX, "cox_crystal_bomb").getScaleHeight());
		assertEquals(-4, calibration(PohTargetSlot.TOY_BOX, "cox_crystal_bomb").getOffsetHeight());
		assertEquals(336, calibration(PohTargetSlot.TREASURE_CHEST, "cox_crystal_bomb").getScaleX());
		assertEquals(210, calibration(PohTargetSlot.TREASURE_CHEST, "cox_crystal_bomb").getScaleHeight());
		assertEquals(384, calibration(PohTargetSlot.FANCY_DRESS_BOX, "cox_crystal_bomb").getScaleX());
		assertEquals(240, calibration(PohTargetSlot.FANCY_DRESS_BOX, "cox_crystal_bomb").getScaleHeight());

		Catalogue.Calibration tob =
			calibration(PohTargetSlot.FANCY_DRESS_BOX, "tob_monumental_chest");
		assertEquals(100, tob.getScaleX());
		assertEquals(100, tob.getScaleHeight());
		assertEquals(100, tob.getScaleY());
	}

	@Test
	public void openStatesUseTheirPairedModels()
	{
		assertArrayEquals(new int[] {42281}, stateDefinition("tob_monumental_chest", true).getModelIds());
		assertArrayEquals(new int[] {47946}, stateDefinition("cox_bank_chest", true).getModelIds());
		assertArrayEquals(new int[] {32465}, stateDefinition("cox_thieving_chest", true).getModelIds());
		assertArrayEquals(new int[] {46275}, stateDefinition("toa_chest_1", true).getModelIds());
		assertArrayEquals(new int[] {46274}, stateDefinition("toa_chest_2", true).getModelIds());
		assertArrayEquals(new int[] {37450}, stateDefinition("gauntlet_reward_chest", true).getModelIds());
		assertArrayEquals(new int[] {34265}, stateDefinition("deadman_supply_chest", true).getModelIds());
		assertArrayEquals(new int[] {46285}, stateDefinition("toa_sarcophagus", true).getModelIds());
		assertArrayEquals(new int[] {32756}, stateDefinition("cox_crystal_chest", true).getModelIds());
		assertNotEquals(stateDefinition("cox_ancient_chest", false), stateDefinition("cox_ancient_chest", true));
		assertArrayEquals(new int[] {32755},
			stateDefinition("cox_ancient_chest", false).getModelIds());
		assertArrayEquals(new int[] {32756},
			stateDefinition("cox_ancient_chest", true).getModelIds());
		assertEquals(-512, calibration(PohTargetSlot.FANCY_DRESS_BOX, "cox_crystal_chest").getRotation());
		assertEquals(512, calibration(PohTargetSlot.FANCY_DRESS_BOX, "cox_ancient_chest").getRotation());
		assertEquals(9505, definition("toa_sarcophagus:open").getSpawnAnimationId());
		assertEquals(14167, definition("castle_drakan_portal").getSpawnAnimationId());
		assertEquals(7823, definition("deadman_supply_chest").getSpawnAnimationId());
		assertTrue(definition("deadman_supply_chest").isSpawnOnce());
		assertNotEquals(stateDefinition("deadman_supply_chest", false), stateDefinition("deadman_supply_chest", true));
		assertEquals(stateDefinition("cox_crystal_bomb", false), stateDefinition("cox_crystal_bomb", true));
		assertEquals(-1, calibration(PohTargetSlot.CAPE_RACK, "deadman_supply_chest").getOffsetHeight());
		assertEquals(-1, calibration(PohTargetSlot.FANCY_DRESS_BOX, "deadman_supply_chest").getOffsetHeight());
		assertEquals(-1, calibration(PohTargetSlot.TREASURE_CHEST, "deadman_supply_chest").getOffsetHeight());
	}

	@Test
	public void targetFamiliesContainEveryConstructedVariant()
	{
		assertEquals(2, target(PohTargetSlot.ENTRANCE_PORTAL).objectIds.length);
		assertEquals(6, target(PohTargetSlot.CAPE_RACK).objectIds.length);
		assertEquals(6, target(PohTargetSlot.FANCY_DRESS_BOX).objectIds.length);
		assertEquals(6, target(PohTargetSlot.ARMOUR_CASE).objectIds.length);
		assertEquals(14, target(PohTargetSlot.MAGIC_WARDROBE).objectIds.length);
		assertEquals(6, target(PohTargetSlot.TOY_BOX).objectIds.length);
		assertEquals(6, target(PohTargetSlot.TREASURE_CHEST).objectIds.length);
	}

	@Test
	public void renderingHelpersRemainBounded()
	{
		assertTrue(PohCosmeticTransmogsManager.isVisibleLevel(0, 0));
		assertTrue(PohCosmeticTransmogsManager.isVisibleLevel(1, 1));
		assertTrue(PohCosmeticTransmogsManager.isVisibleLevel(2, 2));
		assertTrue(PohCosmeticTransmogsManager.isVisibleLevel(1, 2));
		assertFalse(PohCosmeticTransmogsManager.isVisibleLevel(1, 0));
		assertFalse(PohCosmeticTransmogsManager.isVisibleLevel(2, 0));
		assertFalse(PohCosmeticTransmogsManager.isVisibleLevel(0, 1));
		assertFalse(PohCosmeticTransmogsManager.isVisibleLevel(2, 1));
	}

	private static Catalogue.Calibration calibration(PohTargetSlot furniture, String objectId)
	{
		Catalogue.Definition definition = definition(objectId);
		assertNotNull(definition);
		return Catalogue.ModelFactory.calibration(Catalogue.current.appearances.get(objectId),
			target(furniture));
	}

	private static <T extends Enum<T> & AppearanceOption> void assertRecipes(T[] appearances)
	{
		for (T appearance : appearances)
		{
			if (!appearance.getAppearanceKey().isEmpty())
			{
				Catalogue.Definition definition =
					Catalogue.current.appearances.get(appearance.getAppearanceKey());
				assertNotNull(appearance.toString(), definition);
				assertTrue(appearance.toString(), definition.getModelIds().length > 0);
			}
		}
	}

	private static boolean contains(short[] values, short value)
	{
		for (short candidate : values)
		{
			if (candidate == value)
			{
				return true;
			}
		}
		return false;
	}
	private static TargetSpec target(PohTargetSlot slot)
	{
		return Catalogue.current.targets.get(slot.getTargetKey());
	}

	private static boolean isAllowed(PohTargetSlot slot, String key)
	{
		return ((AppearanceOption) slot.option(key)).getAppearanceKey().equals(key);
	}

	private static Catalogue.Definition definition(String key)
	{
		String[] parts = key.split(":");
		Catalogue.Recipe recipe = Catalogue.current.appearances.get(parts[0]);
		return recipe == null ? null : parts.length == 1 ? recipe : recipe.state(parts[1].equals("open"));
	}

	private static Catalogue.Definition stateDefinition(String key, boolean opened)
	{
		return Catalogue.current.appearances.get(key).state(opened);
	}
}
