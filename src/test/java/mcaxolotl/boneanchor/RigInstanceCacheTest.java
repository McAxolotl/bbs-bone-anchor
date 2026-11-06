package mcaxolotl.boneanchor;

import java.util.*;
import mcaxolotl.boneanchor.client.BindingScenes;
import mcaxolotl.boneanchor.client.BoneAnchorRuntime;
import mcaxolotl.boneanchor.client.RigInstances;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.animation.Animations;
import mchorse.bbs_mod.cubic.data.model.*;
import mchorse.bbs_mod.cubic.model.ModelSetupQueue;
import mchorse.bbs_mod.film.*;
import mchorse.bbs_mod.film.replays.*;
import mchorse.bbs_mod.forms.entities.*;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.Anchor;
import mchorse.bbs_mod.forms.renderers.*;
import mchorse.bbs_mod.forms.renderers.utils.*;
import mchorse.bbs_mod.math.molang.MolangParser;
import mchorse.bbs_mod.cubic.MolangHelper;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import net.minecraft.client.util.math.MatrixStack;

/**
 * Every placed model form inside an anchor-enabled film gets a private mutable rig: it must be
 * reused between frames, kept per form when two actors share one resource, trimmed instead of
 * growing without bound, and dropped when the form leaves the scene.
 */
public final class RigInstanceCacheTest {
    private static int count;
    private static void check(boolean test, String message) { count++; if (!test) throw new AssertionError(message); }

    public static int run() throws Exception {
        count = 0;
        try (Fixture f = new Fixture()) {
            ModelInstance shared = f.asset("cache_shared");

            ModelInstance first = RigInstances.view(f.bound, shared);
            check(first != shared, "A bound form gets its own rig");
            check(RigInstances.view(f.bound, shared) == first, "The same asset reuses the rig it already built");

            ModelInstance second = RigInstances.view(f.other, shared);
            check(second != first && second.model != first.model, "A second actor keeps a separate rig for the same resource");

            for (int i = 0; i < 40; i++) RigInstances.view(f.bound, f.asset("cache_" + i));
            check(RigInstances.view(f.bound, shared) != first, "The rig cache stays bounded instead of growing with every asset");
            ModelSetupQueue.drain();

            f.removeBound();
            check(RigInstances.view(f.bound, shared) == shared, "An actor outside the scene uses BBS's own rig");
            f.restoreBound();
            check(RigInstances.view(f.bound, shared) != first, "Leaving the scene drops the rigs it cached");
            ModelSetupQueue.drain();

            check(RigInstances.view(f.unbound, shared) == shared, "A form outside the scene keeps BBS's own rig");
        }
        return count;
    }

    private static final class Fixture implements AutoCloseable {
        final ModelForm bound = new ModelForm(), other = new ModelForm(), unbound = new ModelForm();
        final StubEntity boundEntity = new StubEntity(), otherEntity = new StubEntity();
        final ModelInstance boundInstance = new ModelInstance("cache_bound", rig(), null, null);
        final ModelInstance otherInstance = new ModelInstance("cache_other", rig(), null, null);
        final BaseFilmController controller;
        String boundId;

        Fixture() {
            this.boundInstance.form = this.bound;
            this.otherInstance.form = this.other;
            this.bound.setRenderer(renderer(this.bound, this.boundInstance));
            this.other.setRenderer(renderer(this.other, this.otherInstance));

            Film film = new Film();
            Replay boundReplay = film.replays.addReplay(), otherReplay = film.replays.addReplay();
            ((BaseValueBasic<Anchor>) this.bound.getBasic(BoneAnchors.key("hand")))
                .set(new Anchor(otherReplay.getId(), "hand", true, false, false));

            this.controller = new BaseFilmController(film) {
                @Override public Map<String, Integer> getActors() { return Map.of(); }
                @Override public int getTick() { return 0; }
            };
            this.boundEntity.setForm(this.bound);
            this.otherEntity.setForm(this.other);
            this.boundId = boundReplay.getId();
            this.controller.entities.put(this.boundId, this.boundEntity);
            this.controller.entities.put(otherReplay.getId(), this.otherEntity);
            BoneAnchorRuntime.remember(this.controller);
            BindingScenes.scene(this.controller);
        }

        ModelInstance asset(String id) {
            MolangParser parser = new MolangParser();
            MolangHelper.registerVars(parser);
            return new ModelInstance(id, rig(), new Animations(parser), null);
        }

        void removeBound() {
            this.controller.entities.remove(this.boundId);
            BoneAnchorRuntime.remember(this.controller);
            BindingScenes.scene(this.controller);
        }

        void restoreBound() {
            this.controller.entities.put(this.boundId, this.boundEntity);
            BoneAnchorRuntime.remember(this.controller);
            BindingScenes.scene(this.controller);
        }

        @Override public void close() { BoneAnchorRuntime.forget(this.controller); }
    }

    private static ModelFormRenderer renderer(ModelForm form, ModelInstance instance) {
        return new ModelFormRenderer(form) {
            @Override public ModelInstance getModel() { return instance; }
            @Override public void ensureAnimator(float transition) {}
            @Override public void collectMatrices(IEntity entity, MatrixStack stack, MatrixCache out, String prefix, float transition) {}
        };
    }

    private static Model rig() {
        Model model = new Model(null);
        ModelGroup root = new ModelGroup("Root");
        root.children.add(new ModelGroup("hand"));
        model.topGroups.add(root);
        model.initialize();
        model.snapshotChannels();
        return model;
    }

    private RigInstanceCacheTest() {}
}
