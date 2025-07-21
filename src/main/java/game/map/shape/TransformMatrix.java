package game.map.shape;

import static game.map.MapKey.*;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Arrays;

import org.lwjgl.BufferUtils;
import org.w3c.dom.Element;

import common.Vector3f;
import game.map.Axis;
import game.map.MutableAngle;
import game.map.MutablePoint;
import util.Logger;
import util.MathUtil;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlSerializable;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class TransformMatrix implements XmlSerializable
{
	private double[][] mat;

	public transient boolean usePreview;
	public transient boolean baked = true;
	public double[] txRot = new double[3];
	public double[] txScale = { 1.0f, 1.0f, 1.0f };

	public TransformMatrix()
	{
		mat = new double[4][4];
	}

	public TransformMatrix(TransformMatrix other)
	{
		mat = new double[4][4];
		for (int row = 0; row < 4; row++)
			for (int col = 0; col < 4; col++)
				mat[row][col] = other.mat[row][col];

		usePreview = other.usePreview;
		baked = other.baked;
		txRot = new double[] { other.txRot[0], other.txRot[1], other.txRot[2] };
		txScale = new double[] { other.txScale[0], other.txScale[1], other.txScale[2] };
	}

	public TransformMatrix(Axis axis, double angle)
	{
		this();
		setRotation(axis, angle);
	}

	public static TransformMatrix read(XmlReader xmr, Element matrixElement)
	{
		TransformMatrix mat = new TransformMatrix();
		mat.fromXML(xmr, matrixElement);
		return mat;
	}

	@Override
	public void fromXML(XmlReader xmr, Element matrixElem)
	{
		double[] xm = xmr.readDoubleArray(matrixElem, ATTR_TX_MAT_M, 16);
		int k = 0;
		for (int i = 0; i < 4; i++)
			for (int j = 0; j < 4; j++)
				mat[i][j] = xm[k++];

		if (xmr.hasAttribute(matrixElem, ATTR_TX_ROT))
			txRot = xmr.readDoubleArray(matrixElem, ATTR_TX_ROT, 3);

		if (xmr.hasAttribute(matrixElem, ATTR_TX_SCALE))
			txScale = xmr.readDoubleArray(matrixElem, ATTR_TX_SCALE, 3);
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag matrixTag = xmw.createTag(TAG_TX_MAT, true);
		xmw.addDoubleArray(matrixTag, ATTR_TX_MAT_M,
			mat[0][0], mat[0][1], mat[0][2], mat[0][3],
			mat[1][0], mat[1][1], mat[1][2], mat[1][3],
			mat[2][0], mat[2][1], mat[2][2], mat[2][3],
			mat[3][0], mat[3][1], mat[3][2], mat[3][3]);

		if (txRot[0] != 0.0f || txRot[1] != 0.0f || txRot[2] != 0.0f)
			xmw.addDoubleArray(matrixTag, ATTR_TX_ROT, txRot[0], txRot[1], txRot[2]);

		if (txScale[0] != 1.0f || txScale[1] != 1.0f || txScale[2] != 1.0f)
			xmw.addDoubleArray(matrixTag, ATTR_TX_SCALE, txScale[0], txScale[1], txScale[2]);

		xmw.printTag(matrixTag);
	}

	public TransformMatrix deepCopy()
	{
		return new TransformMatrix(this);
	}

	public double get(int row, int col)
	{
		return mat[row][col];
	}

	public void set(double value, int row, int col)
	{
		mat[row][col] = value;
	}

	public void set(TransformMatrix other)
	{
		for (int row = 0; row < 4; row++)
			for (int col = 0; col < 4; col++)
				mat[row][col] = other.mat[row][col];
	}

	/*
	 * The RDP operates on s15.16 format fixed-precision matrices, composed of two
	 * short[4][4] sub-matrices containing the whole and fractional components.
	 *
	 * These matricies are stored in column-major order:
	 * m11 m21 m31 m41, m12 m22 m32 m42, m13 m23 m33 m43, m14 m24 m34 m44
	 */
	public void readRDP(ByteBuffer bb)
	{
		short[][] whole = new short[4][4];
		for (int i = 0; i < 4; i++)
			for (int j = 0; j < 4; j++)
				whole[j][i] = bb.getShort();

		short[][] frac = new short[4][4];
		for (int i = 0; i < 4; i++)
			for (int j = 0; j < 4; j++)
				frac[j][i] = bb.getShort();

		for (int i = 0; i < 4; i++)
			for (int j = 0; j < 4; j++) {
				int v = (whole[i][j] << 16) | (frac[i][j] & 0x0000FFFF);
				mat[i][j] = v / 65536.0;
			}
	}

	public void writeRDP(RandomAccessFile raf) throws IOException
	{
		short[][] whole = new short[4][4];
		short[][] frac = new short[4][4];

		for (int i = 0; i < 4; i++)
			for (int j = 0; j < 4; j++) {
				int v = (int) (mat[i][j] * 65536.0);
				whole[i][j] = (short) (v >> 16);
				frac[i][j] = (short) v;
			}

		for (int i = 0; i < 4; i++)
			for (int j = 0; j < 4; j++)
				raf.writeShort(whole[j][i]);

		for (int i = 0; i < 4; i++)
			for (int j = 0; j < 4; j++)
				raf.writeShort(frac[j][i]);
	}

	@Override
	public int hashCode()
	{
		return Arrays.deepHashCode(mat);
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof TransformMatrix))
			return false;

		TransformMatrix other = (TransformMatrix) obj;
		for (int row = 0; row < 4; row++)
			for (int col = 0; col < 4; col++)
				if (mat[row][col] != other.mat[row][col])
					return false;
		return true;
	}

	public boolean isZero()
	{
		for (int row = 0; row < 4; row++)
			for (int col = 0; col < 4; col++)
				if (mat[row][col] != 0)
					return false;
		return true;
	}

	public void transpose()
	{
		double[][] temp = new double[4][4];

		for (int row = 0; row < 4; row++)
			for (int col = 0; col < 4; col++)
				temp[row][col] = mat[col][row];

		mat = temp;
	}

	public void setIdentity()
	{
		mat = new double[4][4];
		mat[0][0] = 1;
		mat[1][1] = 1;
		mat[2][2] = 1;
		mat[3][3] = 1;
	}

	public void setTranslation(double dx, double dy, double dz)
	{
		setIdentity();
		mat[0][3] = dx;
		mat[1][3] = dy;
		mat[2][3] = dz;
	}

	public void translate(double dx, double dy, double dz)
	{
		mat[0][3] += dx;
		mat[1][3] += dy;
		mat[2][3] += dz;
	}

	public void translate(Vector3f vec)
	{
		mat[0][3] += vec.x;
		mat[1][3] += vec.y;
		mat[2][3] += vec.z;
	}

	public void translateMinus(double dx, double dy, double dz)
	{
		mat[0][3] -= dx;
		mat[1][3] -= dy;
		mat[2][3] -= dz;
	}

	public void translateMinus(Vector3f vec)
	{
		mat[0][3] -= vec.x;
		mat[1][3] -= vec.y;
		mat[2][3] -= vec.z;
	}

	public void makeRotation(Vector3f ax, Vector3f ay, Vector3f az)
	{
		setIdentity();
		ax.normalize();
		ay.normalize();
		az.normalize();

		mat[0][0] = ax.x;
		mat[1][0] = ax.y;
		mat[2][0] = ax.z;

		mat[0][1] = ay.x;
		mat[1][1] = ay.y;
		mat[2][1] = ay.z;

		mat[0][2] = az.x;
		mat[1][2] = az.y;
		mat[2][2] = az.z;
	}

	public void setRotation(Axis axis, double angle)
	{
		mat[3][3] = 1;

		double cosAngle = Math.cos(Math.toRadians(angle));
		double sinAngle = Math.sin(Math.toRadians(angle));

		switch (axis) {
			case X:
				mat[0][0] = 1;
				mat[1][1] = cosAngle;
				mat[2][2] = cosAngle;
				mat[2][1] = sinAngle;
				mat[1][2] = -sinAngle;
				break;
			case Y:
				mat[0][0] = cosAngle;
				mat[2][0] = -sinAngle;
				mat[1][1] = 1;
				mat[0][2] = sinAngle;
				mat[2][2] = cosAngle;
				break;
			case Z:
				mat[0][0] = cosAngle;
				mat[1][1] = cosAngle;
				mat[1][0] = sinAngle;
				mat[0][1] = -sinAngle;
				mat[2][2] = 1;
				break;
		}
	}

	public void rotate(Axis axis, double angle)
	{
		TransformMatrix rot = TransformMatrix.identity();
		rot.setRotation(axis, angle);
		concat(rot);
	}

	public void rotate(double x, double y, double z, double angle)
	{
		double len = Math.sqrt(x * x + y * y + z * z);
		if (len < MathUtil.VERY_SMALL_NUMBER)
			return;

		double nx = x / len;
		double ny = y / len;
		double nz = z / len;

		double cos = Math.cos(Math.toRadians(angle));
		double sin = Math.sin(Math.toRadians(angle));
		double soc = 1.0 - cos;

		TransformMatrix rot = TransformMatrix.identity();

		rot.mat[0][0] = soc * nx * nx + cos;
		rot.mat[0][1] = soc * nx * ny - nz * sin;
		rot.mat[0][2] = soc * nx * nz + ny * sin;

		rot.mat[1][0] = soc * nx * ny + nz * sin;
		rot.mat[1][1] = soc * ny * ny + cos;
		rot.mat[1][2] = soc * ny * nz - nx * sin;

		rot.mat[2][0] = soc * nx * nz - ny * sin;
		rot.mat[2][1] = soc * ny * nz + nx * sin;
		rot.mat[2][2] = soc * nz * nz + cos;

		concat(rot);
	}

	public void scale(double s)
	{
		scale(s, s, s);
	}

	public void scale(double sx, double sy, double sz)
	{
		TransformMatrix scale = TransformMatrix.identity();
		scale.setScale(sx, sy, sz);
		concat(scale);
	}

	public void setScale(double s)
	{
		setScale(s, s, s);
	}

	public void setScale(double sx, double sy, double sz)
	{
		setIdentity();
		mat[0][0] = sx;
		mat[1][1] = sy;
		mat[2][2] = sz;
	}

	/**
	 * Same as T(r).S.T(-r)
	 */
	public void setScaleAbout(double sx, double sy, double sz, double x, double y, double z)
	{
		setIdentity();
		mat[0][0] = sx;
		mat[1][1] = sy;
		mat[2][2] = sz;

		mat[0][3] = x - sx * x;
		mat[1][3] = y - sy * y;
		mat[2][3] = z - sz * z;
	}

	public TransformMatrix getInverse()
	{
		TransformMatrix inverse = new TransformMatrix();

		double s0 = mat[0][0] * mat[1][1] - mat[1][0] * mat[0][1];
		double s1 = mat[0][0] * mat[1][2] - mat[1][0] * mat[0][2];
		double s2 = mat[0][0] * mat[1][3] - mat[1][0] * mat[0][3];
		double s3 = mat[0][1] * mat[1][2] - mat[1][1] * mat[0][2];
		double s4 = mat[0][1] * mat[1][3] - mat[1][1] * mat[0][3];
		double s5 = mat[0][2] * mat[1][3] - mat[1][2] * mat[0][3];

		double c5 = mat[2][2] * mat[3][3] - mat[3][2] * mat[2][3];
		double c4 = mat[2][1] * mat[3][3] - mat[3][1] * mat[2][3];
		double c3 = mat[2][1] * mat[3][2] - mat[3][1] * mat[2][2];
		double c2 = mat[2][0] * mat[3][3] - mat[3][0] * mat[2][3];
		double c1 = mat[2][0] * mat[3][2] - mat[3][0] * mat[2][2];
		double c0 = mat[2][0] * mat[3][1] - mat[3][0] * mat[2][1];

		double invdet = 1.0f / (s0 * c5 - s1 * c4 + s2 * c3 + s3 * c2 - s4 * c1 + s5 * c0);

		if (Math.abs(invdet) < 0.0000001) {
			Logger.logWarning("Could not find inverse for singular matrix.");
			return new TransformMatrix(this);
		}

		inverse.mat[0][0] = (mat[1][1] * c5 - mat[1][2] * c4 + mat[1][3] * c3) * invdet;
		inverse.mat[0][1] = (-mat[0][1] * c5 + mat[0][2] * c4 - mat[0][3] * c3) * invdet;
		inverse.mat[0][2] = (mat[3][1] * s5 - mat[3][2] * s4 + mat[3][3] * s3) * invdet;
		inverse.mat[0][3] = (-mat[2][1] * s5 + mat[2][2] * s4 - mat[2][3] * s3) * invdet;

		inverse.mat[1][0] = (-mat[1][0] * c5 + mat[1][2] * c2 - mat[1][3] * c1) * invdet;
		inverse.mat[1][1] = (mat[0][0] * c5 - mat[0][2] * c2 + mat[0][3] * c1) * invdet;
		inverse.mat[1][2] = (-mat[3][0] * s5 + mat[3][2] * s2 - mat[3][3] * s1) * invdet;
		inverse.mat[1][3] = (mat[2][0] * s5 - mat[2][2] * s2 + mat[2][3] * s1) * invdet;

		inverse.mat[2][0] = (mat[1][0] * c4 - mat[1][1] * c2 + mat[1][3] * c0) * invdet;
		inverse.mat[2][1] = (-mat[0][0] * c4 + mat[0][1] * c2 - mat[0][3] * c0) * invdet;
		inverse.mat[2][2] = (mat[3][0] * s4 - mat[3][1] * s2 + mat[3][3] * s0) * invdet;
		inverse.mat[2][3] = (-mat[2][0] * s4 + mat[2][1] * s2 - mat[2][3] * s0) * invdet;

		inverse.mat[3][0] = (-mat[1][0] * c3 + mat[1][1] * c1 - mat[1][2] * c0) * invdet;
		inverse.mat[3][1] = (mat[0][0] * c3 - mat[0][1] * c1 + mat[0][2] * c0) * invdet;
		inverse.mat[3][2] = (-mat[3][0] * s3 + mat[3][1] * s1 - mat[3][2] * s0) * invdet;
		inverse.mat[3][3] = (mat[2][0] * s3 - mat[2][1] * s1 + mat[2][2] * s0) * invdet;

		return inverse;
	}

	// A.concat(B) is equivalent to A = B * A
	public TransformMatrix concat(TransformMatrix other)
	{
		double[][] temp = new double[4][4];

		for (int row = 0; row < 4; row++)
			for (int col = 0; col < 4; col++) {
				double sum = 0;
				for (int n = 0; n < 4; n++)
					sum += mat[n][col] * other.mat[row][n];
				temp[row][col] = sum;
			}

		mat = temp;
		return this;
	}

	public static TransformMatrix multiply(TransformMatrix left, TransformMatrix right)
	{
		TransformMatrix product = new TransformMatrix();

		for (int row = 0; row < 4; row++)
			for (int col = 0; col < 4; col++) {
				double sum = 0;
				for (int n = 0; n < 4; n++)
					sum += right.mat[n][col] * left.mat[row][n];
				product.mat[row][col] = sum;
			}

		return product;
	}

	public TransformMatrix copyWithPreview()
	{
		TransformMatrix result = deepCopy();
		if (result.usePreview)
			result.bake();
		return result;
	}

	@Deprecated
	public static TransformMatrix multiplyWithPreview(TransformMatrix left, TransformMatrix right)
	{
		TransformMatrix product = new TransformMatrix();
		double[][] leftSource = left.usePreview ? left.getBakedMatrix() : left.mat;
		double[][] rightSource = right.usePreview ? right.getBakedMatrix() : right.mat;

		for (int row = 0; row < 4; row++)
			for (int col = 0; col < 4; col++) {
				double sum = 0;
				for (int n = 0; n < 4; n++)
					sum += rightSource[n][col] * leftSource[row][n];
				product.mat[row][col] = sum;
			}

		return product;
	}

	public static TransformMatrix identity()
	{
		TransformMatrix t = new TransformMatrix();
		t.mat[0][0] = 1.0;
		t.mat[1][1] = 1.0;
		t.mat[2][2] = 1.0;
		t.mat[3][3] = 1.0;
		return t;
	}

	public void perspective(float vfov, float aspectRatio, float nearClip, float farClip)
	{
		float f = (float) (1.0f / Math.tan(Math.toRadians(vfov / 2)));

		mat = new double[4][4];
		mat[0][0] = f / aspectRatio;
		mat[1][1] = f;
		mat[2][2] = (nearClip + farClip) / (nearClip - farClip);
		mat[3][2] = -1.0;
		mat[2][3] = (2 * farClip * nearClip) / (nearClip - farClip);
	}

	public void ortho(float left, float right, float bottom, float top, float near, float far)
	{
		mat = new double[4][4];
		mat[0][0] = 2.0 / (right - left);
		mat[1][1] = 2.0 / (top - bottom);
		mat[2][2] = -2.0 / (far - near);
		mat[0][3] = -(right + left) / (right - left);
		mat[1][3] = -(top + bottom) / (top - bottom);
		mat[2][3] = -(far + near) / (far - near);
		mat[3][3] = 1.0;
	}

	public static TransformMatrix lookAt(Vector3f eye, Vector3f obj, Vector3f up)
	{
		Vector3f forward = Vector3f.sub(eye, obj).normalize();
		if (forward.length() == 0)
			forward = new Vector3f(1.0f, 0.0f, 0.0f);

		Vector3f right = Vector3f.cross(up, forward).normalize();
		Vector3f newUp = Vector3f.cross(forward, right).normalize();

		TransformMatrix t = new TransformMatrix();
		t.mat[0][0] = right.x;
		t.mat[0][1] = right.y;
		t.mat[0][2] = right.z;
		t.mat[0][3] = -Vector3f.dot(right, eye);

		t.mat[1][0] = newUp.x;
		t.mat[1][1] = newUp.y;
		t.mat[1][2] = newUp.z;
		t.mat[1][3] = -Vector3f.dot(newUp, eye);

		t.mat[2][0] = forward.x;
		t.mat[2][1] = forward.y;
		t.mat[2][2] = forward.z;
		t.mat[2][3] = -Vector3f.dot(forward, eye);

		t.mat[3][3] = 1;

		return t;
	}

	public void store(FloatBuffer buffer)
	{
		store(buffer, true);
	}

	public void store(FloatBuffer buffer, boolean flip)
	{
		for (int col = 0; col < 4; col++) {
			for (int row = 0; row < 4; row++)
				buffer.put((float) mat[row][col]);
		}
		if (flip)
			buffer.flip();
	}

	public void load(FloatBuffer buffer)
	{
		buffer.rewind();
		for (int col = 0; col < 4; col++) {
			for (int row = 0; row < 4; row++)
				mat[row][col] = buffer.get();
		}
	}

	public FloatBuffer toFloatBuffer()
	{
		FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
		store(buffer);
		return buffer;
	}

	/**
	 * Applies this transformation matrix to a point and invokes setPosition on it.
	 * This type of transformation is NOT safe for generating backups. Undo/redo will not work.
	 */
	public void forceTransform(MutablePoint p)
	{
		int[] product = new int[3];

		double x = p.getX();
		double y = p.getY();
		double z = p.getZ();

		product[0] = (int) (x * mat[0][0] + y * mat[0][1] + z * mat[0][2] + mat[0][3]);
		product[1] = (int) (x * mat[1][0] + y * mat[1][1] + z * mat[1][2] + mat[1][3]);
		product[2] = (int) (x * mat[2][0] + y * mat[2][1] + z * mat[2][2] + mat[2][3]);

		p.setPosition(product[0], product[1], product[2]);
	}

	/**
	 * Applies this transformation matrix to the src point and invokes setPosition on the dest point.
	 * This type of transformation is NOT safe for generating backups. Undo/redo will not work.
	 */
	public void forceTransform(MutablePoint src, MutablePoint dest)
	{
		int[] product = new int[3];

		double x = src.getX();
		double y = src.getY();
		double z = src.getZ();

		product[0] = (int) (x * mat[0][0] + y * mat[0][1] + z * mat[0][2] + mat[0][3]);
		product[1] = (int) (x * mat[1][0] + y * mat[1][1] + z * mat[1][2] + mat[1][3]);
		product[2] = (int) (x * mat[2][0] + y * mat[2][1] + z * mat[2][2] + mat[2][3]);

		dest.setPosition(product[0], product[1], product[2]);
	}

	/**
	 * Applies this transformation matrix to a {@link MutablePoint} and sets
	 * its temporary position. This type of transformation is NOT safe for
	 * generating backups. Undo/redo will not work.
	 */
	public void applyTransform(MutablePoint p)
	{
		int[] product = new int[3];

		double x = p.getX();
		double y = p.getY();
		double z = p.getZ();

		product[0] = (int) (x * mat[0][0] + y * mat[0][1] + z * mat[0][2] + mat[0][3]);
		product[1] = (int) (x * mat[1][0] + y * mat[1][1] + z * mat[1][2] + mat[1][3]);
		product[2] = (int) (x * mat[2][0] + y * mat[2][1] + z * mat[2][2] + mat[2][3]);

		p.setTempPosition(product[0], product[1], product[2]);
	}

	public Vector3f applyTransform(Vector3f vec)
	{
		Vector3f result = new Vector3f();

		result.x = (float) (vec.x * mat[0][0] + vec.y * mat[0][1] + vec.z * mat[0][2] + mat[0][3]);
		result.y = (float) (vec.x * mat[1][0] + vec.y * mat[1][1] + vec.z * mat[1][2] + mat[1][3]);
		result.z = (float) (vec.x * mat[2][0] + vec.y * mat[2][1] + vec.z * mat[2][2] + mat[2][3]);

		return result;
	}

	/**
	 * Applies this transformation matrix to a {@link MutableAngle} and sets
	 * its temporary angle value. This type of transformation is NOT safe for
	 * generating backups. Undo/redo will not work.
	 */
	public void applyTransform(MutableAngle a)
	{
		double x, y, z;
		double x2, y2, z2;
		double angle = 0;

		//TODO do these matrix elements need to be transposed??
		switch (a.axis) {
			case X:
				y = Math.sin(Math.toRadians(a.getAngle()));
				z = -Math.cos(Math.toRadians(a.getAngle()));
				y2 = (int) (y * mat[1][1] + z * mat[2][1]);
				z2 = (int) (y * mat[1][2] + z * mat[2][2]);
				angle = Math.toDegrees(Math.atan2(y2, -z2));
				break;
			case Y:
				x = Math.cos(Math.toRadians(a.getAngle()));
				z = -Math.sin(Math.toRadians(a.getAngle()));
				x2 = (x * mat[0][0] + z * mat[2][0]);
				z2 = (x * mat[0][2] + z * mat[2][2]);
				angle = Math.toDegrees(Math.atan2(-z2, x2));
				break;
			case Z:
				x = Math.cos(Math.toRadians(a.getAngle()));
				y = Math.sin(Math.toRadians(a.getAngle()));
				x2 = (x * mat[0][0] + y * mat[1][0]);
				y2 = (x * mat[0][1] + y * mat[1][1]);
				angle = Math.toDegrees(Math.atan2(y, x));
				break;
			default:
				throw new UnsupportedOperationException("MutableAngle axis must be a basis vector.");
		}

		a.setTempAngle(angle);
	}

	/**
	 * Applies this transformation matrix to the src point and invokes setTransformPosition on the
	 * dest point. This type of transformation is safe for generating backups and undo/redo.
	 * @param src
	 * @param dest
	 */
	public void applyTransform(MutablePoint src, MutablePoint dest)
	{
		int[] product = new int[3];

		double x = src.getX();
		double y = src.getY();
		double z = src.getZ();

		product[0] = (int) (x * mat[0][0] + y * mat[0][1] + z * mat[0][2] + mat[0][3]);
		product[1] = (int) (x * mat[1][0] + y * mat[1][1] + z * mat[1][2] + mat[1][3]);
		product[2] = (int) (x * mat[2][0] + y * mat[2][1] + z * mat[2][2] + mat[2][3]);

		dest.setTempPosition(product[0], product[1], product[2]);
	}

	public static void main(String args[])
	{
		TransformMatrix a = new TransformMatrix();
		TransformMatrix b = new TransformMatrix();

		a.setRotation(Axis.X, -45);
		b.setRotation(Axis.Y, 60);

		//a.rotate(Axis.Z, 90);
		System.out.println("A =");
		System.out.println(a);
		System.out.println("B =");
		System.out.println(b);
		System.out.println("A * B");
		System.out.println(multiply(a, b));
		System.out.println("B * A");
		System.out.println(multiply(b, a));

		a.concat(b);
		System.out.println("A cat b");
		System.out.println(a);

		TransformMatrix m = new TransformMatrix();
		m.setIdentity();
		m.scale(21.0, 1.0, 3.0);
		m.rotate(Axis.X, 80);
		m.rotate(Axis.Y, 45);
		m.rotate(Axis.Z, 30);

		DecomposedTransform decomp = getDecomposition(m);
		System.out.println(decomp);
	}

	@Override
	public String toString()
	{
		StringBuilder sb = new StringBuilder();

		for (int row = 0; row < 4; row++) {
			sb.append(String.format("%f %f %f %f\n",
				//	sb.append(String.format("%9.2f %9.2f %9.2f %9.2f\n",
				mat[row][0], mat[row][1], mat[row][2], mat[row][3]));
		}

		return sb.toString();
	}

	public double[][] getBakedMatrix()
	{
		TransformMatrix baked = new TransformMatrix();
		baked.setIdentity();
		baked.scale(txScale[0], txScale[1], txScale[2]);

		TransformMatrix RX = new TransformMatrix(Axis.X, txRot[0]);
		TransformMatrix RY = new TransformMatrix(Axis.Y, txRot[1]);
		TransformMatrix RZ = new TransformMatrix(Axis.Z, txRot[2]);
		/*
			switch((RotationOrder)bakeOrderComboBox.getSelectedItem())
			{
			case XYZ:	mat.concat(RX).concat(RY).concat(RZ); break;
			case XZY:	mat.concat(RX).concat(RZ).concat(RY); break;
			case YXZ:	mat.concat(RY).concat(RX).concat(RZ); break;
			case YZX:	mat.concat(RY).concat(RZ).concat(RX); break;
			case ZXY:	mat.concat(RZ).concat(RX).concat(RY); break;
			case ZYX:	mat.concat(RZ).concat(RY).concat(RX); break;
			}
		*/
		baked.concat(RX).concat(RY).concat(RZ);
		baked.translate(mat[0][3], mat[1][3], mat[2][3]);
		return baked.mat;
	}

	private void bake()
	{
		double[][] baked = getBakedMatrix();
		for (int i = 0; i < 4; i++)
			for (int j = 0; j < 4; j++)
				mat[i][j] = baked[i][j];
	}

	public static class DecomposedTransform
	{
		public double tx, ty, tz;
		public double rx, ry, rz;
		public double sx, sy, sz;

		@Override
		public String toString()
		{
			return String.format("trn = %f %f %f%nrot = %f %f %f%nsca = %f %f %f", tx, ty, tz, rx, ry, rz, sx, sy, sz);
		}
	}

	public void decompose()
	{
		DecomposedTransform decomp = getDecomposition(this);
		if (decomp == null)
			return;

		txRot[0] = decomp.rx;
		txRot[1] = decomp.ry;
		txRot[2] = decomp.rz;

		txScale[0] = decomp.sx;
		txScale[1] = decomp.sy;
		txScale[2] = decomp.sz;
	}

	public static DecomposedTransform getDecomposition(TransformMatrix tx)
	{
		if (tx.mat[3][0] != 0.0)
			return null;
		if (tx.mat[3][1] != 0.0)
			return null;
		if (tx.mat[3][2] != 0.0)
			return null;
		if (tx.mat[3][3] != 1.0)
			return null;

		DecomposedTransform decomp = new DecomposedTransform();
		decomp.tx = tx.mat[0][3];
		decomp.ty = tx.mat[1][3];
		decomp.tz = tx.mat[2][3];

		Vector3f col0 = new Vector3f((float) tx.mat[0][0], (float) tx.mat[1][0], (float) tx.mat[2][0]);
		Vector3f col1 = new Vector3f((float) tx.mat[0][1], (float) tx.mat[1][1], (float) tx.mat[2][1]);
		Vector3f col2 = new Vector3f((float) tx.mat[0][2], (float) tx.mat[1][2], (float) tx.mat[2][2]);

		// assume non-negative scale
		decomp.sx = col0.length();
		decomp.sy = col1.length();
		decomp.sz = col2.length();

		double[][] rot = new double[3][3];

		rot[0][0] = tx.mat[0][0] / decomp.sx;
		rot[1][0] = tx.mat[1][0] / decomp.sx;
		rot[2][0] = tx.mat[2][0] / decomp.sx;

		rot[0][1] = tx.mat[0][1] / decomp.sy;
		rot[1][1] = tx.mat[1][1] / decomp.sy;
		rot[2][1] = tx.mat[2][1] / decomp.sy;

		rot[0][2] = tx.mat[0][2] / decomp.sz;
		rot[1][2] = tx.mat[1][2] / decomp.sz;
		rot[2][2] = tx.mat[2][2] / decomp.sz;

		// assume XYZ, that is, v' = RzRyRx(v)
		decomp.rx = Math.toDegrees(Math.atan2(rot[2][1], rot[2][2]));
		decomp.ry = Math.toDegrees(Math.atan2(-rot[2][0], Math.sqrt(rot[2][1] * rot[2][1] + rot[2][2] * rot[2][2])));
		decomp.rz = Math.toDegrees(Math.atan2(rot[1][0], rot[0][0]));

		// clean up rounding errrors -- assume no more than 3 decimal digits
		decomp.sx = Math.round(decomp.sx * 1000.0) / 1000.0;
		decomp.sy = Math.round(decomp.sy * 1000.0) / 1000.0;
		decomp.sz = Math.round(decomp.sz * 1000.0) / 1000.0;
		decomp.rx = Math.round(decomp.rx * 1000.0) / 1000.0;
		decomp.ry = Math.round(decomp.ry * 1000.0) / 1000.0;
		decomp.rz = Math.round(decomp.rz * 1000.0) / 1000.0;

		return decomp;
	}
}
