package game.map.shading;

import com.google.gson.annotations.SerializedName;

public enum FalloffType
{
	@SerializedName("uniform")
	Uniform,
	@SerializedName("linear")
	Linear,
	@SerializedName("quadratic")
	Quadratic;
}
