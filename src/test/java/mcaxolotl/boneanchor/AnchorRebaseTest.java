package mcaxolotl.boneanchor;

import java.util.*;
import mcaxolotl.boneanchor.client.BindingResolver;
import mcaxolotl.boneanchor.client.BindingScenes;
import mcaxolotl.boneanchor.client.BoneAnchorRuntime;
import mcaxolotl.boneanchor.client.NativeAnchorEditing;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.constraints.BoneConstraint;
import mchorse.bbs_mod.cubic.data.model.*;
import mchorse.bbs_mod.film.*;
import mchorse.bbs_mod.film.replays.*;
import mchorse.bbs_mod.forms.entities.*;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.Anchor;
import mchorse.bbs_mod.forms.renderers.*;
import mchorse.bbs_mod.forms.renderers.utils.*;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.*;

/**
 * "Keep transform" is measured through the resolver, so it has to survive a per-bone re-pick: the
 * bone must stay where it was, the measurement must use the settled keyframe values rather than a
 * crossfade, and a bone the solver owns must refuse the rebase instead of compensating nonsense.
 */
public final class AnchorRebaseTest {
    private static int count;
    private static void check(boolean test, String message) { count++; if (!test) throw new AssertionError(message); }

    public static int run() throws Exception {
        count = 0;
        try (Fixture f = new Fixture()) {
            Anchor from = f.anchor(f.handId, "hand");
            Anchor to = f.anchor(f.otherId, "hand");

            f.bind(from);
            Matrix4f settled = f.world();

            Anchor rebased = f.anchor(f.otherId, "hand");
            check(NativeAnchorEditing.rebase(f.slot(), "arm", from.copy(), rebased), "Keep transform computes a compensation");
            f.bind(rebased);
            Matrix4f moved = f.world();
            check(settled.equals(moved, 1e-3f), "Bone keeps its world position across the re-pick: " + settled + " != " + moved);

            Anchor blended = f.anchor(f.handId, "hand");
            blended.previous = f.anchor(f.otherId, "hand");
            blended.x = .5f;
            Anchor compensated = f.anchor(f.otherId, "hand");
            f.bind(from);
            check(NativeAnchorEditing.rebase(f.slot(), "arm", blended, compensated), "Keep transform ignores the crossfade state");
            f.bind(compensated);
            check(settled.equals(f.world(), 1e-3f), "Compensation targets the settled frame, not the crossfade");

            var constraint = new BoneConstraint();
            constraint.limitY = true;
            constraint.minY = 0F;
            constraint.maxY = 0F;
            f.source.bones.getOrCreate("arm").constraints.set(constraint);
            Anchor untouched = f.anchor(f.otherId, "hand");
            check(!NativeAnchorEditing.rebase(f.slot(), "arm", from.copy(), untouched), "Solver-owned source bone refuses the rebase");
            check(untouched.transform.isDefault(), "Refused rebase leaves the transform untouched");
        }
        return count;
    }

    private static final class Fixture implements AutoCloseable {
        final ModelForm source = new ModelForm(), hand = new ModelForm(), other = new ModelForm();
        final StubEntity sourceEntity = new StubEntity(), handEntity = new StubEntity(), otherEntity = new StubEntity();
        final ModelInstance sourceInstance = new ModelInstance("rebase_source", rig("arm"), null, null);
        final ModelInstance handInstance = new ModelInstance("rebase_hand", rig("hand"), null, null);
        final ModelInstance otherInstance = new ModelInstance("rebase_other", rig("hand"), null, null);
        final Film film = new Film();
        final BaseFilmController controller;
        final String sourceId, handId, otherId;

        Fixture() {
            this.sourceInstance.form = this.source;
            this.handInstance.form = this.hand;
            this.otherInstance.form = this.other;
            this.source.setRenderer(new ModelFormRenderer(this.source) {
                @Override public ModelInstance getModel() { return Fixture.this.sourceInstance; }
                @Override public void ensureAnimator(float transition) {}
            });
            this.hand.setRenderer(fake(this.hand, this.handInstance, 2, 3, 4));
            this.other.setRenderer(fake(this.other, this.otherInstance, 5, 6, 7));

            Replay sourceReplay = this.film.replays.addReplay(), handReplay = this.film.replays.addReplay(), otherReplay = this.film.replays.addReplay();
            this.sourceId = sourceReplay.getId();
            this.handId = handReplay.getId();
            this.otherId = otherReplay.getId();
            this.controller = new BaseFilmController(this.film) {
                @Override public Map<String, Integer> getActors() { return Map.of(); }
                @Override public int getTick() { return 0; }
            };
            this.sourceEntity.setForm(this.source);
            this.handEntity.setForm(this.hand);
            this.otherEntity.setForm(this.other);
            this.controller.entities.put(this.sourceId, this.sourceEntity);
            this.controller.entities.put(this.handId, this.handEntity);
            this.controller.entities.put(this.otherId, this.otherEntity);
            BoneAnchorRuntime.remember(this.controller);
        }

        Anchor anchor(String actor, String bone) { return new Anchor(actor, bone, true, true, true); }

        BindingScenes.Slot slot() { return BindingScenes.scene(this.controller).byForm.get(this.source); }

        void bind(Anchor anchor) {
            ((BaseValueBasic<Anchor>) this.source.getBasic(BoneAnchors.key("arm"))).set(anchor);
        }

        /** The bound bone's world matrix, exactly as the renderer would compose it. */
        Matrix4f world() {
            MatrixCache cache = BindingResolver.collect(this.source, 0);
            Matrix4f local = cache.get("arm").matrix();
            return FilmMatrices.getMatrixForRenderWithRotation(this.sourceEntity, 0, 0, 0, 0).mul(local);
        }

        @Override public void close() { BoneAnchorRuntime.forget(this.controller); }
    }

    private static ModelFormRenderer fake(ModelForm form, ModelInstance instance, float x, float y, float z) {
        return new ModelFormRenderer(form) {
            @Override public ModelInstance getModel() { return instance; }
            @Override public void ensureAnimator(float transition) {}
            @Override public void collectMatrices(IEntity entity, MatrixStack stack, MatrixCache out, String prefix, float transition) {
                /* A real cache carries the model-space flip, exactly like BBS's own output. */
                Matrix4f bone = new Matrix4f().rotateY((float) java.lang.Math.PI).mul(new Matrix4f().translation(x, y, z));
                out.put("", new Matrix4f(), new Matrix4f());
                out.put("hand", new Matrix4f(bone), new Matrix4f(bone));
            }
        };
    }

    private static Model rig(String bone) {
        Model model = new Model(null);
        ModelGroup root = new ModelGroup("Root");
        ModelGroup group = new ModelGroup(bone);
        group.initial.translate.set(4, 8, 0);
        root.children.add(group);
        model.topGroups.add(root);
        model.initialize();
        model.snapshotChannels();
        return model;
    }

    private AnchorRebaseTest() {}
}
