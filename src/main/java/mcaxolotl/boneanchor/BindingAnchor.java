package mcaxolotl.boneanchor;

import java.util.*;
import mchorse.bbs_mod.forms.forms.utils.Anchor;
import mchorse.bbs_mod.data.types.*;

/** Target form path and bone name are separate; '/' in a bone is never a path delimiter. */
public final class BindingAnchor extends Anchor {
    public List<String> targetPath=List.of();
    public String targetBone;
    private String projection;
    public boolean structured() { return hasTarget() && projection!=null && projection.equals(attachment); }
    public void target(List<String> path,String bone) {
        targetPath=List.copyOf(path);targetBone=bone;
        attachment=String.join("/",path)+(bone==null?"":(path.isEmpty()?"":"/")+bone);projection=attachment;
    }
    public static BindingAnchor of(Anchor anchor) {
        BindingAnchor copy=new BindingAnchor();copy.replay=anchor.replay;copy.attachment=anchor.attachment;
        copy.inheritPosition=anchor.inheritPosition;copy.inheritRotation=anchor.inheritRotation;copy.inheritScale=anchor.inheritScale;
        copy.transform.copy(anchor.transform);
        if(anchor instanceof BindingAnchor own && own.structured())copy.target(own.targetPath,own.targetBone);
        return copy;
    }
    @Override public BindingAnchor copy(){return of(this);}
    @Override public boolean hasSameTarget(Anchor other) {
        if(!super.hasSameTarget(other))return false;
        if(other instanceof BindingAnchor b && structured() && b.structured())
            return targetPath.equals(b.targetPath)&&Objects.equals(targetBone,b.targetBone);
        return true;
    }
    @Override public void fromData(MapType data) {
        super.fromData(data);projection=null;
        if(data.has("target")) {
            MapType target=data.getMap("target");List<String> path=new ArrayList<>();
            for(BaseType part:target.getList("form_path"))path.add(part.asString());
            target(path,target.getString("kind").equals("bone")?target.getString("bone"):null);
        }
    }
    @Override public void toData(MapType data) {
        super.toData(data);
        if(structured()) {
            MapType target=new MapType();ListType path=new ListType();for(String id:targetPath)path.addString(id);
            target.put("form_path",path);target.putString("kind",targetBone==null?"form":"bone");
            if(targetBone!=null)target.putString("bone",targetBone);
            data.put("target",target);data.remove("attachment");
        } else {
            data.remove("target");
        }
    }
}
