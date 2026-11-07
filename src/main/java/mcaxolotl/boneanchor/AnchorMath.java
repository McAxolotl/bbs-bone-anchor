package mcaxolotl.boneanchor;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class AnchorMath {
    public record Pose(Vector3f position, Quaternionf rotation, Vector3f scale) {}

    public static Matrix4f parentLocal(Matrix4f parentWorld, Matrix4f attachmentWorld) {
        if (!attachmentWorld.isFinite()) throw new IllegalArgumentException("Anchor frame is non-finite or singular");
        // BBS attachment frames include a half turn after the bone pivot frame.
        return inverse(parentWorld).mul(attachmentWorld).rotateY((float)Math.PI);
    }

    /** Invert a frame only after proving it cannot turn downstream matrices into NaNs. */
    public static Matrix4f inverse(Matrix4f matrix) {
        float determinant = matrix == null || !matrix.isFinite() ? Float.NaN : matrix.determinant();
        if (!Float.isFinite(determinant) || Math.abs(determinant) < 1e-8f)
            throw new IllegalArgumentException("Anchor frame is non-finite or singular");
        return new Matrix4f(matrix).invert();
    }

    /** Reject shear or singular transforms the cubic bone's TRS fields cannot represent. */
    public static Pose decompose(Matrix4f matrix) {
        Vector3f x = matrix.getColumn(0, new Vector3f());
        Vector3f y = matrix.getColumn(1, new Vector3f());
        Vector3f z = matrix.getColumn(2, new Vector3f());
        Vector3f scale = new Vector3f(x.length(), y.length(), z.length());
        float determinant = matrix.determinant();
        if (!matrix.isFinite() || !scale.isFinite() || scale.x < 1e-6f || scale.y < 1e-6f || scale.z < 1e-6f
            || Math.abs(determinant) < 1e-8f || Math.abs(matrix.m03()) > 1e-6f || Math.abs(matrix.m13()) > 1e-6f
            || Math.abs(matrix.m23()) > 1e-6f || Math.abs(matrix.m33() - 1f) > 1e-6f) {
            throw new IllegalArgumentException("Anchor transform has zero scale or an invalid matrix");
        }
        // Match BBS Transform.fromMatrix: a mirrored basis keeps its sign on X.
        if (determinant < 0f) scale.x = -scale.x;
        x.div(scale.x); y.div(scale.y); z.div(scale.z);
        if (Math.abs(x.dot(y)) > 1e-4f || Math.abs(x.dot(z)) > 1e-4f || Math.abs(y.dot(z)) > 1e-4f) {
            throw new IllegalArgumentException("Anchor transform contains unsupported shear");
        }
        Matrix3f rotationBasis = new Matrix3f().setColumn(0, x).setColumn(1, y).setColumn(2, z);
        Quaternionf rotation = new Quaternionf().setFromNormalized(rotationBasis);
        if (!rotation.isFinite()) throw new IllegalArgumentException("Anchor rotation is non-finite");
        return new Pose(matrix.getTranslation(new Vector3f()), rotation, scale);
    }

    /** Copy affine bases intact when possible; never silently approximate shear for a mixed basis. */
    public static Matrix4f filter(Matrix4f target,Matrix4f fallback,boolean position,boolean rotation,boolean scale) {
        Vector3f translation=(position?target:fallback).getTranslation(new Vector3f());
        if(rotation==scale)return new Matrix4f(rotation?target:fallback).setTranslation(translation);
        Pose targetPose=decompose(target),basePose=decompose(fallback);
        return new Matrix4f().translationRotateScale(translation,rotation?targetPose.rotation:basePose.rotation,
            scale?targetPose.scale:basePose.scale);
    }

    /** Express a world frame relative to a parent frame. */
    public static Matrix4f local(Matrix4f parentWorld, Matrix4f world) {
        if (world == null || !world.isFinite()) throw new IllegalArgumentException("Anchor world frame is non-finite");
        Matrix4f local = inverse(parentWorld).mul(world);
        if (!local.isFinite()) throw new IllegalArgumentException("Anchor local frame is non-finite");
        return local;
    }

    public static Vector3f offset(Vector3f desiredPivot, Vector3f translate, Vector3f restPivot) {
        return new Vector3f(desiredPivot).sub((2 * restPivot.x - translate.x) / 16f, translate.y / 16f, translate.z / 16f);
    }
    private AnchorMath() {}
}
