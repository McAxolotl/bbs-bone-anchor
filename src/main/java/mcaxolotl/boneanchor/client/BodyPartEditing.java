package mcaxolotl.boneanchor.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import mcaxolotl.boneanchor.AnchorMath;
import mcaxolotl.boneanchor.AnchorRetargeting;
import mcaxolotl.boneanchor.BoneAnchors;
import mcaxolotl.boneanchor.compat.mixin.FormPropertiesAccessor;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.FormProperties;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.Anchor;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import mchorse.bbs_mod.settings.values.base.BaseValueGroup;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.forms.editors.UIForms;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import org.joml.Matrix4f;

/**
 * Converts a BodyPart's nested form into a Replay and expresses the former
 * BodyPart parent relationship as an Anchor keyframe on the new Replay form.
 */
public final class BodyPartEditing {
    public static void contextMenu(ContextMenuManager menu, UIFilmPanel panel, Replay source, UIForms bodyParts) {
        UIForms.FormEntry entry = hoveredEntry(bodyParts);
        BodyPart part = entry == null ? null : entry.part;
        if (menu == null || !convertible(panel, source, part)) return;

        menu.action(new TooltipContextAction(
            Icons.SCENE,
            L10n.lang("bbs_bone_anchor.body_part.convert_to_replay"),
            L10n.lang("bbs_bone_anchor.body_part.convert_to_replay.tooltip"),
            () -> createReplay(panel, source, part, true)
        ));
        menu.action(new TooltipContextAction(
            Icons.DUPE,
            L10n.lang("bbs_bone_anchor.body_part.copy_as_replay"),
            L10n.lang("bbs_bone_anchor.body_part.copy_as_replay.tooltip"),
            () -> createReplay(panel, source, part, false)
        ));
    }

    private static boolean convertible(UIFilmPanel panel, Replay source, BodyPart part) {
        return panel != null && ownsPart(panel.getData(), source, part)
            && panel.getController().getEntities().containsKey(source.getId());
    }

    /** Reject stale menu entries, detached parts and forms belonging to a different Replay. */
    static boolean ownsPart(Film film, Replay source, BodyPart part) {
        if (film == null || source == null || source.relative.get() || part == null || part.getForm() == null
            || film.replays.getById(source.getId()) != source || part.getManager() == null
            || part.getManager().get(part.getId()) != part) return false;
        Form root = source.form.getOriginalValue();
        String path = FormUtils.getPath(part.getForm());
        return root != null && source.form.get() == root && !path.isEmpty()
            && FormUtils.getForm(root, path) == part.getForm();
    }

    private static void createReplay(UIFilmPanel panel, Replay source, BodyPart part, boolean move) {
        if (!convertible(panel, source, part)) return;
        Anchor anchor = bodyPartAnchor(panel.getController().editorController, source, part, 0F);
        panel.flushUndo();
        Replay target = createReplay(panel.getData(), source, part, anchor, panel.getCursor(), move);
        if (target == null) return;
        panel.getController().createEntities();
        panel.replayEditor.replaysList.replays.refreshReplayList();
        panel.replayEditor.setReplay(target);
        panel.flushUndo();
    }

    /** Prepare the new Replay before mutating the film, then record the conversion as one edit. */
    @SuppressWarnings("unchecked")
    static Replay createReplay(Film film, Replay source, BodyPart part, Anchor anchor, float cursor, boolean move) {
        if (!ownsPart(film, source, part) || anchor == null || !Float.isFinite(cursor)) return null;
        Form bodyPartForm = FormUtils.copy(part.getForm());
        if (bodyPartForm == null) return null;
        String sourcePrefix = FormUtils.getPath(part.getForm());
        Form owner = part.getManager().getOwner();
        Replay target = new Replay("");
        copyReplayPresentation(source, target);
        target.keyframes.fromData(source.keyframes.toData());
        target.form.set(bodyPartForm);
        transferFormTracks(source, target, sourcePrefix);
        clearRootAnchors(bodyPartForm);
        removeRootAnchorTracks(target.properties);
        bodyPartForm.anchor.set(anchor.copy());
        KeyframeChannel<Anchor> channel = target.properties.getOrCreate(bodyPartForm, TrackId.property("", "anchor"));
        if (channel == null) return null;
        channel.insert(cursor, anchor.copy());

        BaseValue.edit(film.replays, IValueListener.FLAG_BATCH | IValueListener.FLAG_UNMERGEABLE, ignored -> {
            film.replays.add(target);
            if (move) {
                retargetAnchors(film, source.getId(), sourcePrefix, target.getId());
                removeFormTracks(source, sourcePrefix);
                BaseValue.edit(source.form, value -> owner.parts.removeBodyPart(part));
            }
        });
        return target;
    }

