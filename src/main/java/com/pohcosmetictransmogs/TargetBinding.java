package com.pohcosmetictransmogs;

import net.runelite.api.GameObject;

/** One selected recipe shared by every scene ID of a logical target. */
final class TargetBinding
{
	final TargetSpec target;
	final Catalogue.Recipe appearance;

	TargetBinding(TargetSpec target, Catalogue.Recipe appearance)
	{
		this.target = target;
		this.appearance = appearance;
	}

	Catalogue.Definition state(int objectId)
	{
		return target.isStateful() ? appearance.state(target.isOpen(objectId)) : appearance;
	}

	Catalogue.Calibration calibration(int sizeX, int sizeY)
	{
		return Catalogue.ModelFactory.calibration(appearance, target,
			target.sizeX > 0 ? target.sizeX : sizeX,
			target.sizeY > 0 ? target.sizeY : sizeY);
	}

	int baseOrientation(GameObject object)
	{
		return (object.getOrientation() + target.orientationOffset) & 2047;
	}

	Catalogue.Calibration calibration(GameObject object)
	{
		int sizeX = object.sizeX();
		int sizeY = object.sizeY();
		Catalogue.Calibration selected = appearance.placements.getOrDefault(target.key, appearance);
		// Scene dimensions already include rotation. For the new fixed-facing mode,
		// recover target-local dimensions before the factory applies relative fitting.
		// Keep legacy inherited fitting unchanged for existing catalogue entries.
		if (!selected.inheritRotation && Catalogue.ModelFactory.logicalQuarterTurn(baseOrientation(object)))
		{
			int swap = sizeX;
			sizeX = sizeY;
			sizeY = swap;
		}
		return Catalogue.ModelFactory.calibration(appearance, target,
			target.sizeX > 0 ? target.sizeX : sizeX,
			target.sizeY > 0 ? target.sizeY : sizeY, baseOrientation(object));
	}
}
