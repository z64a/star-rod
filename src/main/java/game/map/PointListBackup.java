package game.map;

import game.map.MutablePoint.PointBackup;
import util.identity.IdentityHashSet;

public class PointListBackup extends ReversibleTransform
{
	private IdentityHashSet<PointBackup> backupList;

	public PointListBackup()
	{
		backupList = new IdentityHashSet<>();
	}

	public PointListBackup(PointBackup pb)
	{
		backupList = new IdentityHashSet<>();
		backupList.add(pb);
	}

	public void addPoint(PointBackup pb)
	{
		backupList.add(pb);
	}

	@Override
	public void transform()
	{
		for (PointBackup b : backupList)
			b.pos.setPosition(b.newx, b.newy, b.newz);
	}

	@Override
	public void revert()
	{
		for (PointBackup b : backupList)
			b.pos.setPosition(b.oldx, b.oldy, b.oldz);
	}

}
