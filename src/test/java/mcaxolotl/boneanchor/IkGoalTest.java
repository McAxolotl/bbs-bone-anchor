package mcaxolotl.boneanchor;

import java.util.*;
import mcaxolotl.boneanchor.client.BindingResolver;
import mcaxolotl.boneanchor.client.BindingScenes;
import mcaxolotl.boneanchor.client.BoneAnchorRuntime;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.*;
import mchorse.bbs_mod.cubic.ik.ModelIKRuntime;
import mchorse.bbs_mod.film.*;
import mchorse.bbs_mod.film.replays.*;
import mchorse.bbs_mod.film.replays.tracks.*;
import mchorse.bbs_mod.forms.entities.*;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.Anchor;
import mchorse.bbs_mod.forms.renderers.*;
import mchorse.bbs_mod.forms.renderers.utils.*;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import mchorse.bbs_mod.utils.keyframes.*;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.*;

/**
 * An IK target track only means anything inside a film, where the controller can resolve another
 * actor's bone. BBS's own TargetTrack skips a keyframe that binds nothing, so a targetless keyframe
 * must not contribute a goal either.
 */
public final class IkGoalTest {
    private static int count;
    private static void check(boolean test, String message) { count++; if (!test) throw new AssertionError(message); }

    public static int run() throws Exception {
        count = 0;
        try (Fixture f = new Fixture()) {
            Map<String, Vector3f> original = new HashMap<>();
            Map<String, Float> weights = new HashMap<>();
            original.put("keeper", new Vector3f(9, 9, 9));
            weights.put("keeper", .25f);

            check(f.scene().byForm.get(f.source) != null, "Fixture form is registered in the scene");
            check(ModelIKRuntime.getChains(f.sourceInstance.model, f.source).keySet().contains("ik_tip"), "Fixture IK chain compiles");

            Anchor targetless = new Anchor();
            targetless.transform.translate.set(5, 0, 0);
            f.track.insert(0, targetless);
            try (BindingResolver.Request request = BindingResolver.request(f.scene(), 0)) {
                BindingResolver.Goals goals = BindingResolver.goals(f.sourceInstance, original, weights);
                check(goals.targets() == original, "Targetless IK keyframe contributes no goal");
                check(goals.weights() == weights, "Targetless IK keyframe touches no weights");
            }

            f.track.insert(0, new Anchor(f.targetId, "hand", true, true, true));
            try (BindingResolver.Request request = BindingResolver.request(f.scene(), 0)) {
                BindingResolver.Goals goals = BindingResolver.goals(f.sourceInstance, original, weights);
                Vector3f goal = goals.targets().get("controller");
                check(goal != null, "Bound IK keyframe resolves a goal");
                check(goal != null && goal.distance(new Vector3f(2, 3, 4)) < 1e-4f,
                    "Goal lands on the target bone inside the model's own space: " + goal);
                check(Float.valueOf(1F).equals(goals.weights().get("controller")), "Bound goal keeps full weight");
                check(new Vector3f(9, 9, 9).equals(goals.targets().get("keeper")), "Unrelated goal is preserved");
                check(Float.valueOf(.25f).equals(goals.weights().get("keeper")), "Unrelated weight is preserved");
            }
        }
        return count;
    }

    private static final class Fixture implements AutoCloseable {
        final ModelForm source = new ModelForm(), other = new ModelForm();
        final StubEntity sourceEntity = new StubEntity(), otherEntity = new StubEntity();
        final ModelInstance sourceInstance = new ModelInstance("ik_goal_source", rig(true), null, null);
        final ModelInstance otherInstance = new ModelInstance("ik_goal_target", rig(false), null, null);
        final Film film = new Film();
        final BaseFilmController controller;
        final KeyframeChannel<Anchor> track;
        final String sourceId, targetId;

        Fixture() {
            this.sourceInstance.form = this.source;
            this.otherInstance.form = this.other;
            this.source.setRenderer(new ModelFormRenderer(this.source) {
                @Override public ModelInstance getModel() { return Fixture.this.sourceInstance; }
                @Override public void ensureAnimator(float transition) {}
            });
            this.other.setRenderer(new ModelFormRenderer(this.other) {
                @Override public ModelInstance getModel() { return Fixture.this.otherInstance; }
                @Override public void ensureAnimator(float transition) {}
                @Override public void collectMatrices(IEntity entity, MatrixStack stack, MatrixCache out, String prefix, float transition) {
                    /* A real cache carries the model-space flip; see BBS's own collectMatrices output. */
                    Matrix4f hand = new Matrix4f().rotateY((float) java.lang.Math.PI).mul(new Matrix4f().translation(2, 3, 4));
                    out.put("", new Matrix4f(), new Matrix4f());
                    out.put("hand", new Matrix4f(hand), new Matrix4f(hand));
                }
            });

            var chain = this.source.bones.getOrCreate("ik_tip");
            chain.ikTarget.set("controller");
            chain.ikChainLength.set(2);

            Replay target = this.film.replays.addReplay();
            Replay holder = this.film.replays.addReplay();
            this.sourceId = holder.getId();
            this.targetId = target.getId();
            ((BaseValueBasic<Anchor>) this.source.getBasic(BoneAnchors.key("controller")))
                .set(new Anchor(this.targetId, "hand", true, false, false));
            this.track = (KeyframeChannel<Anchor>) holder.properties.register(TrackId.ikTarget("", "controller"), KeyframeFactories.ANCHOR);

            this.controller = new BaseFilmController(this.film) {
                @Override public Map<String, Integer> getActors() { return Map.of(); }
                @Override public int getTick() { return 0; }
            };
            this.sourceEntity.setForm(this.source);
            this.otherEntity.setForm(this.other);
            this.controller.entities.put(this.sourceId, this.sourceEntity);
            this.controller.entities.put(this.targetId, this.otherEntity);
            BoneAnchorRuntime.remember(this.controller);
        }

        BindingScenes.Scene scene() { return BindingScenes.scene(this.controller); }

        @Override public void close() {
            BoneAnchorRuntime.forget(this.controller);
        }
    }

    /** The chain owner carries the target bone; the other actor only needs a bone for the anchor to hit. */
    private static Model rig(boolean chain) {
        Model model = new Model(null);
        ModelGroup root = new ModelGroup("Root");
        if (chain) {
            ModelGroup bone = new ModelGroup("controller"), ikRoot = new ModelGroup("ik_root"), ikTip = new ModelGroup("ik_tip");
            bone.initial.translate.set(0, 10, 0);
            ikTip.initial.translate.set(0, 16, 0);
            ikRoot.children.add(ikTip);
            root.children.add(bone);
            root.children.add(ikRoot);
        } else {
            root.children.add(new ModelGroup("hand"));
        }
        model.topGroups.add(root);
        model.initialize();
        model.snapshotChannels();
        return model;
    }

    private IkGoalTest() {}
}
