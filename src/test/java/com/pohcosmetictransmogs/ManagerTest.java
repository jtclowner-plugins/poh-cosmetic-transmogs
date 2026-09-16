package com.pohcosmetictransmogs;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Animation;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.JagexColor;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.Point;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.hooks.DrawCallbacks;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.Test;

import static com.pohcosmetictransmogs.ApiDouble.DEFAULT;
import static org.junit.Assert.*;

public class ManagerTest
{
	@Test
	public void unchangedRefreshPreservesReplacementAndAnimationWithoutRendererWork()
	{
		Harness h = new Harness("\"animationId\":99,");
		GameObject object = h.object(1);
		h.loaded.add(object);
		h.manager.addObject(object);
		RuneLiteObject replacement = h.only();
		replacement.tick(3);
		int invalidations = h.invalidations;
		int registrations = h.registrations;
		int removals = h.removals;
		int lights = h.lights;
		int modelLoads = h.modelLoads;
		int creations = h.creations;
		int animationLoads = h.animationLoads;
		h.manager.syncVisibleLevels();
		h.manager.setHidden(false);
		h.manager.scheduleSceneScan();
		h.manager.scanPendingWorldViews();
		assertSame(replacement, h.only());
		assertEquals(3, replacement.getAnimationController().getFrame());
		assertEquals(invalidations, h.invalidations);
		assertEquals(registrations, h.registrations);
		assertEquals(removals, h.removals);
		assertEquals(lights, h.lights);
		assertEquals(modelLoads, h.modelLoads);
		assertEquals(creations, h.creations);
		assertEquals(animationLoads, h.animationLoads);
	}

	@Test
	public void changedPlacementRebuildsButRepeatedPlacementDoesNot()
	{
		Harness h = new Harness("");
		h.manager.addObject(h.object(1));
		for (Runnable change : new Runnable[] {
			() -> h.orientation = 512, () -> h.z = 25, () -> h.sizeX = 2,
			() -> h.sizeY = 3, () -> h.sceneX = 8, () -> h.sceneY = 9,
			() -> { h.objectPlane = 1; h.worldPlane = 1; }})
		{
			RuneLiteObject previous = h.only();
			change.run();
			h.manager.setHidden(false);
			assertNotSame(previous, h.only());
			assertFalse(previous.isActive());
			RuneLiteObject replacement = h.only();
			h.manager.setHidden(false);
			assertSame(replacement, h.only());
		}
		assertEquals(512, h.only().getOrientation());
		assertEquals(25, h.only().getZ());
		assertEquals(1, h.only().getLevel());
	}

	@Test
	public void refreshPreservesBobbingAndInProgressTransition()
	{
		Harness h = new Harness("\"bobbing\":true,");
		GameObject closed = h.object(1);
		h.manager.addObject(closed);
		h.manager.removeObject(closed);
		h.manager.addObject(h.object(2));
		RuneLiteObject replacement = h.only();
		replacement.tick(40);
		assertEquals(-4, replacement.getZ());
		h.manager.setHidden(false);
		assertSame(replacement, h.only());
		assertEquals(-4, replacement.getZ());
		assertNotNull(replacement.getAnimationController());
	}

	@Test
	public void refreshDoesNotInterruptShatterHandoff()
	{
		Harness h = new Harness("\"open\":{\"modelIds\":[20]},"
			+ "\"transitionModelId\":30,\"transitionAnimationId\":100,\"transitionHandoff\":20,");
		GameObject closed = h.object(1);
		h.manager.addObject(closed);
		h.manager.removeObject(closed);
		h.manager.addObject(h.object(2));
		RuneLiteObject effect = h.only();
		h.manager.setHidden(false);
		assertSame(effect, h.only());
		effect.tick(2);
		assertEquals(2, h.active.size());
		h.manager.setHidden(false);
		assertEquals(2, h.active.size());
		effect.tick(8);
		assertEquals(1, h.active.size());
		assertNotSame(effect, h.only());
	}

	@Test
	public void colourRefreshAndVisibilityRecoveryStillReplaceAppliedState()
	{
		Harness h = new Harness("");
		GameObject object = h.object(1);
		h.manager.addObject(object);
		RuneLiteObject previous = h.only();
		h.manager.refreshColours();
		assertNotSame(previous, h.only());
		assertEquals(2, h.lights);
		h.worldPlane = 1;
		h.manager.syncVisibleLevels();
		assertTrue(h.active.isEmpty());
		assertFalse(h.manager.shouldDrawObject(object));
		h.worldPlane = 0;
		h.manager.syncVisibleLevels();
		assertEquals(1, h.active.size());
		h.manager.removeObject(object);
		h.manager.addObject(object);
		assertEquals(1, h.active.size());
	}

	@Test
	public void refreshRecoversAnUnregisteredReplacement()
	{
		Harness h = new Harness("");
		h.manager.addObject(h.object(1));
		RuneLiteObject previous = h.only();
		previous.setActive(false);
		h.manager.setHidden(false);
		assertNotSame(previous, h.only());
	}

	@Test
	public void unavailableAnimationsRemainEligibleForRefresh()
	{
		for (int missingId : new int[] {99, 100})
		{
			Harness h = new Harness("\"animationId\":99,\"spawnAnimationId\":100,");
			h.missingAnimationId = missingId;
			h.manager.addObject(h.object(1));
			RuneLiteObject previous = h.only();
			h.missingAnimationId = -1;
			h.manager.setHidden(false);
			assertNotSame(previous, h.only());
			assertEquals(100, h.only().getAnimationController().getAnimation().getId());
		}
	}

