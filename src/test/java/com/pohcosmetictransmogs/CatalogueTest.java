package com.pohcosmetictransmogs;

import java.io.StringReader;
import java.util.Collections;
import java.util.Map;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.Test;

import static org.junit.Assert.*;

public class CatalogueTest
{
	@Test
	public void placementsInheritTransformsAndExpandGroupedTargets()
	{
		Catalogue catalogue = read("{\"appearances\":{\"gem\":{\"modelIds\":[10],\"scale\":200,"
			+ "\"rotation\":512,\"offsetY\":16,\"flipX\":true,"
			+ "\"placements\":{\"a,b\":{\"scaleX\":250},\"c\":{\"scale\":100,\"scaleHeight\":150,\"flipX\":false}}}}}");
		Catalogue.Recipe gem = catalogue.appearances.get("gem");
		assertEquals(3, gem.placements.size());
		assertSame(gem.placements.get("a"), gem.placements.get("b"));
		Catalogue.Calibration a = gem.placements.get("a");
		assertEquals(250, a.scaleX);
		assertEquals(200, a.scaleY);
		assertEquals(200, a.scaleHeight);
		assertEquals(512, a.rotation);
		assertEquals(16, a.offsetY);
		assertTrue(a.flipX);
		Catalogue.Calibration c = gem.placements.get("c");
		assertEquals(100, c.scaleX);
		assertEquals(100, c.scaleY);
		assertEquals(150, c.scaleHeight);
		assertFalse(c.flipX);
		assertEquals(200, gem.scaleX);
	}

	@Test
	public void statesInheritSharedModelsAndColoursAndCanExplicitlyResetThem()
	{
		Catalogue catalogue = read("{\"appearances\":{\"gem\":{\"modelIds\":[10],\"scale\":180,"
			+ "\"sizeX\":3,\"sizeY\":2,\"animationId\":99,\"spawnAnimationId\":100,\"spawnOnce\":true,"
			+ "\"recolours\":{\"127\":730},\"colours\":{\"CRYSTALS\":[730]},"
			+ "\"closed\":{\"animationId\":98},"
			+ "\"open\":{\"modelIds\":[11],\"animationId\":-1,\"spawnAnimationId\":-1,"
			+ "\"spawnOnce\":false,\"recolours\":{},\"colours\":{}}}}}");
		Catalogue.Recipe gem = catalogue.appearances.get("gem");
		assertArrayEquals(new int[] {10}, gem.closed.modelIds);
		assertEquals(gem.recolours, gem.closed.recolours);
		assertArrayEquals(new short[] {730}, gem.closed.colours(Catalogue.ColourChannel.CRYSTALS));
		assertEquals(98, gem.closed.animationId);
		assertEquals(-1, gem.open.animationId);
		assertEquals(-1, gem.open.spawnAnimationId);
		assertFalse(gem.open.spawnOnce);
		assertEquals(3, gem.open.sizeX);
		assertEquals(2, gem.open.sizeY);
		assertEquals(180, gem.open.scaleX);
		assertTrue(gem.open.recolours.isEmpty());
		assertTrue(gem.open.colours.isEmpty());
		assertEquals(1, gem.recolours.size());
	}

	@Test
	public void fixedRecoloursAndConfigChannelsHaveIndependentEffects()
	{
		Catalogue catalogue = read("{\"appearances\":{\"gem\":{\"modelIds\":[10],"
			+ "\"recolours\":{\"127\":730},\"colours\":{\"PORTAL\":[730]}}}}");
		Catalogue.Recipe gem = catalogue.appearances.get("gem");
		assertEquals(Short.valueOf((short) 730), gem.recolours.get((short) 127));
		assertArrayEquals(new short[] {730}, gem.colours(Catalogue.ColourChannel.PORTAL));
		assertEquals(0, gem.colours(Catalogue.ColourChannel.CRYSTALS).length);
	}

	@Test
	public void footprintAndMeshScaleHaveIndependentDefaults()
	{
		Catalogue.Definition definition = new Catalogue.Definition();
		definition.sizeX = 6;
		definition.sizeY = 8;
		definition.scaleX = 200;
		definition.normalize();
		assertEquals(6, definition.sizeX);
		assertEquals(8, definition.sizeY);
		assertEquals(200, definition.scaleX);
		assertEquals(128, definition.scaleY);
		assertEquals(128, definition.scaleHeight);
		Catalogue.Definition defaults = new Catalogue.Definition();
		defaults.normalize();
		assertEquals(1, defaults.sizeX);
		assertEquals(1, defaults.sizeY);
	}

