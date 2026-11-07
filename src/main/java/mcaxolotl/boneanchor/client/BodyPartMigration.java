package mcaxolotl.boneanchor.client;

import java.util.ArrayList;
import java.util.Collections;
import mcaxolotl.boneanchor.compat.mixin.FormPropertiesAccessor;
import mchorse.bbs_mod.film.replays.FormProperties;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

/** Migrate authored data and representable entity inputs without a live link to the source. */
final class BodyPartMigration {
    static IEntity independentEntity(BodyPart part) {
        for (BaseValue value = part; value != null; value = value.getParent()) {
            if (value instanceof BodyPart ancestor && !ancestor.useTarget.get()) return ancestor.getEntity();
        }
        return null;
    }

    /** Store only non-default, recordable inputs; age, swing phase and limb history have no tracks. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static boolean snapshot(ReplayKeyframes target, IEntity input, float cursor) {
        if (input == null) return false;
        ReplayKeyframes recorded = new ReplayKeyframes("snapshot");
        recorded.record(0, input, null);
        for (KeyframeChannel channel : recorded.getChannels()) {
            Object value = channel.getKeyframes().isEmpty() ? channel.getFactory().createEmpty()
                : ((mchorse.bbs_mod.utils.keyframes.Keyframe) channel.getKeyframes().get(0)).getValue();
            if (value instanceof Number number && !Double.isFinite(number.doubleValue())) return false;
            if (!channel.getFactory().compare(value, channel.getFactory().createEmpty())) {
                KeyframeChannel destination = (KeyframeChannel) target.get(channel.getId());
                destination.insert(cursor, channel.getFactory().copy(value));
            }
        }
        return true;
    }

    static void copyStates(BodyPart part, Form target) {
        String subtree = FormUtils.getPath(part.getForm());
        var owners = new ArrayList<Form>();
        for (Form owner = part.getManager().getOwner(); owner != null; owner = owner.getParentForm()) owners.add(owner);
        Collections.reverse(owners);
        for (Form owner : owners) {
            String path = FormUtils.getPath(owner);
            String prefix = path.isEmpty() ? subtree : subtree.substring(path.length() + 1);
            for (var state : owner.states.getAllTyped()) {
                FormProperties projected = new FormProperties("properties");
                BodyPartEditing.transferFormTracks(state.properties, projected, prefix);
                BodyPartEditing.removeRootAnchorTracks(projected);
                if (projected.tracks.isEmpty() && ((FormPropertiesAccessor)(Object) projected).boneAnchor$foreignTracks().isEmpty()) continue;
                var existing = target.states.getById(state.id.get());
                var copy = target.states.addState();
                String freshId = copy.id.get();
                copy.fromData(state.toData());
                if (existing != null) copy.id.set(freshId);
                String custom = copy.customId.get();
                if (!custom.isEmpty() && target.states.getAllTyped().stream().anyMatch(s -> s != copy && (s.customId.get().equals(custom) || s.id.get().equals(custom))))
                    copy.customId.set(copy.id.get() + "/" + custom);
                copy.properties.fromData(projected.toData());
            }
        }
    }

    static void removeStates(BodyPart part) {
        String subtree = FormUtils.getPath(part.getForm());
        for (Form owner = part.getManager().getOwner(); owner != null; owner = owner.getParentForm()) {
            String path = FormUtils.getPath(owner);
            for (var state : owner.states.getAllTyped())
                BodyPartEditing.removeFormTracks(state.properties, path.isEmpty() ? subtree : subtree.substring(path.length() + 1));
        }
    }

    private BodyPartMigration() {}
}
