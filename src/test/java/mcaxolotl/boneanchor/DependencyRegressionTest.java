package mcaxolotl.boneanchor;

import java.util.*;
import mcaxolotl.boneanchor.client.BindingScenes;
import mcaxolotl.boneanchor.client.BoneAnchorRuntime;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.*;
import mchorse.bbs_mod.cubic.ik.ModelIKRuntime;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.forms.entities.*;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.Anchor;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.forms.renderers.utils.*;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Exercise the real BBS cache entry points, without editing a user's film. */
public final class DependencyRegressionTest {
    private interface Case { void run() throws Exception; }
    public static String run() throws Exception {
        List<Throwable> failures = new ArrayList<>();
        test(failures,"invalid target must not block valid binding", () -> {
            try (Fixture f = new Fixture()) {
                f.bind("decoration","C","missing");
                f.third.anchor.set(new Anchor("A","",true,true,true));
                f.assertHand(f.direct(), "invalid target blocked the controller");
            }
        });
        test(failures,"frame cache must follow target changes", () -> {
            try (Fixture f = new Fixture()) {
                f.assertHand(RenderFrame.collect(f.source,f.sourceEntity,0),"initial frame sample");
                f.moveHand();
                f.assertHand(RenderFrame.collect(f.source,f.sourceEntity,0),"stale frame-cache matrix");
            }
        });
        test(failures,"scoped cache must follow target changes", () -> {
            try (Fixture f = new Fixture()) {
                FormFrameCache span = new FormFrameCache();
                f.assertHand(span.collect(f.source,f.sourceEntity,0),"initial span sample");
                f.moveHand();
                f.assertHand(span.collect(f.source,f.sourceEntity,0),"stale scoped-cache matrix");
            }
        });
        test(failures,"ordinary and unbound forms retain native caching", () -> {
            try (Fixture f = new Fixture()) {
                MatrixCache first = RenderFrame.collect(f.target,f.targetEntity,0);
                if (first != RenderFrame.collect(f.target,f.targetEntity,0))
                    throw new AssertionError("Ordinary form no longer uses frame cache");
                FormFrameCache span = new FormFrameCache();
                MatrixCache scoped = span.collect(f.target,f.targetEntity,0);
                if (scoped != span.collect(f.target,f.targetEntity,0))
                    throw new AssertionError("Ordinary form no longer uses scoped cache");
                f.bind("controller","","");
                MatrixCache unbound = RenderFrame.collect(f.source,f.sourceEntity,0);
                if (unbound != RenderFrame.collect(f.source,f.sourceEntity,0))
                    throw new AssertionError("Unbound form still bypasses frame cache");
            }
        });
        test(failures,"invalid target under an ordinary container must not block binding", () -> {
            try (Fixture f = new Fixture()) {
                var wrapper = new mchorse.bbs_mod.forms.forms.AnchorForm();
                wrapper.anchor.set(new Anchor("A","",true,true,true));
                f.thirdEntity.setForm(wrapper);
                f.bind("decoration","C","missing");
                f.assertHand(f.direct(),"Container's missing target introduced a false cycle");
            }
        });
        test(failures,"body-part target path remains addressable", () -> {
            try (Fixture f = new Fixture()) {
                var wrapper = new mchorse.bbs_mod.forms.forms.AnchorForm();
                var child = new ModelForm();
                child.setRenderer(targetRenderer(child,f.targetRig,"hand",f.hand));
                var part = new mchorse.bbs_mod.forms.forms.BodyPart(""); part.setForm(child);
                wrapper.parts.addBodyPart(part);
                f.thirdEntity.setForm(wrapper);
                f.bind("controller","C",part.getId() + "/hand");
                f.assertHand(f.direct(),"Valid body-part target was rejected");
            }
        });
        test(failures,"unrelated IK chains do not own the bound bone", () -> {
            try (Fixture f = new Fixture()) {
                ModelGroup root=f.sourceRig.getGroup("Root"),ikRoot=new ModelGroup("ik_root"),ikTip=new ModelGroup("ik_tip");
                ikRoot.children.add(ikTip);root.children.add(ikRoot);f.sourceRig.initialize();f.sourceRig.snapshotChannels();
                var ik=f.source.bones.getOrCreate("ik_tip");ik.ikTarget.set("decoration");ik.ikChainLength.set(2);
                RenderFrame.invalidate();
                f.assertHand(f.direct(),"An enabled chain over another branch blocked this binding");
            }
        });
        test(failures,"removing the last binding disables scene work", () -> {
            try (Fixture f = new Fixture()) {
                if (!f.scene().enabled()) throw new AssertionError("SCENE_SHOULD_START_ENABLED");
                @SuppressWarnings("unchecked") BaseValueBasic<Anchor> controller=(BaseValueBasic<Anchor>)f.source.getBasic(BoneAnchors.key("controller"));
                controller.set(new Anchor());
                BoneAnchorRuntime.remember(f.film);
                if (f.scene().enabled()) throw new AssertionError("SCENE_STAYED_ENABLED");
            }
        });
        if (!failures.isEmpty()) {
            AssertionError failure = new AssertionError("Dependency regressions failed: " + failures.size() + "/8");
            failures.forEach(failure::addSuppressed);
            throw failure;
        }
        return "DEPENDENCY_REGRESSIONS_OK 8";
    }
    private static void test(List<Throwable> failures,String name,Case action) {
        try { action.run(); }
        catch (Throwable failure) { failures.add(new AssertionError(name,failure)); }
    }
    private static Model rig(String... names) {
        Model model = new Model(null);
        ModelGroup root = new ModelGroup("Root");
        for (String name : names) root.children.add(new ModelGroup(name));
        model.topGroups.add(root); model.initialize(); model.snapshotChannels();
        return model;
    }
    private static final class Fixture implements AutoCloseable {
        final ModelForm source = new ModelForm(), target = new ModelForm(), third = new ModelForm();
        final StubEntity sourceEntity = new StubEntity(), targetEntity = new StubEntity(), thirdEntity = new StubEntity();
        final Model sourceRig = rig("controller","decoration"), targetRig = rig("hand"), thirdRig = rig("present");
        final ModelInstance instance = new ModelInstance("dependency_source",sourceRig,null,null);
        final Matrix4f hand = new Matrix4f().translation(3,2,1);
        final BaseFilmController film = new BaseFilmController(null) {
            public Map<String,Integer> getActors() { return Map.of(); }
            public int getTick() { return 0; }
        };
        final boolean cacheWasEnabled;
        Fixture() {
            cacheWasEnabled = BBSSettings.framePoseCache == null || BBSSettings.framePoseCache.get();
            if (BBSSettings.framePoseCache != null) BBSSettings.framePoseCache.set(true);
            sourceEntity.setForm(source); targetEntity.setForm(target); thirdEntity.setForm(third);
            instance.form = source;
            source.setRenderer(new ModelFormRenderer(source) {
                @Override public ModelInstance getModel() { return instance; }
                @Override public void ensureAnimator(float transition) {}
                @Override public void collectMatrices(IEntity entity,MatrixStack stack,MatrixCache out,String prefix,float transition) {
                    sourceRig.resetPose(); instance.form = source;
                    BoneAnchorRuntime.sample(source,transition);
                    ModelIKRuntime.apply(instance,null,null);
                    MatrixCache local = new MatrixCache(); instance.captureMatrices(local);
                    out.put("",new Matrix4f(),new Matrix4f());
                    for (var entry : local.entrySet()) {
                        Matrix4f matrix = new Matrix4f().rotateY((float)Math.PI).mul(entry.getValue().matrix());
                        out.put(entry.getKey(),matrix,new Matrix4f(matrix));
                    }
                }
            });
            target.setRenderer(targetRenderer(target,targetRig,"hand",hand));
            third.setRenderer(targetRenderer(third,thirdRig,"present",new Matrix4f()));
            film.entities.put("A",sourceEntity); film.entities.put("B",targetEntity); film.entities.put("C",thirdEntity);
            bind("controller","B","hand");
            BoneAnchorRuntime.remember(film); RenderFrame.invalidate();
        }
        @SuppressWarnings("unchecked") void bind(String bone,String actor,String attachment) {
            ((BaseValueBasic<Anchor>)source.getBasic(BoneAnchors.key(bone))).set(new Anchor(actor,attachment,true,false,false));
        }
        MatrixCache direct() { return ((ModelFormRenderer)source.getRenderer()).collectMatrices(sourceEntity,0); }
        BindingScenes.Scene scene() { return BindingScenes.scene(film); }
        void moveHand() { hand.translation(8,6,4); target.bumpPoseVersion(); }
        void assertHand(MatrixCache matrices,String message) {
            Matrix4f matrix = matrices.get("controller").matrix();
            Vector3f expected = hand.getTranslation(new Vector3f());
            Vector3f actual = matrix == null ? new Vector3f(Float.NaN) : matrix.getTranslation(new Vector3f());
            if (!actual.isFinite() || actual.distance(expected) > 1e-4f)
                throw new AssertionError(message + ": " + actual + " != " + expected);
        }
        @Override public void close() {
            BoneAnchorRuntime.forget(film);
            if (BBSSettings.framePoseCache != null) BBSSettings.framePoseCache.set(cacheWasEnabled);
            RenderFrame.invalidate();
        }
    }
    private static ModelFormRenderer targetRenderer(ModelForm form,Model model,String bone,Matrix4f matrix) {
        ModelInstance instance = new ModelInstance("dependency_target_" + bone,model,null,null); instance.form = form;
        return new ModelFormRenderer(form) {
            @Override public ModelInstance getModel() { return instance; }
            @Override public void ensureAnimator(float transition) {}
            @Override public void collectMatrices(IEntity entity,MatrixStack stack,MatrixCache out,String prefix,float transition) {
                out.put(prefix,new Matrix4f(),new Matrix4f());
                out.put(mchorse.bbs_mod.utils.StringUtils.combinePaths(prefix,bone),new Matrix4f(matrix),new Matrix4f(matrix));
            }
        };
    }
    private DependencyRegressionTest() {}
}