	@Test
	public void refreshDistinguishesWorldViewsWithTheSameId()
	{
		Harness h = new Harness("");
		h.manager.addObject(h.object(1));
		RuneLiteObject previous = h.only();
		h.objectWorld = ApiDouble.of(WorldView.class, (name, args) ->
		{
			switch (name)
			{
				case "getId": return h.world.getId();
				case "getScene": return h.world.getScene();
				default: return DEFAULT;
			}
		});
		h.manager.setHidden(false);
		assertNotSame(previous, h.only());
	}

	@Test
	public void rendererAvailabilityRequiresGpuAndCallbacks()
	{
		Harness h = new Harness("");
		assertTrue(h.manager.isSupportedRenderer());
		h.gpu = false;
		assertFalse(h.manager.isSupportedRenderer());
		h.gpu = true;
		h.callbacksAvailable = false;
		assertFalse(h.manager.isSupportedRenderer());
		h.callbacksAvailable = true;
		assertTrue(h.manager.isSupportedRenderer());
		h.manager.stop();
	}

	@Test
	public void pausingRestoresOriginalsAndRestartRescansLoadedObjects()
	{
		Harness h = new Harness("");
		GameObject object = h.object(1);
		h.loaded.add(object);
		h.manager.addObject(object);
		assertFalse(h.manager.shouldDrawObject(object));
		h.gpu = false;
		h.manager.stop();
		assertTrue(h.active.isEmpty());
		assertTrue(h.manager.shouldDrawObject(object));
		h.manager.addObject(object);
		assertTrue(h.active.isEmpty());
		h.gpu = true;
		h.manager.start(Collections.emptyMap(), false);
		assertEquals(1, h.active.size());
		assertFalse(h.manager.shouldDrawObject(object));
		h.manager.stop();
		h.manager.start(Collections.emptyMap(), true);
		assertTrue(h.active.isEmpty());
		assertTrue(h.manager.shouldDrawObject(object));
		h.manager.stop();
	}

	@Test
	public void configColourChannelsRunAfterFixedRecoloursAndRespectToggles()
	{
		for (boolean enabled : new boolean[] {false, true})
		{
			Harness h = new Harness("\"recolours\":{\"127\":730},\"colours\":{"
				+ "\"CRYSTALS\":[730],\"TOB_CHEST\":[731],\"GAUNTLET_CHEST\":[732],"
				+ "\"DEADMAN_CHEST\":[733],\"TOA_CONTAINERS\":[734]},", new PohCosmeticTransmogsConfig()
			{
				@Override public AppearanceColour recolourColour() { return AppearanceColour.GREEN; }
				@Override public boolean recolourCrystals() { return enabled; }
				@Override public boolean recolourTobChest() { return enabled; }
				@Override public boolean recolourGauntletChest() { return enabled; }
				@Override public boolean recolourDeadmanChest() { return enabled; }
				@Override public boolean recolourToaContainers() { return enabled; }
			});
			h.manager.addObject(h.object(1));
			int fixed = h.operations.indexOf("recolor:127:730");
			assertTrue(fixed >= 0);
			for (short colour = 730; colour <= 734; colour++)
			{
				short recoloured = JagexColor.packHSL(14,
					JagexColor.unpackSaturation(colour), JagexColor.unpackLuminance(colour));
				int index = h.operations.indexOf("recolor:" + colour + ":" + recoloured);
				assertEquals(enabled, index > fixed);
			}
			h.manager.stop();
		}
	}

	@Test
	public void alignmentUsesActualFootprintParityPerAxis()
	{
		for (int targetX = 1; targetX <= 4; targetX++)
		{
			for (int targetY = 1; targetY <= 4; targetY++)
			{
				for (int replacementSize = 1; replacementSize <= 7; replacementSize++)
				{
					for (Catalogue.Alignment alignment : Catalogue.Alignment.values())
					{
						Harness h = new Harness("\"sizeX\":" + replacementSize + ",\"sizeY\":" + replacementSize
							+ ",\"alignment\":\"" + alignment + "\",");
						h.sizeX = targetX;
						h.sizeY = targetY;
						h.manager.addObject(h.object(1));
						assertEquals((10 + targetX) * 64 + ((targetX - replacementSize) & 1) * alignment.x * 64,
							h.only().getLocation().getX());
						assertEquals((10 + targetY) * 64 + ((targetY - replacementSize) & 1) * alignment.y * 64,
							h.only().getLocation().getY());
						h.manager.stop();
					}
				}
			}
		}
	}

	@Test
	public void alignmentRespectsFinalQuarterTurnAndAddsRotatedManualOffsets()
	{
		Harness h = new Harness("\"sizeX\":2,\"sizeY\":3,\"rotation\":512,"
			+ "\"alignment\":\"SOUTH_WEST\",\"offsetX\":128,\"offsetY\":256,");
		h.manager.addObject(h.object(1));
		assertEquals(704 + 128, h.only().getLocation().getX());
		assertEquals(704 - 64 + 256, h.only().getLocation().getY());
		h.manager.stop();

		h = new Harness("\"sizeX\":2,\"sizeY\":3,\"alignment\":\"SOUTH_WEST\","
			+ "\"offsetX\":128,\"offsetY\":256,");
		h.orientation = 512;
		h.manager.addObject(h.object(1));
		assertEquals(704 + 256, h.only().getLocation().getX());
		assertEquals(704 - 64 - 128, h.only().getLocation().getY());
		h.manager.stop();
	}

