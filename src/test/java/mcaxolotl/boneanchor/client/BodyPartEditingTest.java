package mcaxolotl.boneanchor.client;

import java.util.List;
import java.util.Map;
import mcaxolotl.boneanchor.BindingAnchor;
import mcaxolotl.boneanchor.BoneAnchors;
import mcaxolotl.boneanchor.compat.mixin.FormPropertiesAccessor;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.FilmMatrices;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.Anchor;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.controller.FilmEditorController;
import mchorse.bbs_mod.ui.forms.editors.UIFormUndoHandler;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.UIRenderingContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.context.UISimpleContextMenu;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeLoop;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;

/** Exercises the same prepared conversion transaction used by the Body parts menu. */
public final class BodyPartEditingTest {
    private static int count;
    private static void check(boolean condition, String message) {
        count++;
        if (!condition) throw new AssertionError(message);
    }

    public static int run() {
        count = 0;
        copy();
        convert();
        guards();
        frames();
        parentFrames();
        menu();
        return count;
    }

    private static void copy() {
        Fixture f = new Fixture();
        BaseType before = f.source.toData();
        BaseType watcherBefore = f.watcher.toData();
        Replay target = f.create(false);
        check(target != null && f.film.replays.getList().size() == 3, "Copy creates one Replay");
        check(before.equals(f.source.toData()), "Copy leaves every source value and track intact");
        check(watcherBefore.equals(f.watcher.toData()), "Copy never retargets existing Replays");
        check(f.root.parts.get(f.part.getId()) == f.part, "Copy retains the original BodyPart");
        check(target.form.get() != f.partForm, "The copied form is independent");
        check(FormUtils.getPath(target.form.get()).isEmpty(), "The new form has a root address");
        verifyTarget(f, target);
        KeyframeChannel<?> copied = target.properties.get(TrackId.property("", "lighting"));
        check(copied != f.lighting && copied.getKeyframes().get(0) != f.lighting.getKeyframes().get(0), "Keyframes are deep copies");
        copied.getKeyframes().get(0).setTick(7);
        check(f.lighting.getKeyframes().get(0).getTick() == 0, "Editing copied keys cannot change original keys");
        check(copied.getLoops().equals(f.lighting.getLoops()), "Track loops survive the copy");
        copied.removeLoop("loop");
        check(f.lighting.getLoops().size() == 1, "Loop lists are independent");
        List<MapType> copiedForeign = foreign(target);
        check(copiedForeign.size() == 2 && !copiedForeign.get(0).has("form"), "Foreign root track omits the old path");
        check(copiedForeign.get(1).getString("form").equals(f.child.getId()), "Foreign descendant track is rebased");
        check(copiedForeign.get(0).getString("marker").equals("first"), "Foreign tracks keep their order");
        copiedForeign.get(0).putString("marker", "changed");
        check(foreign(f.source).get(0).getString("marker").equals("first"), "Foreign payloads are deep copied");
    }

