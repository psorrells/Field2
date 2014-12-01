package field.linalg;

import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.DoubleBuffer;

/**
 * A class representing a 3-vector (both a position and direction in 3-space).
 */
public class Vec3 {


	private static final long serialVersionUID = 1L;

	public double x, y, z;

	/**
	 * Constructor for Vec3.
	 */
	public Vec3() {
		super();
	}


	/**
	 * Constructor
	 */
	public Vec3(double x, double y, double z) {
		set(x, y, z);
	}

	public Vec3(Vec3 to) {
		set(to.x, to.y, to.z);
	}

	public Vec3(FloatBuffer f) {
		set(f.get(), f.get(), f.get());
	}

	/* (non-Javadoc)
	 * @see org.lwjgl.util.vector.WritableVector2f#set(double, double)
	 */
	public void set(double x, double y) {
		this.x = x;
		this.y = y;
	}

	/* (non-Javadoc)
	 * @see org.lwjgl.util.vector.WritableVector3#set(double, double, double)
	 */
	public void set(double x, double y, double z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	/**
	 * Load from another Vec3
	 *
	 * @param src The source vector
	 * @return this
	 */
	public Vec3 set(Vec3 src) {
		x = src.getX();
		y = src.getY();
		z = src.getZ();
		return this;
	}

	/**
	 * @return the length of the vector
	 */
	public final double length() {
		return (double) Math.sqrt(lengthSquared());
	}

	/**
	 * @return the length squared of the vector
	 */
	public double lengthSquared() {
		return x * x + y * y + z * z;
	}

	/**
	 * Translate a vector
	 *
	 * @param x The translation in x
	 * @param y the translation in y
	 * @return this
	 */
	public Vec3 translate(double x, double y, double z) {
		this.x += x;
		this.y += y;
		this.z += z;
		return this;
	}

	/**
	 * Add a Vec3 to another Vec3 and place the result in a destination
	 * vector.
	 *
	 * @param left  The LHS vector
	 * @param right The RHS vector
	 * @param dest  The destination vector, or null if a new Vec3 is to be created
	 * @return the sum of left and right in dest
	 */
	public static Vec3 add(Vec3 left, Vec3 right, Vec3 dest) {
		if (dest == null) return new Vec3(left.x + right.x, left.y + right.y, left.z + right.z);
		else {
			dest.set(left.x + right.x, left.y + right.y, left.z + right.z);
			return dest;
		}
	}


	/**
	 * Add a Vec3 to another Vec3 times a scalar and place the result in a destination
	 * vector.
	 *
	 * @param left  The LHS vector
	 * @param w the weight
	 * @param right The RHS vector
	 * @param dest  The destination vector, or null if a new Vec3 is to be created
	 * @return the sum of left and right in dest
	 */
	public static Vec3 add(Vec3 left, double w, Vec3 right, Vec3 dest) {
		if (dest == null) return new Vec3(left.x + w*right.x, left.y + w*right.y, left.z + w*right.z);
		else {
			dest.set(left.x + w*right.x, left.y + w*right.y, left.z + w*right.z);
			return dest;
		}
	}

	/**
	 * Subtract a Vec3 from another Vec3 and place the result in a destination
	 * vector.
	 *
	 * @param left  The LHS vector
	 * @param right The RHS vector
	 * @param dest  The destination vector, or null if a new Vec3 is to be created
	 * @return left minus right in dest
	 */
	public static Vec3 sub(Vec3 left, Vec3 right, Vec3 dest) {
		if (dest == null) return new Vec3(left.x - right.x, left.y - right.y, left.z - right.z);
		else {
			dest.set(left.x - right.x, left.y - right.y, left.z - right.z);
			return dest;
		}
	}

	/**
	 * The cross product of two vectors.
	 *
	 * @param left  The LHS vector
	 * @param right The RHS vector
	 * @param dest  The destination result, or null if a new Vec3 is to be created
	 * @return left cross right
	 */
	public static Vec3 cross(Vec3 left, Vec3 right, Vec3 dest) {

		if (dest == null) dest = new Vec3();

		dest.set(left.y * right.z - left.z * right.y, right.x * left.z - right.z * left.x, left.x * right.y - left.y * right.x);

		return dest;
	}


	/**
	 * Negate a vector
	 *
	 * @return this
	 */
	public Vec3 negate() {
		x = -x;
		y = -y;
		z = -z;
		return this;
	}

	/**
	 * Negate a Vec3 and place the result in a destination vector.
	 *
	 * @param dest The destination Vec3 or null if a new Vec3 is to be created
	 * @return the negated vector
	 */
	public Vec3 negate(Vec3 dest) {
		if (dest == null) dest = new Vec3();
		dest.x = -x;
		dest.y = -y;
		dest.z = -z;
		return dest;
	}


	/**
	 * Normalise this Vec3 and place the result in another vector.
	 *
	 * @param dest The destination vector, or null if a new Vec3 is to be created
	 * @return the normalised vector
	 */
	public Vec3 normalise(Vec3 dest) {
		double l = length();

		if (dest == null) dest = new Vec3(x / l, y / l, z / l);
		else dest.set(x / l, y / l, z / l);

		return dest;
	}

	/**
	 * Normalise this Vec3 inplace.
	 *
	 * @return this
	 */
	public Vec3 normalise() {
		double l = length();

		set(x / l, y / l, z / l);
		return this;
	}

	/**
	 * The dot product of two vectors is calculated as
	 * v1.x * v2.x + v1.y * v2.y + v1.z * v2.z
	 *
	 * @param left  The LHS vector
	 * @param right The RHS vector
	 * @return left dot right
	 */
	public static double dot(Vec3 left, Vec3 right) {
		return left.x * right.x + left.y * right.y + left.z * right.z;
	}

	/**
	 * Calculate the angle between two vectors, in radians
	 *
	 * @param a A vector
	 * @param b The other vector
	 * @return the angle between the two vectors, in radians
	 */
	public static double angle(Vec3 a, Vec3 b) {
		double dls = dot(a, b) / (a.length() * b.length());
		if (dls < -1f) dls = -1f;
		else if (dls > 1.0f) dls = 1.0f;
		return (double) Math.acos(dls);
	}

	public Vec3 load(FloatBuffer buf) {
		x = buf.get();
		y = buf.get();
		z = buf.get();
		return this;
	}

	public Vec3 load(DoubleBuffer buf) {
		x = buf.get();
		y = buf.get();
		z = buf.get();
		return this;
	}

	public Vec3 scale(double scale) {

		x *= scale;
		y *= scale;
		z *= scale;

		return this;

	}

	public Vec3 store(FloatBuffer buf) {

		buf.put((float)x);
		buf.put((float)y);
		buf.put((float)z);

		return this;
	}

	public Vec3 store(DoubleBuffer buf) {

		buf.put((float)x);
		buf.put((float)y);
		buf.put((float)z);

		return this;
	}

	public String toString() {
		StringBuilder sb = new StringBuilder(64);

		sb.append("Vec3[");
		sb.append(x);
		sb.append(", ");
		sb.append(y);
		sb.append(", ");
		sb.append(z);
		sb.append(']');
		return sb.toString();
	}

	/**
	 * @return x
	 */
	public final double getX() {
		return x;
	}

	/**
	 * @return y
	 */
	public final double getY() {
		return y;
	}

	/**
	 * Set X
	 *
	 * @param x
	 */
	public final void setX(double x) {
		this.x = x;
	}

	/**
	 * Set Y
	 *
	 * @param y
	 */
	public final void setY(double y) {
		this.y = y;
	}

	/**
	 * Set Z
	 *
	 * @param z
	 */
	public void setZ(double z) {
		this.z = z;
	}

	public double getZ() {
		return z;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Vec3)) return false;