	@Test
	public void omittedAlignmentPreservesTheOriginalCentre()
	{
		Harness h = new Harness("\"sizeX\":2,\"sizeY\":2,");
		h.manager.addObject(h.object(1));
		assertEquals(704, h.only().getLocation().getX());
		assertEquals(704, h.only().getLocation().getY());
		h.manager.stop();
	}

	@Test
	public void diagonalSquareRotationAndScaleDoNotChangeDeclaredAlignment()
	{
		Harness h = new Harness("\"sizeX\":2,\"sizeY\":2,\"rotation\":256,"
			+ "\"scaleX\":900,\"alignment\":\"SOUTH_WEST\",");
		h.manager.addObject(h.object(1));
		assertEquals(640, h.only().getLocation().getX());
		assertEquals(640, h.only().getLocation().getY());
		h.manager.stop();
	}

	@Test
	public void radiusUsesFootprintAndOriginalInteractionsRemainInTheScene()
	{
		Harness h = new Harness("\"sizeX\":3,\"sizeY\":7,\"scaleX\":900,");
		GameObject object = h.object(1);
		h.manager.addObject(object);
		assertEquals(444, h.only().getRadius());
		assertFalse(h.manager.shouldDrawObject(object));
		assertTrue(h.manager.getActiveObjects().contains(object));
		h.manager.setHidden(true);
		assertTrue(h.active.isEmpty());
		assertTrue(h.manager.shouldDrawObject(object));
		h.manager.setHidden(false);
		assertEquals(1, h.active.size());
		h.manager.stop();
		assertTrue(h.active.isEmpty());
		assertTrue(h.manager.shouldDrawObject(object));
		assertTrue(h.invalidations > 0);
	}

	@Test
	public void stateChangesRetireOldObjectsUntilDespawn()
	{
		Harness h = new Harness("\"open\":{\"modelIds\":[20]},");
		GameObject closed = h.object(1);
		GameObject open = h.object(2);
		h.manager.addObject(closed);
		Model closedModel = h.only().getModel();
		h.manager.addObject(open);
		assertEquals(1, h.active.size());
		assertNotSame(closedModel, h.only().getModel());
		assertFalse(h.manager.shouldDrawObject(closed));
		assertFalse(h.manager.shouldDrawObject(open));
		h.manager.removeObject(closed);
		assertEquals(1, h.active.size());
		assertFalse(h.manager.shouldDrawObject(open));
		h.manager.removeObject(open);
		assertTrue(h.active.isEmpty());
	}

	@Test
	public void removingASelectionRestoresOriginalDrawing()
	{
		Harness h = new Harness("");
		Catalogue.current.appearances.get("gem").bindTargets = new String[0];
		h.manager.setCatalogue(Catalogue.current);
		h.manager.setSelections(Map.of("box", "gem"));
		GameObject object = h.object(1);
		h.manager.addObject(object);
		assertFalse(h.manager.shouldDrawObject(object));
		h.manager.setSelections(Collections.emptyMap());
		assertTrue(h.active.isEmpty());
		assertTrue(h.manager.shouldDrawObject(object));
		h.manager.removeObject(object);
		h.manager.setSelections(Map.of("box", "gem"));
		h.manager.addObject(h.object(1));
		assertTrue(h.manager.shouldDrawObject(object));
	}

	@Test
	public void missingModelsRetryWithoutChangingBindings()
	{
		Harness h = new Harness("");
		h.modelsAvailable = false;
		GameObject object = h.object(1);
		h.manager.addObject(object);
		assertTrue(h.active.isEmpty());
		h.modelsAvailable = true;
		h.manager.loadMissingModels();
		assertEquals(1, h.active.size());
		assertFalse(h.manager.shouldDrawObject(object));
	}

	@Test
	public void equalModelIdsInDifferentRecipesHaveIndependentCacheEntries()
	{
		Harness h = new Harness("");
		Catalogue.current = Catalogue.loadCatalogue(RuneLiteAPI.GSON, null,
			new StringReader("{\"targets\":{\"a\":{\"objectIds\":[1]},\"b\":{\"objectIds\":[3]}},"
				+ "\"appearances\":{\"red\":{\"modelIds\":[10],\"recolours\":{\"127\":730},\"bindTargets\":[\"a\"]},"
				+ "\"blue\":{\"modelIds\":[10],\"recolours\":{\"127\":44762},\"bindTargets\":[\"b\"]}}}"));
		h.manager.setCatalogue(Catalogue.current);
		h.manager.addObject(h.object(1));
		h.manager.addObject(h.object(3));
		assertEquals(2, h.active.size());
		assertEquals(2, h.lights);
		assertTrue(h.operations.contains("recolor:127:730"));
		assertTrue(h.operations.contains("recolor:127:-20774"));
	}

	@Test
	public void scaleTransitionsKeepTheBottomFixedAndReverse()
	{
		Harness h = new Harness("");
		GameObject closed = h.object(1);
		h.manager.addObject(closed);
		h.manager.removeObject(closed);
		GameObject open = h.object(2);
		h.manager.addObject(open);
		RuneLiteObject replacement = h.only();
		assertNotNull(replacement.getAnimationController());
		replacement.tick(30);
		h.operations.clear();
		replacement.getModel();
		assertTrue(h.operations.contains("scale:160:160:160"));
		assertTrue(h.operations.contains("translate:0:-5:0"));
		h.manager.removeObject(open);
		h.manager.addObject(h.object(1));
		replacement = h.only();
		replacement.tick(30);
		h.operations.clear();
		replacement.getModel();
		assertTrue(h.operations.contains("scale:128:128:128"));
	}