    @SuppressWarnings("unchecked")
    private static void convert() {
        Fixture f = new Fixture();
        boolean[] synced = {false};
        f.history = new UIFormUndoHandler(new UIElement()) {
            @Override protected void handleValue(BaseValue value) {
                synced[0] = value == f.film.replays && value.isSynced();
            }
        };
        f.history.getUndoManager().setCallback(null);
        BaseType before = f.film.toData();
        Anchor runtime = f.watcher.form.get().anchor.getRuntimeValue();
        Replay target = f.create(true);
        check(target != null && f.film.replays.getList().size() == 3, "Convert creates one Replay");
        check(f.root.parts.get(f.part.getId()) == null, "Convert removes the source BodyPart");
        check(f.root.parts.get(f.sibling.getId()) == f.sibling, "Convert keeps siblings");
        check(f.source.properties.tracks.keySet().stream().noneMatch(id -> id.formPath().equals(f.path) || id.formPath().startsWith(f.path + "/")), "Only transferred paths disappear from the source");
        check(f.source.properties.has(f.emptyTrack), "Unrelated empty tracks are not cleaned up");
        check(foreign(f.source).size() == 1 && foreign(f.source).get(0).getString("marker").equals("outside"), "Only transferred foreign paths disappear");
        verifyTarget(f, target);
        Anchor original = f.watcher.form.get().anchor.getOriginalValue();
        check(original.replay.equals(target.getId()) && original.attachment.equals(""), "Authored whole-form Anchor is retargeted even under a runtime override");
        check(runtime.replay.equals(target.getId()) && runtime.attachment.equals(f.child.getId() + "/head"), "Runtime Anchor follows the moved subtree");
        check(runtime.previous.replay.equals(target.getId()) && runtime.previous.attachment.isEmpty(), "Previous runtime endpoint is retargeted");
        BindingAnchor bound = (BindingAnchor) f.watcher.form.get().getBasic(BoneAnchors.key("arm")).getOriginalValue();
        check(bound.replay.equals(target.getId()) && bound.targetPath.equals(List.of(f.child.getId())) && bound.targetBone.equals("head/with/slashes"), "Structured bone endpoint preserves the raw bone name");
        KeyframeChannel<?> stateChannel = f.watcher.form.get().states.getAllTyped().get(0).properties.get(TrackId.property("", "anchor"));
        check(((Anchor) stateChannel.getKeyframes().get(0).getValue()).replay.equals(target.getId()), "Animation-state Anchor references are retargeted");
        Anchor keyed = (Anchor) firstValue(f.watcher, TrackId.property("", "anchor"));
        check(keyed.replay.equals(target.getId()) && keyed.attachment.equals(f.child.getId()), "Animated Anchor endpoint is retargeted");
        Anchor outside = (Anchor) firstValue(f.watcher, TrackId.property("", BoneAnchors.key("leg")));
        check(outside.replay.equals(f.source.getId()) && outside.attachment.equals(f.sibling.getId()), "Unrelated endpoint stays on source");
        Form nested = FormUtils.getForm(target.form.get(), f.child.getId());
        check(nested.anchor.getOriginalValue().replay.equals(target.getId()), "References inside the moved form are retargeted too");
        check(((Anchor) firstValue(target, TrackId.property(f.child.getId(), "anchor"))).replay.equals(target.getId()), "Transferred Anchor keyframes are retargeted");

        BaseType after = f.film.toData();
        Film restored = new Film(); restored.fromData(after.copy());
        check(restored.replays.getById(f.watcher.getId()).form.get().anchor.getOriginalValue().replay.equals(target.getId()), "Retargeted saved value survives reload");
        check(restored.replays.getById(f.source.getId()).form.get().parts.get(f.part.getId()) == null, "Removed part stays removed after reload");
        check(foreign(restored.replays.getById(target.getId())).size() == 2, "Unknown tracks survive reload");

        f.history.submitUndo(true);
        check(f.history.getUndoManager().getTotalUndos() == 1, "Conversion records one native undo entry");
        check(synced[0], "The undo payload is the synced Replay list, not the unsynced film root");
        f.history.getUndoManager().undo(f.film);
        check(before.equals(f.film.toData()), "Undo restores source, references and all tracks together");
        f.history.getUndoManager().redo(f.film);
        check(after.equals(f.film.toData()), "Redo restores the same Replay ID and redirected references");
        check(!BodyPartEditing.ownsPart(f.film, f.source, f.part), "Stale objects cannot be used after undo/redo reloads the film");
    }

    private static void verifyTarget(Fixture f, Replay target) {
        Form form = target.form.get();
        Anchor anchor = form.anchor.getOriginalValue();
        check(anchor.replay.equals(f.source.getId()) && anchor.attachment.isEmpty(), "New root Anchor follows the former owner");
        check(anchor.transform.createMatrix().equals(f.anchor.transform.createMatrix(), 1e-5f), "BodyPart local transform is preserved");
        KeyframeChannel<?> channel = target.properties.get(TrackId.property("", "anchor"));
        check(channel != null && channel.getKeyframes().size() == 1 && channel.getKeyframes().get(0).getTick() == 12, "Generated Anchor has one key at the cursor");
        check(form.get(BoneAnchors.key("inactive")) == null && !target.properties.has(TrackId.property("", BoneAnchors.key("inactive"))), "Dormant root-only bone Anchors do not become active");
        check(target.properties.has(TrackId.property("", "lighting")) && target.properties.has(TrackId.property(f.child.getId(), "lighting")), "Root and child property tracks are rebased");
        check(target.category.get().equals("props") && target.looping.get() == 1 && !target.shadow.get(), "Presentation settings are preserved");
        var stateTracks = form.states.getAllTyped().get(0).properties;
        check(!stateTracks.has(TrackId.property("", "anchor")) && !stateTracks.has(TrackId.property("", BoneAnchors.key("inactive"))), "Dormant state Anchors cannot override the generated root Anchor");
        check(stateTracks.has(TrackId.property("", "lighting")), "Non-Anchor animation state tracks are preserved");
        check(target.keyframes.toData().equals(f.source.keyframes.toData()), "Replay keyframes are copied");
        check(target.label.get().isEmpty() && !target.actor.get() && !target.fp.get(), "Actor flags and label stay at defaults");
    }