		Vec3 vec3 = (Vec3) o;

		if (Double.compare(vec3.x, x) != 0) return false;
		if (Double.compare(vec3.y, y) != 0) return false;
		if (Double.compare(vec3.z, z) != 0) return false;

		return true;
	}

	@Override
	public int hashCode() {
		long result = (x != +0.0f ? Double.doubleToLongBits(x) : 0);
		result = 31 * result + (y != +0.0f ? Double.doubleToLongBits(y) : 0);
		result = 31 * result + (z != +0.0f ? Double.doubleToLongBits(z) : 0);
		return (int)(result ^ (result >>> 32));
	}

	public double distanceFrom(Vec3 v) {
		return Math.sqrt((v.x-x)*(v.x-x)+(v.y-y)*(v.y-y)+(v.z-z)*(v.z-z));
	}


	/**
	 * returns Vec2(x, y);
	 */
	public Vec2 toVec2() {
		return new Vec2(x, y);
	}

	/**
	 * blend two Vec3 to create a third. out can contain a pre-allocated return Vec3 or null
	 */

	static public Vec3 lerp(Vec3 a, Vec3 b, double alpha, Vec3 out)
	{
		if (out==null) out = new Vec3();

		out.x = a.x*alpha+b.x*(1-alpha);
		out.y = a.y*alpha+b.y*(1-alpha);
		out.z = a.z*alpha+b.z*(1-alpha);

		return out;
	}


	/**
	 * set this Vec3 to the blend of two Vec3
	 */
	public Vec3 lerp(Vec3 a, Vec3 b, double alpha)
	{
		this.x = a.x*alpha+b.x*(1-alpha);
		this.y = a.y*alpha+b.y*(1-alpha);
		this.z = a.z*alpha+b.z*(1-alpha);

		return this;
	}

	/**
	 * copies this Vec3
	 */
	public Vec3 clone()
	{
		return new Vec3(this);
	}


	/**
	 * Dot product of this and another Vec3
	 */
	public double dot(Vec3 a)
	{
		return Vec3.dot(this, a);
	}

}
