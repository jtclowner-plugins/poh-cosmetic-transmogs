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

	Catalogue.Calibration calibration()
	{
		return Catalogue.ModelFactory.calibration(appearance, target);
	}

	int baseOrientation(GameObject object)
	{
		return (object.getOrientation() + target.orientationOffset) & 2047;
	}
}