    private static void guards() {
        Fixture f = new Fixture();
        BaseType before = f.film.toData();
        check(BodyPartEditing.ownsPart(f.film, f.source, f.part), "Attached body part belongs to the source");
        check(!BodyPartEditing.ownsPart(f.film, f.watcher, f.part), "Mismatched source Replay is rejected");
        check(BodyPartEditing.createReplay(f.film, f.source, f.part, null, 0, true) == null, "Missing parent frame rejects conversion");
        check(before.equals(f.film.toData()), "Rejected conversion is a no-op");
        f.source.relative.set(true);
        check(f.create(true) == null, "Relative-coordinate Replay is rejected");
        f.source.relative.set(false);
        f.root.parts.removeBodyPart(f.part);
        check(f.create(true) == null, "Detached BodyPart with a stale manager is rejected");
    }

    private static void frames() {
        Matrix4f parent = new Matrix4f().translation(1, 2, 3).rotateY(.8f).scale(2);
        Matrix4f local = new Matrix4f().translation(3, -1, 2).rotateXYZ(.2f, .3f, -.4f).scale(-1, 2, .5f);
        Anchor anchor = BodyPartEditing.frameAnchor("source", "bone", parent, new Matrix4f(parent).mul(local));
        check(anchor != null && anchor.transform.createMatrix().equals(local, 1e-4f), "Mirrored TRS frame round-trips");
        check(BodyPartEditing.frameAnchor("source", "bone", parent, new Matrix4f().m30(Float.NaN)) == null, "Non-finite world frame is rejected");
        check(BodyPartEditing.frameAnchor("source", "bone", new Matrix4f(), new Matrix4f().m10(.5f)) == null, "Shear is rejected instead of silently approximated");
        check(BodyPartEditing.frameAnchor("source", "bone", parent, new Matrix4f().scale(0)) == null, "Singular local frame is rejected");
        check(BodyPartEditing.frameAnchor("source", "bone", new Matrix4f().scale(0), local) == null, "Singular parent frame is rejected");
        check(BodyPartEditing.frameAnchor("source", "bone", parent, null) == null, "Missing world frame is rejected");
    }

