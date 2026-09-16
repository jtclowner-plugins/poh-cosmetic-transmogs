package com.pohcosmetictransmogs;

/** Scene IDs and placement semantics for one logical target. */
final class TargetSpec
{
	String key;
	int[] objectIds = {};
	int[] openObjectIds = {};
	int orientationOffset;
	boolean scaleTransition;
	boolean portalRecolour;

	boolean isOpen(int objectId)
	{
		for (int id : openObjectIds)
		{
			if (id == objectId)
			{
				return true;
			}
		}
		return false;
	}

	boolean isStateful()
	{
		return openObjectIds.length > 0;
	}

}
