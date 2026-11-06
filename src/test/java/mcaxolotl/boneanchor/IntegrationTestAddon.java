package mcaxolotl.boneanchor;
import mcaxolotl.boneanchor.client.*;
import java.util.*;
import mchorse.bbs_mod.api.*;
import mchorse.bbs_mod.api.client.events.BBSClientReadyEvent;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.*;
import mchorse.bbs_mod.cubic.ik.ModelIKRuntime;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.replays.FormProperties;
import mchorse.bbs_mod.film.replays.tracks.*;
import mchorse.bbs_mod.forms.entities.*;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.Anchor;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
public final class IntegrationTestAddon implements BBSAddonMod {
    private static int count;
    private static void check(boolean test, String message) { count++; if (!test) throw new AssertionError(message); }
    @Subscribe public void ready(BBSClientReadyEvent event) {
        boolean isolated = Boolean.getBoolean("bbsboneanchor.smoke");
        var marker = java.nio.file.Path.of("config/bbs_bone_anchor_smoke.once");
        if (!isolated && !java.nio.file.Files.exists(marker)) return;
        int[] ticks = {0}; boolean[] finished = {false};
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (finished[0] || ++ticks[0] < 100) return;
            var manager = mchorse.bbs_mod.BBSModClient.getModels();
            if (!isolated && manager.getModel("player/alex") == null) return;
            finished[0] = true;
            try {
                java.nio.file.Files.deleteIfExists(marker);
                run();
                String report = "BONE_ANCHOR_SMOKE_OK " + count + "\nHIERARCHY_FILTER_CHECKS " + AnchorTrackLayoutTest.run() + "\nIK_GOAL_CHECKS " + IkGoalTest.run() + "\nNATIVE_PLACEMENT_CHECKS " + NativePlacementTest.run() + "\nANCHOR_REBASE_CHECKS " + AnchorRebaseTest.run() + "\nRIG_CACHE_CHECKS " + RigInstanceCacheTest.run() + "\n" + DependencyRegressionTest.run() + "\nSCENE_BINDING_CHECKS " + SceneBindingTest.run();
                System.out.println(report);
                java.nio.file.Files.writeString(java.nio.file.Path.of("config/bbs_bone_anchor_smoke-report.txt"), report);
                if (isolated) System.exit(0);
            } catch (Throwable error) {
                error.printStackTrace(); System.out.println("BONE_ANCHOR_SMOKE_FAILED");
                try { java.nio.file.Files.writeString(java.nio.file.Path.of("config/bbs_bone_anchor_smoke-report.txt"), "FAILED: " + error); } catch (Exception ignored) {}
                if (isolated) System.exit(23);
            }
        });
    }
    @SuppressWarnings("unchecked") private void run() throws Exception {
        count = 0;
        String key = BoneAnchors.key("controller");
        ModelForm source = new ModelForm();
        var property = (BaseValueBasic<Anchor>)source.getBasic(key);
        check(property != null, "Mixin lazy property creation");
        Anchor a = new Anchor("target", "hand", true, false, false);
        property.set(a);
        ModelForm loaded = new ModelForm(); loaded.fromData(source.toData());
        check(((Anchor)loaded.getBasic(key).get()).replay.equals("target"), "Save/load anchor");
        check(key.equals("anchor:controller"), "Track key uses original bone name");
        check(source.toData().asMap().has("anchor:controller"), "Saved property uses raw bone name");
        ModelForm defaults = new ModelForm();
        String unused = BoneAnchors.key("unused");
        defaults.getBasic(unused);
        check(!defaults.toData().asMap().has(unused), "Untouched per-bone Anchor stays out of form data");
        ModelForm rehydrated = new ModelForm(); rehydrated.fromData(defaults.toData());
        FormProperties properties = new FormProperties("properties");
        KeyframeChannel<Anchor> channel = properties.register(TrackId.property("", unused), BoneAnchors.FACTORY);
        channel.insert(0, a.copy());
        properties.applyProperties(rehydrated, 0);
        check(rehydrated.getBasic(unused) != null, "An animated omitted Anchor property is recreated on playback");
        check(((Anchor)rehydrated.getBasic(unused).get()).replay.equals("target"), "Animated omitted Anchor receives its track value");
        ((BaseValueBasic<Anchor>)defaults.getBasic(unused)).set(new Anchor("target","hand",true,true,true));
        check(defaults.toData().asMap().has(unused), "Authored per-bone Anchor remains in form data");
        String localized = mchorse.bbs_mod.l10n.L10n.lang("bbs_bone_anchor.bound_only").get();
        check(localized != null && !localized.isBlank() && !localized.equals("bbs_bone_anchor.bound_only"),
            "Registered language file resolves the menu action: " + localized);
        for (String name : new String[]{"anchor", "中文.bone/path", "arm:left", "a b", "007"}) {
            String rawKey = BoneAnchors.key(name);
            var raw = (BaseValueBasic<Anchor>)source.getBasic(rawKey);
            raw.set(a.copy());
            var id = TrackId.property("",rawKey);
            check(TrackId.parse(id.toKey()).subject().equals(rawKey), "Track address preserves raw name");
            check(mchorse.bbs_mod.forms.FormUtils.getProperty(source,id.toKey()) == raw, "Property path preserves raw name");
            ModelForm restored = new ModelForm(); restored.fromData(source.toData());
            check(((Anchor)restored.getBasic(rawKey).get()).replay.equals("target"), "Raw-name serialization round trip");
            var desc = new TrackDescriptor(id,null,source,mchorse.bbs_mod.l10n.keys.IKey.constant(rawKey),null,0,raw);
            check(desc.filterKey().equals(rawKey), "Filter identity preserves entire raw name");
            // Keep this fixture's runtime rig limited to its actual controller.
            source.remove(raw);
        }
        check(source.anchor.get().hasTarget() == false, "Bone named anchor does not alter whole-model Anchor");

        ModelForm copied = new ModelForm(); copied.copy(source);
        check(((Anchor)copied.getBasic(key).get()).attachment.equals("hand"), "Copy dynamic properties");
        ModelForm neutral = new ModelForm();
        copied.copy(neutral);
        check(!((Anchor)copied.getBasic(key).get()).hasTarget(), "Copy replacement clears old binding");
        loaded.fromData(neutral.toData());
        check(!((Anchor)loaded.getBasic(key).get()).hasTarget(), "Read replacement clears old binding");
        source.copy(source);
        check(((Anchor)source.getBasic(key).get()).hasTarget(), "Self-copy preserves binding");

        var tracks = TrackCatalog.of(source);
        var row = tracks.stream().filter(t -> t.id().subject().equals(key)).findFirst().orElseThrow();
        check(TrackId.property("", "anchor").equals(row.parent()), "Fold bone under whole anchor");
        check(row.property() == property, "Timeline drives actual anchor property");
        Class.forName("mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIAnchorKeyframeFactory");
        Model model = new Model(null);
        ModelGroup root = new ModelGroup("Root"), controller = new ModelGroup("controller");
        controller.initial.translate.set(0, .65f, 0); controller.current.copy(controller.initial);
        root.children.add(controller); model.topGroups.add(root); model.initialize(); model.snapshotChannels();
        ModelInstance instance = new ModelInstance("smoke",model,null,null); instance.form = source;
        source.setRenderer(new ModelFormRenderer(source) { @Override public ModelInstance getModel() { return instance; } });
        ModelForm target = new ModelForm();
        Model targetRig = new Model(null);
        targetRig.topGroups.add(new ModelGroup("hand")); targetRig.initialize();
        ModelInstance targetInstance = new ModelInstance("targetFixture",targetRig,null,null); targetInstance.form = target;
        Matrix4f hand = new Matrix4f().translation(6,3,2);
        target.setRenderer(new ModelFormRenderer(target) {
            @Override public ModelInstance getModel() { return targetInstance; }
            @Override public void collectMatrices(IEntity entity, MatrixStack stack, MatrixCache out, String prefix, float transition) {
                out.put("",new Matrix4f(),new Matrix4f()); out.put("hand",new Matrix4f(hand),new Matrix4f(hand));
            }
        });
        StubEntity sourceEntity = new StubEntity(), targetEntity = new StubEntity();
        sourceEntity.setForm(source); targetEntity.setForm(target);
        BaseFilmController film = new BaseFilmController(null) {
            @Override public Map<String,Integer> getActors() { return Map.of(); }
            @Override public int getTick() { return 0; }
        };
        film.entities.put("source",sourceEntity); film.entities.put("target",targetEntity);
        BoneAnchorRuntime.remember(film);
        try {
            BoneAnchorRuntime.sample(source,0);
            ModelIKRuntime.apply(instance,null,null);
            check(controller.offset != null, "Anchor applied before empty IK runtime returns");
            assertPosition(instance,hand,"Actual controller follows hand");
            var orphan = (BaseValueBasic<Anchor>)source.getBasic(BoneAnchors.key("removed_bone"));
            orphan.set(new Anchor("source","",true,false,false));
            model.restoreChannels(); ModelIKRuntime.apply(instance,null,null);
            assertPosition(instance,hand,"Missing source bone does not introduce a self-cycle");
            check(orphan.getOriginalValue().hasTarget(),"Dormant source binding remains saved");
            var targetOrphan = (BaseValueBasic<Anchor>)target.getBasic(BoneAnchors.key("removed_target_bone"));
            targetOrphan.set(new Anchor("source","",true,false,false));
            model.restoreChannels(); ModelIKRuntime.apply(instance,null,null);
            assertPosition(instance,hand,"Missing bone on a dependency actor does not introduce a cycle");
            target.remove(targetOrphan);

            // A binding rejected for solver ownership must not participate in the dependency graph.
            ModelGroup ikRoot = new ModelGroup("ik_root"), ikTip = new ModelGroup("ik_tip");
            ikTip.initial.translate.set(0,16,0); ikTip.current.copy(ikTip.initial);
            model.restoreChannels();
            ikRoot.children.add(ikTip); root.children.add(ikRoot); model.initialize(); model.snapshotChannels();
            var ik = source.bones.getOrCreate("ik_tip");
            ik.ikTarget.set("controller"); ik.ikChainLength.set(2);
            var owned = (BaseValueBasic<Anchor>)source.getBasic(BoneAnchors.key("ik_tip"));
            owned.set(new Anchor("source","",true,false,false));
            mchorse.bbs_mod.forms.renderers.utils.RenderFrame.invalidate();
            model.restoreChannels(); ModelIKRuntime.apply(instance,null,null);
            assertPosition(instance,hand,"IK-owned binding is excluded before cycle detection");
            check(owned.getOriginalValue().hasTarget(),"Solver-owned binding remains saved");
            source.bones.remove(ik);
            var physical = source.bones.getOrCreate("ik_root"); physical.physicsEnd.set("ik_tip");
            mchorse.bbs_mod.forms.renderers.utils.RenderFrame.invalidate();
            model.restoreChannels(); ModelIKRuntime.apply(instance,null,null);
            assertPosition(instance,hand,"Physics-owned binding is excluded before cycle detection");
            source.remove(owned); source.bones.remove(physical); root.children.remove(ikRoot);
            model.initialize(); model.restoreChannels(); model.snapshotChannels();
            source.remove(orphan);
            mchorse.bbs_mod.forms.renderers.utils.RenderFrame.invalidate();
            ModelIKRuntime.apply(instance,null,null);

            BaseFilmController temporary = new BaseFilmController(null) {
                public Map<String,Integer> getActors() { return Map.of(); }
                public int getTick() { return 0; }
            };
            BoneAnchorRuntime.remember(temporary); BoneAnchorRuntime.forget(temporary);
            check(BoneAnchorRuntime.hasBoundOffsets(model), "Unrelated shutdown preserves live film offsets");
            Anchor completed = a.copy(); completed.previous = new Anchor("missing_actor","missing",true,false,false); completed.x = 1;
            property.set(completed); model.restoreChannels(); ModelIKRuntime.apply(instance,null,null);
            assertPosition(instance,hand,"Completed transition ignores missing old actor");
            property.set(new Anchor()); model.restoreChannels(); ModelIKRuntime.apply(instance,null,null);
            Vector3f rest=controllerPosition(instance);
            Anchor oldMissing=a.copy(); oldMissing.previous=new Anchor("missing_actor","missing",true,false,false); oldMissing.x=.5f;
            property.set(oldMissing); model.restoreChannels(); ModelIKRuntime.apply(instance,null,null);
            check(controllerPosition(instance).distance(new Vector3f(rest).lerp(hand.getTranslation(new Vector3f()),.5f))<.0001f,
                "Invalid old endpoint falls back without discarding the new endpoint");
            Anchor newMissing=a.copy(); newMissing.replay="missing_actor"; newMissing.attachment="missing"; newMissing.previous=a.copy(); newMissing.x=.5f;
            property.set(newMissing); model.restoreChannels(); ModelIKRuntime.apply(instance,null,null);
            check(controllerPosition(instance).distance(new Vector3f(rest).lerp(hand.getTranslation(new Vector3f()),.5f))<.0001f,
                "Invalid new endpoint falls back without discarding the old endpoint");
            property.set(a);
            sourceEntity.setPosition(2,0,0);
            model.restoreChannels();
            mchorse.bbs_mod.api.client.events.FormPoseEvents.PARENT_FRAME.invoker().capture(source,sourceEntity,new Matrix4f(),"",.5f);
            ModelIKRuntime.apply(instance,null,null);
            MatrixCache fractional = new MatrixCache(); instance.captureMatrices(fractional);
            Matrix4f fractionalWorld = mchorse.bbs_mod.film.FilmMatrices.getMatrixForRenderWithRotation(sourceEntity,0,0,0,.5f)
                .rotateY((float)Math.PI).mul(fractional.get("controller").matrix());
            check(fractionalWorld.getTranslation(new Vector3f()).distance(hand.getTranslation(new Vector3f())) < .0001f,
                "Public parent-frame event supplies fractional transition");
            sourceEntity.setPosition(0,0,0); BoneAnchorRuntime.sample(source,0);

            model.restoreChannels();
            check(controller.offset == null, "Binding offset rewinds");
            hand.translation(8,4,-1); target.bumpPoseVersion();
            ModelIKRuntime.apply(instance,null,null);
            assertPosition(instance,hand,"Moving hand updates actual controller");
            model.restoreChannels(); property.set(new Anchor()); ModelIKRuntime.apply(instance,null,null);
            check(controller.offset == null, "Unbind removes effect");
            property.set(a); target.anchor.set(new Anchor("source","controller",true,true,true));
            model.restoreChannels(); ModelIKRuntime.apply(instance,null,null);
            check(controller.offset == null, "Cycle skips binding");
            target.anchor.set(new Anchor()); a.attachment = "missing";
            model.restoreChannels(); ModelIKRuntime.apply(instance,null,null);
            check(controller.offset == null, "Missing target bone skips binding");
            a.attachment = "hand"; a.inheritScale = true; hand.scale(2); target.bumpPoseVersion();
            model.restoreChannels(); ModelIKRuntime.apply(instance,null,null);
            check(controller.current.scale.distance(new Vector3f(1)) < .0001f,"Scale binding leaves animation channels read-only");
            check(AnchorScales.get(controller).distance(new Vector3f(2)) < .0001f,"Scale inheritance uses addon state");
            MatrixCache scaled = new MatrixCache(); instance.captureMatrices(scaled);
            check(scaled.get("controller").matrix().getScale(new Vector3f()).distance(new Vector3f(2)) < .0001f,"Matrix capture consumes addon scale");
            check(AnchorScales.key(model) != 0, "Weld cache key includes addon scale");
            var weldKey = ModelInstance.class.getDeclaredMethod("weldKey",Model.class,mchorse.bbs_mod.obj.shapes.ShapeKeys.class,
                int.class,int.class,mchorse.bbs_mod.utils.colors.Color.class,boolean.class,
                mchorse.bbs_mod.ui.framework.elements.utils.StencilMap.class);
            weldKey.setAccessible(true);
            Object[] bakeInputs = {model,null,0,0,mchorse.bbs_mod.utils.colors.Color.white(),false,null};
            long oldKey = (Long)weldKey.invoke(instance,bakeInputs);
            AnchorScales.set(controller,new Vector3f(3));
            check(oldKey != (Long)weldKey.invoke(instance,bakeInputs), "Real weld cache key changes with addon scale");
            AnchorScales.set(controller,new Vector3f(2));
            model.restoreChannels();
            check(AnchorScales.get(controller) == null,"restoreChannels clears temporary scale immediately");
            check(AnchorScales.key(model) == 0,"restoreChannels clears scale contribution to cache key");
            check(!BoneAnchorRuntime.hasBoundOffsets(model),"restoreChannels clears addon pivot flag");
            AnchorScales.set(controller,new Vector3f(2));
            AnchorScales.set(targetRig.getGroup("hand"),new Vector3f(3));
            model.resetPose();
            check(AnchorScales.get(controller) == null,"resetPose clears temporary scale without an IK pass");
            check(AnchorScales.get(targetRig.getGroup("hand")) != null,"Reset affects only this model");
            MatrixCache restMatrices = new MatrixCache(); instance.captureMatrices(restMatrices);
            check(restMatrices.get("controller").matrix().getScale(new Vector3f()).distance(new Vector3f(1)) < .0001f,
                "Direct rest-pose capture is free of previous binding scale");
            AnchorScales.clear(targetRig);

            property.set(new Anchor()); ModelIKRuntime.apply(instance,null,null);
            check(AnchorScales.get(controller) == null,"Unbinding clears evaluated scale");
            check(AnchorScales.key(model) == 0,"Unbinding drops extra weld cache key");
            model.snapshotChannels(); controller.current.scale.set(3);
            model.restoreChannels();
            check(controller.current.scale.distance(new Vector3f(3)) < .0001f,"No addon write leaves other scale contributions alone");
            controller.current.scale.set(1);

        } finally { BoneAnchorRuntime.forget(film); }
    }
    private static Vector3f controllerPosition(ModelInstance instance) {
        MatrixCache bones = new MatrixCache(); instance.captureMatrices(bones);
        return new Matrix4f().rotateY((float)Math.PI).mul(bones.get("controller").matrix()).getTranslation(new Vector3f());
    }
    private static void assertPosition(ModelInstance instance, Matrix4f hand, String label) {
        check(controllerPosition(instance).distance(hand.getTranslation(new Vector3f())) < .0001f,label);
    }
}