    private static void parentFrames() {
        Fixture f = new Fixture();
        StubEntity entity = new StubEntity(); entity.setForm(f.root); entity.setPosition(3, 4, 5);
        entity.setPrevX(3); entity.setPrevY(4); entity.setPrevZ(5);
        BaseFilmController controller = new BaseFilmController(f.film) {
            @Override public int getTick() { return 0; }
            @Override public Map<String, Integer> getActors() { return Map.of(); }
        };
        controller.entities.put(f.source.getId(), entity);
        Model model = new Model(null); model.topGroups.add(new ModelGroup("hand")); model.initialize(); model.snapshotChannels();
        ModelInstance instance = new ModelInstance("conversion", model, null, null); instance.form = f.root;
        Matrix4f localBone = new Matrix4f().translation(2, 1, 3).rotateY(.3f).scale(1.2f);
        f.root.setRenderer(new ModelFormRenderer(f.root) {
            @Override public ModelInstance getModel() { return instance; }
            @Override public void ensureAnimator(float transition) {}
            @Override public ModelInstance evaluateChannels(IEntity target, float transition) { return instance; }
            @Override public void collectMatrices(IEntity target, MatrixStack stack, MatrixCache matrices, String prefix, float transition) {
                Matrix4f base = new Matrix4f(stack.peek().getPositionMatrix()).mul(createEvaluatedTransform(transition).createMatrix());
                matrices.put(prefix, new Matrix4f(base), new Matrix4f(base));
                Matrix4f modelBase = new Matrix4f(base).rotateY((float) Math.PI);
                Matrix4f boneFrame = new Matrix4f(modelBase).mul(localBone);
                matrices.put(StringUtils.combinePaths(prefix, "hand"), boneFrame, new Matrix4f(boneFrame));
                for (BodyPart part : f.root.parts.getAllTyped()) {
                    Matrix4f parent = new Matrix4f(modelBase);
                    if (part.bone.get().equals("hand")) parent.mul(part.filterBoneMatrix(new Matrix4f(localBone)));
                    else parent.rotateY((float) Math.PI);
                    parent.mul(part.transform.get().createMatrix());
                    MatrixStack nested = new MatrixStack(); nested.peek().getPositionMatrix().set(parent);
                    FormUtilsClient.getRenderer(part.getForm()).collectMatrices(part.getRenderEntity(target), nested, matrices, StringUtils.combinePaths(prefix, part.getId()), transition);
                }
            }
        });
        f.root.transform.get().translate.set(2, -1, 3); f.root.transform.get().rotate.set(15, 30, -5);
        f.part.transform.get().translate.set(.5f, 1, -.2f); f.part.transform.get().rotate.set(10, -20, 30);
        f.partForm.transform.get().translate.set(.2f, -.1f, .3f);
        try {
            for (String bone : List.of("", "missing", "hand")) {
                f.part.bone.set(bone);
                for (int mask = 0; mask < 8; mask++) {
                    f.part.inheritPosition.set((mask & 1) != 0);
                    f.part.inheritRotation.set((mask & 2) != 0);
                    f.part.inheritScale.set((mask & 4) != 0);
                    Anchor anchor = BodyPartEditing.bodyPartAnchor(controller, f.source, f.part, 0);
                    check(anchor != null, "BodyPart frame resolves for bone '" + bone + "' and inheritance " + mask);
                    Matrix4f world = FilmMatrices.getAnchorMatrix(controller.entities, entity, f.source, 0, 0, 0, 0, new Anchor(f.source.getId(), f.path, true, true, true));
                    Matrix4f converted = FilmMatrices.getAnchorMatrix(controller.entities, entity, f.source, 0, 0, 0, 0, anchor);
                    converted.mul(FormUtilsClient.getRenderer(f.partForm).createEvaluatedTransform(0).createMatrix());
                    check(converted.equals(world, 1e-4f), "Anchor preserves native matrix for bone '" + bone + "' and inheritance " + mask);
                }
            }
        } finally { BoneAnchorRuntime.forget(controller); }
    }