	@Test
	public void animatedModelsAreScaledAfterPosing()
	{
		Harness h = new Harness("\"animationId\":99,\"scaleX\":200,\"scaleY\":180,\"scaleHeight\":150,");
		h.manager.addObject(h.object(1));
		assertFalse(h.operations.stream().anyMatch(s -> s.startsWith("scale:")));
		h.only().getModel();
		assertTrue(h.operations.indexOf("pose") < h.operations.indexOf("scale:200:150:180"));
	}

	@Test
	public void closingReversesTheOpenSpawnAnimation()
	{
		Harness h = new Harness("\"animationId\":99,\"open\":{\"modelIds\":[20],\"animationId\":99,\"spawnAnimationId\":100},");
		GameObject open = h.object(2);
		h.manager.addObject(open);
		h.manager.removeObject(open);
		h.manager.addObject(h.object(1));
		RuneLiteObject replacement = h.only();
		assertEquals(100, replacement.getAnimationController().getAnimation().getId());
		assertEquals(9, replacement.getAnimationController().getFrame());
		replacement.tick(2);
		assertEquals(7, replacement.getAnimationController().getFrame());
		replacement.tick(10);
		assertEquals(99, replacement.getAnimationController().getAnimation().getId());
	}

	@Test
	public void shatterEffectsHandOffAndAreRemovedOnShutdown()
	{
		Harness h = new Harness("\"sizeX\":2,\"sizeY\":2,\"alignment\":\"SOUTH_WEST\","
			+ "\"open\":{\"modelIds\":[20]},\"transitionModelId\":30,\"transitionAnimationId\":100,\"transitionHandoff\":20,");
		GameObject closed = h.object(1);
		h.manager.addObject(closed);
		h.manager.removeObject(closed);
		h.manager.addObject(h.object(2));
		RuneLiteObject effect = h.only();
		effect.tick(2);
		assertEquals(2, h.active.size());
		for (RuneLiteObject part : h.active)
		{
			assertEquals(640, part.getLocation().getX());
			assertEquals(640, part.getLocation().getY());
		}
		effect.tick(8);
		assertEquals(1, h.active.size());
		h.manager.stop();
		assertTrue(h.active.isEmpty());
	}

	@Test
	public void bobbingContinuesThroughTheScaleTransition()
	{
		Harness h = new Harness("\"bobbing\":true,");
		GameObject closed = h.object(1);
		h.manager.addObject(closed);
		h.only().tick(40);
		assertEquals(-4, h.only().getZ());
		h.manager.removeObject(closed);
		h.manager.addObject(h.object(2));
		h.only().tick(40);
		assertEquals(-4, h.only().getZ());
		assertNotNull(h.only().getAnimationController());
	}

	@Test
	public void reloadRebuildsModelsAndRestoresRemovedTargets()
	{
		Harness h = new Harness("");
		GameObject object = h.object(1);
		h.loaded.add(object);
		h.manager.addObject(object);
		Model previous = h.only().getModel();
		h.manager.setCatalogue(Catalogue.current);
		assertEquals(1, h.active.size());
		assertNotSame(previous, h.only().getModel());
		h.manager.setCatalogue(Catalogue.loadCatalogue(RuneLiteAPI.GSON, null, new StringReader("{}")));
		assertTrue(h.active.isEmpty());
		assertTrue(h.manager.shouldDrawObject(object));
	}

	@Test
	public void delayedSpawnWaitsForVisibilityAndRunsOnce()
	{
		Harness h = new Harness("\"animationId\":99,\"spawnAnimationId\":100,\"spawnOnce\":true,");
		h.gameState = GameState.LOADING;
		GameObject object = h.object(1);
		h.manager.addObject(object);
		RuneLiteObject replacement = h.only();
		replacement.tick(100);
		assertEquals(0, replacement.getAnimationController().getFrame());
		h.gameState = GameState.LOGGED_IN;
		replacement.tick(1);
		replacement.tick(10);
		assertEquals(99, replacement.getAnimationController().getAnimation().getId());
		h.manager.refreshColours();
		assertEquals(99, h.only().getAnimationController().getAnimation().getId());
	}

	@Test
	public void staticMirroringReversesWindingAndNormals()
	{
		Harness h = new Harness("\"placements\":{\"box\":{\"flipX\":true}},");
		h.manager.addObject(h.object(1));
		Model model = h.only().getModel();
		assertArrayEquals(new int[] {3}, model.getFaceIndices1());
		assertArrayEquals(new int[] {1}, model.getFaceIndices3());
		assertArrayEquals(new int[] {-4}, model.getVertexNormalsX());
		assertTrue(h.operations.contains("scale:-128:128:128"));
	}

	@Test
	public void startupScansLoadedViewsOnceWithOrWithoutSelections()
	{
		for (Map<String, String> selections : List.of(Collections.<String, String>emptyMap(), Map.of("box", "gem")))
		{
			Harness h = new Harness("");
			h.manager.stop();
			h.loaded.add(h.object(1));
			ChildWorld child = new ChildWorld(7);
			h.children.add(child.world);
			child.loaded.add(h.object(1, child.world));
			int scans = h.scans;
			h.manager.start(selections, false);
			assertEquals(scans + 1, h.scans);
			assertEquals(1, child.scans);
			assertEquals(2, h.active.size());
		}
	}

