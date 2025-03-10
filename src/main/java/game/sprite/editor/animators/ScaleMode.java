package game.sprite.editor.animators;

import util.Logger;

public enum ScaleMode
{
	UNIFORM ("uniform", 0),
	X ("x", 1),
	Y ("y", 2),
	Z ("z", 3);

	public final String name;
	public final int value;

	private ScaleMode(String name, int value)
	{
		this.name = name;
		this.value = value;
	}

	public static ScaleMode get(int value)
	{
		switch (value) {
			case 0:
				return UNIFORM;
			case 1:
				return X;
			case 2:
				return Y;
			case 3:
				return Z;
		}

		// return something valid by default
		Logger.logWarning("Invalid scale mode requested: " + value);
		return UNIFORM;
	}

	public static ScaleMode get(String name)
	{
		switch (name.toLowerCase()) {
			case "uniform":
				return UNIFORM;
			case "x":
				return X;
			case "y":
				return Y;
			case "z":
				return Z;
		}

		// return something valid by default
		Logger.logWarning("Invalid scale mode requested: " + name);
		return UNIFORM;
	}
};