    private static void menu() {
        Fixture f = new Fixture();
        UIFilmPanel panel = new UIFilmPanel(null) {
            @Override public Film getData() { return f.film; }
            @Override public void flushUndo() { if (f.history != null) f.history.submitUndo(true); }
        };
        FilmEditorController controller = new FilmEditorController(f.film, panel.getController());
        panel.getController().editorController = controller;
        StubEntity entity = new StubEntity(); entity.setForm(f.root);
        controller.entities.put(f.source.getId(), entity);
        var tree = panel.replayEditor.replaysList.bodyParts;
        UIBaseMenu ui = new UIBaseMenu() {}; ui.main.add(panel); ui.main.add(tree);
        panel.replayEditor.replaysList.setBodyPartsReplay(f.source, f.path);
        tree.area.set(10, 10, 200, 200);
        UIContext context = ui.context;
        context.setMouse(20, 10 + tree.scroll.scrollItemSize + 2);
        UISimpleContextMenu menu = (UISimpleContextMenu) tree.createContextMenu(context);
        check(menu != null && menu.actions.getList().size() == 2, "Mixin adds both actions to the real Body parts tree");
        TooltipContextAction convert = (TooltipContextAction) menu.actions.getList().get(0);
        TooltipContextAction copy = (TooltipContextAction) menu.actions.getList().get(1);
        String convertDescription = mchorse.bbs_mod.l10n.L10n.lang("bbs_bone_anchor.body_part.convert_to_replay.tooltip").get();
        String copyDescription = mchorse.bbs_mod.l10n.L10n.lang("bbs_bone_anchor.body_part.copy_as_replay.tooltip").get();
        check(!convertDescription.equals(copyDescription), "Convert and copy have different resolved descriptions");
        context.setup(new UIRenderingContext(null));
        context.batcher = new Batcher2D(null) {
            @Override public void icon(Icon icon, float x, float y, float ax, float ay) {}
            @Override public void text(String text, float x, float y, int color, boolean shadow) {}
            @Override public void gradientHBox(float x1, float y1, float x2, float y2, int left, int right) {}
            @Override public void clip(Area area, UIContext ctx) {}
            @Override public void unclip(UIContext ctx) {}
        };
        menu.actions.area.set(10, 10, 200, menu.actions.scroll.scrollItemSize * 2);
        context.setMouse(20, 12); context.resetTooltip();
        menu.actions.render(context);
        check(context.tooltip.element != null && context.tooltip.element.tooltip.getLabel().get().equals(convertDescription), "Hovering convert queues its explanation after the native row pass");
        context.setMouse(20, 12 + menu.actions.scroll.scrollItemSize); context.resetTooltip();
        menu.actions.render(context);
        check(context.tooltip.element != null && context.tooltip.element.tooltip.getLabel().get().equals(copyDescription), "Hovering copy switches to the copy explanation");
        context.setMouse(500, 500); context.resetTooltip(); menu.actions.render(context);
        check(context.tooltip.element == null, "Leaving the menu does not retain a stale tooltip");
        context.setMouse(20, 180);
        UISimpleContextMenu empty = (UISimpleContextMenu) tree.createContextMenu(context);
        check(empty == null || empty.isEmpty(), "Right-clicking blank tree space does not act on the previous selection");

        f.history = new UIFormUndoHandler(new UIElement());
        f.history.getUndoManager().setCallback(null);
        BaseValue.edit(f.source.category, value -> value.set("pending edit"));
        copy.runnable.run();
        check(f.history.getUndoManager().getTotalUndos() == 2, "Menu copy is separate from the preceding pending edit");
        convert.runnable.run();
        check(f.history.getUndoManager().getTotalUndos() == 3, "Consecutive menu actions each commit one undo entry");
        check(panel.replayEditor.getReplay() != f.source && f.film.replays.getList().size() == 4, "Menu selects the created Replay after rebuilding entities");
        f.history.getUndoManager().undo(f.film);
        Replay restored = f.film.replays.getById(f.source.getId());
        check(f.film.replays.getList().size() == 3 && restored.form.get().parts.get(f.part.getId()) != null, "Undoing convert retains the preceding copy");
        f.history.getUndoManager().undo(f.film);
        restored = f.film.replays.getById(f.source.getId());
        check(f.film.replays.getList().size() == 2 && restored.category.get().equals("pending edit"), "Undoing copy retains the preceding property edit");
        f.history.getUndoManager().undo(f.film);
        check(f.film.replays.getById(f.source.getId()).category.get().equals("props"), "Preceding edit remains independently undoable");
    }

    private static Object firstValue(Replay replay, TrackId id) {
        KeyframeChannel<?> channel = replay.properties.get(id);
        return channel.getKeyframes().get(0).getValue();
    }

    private static List<MapType> foreign(Replay replay) {
        return ((FormPropertiesAccessor)(Object) replay.properties).boneAnchor$foreignTracks();
    }

    private static MapType foreignTrack(String path, String marker) {
        MapType data = new MapType();
        data.putString("kind", "future:track"); data.putString("form", path); data.putString("marker", marker);
        return data;
    }

    private static final class Fixture {
        UIFormUndoHandler history;
        final Film film = new Film() {
            @Override public void preNotify(BaseValue value, int flag) {
                super.preNotify(value, flag);
                if (history != null) history.handlePreValues(value, flag);
            }
        };
        final Replay source = film.replays.addReplay(), watcher = film.replays.addReplay();
        final ModelForm root = new ModelForm(), partForm = new ModelForm(), childForm = new ModelForm();
        final BodyPart part = new BodyPart("11111111"), child = new BodyPart("22222222"), sibling = new BodyPart("33333333");
        final String path;
        final Anchor anchor = new Anchor(source.getId(), "", true, true, true);
        final KeyframeChannel<Float> lighting;
        final TrackId emptyTrack = TrackId.property("", "lighting");

