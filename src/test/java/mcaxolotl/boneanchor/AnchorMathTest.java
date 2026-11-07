package mcaxolotl.boneanchor;
import org.joml.*;
// org.joml exports its own Math, so java.lang.Math must stay explicit.
import java.lang.Math;
public final class AnchorMathTest {
    private static int count;
    private static void near(Vector3f actual, Vector3f expected) {
        count++;
        if (!actual.isFinite() || actual.distance(expected) > 1e-4f) throw new AssertionError(actual + " != " + expected);
    }
    private static void check(boolean condition, String message) { count++; if (!condition) throw new AssertionError(message); }
    public static void main(String[] args) {
        // World target under a rotated, translated, scaled model/parent must land on the hand once.
        Matrix4f parent = new Matrix4f().translation(5,-3,8).rotateXYZ(.3f,.9f,-.2f).scale(1.25f);
        Matrix4f hand = new Matrix4f().translation(7,1,10).rotateXYZ(.1f,-.7f,.5f).scale(.75f);
        Matrix4f local = AnchorMath.parentLocal(parent, hand);
        Matrix4f rebuilt = new Matrix4f(parent).mul(local).rotateY((float)Math.PI);
        near(rebuilt.getTranslation(new Vector3f()), hand.getTranslation(new Vector3f()));
        near(rebuilt.transformDirection(new Vector3f(1,0,0)), hand.transformDirection(new Vector3f(1,0,0)));
        near(rebuilt.transformDirection(new Vector3f(0,1,0)), hand.transformDirection(new Vector3f(0,1,0)));
        Vector3f pivot = new Vector3f(2,10,-4), channels = new Vector3f(5,12,-7), desired = new Vector3f(1,2,3);
        Vector3f offset = AnchorMath.offset(desired,channels,pivot);
        near(new Vector3f((2*pivot.x-channels.x)/16f,channels.y/16f,channels.z/16f).add(offset),desired);
        // Re-evaluation must compute an absolute offset, never add last frame's shift.
        near(AnchorMath.offset(desired,channels,pivot),offset);
        Matrix4f parent2 = new Matrix4f().translation(-9,6,2).rotateY(1.3f).scale(2);
        Matrix4f local2 = AnchorMath.parentLocal(parent2, hand);
        near(new Matrix4f(parent2).mul(local2).getTranslation(new Vector3f()),hand.getTranslation(new Vector3f()));
        try { AnchorMath.parentLocal(new Matrix4f().scale(0),hand); throw new AssertionError("Singular parent accepted"); }
        catch (IllegalArgumentException expected) { count++; }
        try { AnchorMath.inverse(new Matrix4f().scale(0)); throw new AssertionError("Singular frame inverted"); }
        catch (IllegalArgumentException expected) { count++; }
        try { AnchorMath.inverse(new Matrix4f().m00(Float.NaN)); throw new AssertionError("Non-finite frame inverted"); }
        catch (IllegalArgumentException expected) { count++; }
        AnchorMath.Pose pose = AnchorMath.decompose(local);
        Matrix4f recomposed = new Matrix4f().translationRotateScale(pose.position(), pose.rotation(), pose.scale());
        near(recomposed.transformPosition(new Vector3f(1,2,3)), local.transformPosition(new Vector3f(1,2,3)));
        Matrix4f mirrored = new Matrix4f().translation(2,-4,7).rotateXYZ(.4f,-.2f,.8f).scale(-1.5f,2f,3f);
        AnchorMath.Pose mirroredPose = AnchorMath.decompose(mirrored);
        check(mirroredPose.scale().x < 0f, "Mirrored scale keeps its sign on X");
        Matrix4f mirroredAgain = new Matrix4f().translationRotateScale(mirroredPose.position(),mirroredPose.rotation(),mirroredPose.scale());
        near(mirroredAgain.transformPosition(new Vector3f(1,2,3)), mirrored.transformPosition(new Vector3f(1,2,3)));
        Vector3f mirroredDirection = mirroredAgain.transformDirection(new Vector3f(1,0,0));
        near(mirroredDirection, mirrored.transformDirection(new Vector3f(1,0,0)));
        for (Matrix4f invalid : new Matrix4f[]{new Matrix4f().scale(0),
            new Matrix4f().m10(.2f), new Matrix4f().m30(Float.NaN), new Matrix4f().m00(Float.POSITIVE_INFINITY)}) {
            try { AnchorMath.decompose(invalid); throw new AssertionError("Invalid TRS accepted"); }
            catch (IllegalArgumentException expected) { count++; }
        }
        try { AnchorMath.parentLocal(parent, new Matrix4f().m30(Float.NaN)); throw new AssertionError("NaN target accepted"); }
        catch (IllegalArgumentException expected) { count++; }
        System.out.println("Anchor math: " + count + " checks passed");
    }
}