	@Test
	public void loadingAndLoginCoalesceIntoOneRecoveryAfterSceneEstablishment()
	{
		Harness h = new Harness("");
		h.gameState = GameState.LOADING;
		h.manager.clearSceneState();
		h.manager.scheduleSceneScan();
		int scans = h.scans;
		h.manager.scanPendingWorldViews();
		assertEquals(scans, h.scans);
		GameObject missed = h.object(1);
		h.loaded.add(missed);
		h.gameState = GameState.LOGGED_IN;
		h.manager.scheduleSceneScan();
		h.manager.scanPendingWorldViews();
		assertEquals(scans + 1, h.scans);
		assertFalse(h.manager.shouldDrawObject(missed));
		RuneLiteObject replacement = h.only();
		for (int tick = 0; tick < 10; tick++)
		{
			h.manager.scanPendingWorldViews();
		}
		assertEquals(scans + 1, h.scans);
		assertSame(replacement, h.only());
	}

	@Test
	public void childLoadRecoversOnlyThatViewAndLaterEventsNeedNoScans()
	{
		Harness h = new Harness("");
		ChildWorld child = new ChildWorld(7);
		h.children.add(child.world);
		int scans = h.scans;
		h.manager.worldViewLoaded(child.world);
		assertEquals(1, child.scans);
		GameObject missed = h.object(1, child.world);
		child.loaded.add(missed);
		h.manager.scanPendingWorldViews();
		assertEquals(2, child.scans);
		assertEquals(scans, h.scans);
		assertFalse(h.manager.shouldDrawObject(missed));
		child.loaded.remove(missed);
		h.manager.removeObject(missed);
		GameObject spawned = h.object(2, child.world);
		child.loaded.add(spawned);
		h.manager.addObject(spawned);
		h.manager.scanPendingWorldViews();
		assertEquals(2, child.scans);
		assertEquals(scans, h.scans);
		assertTrue(h.manager.shouldDrawObject(missed));
		assertFalse(h.manager.shouldDrawObject(spawned));
		assertEquals(1, h.active.size());
	}

	@Test
	public void globalRecoveryConsumesChildRecoveryWithoutScanningItTwice()
	{
		Harness h = new Harness("");
		ChildWorld child = new ChildWorld(7);
		h.children.add(child.world);
		h.manager.worldViewLoaded(child.world);
		h.manager.scheduleSceneScan();
		int scans = h.scans;
		h.manager.scanPendingWorldViews();
		assertEquals(scans + 1, h.scans);
		assertEquals(2, child.scans);
		h.manager.scanPendingWorldViews();
		assertEquals(2, child.scans);
	}

	@Test
	public void recoveryIncludesLoadedChildNotYetInTheTopLevelIndex()
	{
		Harness h = new Harness("");
		ChildWorld child = new ChildWorld(7);
		h.manager.worldViewLoaded(child.world);
		GameObject missed = h.object(1, child.world);
		child.loaded.add(missed);
		h.manager.scheduleSceneScan();
		h.manager.scanPendingWorldViews();
		assertEquals(2, child.scans);
		assertFalse(h.manager.shouldDrawObject(missed));
	}

	@Test
	public void unloadingAndSceneResetCancelPendingRecovery()
	{
		Harness h = new Harness("");
		ChildWorld old = new ChildWorld(7);
		h.manager.worldViewLoaded(old.world);
		h.manager.removeWorldView(old.world);
		old.loaded.add(h.object(1, old.world));
		ChildWorld current = new ChildWorld(7);
		current.loaded.add(h.object(1, current.world));
		h.manager.worldViewLoaded(current.world);
		h.manager.scanPendingWorldViews();
		assertEquals(1, old.scans);
		assertEquals(2, current.scans);
		assertEquals(1, h.active.size());
		h.manager.worldViewLoaded(current.world);
		h.manager.scheduleSceneScan();
		h.manager.clearSceneState();
		int scans = h.scans;
		h.manager.scanPendingWorldViews();
		assertEquals(scans, h.scans);
		assertEquals(3, current.scans);
		assertTrue(h.active.isEmpty());
	}

	@Test
	public void stoppingCancelsScansAndPausedLoadEventsDoNotScan()
	{
		Harness h = new Harness("");
		h.manager.worldViewLoaded(h.world);
		h.manager.scheduleSceneScan();
		h.manager.stop();
		int scans = h.scans;
		h.manager.worldViewLoaded(h.world);
		h.manager.scheduleSceneScan();
		h.manager.scanPendingWorldViews();
		assertEquals(scans, h.scans);
		h.loaded.add(h.object(1));
		h.manager.start(Collections.emptyMap(), false);
		h.manager.scanPendingWorldViews();
		assertEquals(scans + 1, h.scans);
		assertEquals(1, h.active.size());
	}

	@Test
	public void recoveryDoesNotImmediatelyRetryTheMissingModelItJustDiscovered()
	{
		Harness h = new Harness("");
		h.modelsAvailable = false;
		h.loaded.add(h.object(1));
		h.manager.scheduleSceneScan();
		h.manager.scanPendingWorldViews();
		assertEquals(1, h.modelLoads);
		assertTrue(h.active.isEmpty());
		h.modelsAvailable = true;
		h.manager.loadMissingModels();
		assertEquals(1, h.active.size());
	}

	@Test
	public void missingModelsInvalidateOnlyWhenSuppressionChanges()
	{
		Harness h = new Harness("");
		GameObject object = h.object(1);
		h.modelsAvailable = false;
		h.manager.addObject(object);
		h.manager.loadMissingModels();
		h.manager.loadMissingModels();
		assertEquals(0, h.invalidations);
		h.modelsAvailable = true;
		h.onInvalidation = () -> assertFalse(h.manager.shouldDrawObject(object));
		h.manager.loadMissingModels();
		assertEquals(1, h.invalidations);
		h.modelsAvailable = false;
		h.onInvalidation = () -> assertTrue(h.manager.shouldDrawObject(object));
		h.manager.refreshColours();
		assertEquals(2, h.invalidations);
		h.manager.loadMissingModels();
		h.manager.loadMissingModels();
		assertEquals(2, h.invalidations);
		assertTrue(h.active.isEmpty());
	}