        @SuppressWarnings("unchecked") Fixture() {
            film.setId("film");
            source.form.set(root); watcher.form.set(new ModelForm());
            part.setForm(partForm); root.parts.addBodyPart(part);
            child.setForm(childForm); partForm.parts.addBodyPart(child);
            sibling.setForm(new ModelForm()); root.parts.addBodyPart(sibling);
            path = FormUtils.getPath(partForm);
            anchor.transform.translate.set(1, 2, 3);
            source.category.set("props"); source.looping.set(1); source.shadow.set(false);
            source.label.set("source label"); source.actor.set(true); source.fp.set(true);
            source.keyframes.x.insert(0, 3D); source.keyframes.x.insert(20, 8D);
            lighting = source.properties.getOrCreate(root, TrackId.property(path, "lighting"));
            lighting.insert(0, .2f); lighting.insert(10, .8f); lighting.putLoop(new KeyframeLoop("loop", 0, 10, 30));
            source.properties.getOrCreate(root, TrackId.property(path + "/" + child.getId(), "lighting")).insert(0, .4f);
            source.properties.getOrCreate(root, emptyTrack);
            source.properties.getOrCreate(root, TrackId.property(sibling.getId(), "lighting")).insert(0, .6f);
            partForm.anchor.set(new Anchor("inactive", "", true, true, true));
            source.properties.getOrCreate(root, TrackId.property(path, "anchor")).insert(0, partForm.anchor.get().copy());
            source.properties.register(TrackId.property(path, BoneAnchors.key("inactive")), BoneAnchors.FACTORY).insert(0, partForm.anchor.get().copy());
            var partState = partForm.states.addState();
            partState.properties.register(TrackId.property("", "anchor"), KeyframeFactories.ANCHOR).insert(0, partForm.anchor.get().copy());
            partState.properties.register(TrackId.property("", BoneAnchors.key("inactive")), BoneAnchors.FACTORY).insert(0, partForm.anchor.get().copy());
            partState.properties.register(TrackId.property("", "lighting"), KeyframeFactories.FLOAT).insert(0, .75f);
            childForm.anchor.set(new Anchor(source.getId(), path, true, true, true));
            source.properties.getOrCreate(root, TrackId.property(path + "/" + child.getId(), "anchor")).insert(0, childForm.anchor.get().copy());
            foreign(source).add(foreignTrack(path, "first"));
            foreign(source).add(foreignTrack(path + "/" + child.getId(), "second"));
            foreign(source).add(foreignTrack(sibling.getId(), "outside"));
            Form form = watcher.form.get();
            form.anchor.set(new Anchor(source.getId(), path, true, true, true));
            Anchor runtime = new Anchor(source.getId(), path + "/" + child.getId() + "/head", true, true, true);
            runtime.previous = form.anchor.get().copy(); runtime.x = .5f; form.anchor.setRuntimeValue(runtime);
            BindingAnchor binding = new BindingAnchor(); binding.replay = source.getId();
            binding.target(List.of(part.getId(), child.getId()), "head/with/slashes");
            ((BaseValueBasic<Anchor>) form.getBasic(BoneAnchors.key("arm"))).set(binding);
            form.states.addState().properties.getOrCreate(form, TrackId.property("", "anchor")).insert(0, new Anchor(source.getId(), path, true, true, true));
            watcher.properties.getOrCreate(form, TrackId.property("", "anchor")).insert(0, new Anchor(source.getId(), path + "/" + child.getId(), true, true, true));
            watcher.properties.register(TrackId.property("", BoneAnchors.key("leg")), BoneAnchors.FACTORY).insert(0, new Anchor(source.getId(), sibling.getId(), true, true, true));
        }

        Replay create(boolean move) { return BodyPartEditing.createReplay(film, source, part, anchor, 12, move); }
    }

    private BodyPartEditingTest() {}
}
