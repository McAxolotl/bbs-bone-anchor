package mcaxolotl.boneanchor;

import java.util.*;
import mcaxolotl.boneanchor.client.BindingScenes;
import mcaxolotl.boneanchor.client.BoneAnchorRuntime;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.*;
import mchorse.bbs_mod.film.*;
import mchorse.bbs_mod.film.replays.*;
import mchorse.bbs_mod.forms.entities.*;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.Anchor;
import mchorse.bbs_mod.forms.renderers.*;
import mchorse.bbs_mod.forms.renderers.utils.*;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import mchorse.bbs_mod.utils.Pair;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.*;

/**
 * The addon takes over the root actor's anchor resolution once a film uses bone anchors, so a plain
 * BBS anchor must still resolve to exactly the matrix BBS itself would produce — inheritance flags
 * and the shadow opacity included.
 */
public final class NativePlacementTest {
    private static int count;
    private static void check(boolean test, String message) { count++; if (!test) throw new AssertionError(message); }

    public static int run() throws Exception {
        count = 0;
        try (Fixture f = new Fixture()) {
            compare(f, new Anchor(f.handId, "hand", true, true, true), "full inheritance");
            compare(f, new Anchor(f.handId, "hand", false, true, true), "position not inherited");
            compare(f, new Anchor(f.handId, "hand", true, false, false), "rotation and scale not inherited");
            compare(f, new Anchor(f.handId, "missing", true, true, true), "missing bone falls back to the actor frame");
        }
        return count;
    }

    private static void compare(Fixture f, Anchor anchor, String label) {
        f.hero.anchor.set(anchor.copy());
        f.enable(false);
        Pair<Matrix4f, Float> nativePair = f.placement(f.hero.anchor.get());
        f.enable(true);
        Pair<Matrix4f, Float> addonPair = f.placement(f.hero.anchor.get());

        check(nativePair.a != null && addonPair.a != null, "Resolved placement: " + label);
        check(nativePair.a.equals(addonPair.a, 1e-4f), "Placement matches BBS for " + label + ": " + nativePair.a + " != " + addonPair.a);
        check(java.lang.Math.abs(nativePair.b - addonPair.b) < 1e-4f, "Shadow opacity matches BBS for " + label + ": " + nativePair.b + " != " + addonPair.b);
    }

    private static final class Fixture implements AutoCloseable {
        final ModelForm hero = new ModelForm(), hand = new ModelForm(), spare = new ModelForm();
        final StubEntity heroEntity = new StubEntity(), handEntity = new StubEntity(), spareEntity = new StubEntity();
        final ModelInstance heroInstance = new ModelInstance("placement_hero", rig(false), null, null);
        final ModelInstance handInstance = new ModelInstance("placement_hand", rig(true), null, null);
        final ModelInstance spareInstance = new ModelInstance("placement_spare", rig(true), null, null);
        final Film film = new Film();
        final BaseFilmController controller;
        final String heroId, handId, spareId;

        Fixture() {
            this.heroInstance.form = this.hero;
            this.handInstance.form = this.hand;
            this.spareInstance.form = this.spare;
            this.hero.setRenderer(new ModelFormRenderer(this.hero) {
                @Override public ModelInstance getModel() { return Fixture.this.heroInstance; }
                @Override public void ensureAnimator(float transition) {}
            });
            this.hand.setRenderer(fake(this.hand, this.handInstance));
            this.spare.setRenderer(fake(this.spare, this.spareInstance));
            this.hero.anchor.set(new Anchor());

            Replay heroReplay = this.film.replays.addReplay(), handReplay = this.film.replays.addReplay(), spareReplay = this.film.replays.addReplay();
            this.heroId = heroReplay.getId();
            this.handId = handReplay.getId();
            this.spareId = spareReplay.getId();
            this.controller = new BaseFilmController(this.film) {
                @Override public Map<String, Integer> getActors() { return Map.of(); }
                @Override public int getTick() { return 0; }
            };
            this.heroEntity.setForm(this.hero);
            this.handEntity.setForm(this.hand);
            this.spareEntity.setForm(this.spare);
            this.controller.entities.put(this.heroId, this.heroEntity);
            this.controller.entities.put(this.handId, this.handEntity);
            this.controller.entities.put(this.spareId, this.spareEntity);
            BoneAnchorRuntime.remember(this.controller);
        }

        /** The bone anchor on the unrelated actor is what turns the scene on. */
        void enable(boolean on) {
            ((BaseValueBasic<Anchor>) this.spare.getBasic(BoneAnchors.key("bone")))
                .set(on ? new Anchor(this.handId, "hand", true, false, false) : new Anchor());
            BoneAnchorRuntime.remember(this.controller);
            BindingScenes.scene(this.controller);
        }

        Pair<Matrix4f, Float> placement(Anchor anchor) {
            Matrix4f defaultMatrix = FilmMatrices.getMatrixForRenderWithRotation(this.heroEntity, 0, 0, 0, 0);
            return FilmMatrices.getTotalMatrix(this.controller.entities, anchor, defaultMatrix, 0, 0, 0, 0, 0, false, null);
        }

        @Override public void close() {
            BoneAnchorRuntime.forget(this.controller);
        }
    }

    private static ModelFormRenderer fake(ModelForm form, ModelInstance instance) {
        return new ModelFormRenderer(form) {
            @Override public ModelInstance getModel() { return instance; }
            @Override public void ensureAnimator(float transition) {}
            @Override public void collectMatrices(IEntity entity, MatrixStack stack, MatrixCache out, String prefix, float transition) {
                /* A real cache carries the model-space flip, exactly like BBS's own output. */
                Matrix4f bone = new Matrix4f().rotateY((float) java.lang.Math.PI).mul(new Matrix4f().translation(2, 3, 4));
                out.put("", new Matrix4f(), new Matrix4f());
                out.put("hand", new Matrix4f(bone), new Matrix4f(bone));
            }
        };
    }

    private static Model rig(boolean bone) {
        Model model = new Model(null);
        ModelGroup root = new ModelGroup("Root");
        if (bone) root.children.add(new ModelGroup("hand"));
        model.topGroups.add(root);
        model.initialize();
        model.snapshotChannels();
        return model;
    }

    private NativePlacementTest() {}
}