	@Test
	public void visibilityChangesKeepOriginalSuppressionWithoutRebuildingZones()
	{
		Harness h = new Harness("");
		h.worldPlane = 1;
		GameObject object = h.object(1);
		h.manager.addObject(object);
		assertEquals(1, h.invalidations);
		assertTrue(h.active.isEmpty());
		h.manager.syncVisibleLevels();
		h.manager.setHidden(false);
		assertEquals(1, h.invalidations);
		h.worldPlane = 0;
		h.manager.syncVisibleLevels();
		assertEquals(1, h.active.size());
		assertEquals(1, h.invalidations);
		h.worldPlane = 1;
		h.manager.syncVisibleLevels();
		assertTrue(h.active.isEmpty());
		assertEquals(1, h.invalidations);
		assertFalse(h.manager.shouldDrawObject(object));
	}

	@Test
	public void replacementModelAndPlacementChangesDoNotInvalidateSuppressedScenery()
	{
		Harness h = new Harness("");
		h.manager.addObject(h.object(1));
		RuneLiteObject previous = h.only();
		h.manager.refreshColours();
		assertNotSame(previous, h.only());
		assertEquals(2, h.lights);
		previous = h.only();
		h.orientation = 512;
		h.manager.setHidden(false);
		assertNotSame(previous, h.only());
		assertEquals(512, h.only().getOrientation());
		assertEquals(1, h.invalidations);
	}

	@Test
	public void disablingAndRestoringASelectionInvalidatesEachSuppressionChange()
	{
		Harness h = new Harness("");
		h.manager.stop();
		Catalogue.current.appearances.get("gem").bindTargets = new String[0];
		h.manager.start(Map.of("box", "gem"), false);
		GameObject object = h.object(1);
		h.loaded.add(object);
		h.manager.addObject(object);
		assertEquals(1, h.invalidations);
		h.manager.setSelections(Collections.emptyMap());
		assertTrue(h.manager.shouldDrawObject(object));
		assertEquals(2, h.invalidations);
		h.manager.setSelections(Collections.emptyMap());
		assertEquals(2, h.invalidations);
		h.manager.setSelections(Map.of("box", "gem"));
		assertFalse(h.manager.shouldDrawObject(object));
		assertEquals(3, h.invalidations);
	}

	@Test
	public void suppressionChangesArePublishedBeforeOneInvalidationPerZone()
	{
		Harness h = new Harness("");
		h.loaded.add(h.object(1, null, new Point(5, 5)));
		h.loaded.add(h.object(1, null, new Point(6, 5)));
		h.loaded.add(h.object(1, null, new Point(17, 5)));
		h.onInvalidation = () -> h.loaded.forEach(object -> assertFalse(h.manager.shouldDrawObject(object)));
		h.manager.scheduleSceneScan();
		h.manager.scanPendingWorldViews();
		assertEquals(3, h.active.size());
		assertEquals(2, h.invalidations);
		h.onInvalidation = () -> h.loaded.forEach(object -> assertTrue(h.manager.shouldDrawObject(object)));
		h.manager.setHidden(true);
		assertEquals(4, h.invalidations);
		h.manager.setHidden(true);
		assertEquals(4, h.invalidations);
		h.onInvalidation = () -> h.loaded.forEach(object -> assertFalse(h.manager.shouldDrawObject(object)));
		h.manager.setHidden(false);
		assertEquals(6, h.invalidations);
		h.onInvalidation = () -> h.loaded.forEach(object -> assertTrue(h.manager.shouldDrawObject(object)));
		h.manager.stop();
		assertEquals(8, h.invalidations);
		h.manager.stop();
		assertEquals(8, h.invalidations);
	}

	@Test
	public void rendererChangeInvalidatesSuppressedZonesOnceWithoutRecreatingObjects()
	{
		Harness h = new Harness("");
		GameObject object = h.object(1);
		h.manager.addObject(object);
		RuneLiteObject replacement = h.only();
		h.manager.syncRenderer();
		assertEquals(1, h.invalidations);
		h.callbacks = h.newRenderer();
		h.onInvalidation = () -> assertFalse(h.manager.shouldDrawObject(object));
		h.manager.syncRenderer();
		assertEquals(2, h.invalidations);
		assertSame(replacement, h.only());
		h.manager.syncRenderer();
		assertEquals(2, h.invalidations);
		h.onInvalidation = () -> assertTrue(h.manager.shouldDrawObject(object));
		h.manager.setHidden(true);
		assertEquals(3, h.invalidations);
		h.callbacks = h.newRenderer();
		h.manager.syncRenderer();
		assertEquals(3, h.invalidations);
	}

	@Test
	public void bulkRebuildInvalidatesOnlyTheFinalSuppressionDifference()
	{
		Harness h = new Harness("");
		GameObject object = h.object(1);
		h.loaded.add(object);
		h.manager.addObject(object);
		RuneLiteObject previous = h.only();
		h.manager.setCatalogue(Catalogue.current);
		assertNotSame(previous, h.only());
		assertEquals(1, h.invalidations);
		assertFalse(h.manager.shouldDrawObject(object));
		Catalogue.current.appearances.get("gem").bindTargets = new String[0];
		h.onInvalidation = () -> assertTrue(h.manager.shouldDrawObject(object));
		h.manager.setCatalogue(Catalogue.current);
		assertEquals(2, h.invalidations);
		assertTrue(h.active.isEmpty());
	}

