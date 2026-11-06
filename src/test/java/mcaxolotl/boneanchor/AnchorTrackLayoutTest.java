package mcaxolotl.boneanchor;
import mcaxolotl.boneanchor.client.*;

import java.util.*;
import mchorse.bbs_mod.cubic.data.model.*;
import mchorse.bbs_mod.film.replays.tracks.*;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.Anchor;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

public final class AnchorTrackLayoutTest {
    private static int checks;
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
    @SuppressWarnings("unchecked") public static int run() throws Exception {
        checks = 0;
        ModelForm owner = new ModelForm();
        Model model = new Model(null);
        ModelGroup root = new ModelGroup("Root"), arm = new ModelGroup("arm"), forearm = new ModelGroup("forearm"),
            tip = new ModelGroup("tip"), sibling = new ModelGroup("sibling"), secondRoot = new ModelGroup("secondRoot");
        root.children.add(arm); arm.children.add(forearm); forearm.children.add(tip); root.children.add(sibling);
        model.topGroups.add(root); model.topGroups.add(secondRoot); model.initialize();
        List<TrackDescriptor> tracks = new ArrayList<>();
        TrackId whole = TrackId.property("", "anchor");
        TrackDescriptor wholeRow = new TrackDescriptor(whole,new KeyframeChannel<Anchor>("anchor", mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories.ANCHOR),owner,IKey.constant("Anchor"),null,0,owner.anchor);
        tracks.add(wholeRow);
        Map<String,TrackDescriptor> rows = new HashMap<>();
        for (String bone : List.of("tip","sibling","Root","arm","forearm","secondRoot","orphan")) {
            String key = BoneAnchors.key(bone);
            BoneAnchors.ensure(owner,key);
            var property = (BaseValueBasic<Anchor>)owner.getBasic(key);
            var channel = new KeyframeChannel<Anchor>(key, BoneAnchors.FACTORY);
            var row = new TrackDescriptor(TrackId.property("",key),channel,owner,IKey.constant(bone),null,0,property);
            tracks.add(row); rows.put(bone,row);
        }
        List<TrackDescriptor> filterCatalog = TrackCatalog.of(owner);
        int anchorIndex = -1;
        int firstBoneIndex = -1;
        for (int i = 0; i < filterCatalog.size(); i++) {
            TrackId id = filterCatalog.get(i).id();
            if (id.kind() == TrackKind.PROPERTY && id.subject().equals("anchor")) anchorIndex = i;
            if (id.kind() == TrackKind.PROPERTY && BoneAnchors.isKey(id.subject()) && firstBoneIndex < 0) firstBoneIndex = i;
        }
        check(anchorIndex >= 0 && firstBoneIndex == anchorIndex + 1, "Filter catalog places bone Anchor rows directly after the overall Anchor");
        List<TrackDescriptor> all = new ArrayList<>(tracks);
        AnchorTrackLayout.apply(owner,model,all,false);
        check(all.get(0) == wholeRow,"Whole-model Anchor remains the same editable row");
        Map<String,TrackDescriptor> byBone = new HashMap<>();
        for (var row : all) if (BoneAnchors.isKey(row.id().subject())) byBone.put(BoneAnchors.bone(row.id().subject()),row);
        check(byBone.get("Root").parent().equals(whole),"Root folds under whole Anchor");
        check(byBone.get("tip").title().get().equals("tip"),"Folded child track displays only the original bone name");
        check(byBone.get("arm").parent().equals(rows.get("Root").id()),"Arm folds under Root");
        check(byBone.get("forearm").parent().equals(rows.get("arm").id()),"Forearm folds under arm");
        check(byBone.get("tip").parent().equals(rows.get("forearm").id()),"Tip folds under forearm");
        check(byBone.get("secondRoot").parent().equals(whole),"Multiple roots remain supported");
        check(byBone.get("orphan").parent().equals(whole),"Missing-rig bone remains editable");
        var ordered = TrackCatalog.ordered(all);
        check(ordered.indexOf(byBone.get("tip")) == ordered.indexOf(byBone.get("forearm"))+1,"Depth-first child order");
        check(ordered.indexOf(byBone.get("sibling")) > ordered.indexOf(byBone.get("tip")),"Sibling follows complete branch");
        List<TrackDescriptor> empty = new ArrayList<>(tracks);
        AnchorTrackLayout.apply(owner,model,empty,true);
        check(empty.size()==1 && empty.get(0)==wholeRow,"No bindings keeps whole-model Anchor visible");
        ((KeyframeChannel<Anchor>)rows.get("tip").channel()).insert(100,new Anchor("otherActor","hand",true,false,false));
        List<TrackDescriptor> filtered = new ArrayList<>(tracks);
        AnchorTrackLayout.apply(owner,model,filtered,true);
        Set<String> shown = new HashSet<>();
        for (var row : filtered) if (BoneAnchors.isKey(row.id().subject())) shown.add(BoneAnchors.bone(row.id().subject()));
        check(shown.equals(Set.of("Root","arm","forearm","tip")),"Future binding keeps only its ancestor spine");
        check(rows.get("tip").channel().getList().size()==1,"Filtering does not delete keyframes");
        ((BaseValueBasic<Anchor>)rows.get("orphan").property()).set(new Anchor("otherActor","hand",true,false,false));
        filtered = new ArrayList<>(tracks); AnchorTrackLayout.apply(owner,model,filtered,true);
        check(filtered.stream().anyMatch(t->t.id().equals(rows.get("orphan").id())),"Bound orphan survives filtering");
        check(rows.get("sibling").property()==owner.getBasic(BoneAnchors.key("sibling")),"Hidden bone property retained");
        // Load the added UI mixin and invoke the actual menu extension, including its refresh callback.
        var menu = new mchorse.bbs_mod.ui.utils.context.ContextMenuManager();
        var sheet = new mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet(wholeRow);
        TrackId[] refreshed = {null};
        mchorse.bbs_mod.ui.film.replays.UIReplaysEditorUtils.addOverlayTrackAction(menu,sheet,id -> refreshed[0] = id);
        check(!menu.actions.isEmpty(),"Anchor context menu has bound-only toggle");
        boolean oldFilter = BoneAnchorClient.boundOnly();
        try {
            menu.actions.get(0).runnable.run();
            check(BoneAnchorClient.boundOnly() != oldFilter,"Menu toggles the session filter");
            check(whole.equals(refreshed[0]),"Menu refreshes and expands Anchor");
        } finally { BoneAnchorClient.setBoundOnly(oldFilter); }
        return checks;
    }
    private AnchorTrackLayoutTest() {}
}
