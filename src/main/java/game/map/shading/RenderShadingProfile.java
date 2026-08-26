package game.map.shading;

import java.util.ArrayList;
import java.util.List;

import common.BaseCamera;
import common.Vector3f;
import game.map.Axis;
import game.map.Map;
import game.map.marker.LightComponent;
import game.map.marker.Marker;
import game.map.marker.Marker.MarkerType;
import game.map.shape.TransformMatrix;
import game.sprite.Sprite;
import renderer.shaders.scene.SpriteShader;
import util.ColorUtils;
import util.MathUtil;

/**
 * Shading profile used during rendering, collected from Marker LightComponents
 */
public class RenderShadingProfile
{
	public static class RenderShadingLight
	{
		public final boolean enabled;
		public final int[] rgb = new int[3];
		public final int[] pos = new int[3];
		public final float falloff;
		public final FalloffType falloffType;

		public RenderShadingLight(LightComponent comp)
		{
			int color[] = ColorUtils.unpack(comp.color.get());
			rgb[0] = color[0];
			rgb[1] = color[1];
			rgb[2] = color[2];

			pos[0] = comp.parentMarker.position.getX();
			pos[1] = comp.parentMarker.position.getY();
			pos[2] = comp.parentMarker.position.getZ();

			falloff = (float) LightComponent.dist2coeff(comp.falloffType, comp.falloffDist);
			falloffType = comp.falloffType;
			enabled = comp.enabled.get();
		}
	}

	public final boolean enabled;
	public final int intensity;
	public final int[] baseColor = new int[3];
	public final List<RenderShadingLight> lights = new ArrayList<>();

	public RenderShadingProfile(Map map)
	{
		enabled = map.features.hasSpriteShading.get();
		intensity = map.features.shadingOffset.get();

		int rgb[] = ColorUtils.unpack(map.features.shadingBaseColor.get());
		baseColor[0] = rgb[0];
		baseColor[1] = rgb[1];
		baseColor[2] = rgb[2];

		lights.clear();

		if (map == null || map.features == null)
			return;

		for (Marker m : map.markerTree) {
			if (m.getType() == MarkerType.Light) {
				lights.add(new RenderShadingLight(m.lightComponent));
				if (lights.size() == 8)
					break; // only the first 7 lights count, enabled or not
			}
		}
	}

	private TransformMatrix viewMtx = new TransformMatrix();
	private TransformMatrix modelMtx = new TransformMatrix();
	private TransformMatrix projViewMtx = null;

	private float[] highlightColor = new float[3];
	private float[] shadowColor = new float[3];
	private float[] offset = new float[3];

	public void setSpriteRenderingPos(BaseCamera camera, float x, float y, float z, float yaw)
	{
		// must be composed in EXACTLY this order!

		modelMtx.setIdentity();
		modelMtx.scale(Sprite.WORLD_SCALE, Sprite.WORLD_SCALE, Sprite.WORLD_SCALE);
		modelMtx.rotate(Axis.Y, yaw);
		modelMtx.translate(new Vector3f(x, y, z));

		viewMtx.setIdentity();
		viewMtx.translate(new Vector3f(-camera.pos.x, -camera.pos.y, -camera.pos.z));
		viewMtx.rotate(Axis.Y, camera.yaw);
		viewMtx.rotate(Axis.X, camera.pitch);

		projViewMtx = TransformMatrix.multiply(camera.projMatrix, viewMtx);
	}