	@Test
	public void shutdownRestoresRetiredSceneryEvenWhenTheNewStateHasNoModel()
	{
		Harness h = new Harness("\"open\":{\"modelIds\":[20]},");
		GameObject closed = h.object(1);
		GameObject open = h.object(2);
		h.manager.addObject(closed);
		h.modelsAvailable = false;
		h.manager.addObject(open);
		assertFalse(h.manager.shouldDrawObject(closed));
		assertTrue(h.manager.shouldDrawObject(open));
		assertEquals(1, h.invalidations);
		h.onInvalidation = () ->
		{
			assertTrue(h.manager.shouldDrawObject(closed));
			assertTrue(h.manager.shouldDrawObject(open));
		};
		h.manager.stop();
		assertEquals(2, h.invalidations);
	}

	@Test
	public void rendererInvalidationKeepsChildScenesDistinctFromTheTopLevel()
	{
		Harness h = new Harness("");
		ChildWorld child = new ChildWorld(7);
		h.manager.addObject(h.object(1));
		h.manager.addObject(h.object(1, child.world));
		assertEquals(2, h.invalidations);
		h.invalidatedScenes.clear();
		h.callbacks = h.newRenderer();
		h.manager.syncRenderer();
		assertEquals(4, h.invalidations);
		assertTrue(h.invalidatedScenes.contains(h.world.getScene()));
		assertTrue(h.invalidatedScenes.contains(child.world.getScene()));
	}

	@Test
	public void hiddenObjectsAndDespawnedObjectsDoNotRequestZoneRebuilds()
	{
		Harness h = new Harness("");
		h.manager.setHidden(true);
		GameObject closed = h.object(1);
		GameObject open = h.object(2);
		h.manager.addObject(closed);
		h.manager.addObject(open);
		h.manager.refreshColours();
		assertTrue(h.manager.shouldDrawObject(closed));
		assertTrue(h.manager.shouldDrawObject(open));
		assertEquals(0, h.invalidations);
		h.manager.setHidden(false);
		assertEquals(1, h.invalidations);
		h.manager.removeObject(closed);
		h.manager.removeObject(open);
		assertEquals(1, h.invalidations);
		assertTrue(h.active.isEmpty());
		h.manager.stop();
		assertEquals(1, h.invalidations);
	}

	@Test
	public void sceneEstablishmentReusesModelsWhenAccountInputsAreUnchanged()
	{
		Harness h = new Harness("");
		GameObject object = h.object(1);
		h.manager.addObject(object);
		Model model = h.only().getModel();
		h.manager.clearSceneState();
		h.manager.refreshAccountType();
		h.manager.addObject(object);
		assertSame(model, h.only().getModel());
		assertEquals(1, h.lights);
		h.manager.refreshAccountType();
		assertEquals(1, h.lights);
	}

	@Test
	public void accountChangesRebuildModelsOnlyWhenNodeRecolourIsEnabled()
	{
		for (boolean enabled : new boolean[] {false, true})
		{
			Harness h = new Harness("", new PohCosmeticTransmogsConfig()
			{
				@Override public boolean recolourNodePortal() { return enabled; }
			});
			h.manager.addObject(h.object(1));
			h.accountType = 1;
			h.manager.refreshAccountType();
			assertEquals(enabled ? 2 : 1, h.lights);
			h.manager.refreshAccountType();
			assertEquals(enabled ? 2 : 1, h.lights);
		}
	}

	private static final class ChildWorld
	{
		final List<GameObject> loaded = new ArrayList<>();
		final WorldView world;
		int scans;

		ChildWorld(int id)
		{
			Scene scene = ApiDouble.of(Scene.class, (name, args) ->
			{
				if (name.equals("getTiles"))
				{
					scans++;
					Tile tile = ApiDouble.of(Tile.class, (n, a) -> n.equals("getGameObjects")
						? loaded.toArray(new GameObject[0]) : DEFAULT);
					return new Tile[][][] {{{tile}}};
				}
				return DEFAULT;
			});
			world = ApiDouble.of(WorldView.class, (name, args) ->
			{
				switch (name)
				{
					case "getId": return id;
					case "getScene": return scene;
					case "getSizeX":
					case "getSizeY": return 104;
					default: return DEFAULT;
				}
			});
		}
	}

	private static final class Harness
	{
		final Set<RuneLiteObject> active = Collections.newSetFromMap(new IdentityHashMap<>());
		final List<String> operations = new ArrayList<>();
		final List<GameObject> loaded = new ArrayList<>();
		final List<WorldView> children = new ArrayList<>();
		final List<Scene> invalidatedScenes = new ArrayList<>();
		Runnable onInvalidation = () -> { };
		DrawCallbacks callbacks;
		final Client client;
		final WorldView world;
		final PohCosmeticTransmogsManager manager;
		WorldView objectWorld;
		int missingAnimationId = -1;
		int accountType;
		boolean modelsAvailable = true;
		boolean gpu = true;
		boolean callbacksAvailable = true;
		int sizeX = 1;
		int sizeY = 1;
		int orientation;
		int z;
		int sceneX = 5;
		int sceneY = 5;
		int objectPlane;
		int worldPlane;
		int creations;
		int registrations;
		int removals;
		int modelLoads;
		int animationLoads;
		int invalidations;
		int lights;
		int scans;
		GameState gameState = GameState.LOGGED_IN;

		Harness(String fields)
		{
			this(fields, new PohCosmeticTransmogsConfig() {});
		}