	@Test
	public void readersAccumulateEntriesInOrder()
	{
		Catalogue catalogue = read(
			"{\"targets\":{\"box\":{\"objectIds\":[1,2],\"openObjectIds\":[2]}}}",
			"{\"appearances\":{\"gem\":{\"modelIds\":[10],\"bindTargets\":[\"box\"]}}}",
			"{\"appearances\":{\"gem\":{\"name\":\"Gem\",\"modelIds\":[11],\"bindTargets\":[\"box\"]}}}");
		assertEquals(1, catalogue.targets.size());
		assertEquals(1, catalogue.appearances.size());
		assertArrayEquals(new int[] {11}, catalogue.appearances.get("gem").modelIds);
		Map<Integer, TargetBinding> bindings = catalogue.bind(Collections.emptyMap());
		assertSame(bindings.get(1), bindings.get(2));
		assertTrue(bindings.get(1).target.isStateful());
	}

	@Test
	public void placementsDoNotCreateBindings()
	{
		Catalogue catalogue = read("{\"targets\":{\"box\":{\"objectIds\":[1]}},"
			+ "\"appearances\":{\"gem\":{\"modelIds\":[10],\"placements\":{\"box\":{\"offsetY\":12}}}}}");
		assertTrue(catalogue.bind(Collections.emptyMap()).isEmpty());
		TargetBinding binding = catalogue.bind(Map.of("box", "gem")).get(1);
		assertEquals(12, binding.calibration().offsetY);
	}

	@Test
	public void automaticBindingsUseTheSameTypeAsSelectionFallbacks()
	{
		Catalogue catalogue = read("{\"targets\":{\"a\":{\"objectIds\":[1,2]},"
			+ "\"b\":{\"objectIds\":[3]},\"c\":{\"objectIds\":[4]}},\"appearances\":{"
			+ "\"gem\":{\"modelIds\":[10],\"bindTargets\":[\"a\",\"b\"],"
			+ "\"placements\":{\"a\":{\"scaleX\":200},\"b\":{\"scaleX\":300}}},"
			+ "\"other\":{\"modelIds\":[20]}}}");
		Map<Integer, TargetBinding> bindings = catalogue.bind(Map.of("a", "other", "c", "other"));
		assertEquals("gem", bindings.get(1).appearance.key);
		assertSame(bindings.get(1), bindings.get(2));
		assertSame(bindings.get(1).appearance, bindings.get(3).appearance);
		assertEquals(200, bindings.get(1).calibration().scaleX);
		assertEquals(300, bindings.get(3).calibration().scaleX);
		assertEquals("other", bindings.get(4).appearance.key);
	}

	@Test
	public void modelMetadataDoesNotDefineAppearanceIdentity()
	{
		Catalogue catalogue = read("{\"appearances\":{"
			+ "\"red\":{\"modelIds\":[10],\"recolours\":{\"127\":730}},"
			+ "\"blue\":{\"modelIds\":[10],\"recolours\":{\"127\":44762}},"
			+ "\"explicit\":{\"modelIds\":[10],\"scaleX\":200}}}");
		assertEquals(3, catalogue.appearances.size());
		assertNotSame(catalogue.appearances.get("red"), catalogue.appearances.get("blue"));
		assertEquals(200, catalogue.appearances.get("explicit").scaleX);
	}

	@Test
	public void missingStatesUseTheDefaultAndOnlyStatefulTargetsSelectStates()
	{
		Catalogue catalogue = read("{\"targets\":{\"static\":{\"objectIds\":[1]},"
			+ "\"container\":{\"objectIds\":[2,3],\"openObjectIds\":[3]}},\"appearances\":{"
			+ "\"gem\":{\"modelIds\":[10],\"open\":{\"modelIds\":[11],\"animationId\":99},"
			+ "\"bindTargets\":[\"static\",\"container\"]}}}");
		Catalogue.Recipe gem = catalogue.appearances.get("gem");
		assertSame(gem, gem.closed);
		assertEquals(-1, gem.animationId);
		assertEquals(99, gem.open.animationId);
		Map<Integer, TargetBinding> bindings = catalogue.bind(Collections.emptyMap());
		assertSame(gem, bindings.get(1).state(1));
		assertSame(gem, bindings.get(2).state(2));
		assertSame(gem.open, bindings.get(3).state(3));
	}

	@Test
	public void nullReadersAreOptionalAndConsumedReadersAreClosed()
	{
		boolean[] closed = {false};
		StringReader reader = new StringReader("{}")
		{
			@Override
			public void close()
			{
				closed[0] = true;
				super.close();
			}
		};
		Catalogue catalogue = Catalogue.loadCatalogue(RuneLiteAPI.GSON, null, reader, new StringReader("  "));
		assertTrue(closed[0]);
		assertTrue(catalogue.targets.isEmpty());
		assertTrue(catalogue.appearances.isEmpty());
	}

	private static Catalogue read(String... inputs)
	{
		StringReader[] readers = new StringReader[inputs.length];
		for (int i = 0; i < inputs.length; i++)
		{
			readers[i] = new StringReader(inputs[i]);
		}
		return Catalogue.loadCatalogue(RuneLiteAPI.GSON, readers);
	}
}
