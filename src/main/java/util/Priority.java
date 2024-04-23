package util;

public enum Priority
{
	UPDATE,
	DETAIL,
	STANDARD,
	IMPORTANT,
	MILESTONE,
	WARNING,
	ERROR;

	public boolean greaterThan(Priority other)
	{
		return this.compareTo(other) > 0;
	}

	public boolean lessThan(Priority other)
	{
		return this.compareTo(other) < 0;
	}
}