		Harness(String fields, PohCosmeticTransmogsConfig config)
		{
			Scene scene = ApiDouble.of(Scene.class, (name, args) ->
			{
				if (name.equals("getTiles"))
				{
					scans++;
					Tile tile = ApiDouble.of(Tile.class, (n, a) -> n.equals("getGameObjects")
						? loaded.toArray(new GameObject[0]) : DEFAULT);
					return new Tile[][][] {{{tile}}};
				}
				throw new AssertionError("Original scene mutation: " + name);
			});
			world = ApiDouble.of(WorldView.class, (name, args) ->
			{
				switch (name)
				{
					case "getId": return WorldView.TOPLEVEL;
					case "getPlane": return worldPlane;
					case "getScene": return scene;
					case "worldViews": return ApiDouble.of(IndexedObjectSet.class,
						(n, a) -> n.equals("iterator") ? children.iterator() : DEFAULT);
					default: return DEFAULT;
				}
			});
			callbacks = newRenderer();
			client = ApiDouble.of(Client.class, (name, args) ->
			{
				switch (name)
				{
					case "isGpu": return gpu;
					case "getDrawCallbacks": return callbacksAvailable ? callbacks : null;
					case "getTopLevelWorldView":
					case "getWorldView": return world;
					case "createRuneLiteObject": creations++; return new RuneLiteObject(client());
					case "registerRuneLiteObject": registrations++; active.add((RuneLiteObject) args[0]); return null;
					case "removeRuneLiteObject": removals++; active.remove(args[0]); return null;
					case "isRuneLiteObjectRegistered": return active.contains(args[0]);
					case "loadModelData": modelLoads++; return modelsAvailable ? modelData() : null;
					case "mergeModels": return args[0] instanceof Model[] ? model() : modelData();
					case "applyTransformations": operations.add("pose"); return model();
					case "loadAnimation":
						animationLoads++;
						return (int) args[0] == missingAnimationId ? null : animation((int) args[0]);
					case "getGameState": return gameState;
					case "getVarbitValue": return accountType;
					case "getCameraFpX": return 704f;
					case "getViewportWidth": return 800;
					case "getViewportHeight": return 600;
					case "getScale": return 512;
					default: return DEFAULT;
				}
			});
			Catalogue.current = Catalogue.loadCatalogue(RuneLiteAPI.GSON, null, new StringReader(
				"{\"targets\":{\"box\":{\"objectIds\":[1,2],\"openObjectIds\":[2],\"scaleTransition\":true}},"
				+ "\"appearances\":{\"gem\":{" + fields + "\"modelIds\":[10],\"bindTargets\":[\"box\"]}}}"));
			manager = new PohCosmeticTransmogsManager(client, config);
			manager.start(Collections.emptyMap(), false);
		}

		Client client() { return client; }

		DrawCallbacks newRenderer()
		{
			return ApiDouble.of(DrawCallbacks.class, (name, args) ->
			{
				if (name.equals("invalidateZone"))
				{
					invalidations++;
					invalidatedScenes.add((Scene) args[0]);
					onInvalidation.run();
				}
				return DEFAULT;
			});
		}

		RuneLiteObject only()
		{
			assertEquals(1, active.size());
			return active.iterator().next();
		}

		GameObject object(int id)
		{
			return object(id, null);
		}

		GameObject object(int id, WorldView view)
		{
			return object(id, view, null);
		}

		GameObject object(int id, WorldView view, Point fixedLocation)
		{
			return ApiDouble.of(GameObject.class, (name, args) ->
			{
				switch (name)
				{
					case "getId": return id;
					case "getWorldView": return view != null ? view : objectWorld == null ? world : objectWorld;
					case "getSceneMinLocation": return fixedLocation == null ? new Point(sceneX, sceneY) : fixedLocation;
					case "getSceneMaxLocation": return fixedLocation == null
						? new Point(sceneX - 1 + sizeX, sceneY - 1 + sizeY)
						: new Point(fixedLocation.getX() - 1 + sizeX, fixedLocation.getY() - 1 + sizeY);
					case "getPlane": return objectPlane;
					case "getZ": return z;
					case "sizeX": return sizeX;
					case "sizeY": return sizeY;
					case "getOrientation": return orientation;
					default: return DEFAULT;
				}
			});
		}

		ModelData modelData()
		{
			return ApiDouble.of(ModelData.class, (name, args) ->
			{
				if (name.equals("light")) { lights++; return model(); }
				record(name, args);
				return DEFAULT;
			});
		}

		Model model()
		{
			int[] first = {1}, third = {3}, normals = {4};
			return ApiDouble.of(Model.class, (name, args) ->
			{
				if (name.equals("getXYZMag")) { throw new AssertionError("Radius must not use model bounds"); }
				if (name.equals("getBottomY")) { return 20; }
				if (name.equals("getFaceIndices1")) { return first; }
				if (name.equals("getFaceIndices3")) { return third; }
				if (name.equals("getVertexNormalsX")) { return normals; }
				record(name, args);
				return DEFAULT;
			});
		}

		void record(String name, Object[] args)
		{
			if (name.equals("scale") || name.equals("translate") || name.equals("recolor"))
			{
				StringBuilder text = new StringBuilder(name);
				for (Object arg : args) { text.append(':').append(arg); }
				operations.add(text.toString());
			}
		}

		Animation animation(int id)
		{
			return ApiDouble.of(Animation.class, (name, args) ->
			{
				switch (name)
				{
					case "getId": return id;
					case "isMayaAnim": return true;
					case "getDuration":
					case "getFrameStep": return 10;
					default: return DEFAULT;
				}
			});
		}
	}
}