    /** Reuse the runtime's BodyPart frame, including model-space flips and filtered inheritance. */
    static Anchor bodyPartAnchor(BaseFilmController controller, Replay source, BodyPart part, float transition) {
        if (controller == null || !ownsPart(controller.film, source, part)) return null;
        var scene = BindingScenes.scene(controller);
        var slot = scene.slot(source.getId(), List.of(FormUtils.getPath(part.getForm()).split("/")));
        if (slot == null || slot.parent == null) return null;
        Form owner = slot.parent.form;
        String bone = part.bone.get();
        boolean validBone = (owner instanceof ModelForm || owner instanceof MobForm)
            && !bone.isEmpty() && FormUtilsClient.getBones(owner).contains(bone);
        String ownerPath = String.join("/", slot.parent.path);
        String attachment = validBone ? StringUtils.combinePaths(ownerPath, bone) : ownerPath;
        try (var request = BindingResolver.request(scene, transition)) {
            Matrix4f parentFrame = validBone ? request.session.bone(slot.parent, bone) : request.session.frame(slot.parent);
            return frameAnchor(source.getId(), attachment, parentFrame, request.session.parent(slot));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /**
     * Anchor that keeps a world frame under the parent frame it used to have. The local transform is
     * what preserves the sampled frame: the new whole-form Anchor resolves back to the frame the
     * BodyPart occupied, instead of snapping onto its former parent.
     */
    static Anchor frameAnchor(String replay, String attachment, Matrix4f parentFrame, Matrix4f worldFrame) {
        if (replay == null || attachment == null || parentFrame == null || worldFrame == null) return null;

        Anchor anchor = new Anchor(replay, attachment, true, true, true);
        try {
            Matrix4f local = AnchorMath.local(parentFrame, worldFrame);
            AnchorMath.decompose(local);
            anchor.transform.fromMatrix(local);
        } catch (IllegalArgumentException ignored) {
            return null;
        }

        return anchor;
    }

    private static void copyReplayPresentation(Replay source, Replay target) {
        target.category.set(source.category.get());
        target.enabled.set(source.enabled.get());
        target.shadow.set(source.shadow.get());
        target.shadowSize.set(source.shadowSize.get());
        target.shadowFollow.set(source.shadowFollow.get());
        target.shadowOffset.set(source.shadowOffset.get().copy());
        target.looping.set(source.looping.get());
    }

    private static void retargetAnchors(Film film, String sourceReplayId, String sourcePrefix, String targetReplayId) {
        Set<BaseValue> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Replay replay : film.replays.getList()) {
            retargetValues(replay.form, sourceReplayId, sourcePrefix, targetReplayId, visited);
            retargetValues(replay.properties, sourceReplayId, sourcePrefix, targetReplayId, visited);
        }
    }

    private static void retargetValues(BaseValue value, String sourceReplayId, String sourcePrefix, String targetReplayId, Set<BaseValue> visited) {
        if (value == null || !visited.add(value)) return;
        if (value instanceof BaseValueBasic<?> basic) {
            // Playback overrides must not hide the authored value from a persistent edit.
            retargetValue(basic.getOriginalValue(), sourceReplayId, sourcePrefix, targetReplayId, visited);
            if (basic.getRuntimeValue() != basic.getOriginalValue()) {
                retargetValue(basic.getRuntimeValue(), sourceReplayId, sourcePrefix, targetReplayId, visited);
            }
        } else if (value instanceof Keyframe<?> keyframe) {
            retargetValue(keyframe.getValue(), sourceReplayId, sourcePrefix, targetReplayId, visited);
        } else if (value instanceof BaseValueGroup group) {
            for (BaseValue child : group.getAll()) {
                retargetValues(child, sourceReplayId, sourcePrefix, targetReplayId, visited);
            }
        }
    }

    private static void retargetValue(Object value, String sourceReplayId, String sourcePrefix, String targetReplayId, Set<BaseValue> visited) {
        if (value instanceof Anchor anchor) {
            AnchorRetargeting.retarget(anchor, sourceReplayId, sourcePrefix, targetReplayId);
        } else if (value instanceof Form form) {
            retargetValues(form, sourceReplayId, sourcePrefix, targetReplayId, visited);
        }
    }

    private static UIForms.FormEntry hoveredEntry(UIForms bodyParts) {
        if (bodyParts == null) return null;
        UIContext context = bodyParts.getContext();
        if (context != null) {
            int index = bodyParts.getHoveredIndex(context);
            List<UIForms.FormEntry> entries = bodyParts.getList();
            if (index >= 0 && index < entries.size()) return entries.get(index);
        }
        return null;
    }

    /** Copy tracks by their structured address; raw bone names are never parsed as paths. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void transferFormTracks(Replay source, Replay target, String sourcePrefix) {
        for (TrackId id : source.properties.tracks.keySet()) {
            if (!belongsTo(id.formPath(), sourcePrefix)) continue;
            KeyframeChannel<?> channel = source.properties.tracks.get(id);
            if (channel == null) continue;

            String relative = relativePath(id.formPath(), sourcePrefix);
            TrackId moved = new TrackId(id.kind(), relative, id.subject(), id.property());
            KeyframeChannel copied = new KeyframeChannel<>(moved.toKey(), channel.getFactory());
            copied.copyKeyframes(channel);
            target.properties.put(moved, copied);
        }

        transferForeignTracks(source.properties, target.properties, sourcePrefix);
    }

    private static void transferForeignTracks(FormProperties source, FormProperties target, String sourcePrefix) {
        List<MapType> sourceForeign = ((FormPropertiesAccessor)(Object) source).boneAnchor$foreignTracks();
        List<MapType> targetForeign = ((FormPropertiesAccessor)(Object) target).boneAnchor$foreignTracks();

        for (MapType data : sourceForeign) {
            MapType copied = remapForeignTrack(data, sourcePrefix);
            if (copied != null) targetForeign.add(copied);
        }
    }

    private static void removeFormTracks(Replay source, String prefix) {
        for (TrackId id : new ArrayList<>(source.properties.tracks.keySet())) {
            if (belongsTo(id.formPath(), prefix)) source.properties.remove(id);
        }
        ((FormPropertiesAccessor)(Object) source.properties).boneAnchor$foreignTracks()
            .removeIf(data -> data.has("form") && belongsTo(data.getString("form"), prefix));
    }

    private static MapType remapForeignTrack(MapType data, String sourcePrefix) {
        if (data == null || !data.has("form")) return null;
        String form = data.getString("form");
        if (form == null || !belongsTo(form, sourcePrefix)) return null;

        String mapped = relativePath(form, sourcePrefix);
        MapType copy = (MapType) data.copy();
        if (mapped.isEmpty()) copy.remove("form");
        else copy.putString("form", mapped);
        return copy;
    }

    private static boolean belongsTo(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    private static String relativePath(String path, String prefix) {
        return path.equals(prefix) ? "" : path.substring(prefix.length() + 1);
    }

    private static void removeRootAnchorTracks(FormProperties properties) {
        properties.remove(TrackId.property("", "anchor"));
        for (TrackId id : new ArrayList<>(properties.tracks.keySet())) {
            if (id.formPath().isEmpty() && id.is(mchorse.bbs_mod.film.replays.tracks.TrackKind.PROPERTY) && BoneAnchors.isKey(id.subject())) {
                properties.remove(id);
            }
        }
    }

    private static void clearRootAnchors(Form form) {
        if (form == null) return;
        form.anchor.set(new Anchor());
        for (var state : form.states.getAllTyped()) removeRootAnchorTracks(state.properties);
        for (var value : new ArrayList<>(form.getAll())) {
            if (BoneAnchors.isKey(value.getId())) form.remove(value);
        }
    }

    private BodyPartEditing() {}
}