	public void calculateShaderParams(TransformMatrix mtx)
	{
		double Myx = modelMtx.get(1, 0);
		double Myy = modelMtx.get(1, 1);
		double Myz = modelMtx.get(1, 2);

		double Mzx = modelMtx.get(2, 0);
		double Pzx = projViewMtx.get(2, 0);

		double Mzy = modelMtx.get(2, 1);

		double Mzz = modelMtx.get(2, 2);
		double Pzz = projViewMtx.get(2, 2);

		float[] lightDir = new float[3];
		float[] commonColor = new float[3];

		float[] frontHighlightColor = new float[3];
		float[] backHighlightColor = new float[3];

		highlightColor = new float[3];
		shadowColor = new float[3];
		offset = new float[2];

		shadowColor[0] = baseColor[0];
		shadowColor[1] = baseColor[1];
		shadowColor[2] = baseColor[2];

		boolean facingLeft = ((Mzz * Pzz - Mzx * Pzx) < 0);

		for (RenderShadingLight source : lights) {
			if (!source.enabled)
				continue;

			// might not be correct, unsure if we're adding component offsets correctly here
			double dx = (modelMtx.get(0, 3) + mtx.get(3, 0)) - source.pos[0];
			double dy = (modelMtx.get(1, 3) + mtx.get(3, 1)) - source.pos[1];
			double dz = (modelMtx.get(2, 3) + mtx.get(3, 2)) - source.pos[2];
			double dist2 = dx * dx + dy * dy + dz * dz;

			double dist = 0.0;
			double invDist = 0.0;
			if (dist2 > MathUtil.VERY_SMALL_NUMBER) {
				dist = Math.sqrt(dist2);
				invDist = 1.0 / dist;
			}

			double nx = dx * invDist;
			double ny = dy * invDist;
			double nz = dz * invDist;

			double intensityScale = 1.0;

			if (source.falloffType == FalloffType.Linear) {
				// logic seems sus, should be AND i think. bug in original source?
				if (dist != 0.0 || source.falloff != 0.0)
					intensityScale = 1.0 / (dist * source.falloff);
			}
			else if (source.falloffType == FalloffType.Quadratic) {
				if (dist2 != 0.0 || source.falloff != 0.0)
					intensityScale = 1.0 / (dist2 * source.falloff);
			}

			nx *= intensityScale;
			ny *= intensityScale;
			nz *= intensityScale;

			lightDir[0] += nx;
			lightDir[1] += ny;
			lightDir[2] += nz;

			if (1.0 < intensityScale)
				intensityScale = 1.0;

			double Uzx = facingLeft ? -Mzx : Mzx;
			double Uzz = facingLeft ? Mzz : -Mzz;
			double Su = Uzx * nx + Mzy * ny + Uzz * nz;
			double Ru = intensityScale * Math.abs(Su);

			double Vzx = facingLeft ? -Mzx : Mzx;
			double Vzz = facingLeft ? -Mzz : Mzz;
			double Sv = Vzz * nx + Mzy * ny + Vzx * nz;
			double Rv = intensityScale * Math.abs(Sv);

			int[] lightColor = source.rgb;

			if (0.0 < Su) {
				commonColor[0] += lightColor[0] * Ru;
				commonColor[1] += lightColor[1] * Ru;
				commonColor[2] += lightColor[2] * Ru;
			}
			else {
				shadowColor[0] += lightColor[0] * Ru;
				shadowColor[1] += lightColor[1] * Ru;
				shadowColor[2] += lightColor[2] * Ru;
			}

			if (0.0 < Sv) {
				backHighlightColor[0] += lightColor[0] * Rv;
				backHighlightColor[1] += lightColor[1] * Rv;
				backHighlightColor[2] += lightColor[2] * Rv;
			}
			else {
				frontHighlightColor[0] += lightColor[0] * Rv;
				frontHighlightColor[1] += lightColor[1] * Rv;
				frontHighlightColor[2] += lightColor[2] * Rv;
			}
		}

		double Wzx = facingLeft ? -Mzx : Mzx;
		double Wzz = facingLeft ? -Mzz : Mzz;

		if (0.0 < Wzz * lightDir[0] + Mzy * lightDir[1] + Wzx * lightDir[2]) {
			highlightColor[0] = baseColor[0] + commonColor[0] + backHighlightColor[0];
			highlightColor[1] = baseColor[1] + commonColor[1] + backHighlightColor[1];
			highlightColor[2] = baseColor[2] + commonColor[2] + backHighlightColor[2];
		}
		else {
			highlightColor[0] = baseColor[0] + commonColor[0] + frontHighlightColor[0];
			highlightColor[1] = baseColor[1] + commonColor[1] + frontHighlightColor[1];
			highlightColor[2] = baseColor[2] + commonColor[2] + frontHighlightColor[2];
		}

		// in appendGfx function

		double lightDir2 = lightDir[0] * lightDir[0] + lightDir[1] * lightDir[1] + lightDir[2] * lightDir[2];

		double ambientPower = intensity;
		if (lightDir2 < 1.0)
			ambientPower = (int) (ambientPower * lightDir2);

		double invLightDir = 0.0;
		if (lightDir2 > MathUtil.VERY_SMALL_NUMBER)
			invLightDir = 1.0 / Math.sqrt(lightDir2);

		double nlx = lightDir[0] * invLightDir;
		double nly = lightDir[1] * invLightDir;
		double nlz = lightDir[2] * invLightDir;

		// this will be SLIGHTLY off since Pzz != in-game Pzz
		// it should be 1.0, but its not due to floating point shenanigans
		// to see for yourself, the value is loaded at [80148BA0]
		// everything else is accurate
		offset[0] = (float) (ambientPower * (nlz * Pzx - nlx * Pzz));
		if (!facingLeft)
			offset[0] *= -1.0f;

		double R2 = nlx * nlx + nlz * nlz;
		double R = R2;
		if (R2 > MathUtil.VERY_SMALL_NUMBER)
			R = Math.sqrt(R2);

		double K2 = Myx * Myx + Myz * Myz;
		double K = K2;
		if (K2 > MathUtil.VERY_SMALL_NUMBER)
			K = Math.sqrt(K2);

		offset[1] = (float) (-ambientPower * (R * K + Myy * nly));

		if (shadowColor[0] > 255.0)
			shadowColor[0] = 255.0f;
		if (shadowColor[1] > 255.0)
			shadowColor[1] = 255.0f;
		if (shadowColor[2] > 255.0)
			shadowColor[2] = 255.0f;

		if (highlightColor[0] > 255.0)
			highlightColor[0] = 255.0f;
		if (highlightColor[1] > 255.0)
			highlightColor[1] = 255.0f;
		if (highlightColor[2] > 255.0)
			highlightColor[2] = 255.0f;
	}

	public void setShaderParams(SpriteShader shader)
	{
		shader.shadingOffset.set(offset[0], -offset[1]);
		shader.shadingShadow.set(shadowColor[0], shadowColor[1], shadowColor[2]);
		shader.shadingHighlight.set(highlightColor[0], highlightColor[1], highlightColor[2]);
	}
}
