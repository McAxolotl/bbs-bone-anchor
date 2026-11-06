package mcaxolotl.boneanchor.client;

import java.util.*;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.*;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Immutable value copies; querying a bone must never leave a renderer in another pose. */
public final class RigPose {
    public record Bone(Transform channels,Quaternionf orient,Vector3f offset,Vector3f scale,
                       boolean visible,boolean poseVisible,int color,int overlay,float lighting){}
    public final Map<String,Bone> bones=new LinkedHashMap<>();
    static RigPose capture(Model model) {
        RigPose pose=new RigPose();
        for(ModelGroup group:model.getAllGroups()) {
            Vector3f scale=AnchorScales.get(group);
            pose.bones.put(group.id,new Bone(group.current.copy(),
                group.orient==null?null:new Quaternionf(group.orient),group.offset==null?null:new Vector3f(group.offset),
                scale==null?null:new Vector3f(scale),group.visible,group.poseVisible,
                group.color.getARGBColor(),group.overlay.getARGBColor(),group.lighting));
        }
        return pose;
    }
    void apply(Model model) {
        BoneAnchorRuntime.clearEvaluation(model);
        for(ModelGroup group:model.getAllGroups()) {
            Bone b=bones.get(group.id);if(b==null)continue;
            group.current.copy(b.channels);group.orient=b.orient==null?null:new Quaternionf(b.orient);
            group.offset=b.offset==null?null:new Vector3f(b.offset);
            group.visible=b.visible;group.poseVisible=b.poseVisible;group.color.set(b.color,true);group.overlay.set(b.overlay,true);group.lighting=b.lighting;
            if(b.scale!=null)AnchorScales.set(group,b.scale);
            if(b.offset!=null || b.scale!=null)BoneAnchorRuntime.markBound(model);
        }
    }
    static Scope scope(ModelInstance instance) { return new Scope(instance); }
    public static final class Scope implements AutoCloseable {
        private final ModelInstance instance;private final RigPose pose;
        private final mchorse.bbs_mod.forms.forms.Form form;
        private final org.joml.Matrix4f base;
        private Scope(ModelInstance instance) {
            this.instance=instance;this.pose=capture((Model)instance.model);form=instance.form;
            base=instance.lastBaseTransform==null?null:new org.joml.Matrix4f(instance.lastBaseTransform);
        }
        public void close(){pose.apply((Model)instance.model);instance.form=form;instance.lastBaseTransform=base;instance.clearChannels();}
    }
}
