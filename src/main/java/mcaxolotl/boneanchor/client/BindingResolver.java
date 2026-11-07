package mcaxolotl.boneanchor.client;

import java.util.*;
import java.util.function.Supplier;
import mcaxolotl.boneanchor.*;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.*;
import mchorse.bbs_mod.cubic.constraints.ModelConstraintsRuntime;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.*;
import mchorse.bbs_mod.forms.forms.utils.Anchor;
import mchorse.bbs_mod.forms.renderers.*;
import mchorse.bbs_mod.forms.renderers.utils.*;
import mchorse.bbs_mod.settings.values.base.*;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.film.FilmMatrices;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** A request-local, staged bone graph. No whole-actor traversal is used to read a model bone. */
public final class BindingResolver {
    private static final ThreadLocal<Session> CURRENT=new ThreadLocal<>();
    private static final ThreadLocal<Integer> NATIVE=ThreadLocal.withInitial(()->0);
    private static final ThreadLocal<Query> QUERY=new ThreadLocal<>();
    private static final ThreadLocal<Form> LOCAL_FORM=new ThreadLocal<>();
    public static boolean localCollection(Form form){return LOCAL_FORM.get()==form;}
    private record Query(BindingScenes.Slot slot,Map<String,Override> overrides,Map<String,Vector3f> goals){}
    private static final ThreadLocal<ModelInstance> DISPATCH=new ThreadLocal<>();
    static boolean intercept(ModelInstance instance,Map<String,Vector3f> targets,Map<String,Vector3f> poles,float transition) {
        if(nativeQuery() || DISPATCH.get()==instance || !(instance.form instanceof ModelForm form))return false;
        BindingScenes.Slot slot=BindingScenes.find(form);if(slot==null || slot.parent!=null || !slot.scene.enabled())return false;
        try(Request request=request(slot.scene,transition)) {
            applyNative(instance,transition);
            ModelInstance previous=DISPATCH.get();DISPATCH.set(instance);
            try {mchorse.bbs_mod.cubic.ik.ModelIKRuntime.apply(instance,targets,poles);}
            finally {DISPATCH.set(previous);}
        }
        return true;
    }
    public record Goals(Map<String,Vector3f> targets,Map<String,Float> weights){}
    public static Goals goals(ModelInstance instance,Map<String,Vector3f> original,Map<String,Float> weights) {
        if(nativeQuery() || !(instance.form instanceof ModelForm form))return new Goals(original,weights);
        Session session=current();BindingScenes.Slot slot=session==null?null:session.scene.byForm.get(form);
        if(slot==null || slot.parent!=null)return new Goals(original,weights);
        Query query=QUERY.get();Map<String,Vector3f> world=query!=null&&query.slot==slot?query.goals:session.goals(slot,null);
        if(world.isEmpty())return new Goals(original,weights);
        Map<String,Vector3f> merged=original==null?new HashMap<>():new HashMap<>(original);
        Map<String,Float> mixed=weights==null?new HashMap<>():new HashMap<>(weights);
        Matrix4f inverse=invertOrNull(session.modelBase(slot));
        if(inverse==null)return new Goals(original,weights);
        for(var entry:world.entrySet()) {
            if(entry.getValue()==null || !entry.getValue().isFinite())continue;
            Vector3f local=inverse.transformPosition(new Vector3f(entry.getValue()));
            if(local.isFinite()){merged.put(entry.getKey(),local);mixed.put(entry.getKey(),1f);}
        }
        return new Goals(merged,mixed);
    }
    public record Override(Vector3f offset,org.joml.Quaternionf rotation,Vector3f scale){}
    private record Node(BindingScenes.Slot slot,String stage,String bone){}
    private record Fk(ModelInstance instance,RigPose pose,MatrixCache fallback){}
    private record Inputs(mchorse.bbs_mod.utils.pose.Transform transform,Anchor header,Map<String,Anchor> anchors,
                          Map<String,mchorse.bbs_mod.cubic.ik.IKControl> controls,Vector3f modelScale){}
    static Session current(){return CURRENT.get();}
    public static boolean nativeQuery(){return NATIVE.get()>0;}
    private static Matrix4f invertOrNull(Matrix4f matrix) {
        try { return AnchorMath.inverse(matrix); }
        catch (IllegalArgumentException ignored) { return null; }
    }
    public static final class Request implements AutoCloseable {
        private final Session previous;public final Session session;
        Request(BindingScenes.Scene scene,float transition) {
            previous=CURRENT.get();session=previous!=null&&previous.scene==scene&&previous.transition==transition?previous:new Session(scene,transition);
            CURRENT.set(session);
        }
        public void close(){CURRENT.set(previous);}
    }
    public static Request request(BindingScenes.Scene scene,float transition){return new Request(scene,transition);}
    static void applyNative(ModelInstance instance,float transition) {
        if(nativeQuery() || !(instance.form instanceof ModelForm form) || !(instance.model instanceof Model model))return;
        Query query=QUERY.get();
        if(query!=null && query.slot.form==form){apply(model,query.overrides);return;}
        BindingScenes.Slot slot=BindingScenes.find(form);if(slot==null || slot.parent!=null || !slot.scene.enabled())return;
        try(Request request=request(slot.scene,transition)) {
            Session s=request.session;s.live.remove(slot);s.matrices.clear();s.overrides.remove(slot);
            s.fk.put(slot,new Fk(instance,RigPose.capture(model),null));s.inputs.put(slot,s.currentInputs(slot));
            Map<String,Override> all=new LinkedHashMap<>();
            for(var value:form.getAll())if(BoneAnchors.isKey(value.getId())) {
                String bone=BoneAnchors.bone(value.getId());if(model.getGroup(bone)==null)continue;
                s.pre(slot,bone);Override override=s.overrides.getOrDefault(slot,Map.of()).get(bone);
                if(override!=null)all.put(bone,override);
            }
            apply(model,all);
        }
    }
    private static void apply(Model model,Map<String,Override> overrides) {
        BoneAnchorRuntime.clearEvaluation(model);
        for(var entry:overrides.entrySet()) {
            ModelGroup group=model.getGroup(entry.getKey());if(group==null)continue;Override value=entry.getValue();
            group.offset=new Vector3f(value.offset);group.orient=new org.joml.Quaternionf(value.rotation);
            AnchorScales.set(group,value.scale);BoneAnchorRuntime.markBound(model);
        }
    }
    public static MatrixCache collect(Form form,float transition) {
        BindingScenes.Slot slot=BindingScenes.find(form);if(slot==null)return null;
        try(Request request=request(slot.scene,transition)) {
            MatrixCache out=new MatrixCache();request.session.collect(slot,new Matrix4f(),out,"");return out;
        }
    }
    public static boolean collectInto(Form form,float transition,Matrix4f incoming,MatrixCache out,String prefix) {
        if(nativeQuery())return false;
        BindingScenes.Slot slot=BindingScenes.find(form);
        if(slot==null || !slot.scene.enabled() || !BindingScenes.subtreeBinding(form))return false;
        try(Request request=request(slot.scene,transition)){request.session.collect(slot,new Matrix4f(incoming),out,prefix);return true;}
    }
    public static final class Session {
        public final BindingScenes.Scene scene;public final float transition;
        private final Map<Node,Matrix4f> matrices=new HashMap<>();
        private final LinkedHashSet<Node> visiting=new LinkedHashSet<>();
        private final Map<Node,Integer> entryDepth=new HashMap<>();
        private final Deque<String> constraints=new ArrayDeque<>();
        private final Set<String> blocked=new HashSet<>();
        private final Map<BindingScenes.Slot,Fk> fk=new IdentityHashMap<>();
        private final Map<BindingScenes.Slot,Inputs> inputs=new IdentityHashMap<>();
        private final Map<BindingScenes.Slot,Anchor> rootHeaders=new IdentityHashMap<>();
        private final Map<BindingScenes.Slot,Map<String,Override>> overrides=new IdentityHashMap<>();
        private int generation;
        private final Set<String> inactive=new HashSet<>();
        Session(BindingScenes.Scene scene,float transition){
            this.scene=scene;this.transition=transition;
            for(var slot:scene.slots.values())if(slot.parent==null)rootHeaders.put(slot,
                mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories.ANCHOR.copy(slot.form.anchor.get()));
        }
        private Inputs currentInputs(BindingScenes.Slot slot) {
            Map<String,Anchor> anchors=new LinkedHashMap<>();
            for(var value:slot.form.getAll())if(slot.parent==null && BoneAnchors.isKey(value.getId()) && value instanceof BaseValueBasic<?> basic && basic.get() instanceof Anchor a)
                anchors.put(BoneAnchors.bone(value.getId()),BoneAnchors.FACTORY.copy(a));
            Map<String,mchorse.bbs_mod.cubic.ik.IKControl> controls=new LinkedHashMap<>();
            if(slot.form instanceof ModelForm form)for(var value:form.bones.getAll())if(value instanceof mchorse.bbs_mod.forms.forms.utils.FormBone bone)
                controls.put(bone.getId(),bone.ik.get().copy());
            var renderer=FormUtilsClient.getRenderer(slot.form);
            Vector3f scale=new Vector3f(1);
            if(renderer instanceof ModelFormRenderer model){var instance=model.getModel();if(instance!=null)scale.set(instance.getScale());}
            return new Inputs(renderer.createEvaluatedTransform(transition),
                slot.parent==null?rootHeaders.get(slot):null,anchors,controls,scale);
        }
        private Inputs inputs(BindingScenes.Slot slot) {
            Inputs current=inputs.get(slot);if(current!=null)return current;
            try(StateValues scope=StateValues.of(slot,transition)){current=currentInputs(slot);inputs.put(slot,current);return current;}
        }
        private String constraint(BindingScenes.Slot slot,String bone){return slot.label(bone);}
        private Matrix4f node(BindingScenes.Slot slot,String stage,String bone,Supplier<Matrix4f> action) {
            Node node=new Node(slot,stage,bone);Matrix4f cached=matrices.get(node);if(cached!=null)return new Matrix4f(cached);
            if(visiting.contains(node)) {
                String reason="Anchor dependency cycle: "+visiting.stream().map(n->n.slot.label(n.bone)+"["+n.stage+"]").toList();
                int count=constraints.size()-entryDepth.getOrDefault(node,0),index=0;
                for(String key:constraints)if(index++<count)blocked.add(key);
                generation++;matrices.clear();overrides.clear();throw new Cycle(reason);
            }
            if(visiting.size()>=256)throw new IllegalArgumentException("Anchor dependency limit exceeded");
            visiting.add(node);entryDepth.put(node,constraints.size());
            try {
                for(int attempt=0;attempt<64;attempt++) {
                    int before=generation;Matrix4f result=action.get();
                    if(before!=generation)continue;
                    if(result!=null && result.isFinite())matrices.put(node,new Matrix4f(result));
                    return result==null?null:new Matrix4f(result);
                }
                throw new IllegalArgumentException("Anchor dependency limit exceeded");
            } finally {visiting.remove(node);entryDepth.remove(node);}
        }
        private static final class Cycle extends RuntimeException{private static final long serialVersionUID=1L;Cycle(String reason){super(reason);}}
        public Matrix4f parent(BindingScenes.Slot slot) {
            return node(slot,"parent",null,()->{
                if(slot.parent==null && nativeParents.containsKey(slot))return new Matrix4f(nativeParents.get(slot));
                Matrix4f nativeFrame=nativeBeforePart(slot);
                Matrix4f selected=slot.parent==null?anchor(slot,null,inputs(slot).header,nativeFrame):nativeFrame;
                return slot.part==null?selected:selected.mul(slot.part.transform.get().createMatrix());
            });
        }
        public Matrix4f nativeIncoming(BindingScenes.Slot slot) {
            // The root's native actor placement already includes its original overall Anchor.
            if(nativeParents.containsKey(slot))return new Matrix4f(nativeParents.get(slot));
            if(slot.parent==null)return parent(slot);
            return nativeBeforePart(slot).mul(slot.part.transform.get().createMatrix());
        }
        private Matrix4f nativeBeforePart(BindingScenes.Slot slot) {
            if(slot.parent!=null && nativeParents.containsKey(slot)) {
                Matrix4f partInverse=invertOrNull(slot.part.transform.get().createMatrix());
                if(partInverse!=null)return new Matrix4f(nativeParents.get(slot)).mul(partInverse);
            }
            if(slot.parent==null) {
                var replay=scene.controller.film==null?null:scene.controller.film.replays.getList().stream().filter(r->r.getId().equals(slot.actor)).findFirst().orElse(null);
                if(replay!=null && replay.relative.get()) {
                    var origin=replay.getRelativeOrigin();return FilmMatrices.getMatrixForRenderWithRotation(slot.entity,origin.x,origin.y,origin.z,transition);
                }
                return FilmMatrices.getMatrixForRenderWithRotation(slot.entity,0,0,0,transition);
            }
            return attachmentFrame(slot.parent,slot.part.bone.get(),slot.part.inheritPosition.get(),
                slot.part.inheritRotation.get(),slot.part.inheritScale.get());
        }
        /** BodyPart inheritance is applied before the owner frame, which may be singular. */
        private Matrix4f attachmentFrame(BindingScenes.Slot owner,String bone,boolean position,boolean rotation,boolean scale) {
            if(bone==null || bone.isEmpty() || !declared(owner,bone))return frame(owner);
            Matrix4f local=localBone(owner,bone);
            if(local==null)return frame(owner);
            if(!position || !rotation || !scale) {
                Matrix4f fallback=new Matrix4f().rotateY((float)Math.PI);
                local=mchorse.bbs_mod.utils.joml.Matrices.compose(position?local:fallback,rotation?local:fallback,scale?local:fallback);
            }
            return modelBase(owner).mul(local);
        }
        public Matrix4f frame(BindingScenes.Slot slot) {
            return node(slot,"frame",null,()->{
                Matrix4f base=parent(slot);FormRenderer<?> renderer=FormUtilsClient.getRenderer(slot.form);
                base.mul(inputs(slot).transform.createMatrix());
                if(renderer instanceof ModelFormRenderer)base.scale(inputs(slot).modelScale);
                return base;
            });
        }
        public Matrix4f modelBase(BindingScenes.Slot slot) {
            Matrix4f base=frame(slot);return slot.form instanceof ModelForm?base.rotateY((float)Math.PI):base;
        }
        private Fk fk(BindingScenes.Slot slot) {
            Fk cached=fk.get(slot);if(cached!=null)return cached;
            FormRenderer<?> generic=FormUtilsClient.getRenderer(slot.form);
            try(StateValues values=StateValues.of(slot,transition)) {
            inputs.putIfAbsent(slot,currentInputs(slot));
            if(generic instanceof ModelFormRenderer renderer) {
                renderer.ensureAnimator(transition);ModelInstance instance=renderer.getModel();
                if(instance!=null && instance.model instanceof Model model) {
                    try(RigPose.Scope scope=RigPose.scope(instance)) {
                        ModelInstance posed=renderer.evaluateChannels(slot.entity,transition);
                        if(posed!=null && !customCollector(renderer)){Fk result=new Fk(instance,RigPose.capture(model),null);fk.put(slot,result);return result;}
                        MatrixCache fallback=nativeMatrices(slot);
                        Fk result=new Fk(instance,RigPose.capture(model),fallback);fk.put(slot,result);return result;
                    }
                }
            }
            Fk result=new Fk(null,null,nativeMatrices(slot));fk.put(slot,result);return result;
            }
        }
        private boolean customCollector(ModelFormRenderer renderer) {
            try{return renderer.getClass().getMethod("collectMatrices",mchorse.bbs_mod.forms.entities.IEntity.class,
                net.minecraft.client.util.math.MatrixStack.class,MatrixCache.class,String.class,float.class)
                .getDeclaringClass()!=ModelFormRenderer.class;}
            catch(ReflectiveOperationException error){return true;}
        }
        private MatrixCache nativeMatrices(BindingScenes.Slot slot) {return nativeMatrices(slot,false);}
        private MatrixCache nativeMatrices(BindingScenes.Slot slot,boolean local) {
            List<RigPose.Scope> scopes=new ArrayList<>();saveSubtree(slot,scopes);
            Form previous=LOCAL_FORM.get();if(local)LOCAL_FORM.set(slot.form);
            NATIVE.set(NATIVE.get()+1);
            try{return FormUtilsClient.getRenderer(slot.form).collectMatrices(slot.entity,transition);}
            finally{
                NATIVE.set(NATIVE.get()-1);
                if(previous==null)LOCAL_FORM.remove();else LOCAL_FORM.set(previous);
                for(int i=scopes.size()-1;i>=0;i--)scopes.get(i).close();
            }
        }
        private void saveSubtree(BindingScenes.Slot slot,List<RigPose.Scope> scopes) {
            if(FormUtilsClient.getRenderer(slot.form) instanceof ModelFormRenderer renderer) {
                ModelInstance instance=renderer.getModel();if(instance!=null && instance.model instanceof Model)scopes.add(RigPose.scope(instance));
            }
            for(var child:slot.children)saveSubtree(child,scopes);
        }
        public Matrix4f pre(BindingScenes.Slot slot,String bone) {
            return node(slot,"pre",bone,()->{
                Fk state=fk(slot);
                if(state.instance==null || !(state.instance.model instanceof Model model) || model.getGroup(bone)==null) {
                    var entry=state.fallback==null?null:state.fallback.get(bone).matrix();
                    return entry==null?null:new Matrix4f(parent(slot)).mul(entry);
                }
                ModelGroup group=model.getGroup(bone);RigPose.Bone initial=state.pose.bones.get(bone);
                Matrix4f fallback;
                if(state.fallback!=null && state.fallback.has(bone))fallback=new Matrix4f(parent(slot)).mul(state.fallback.get(bone).matrix());
                else {
                    Matrix4f ancestor=group.parent==null?modelBase(slot):groupSpace(slot,group.parent);
                    fallback=ancestor.mul(local(group,initial,null)).translate(new Vector3f(group.initial.translate).mul(1f/16f)).rotateY((float)Math.PI);
                }
                Anchor binding=inputs(slot).anchors.get(bone);
                if(binding==null || !BoneAnchors.contributes(binding))return fallback;
                /* IK, physics and constraints own their bones; CEM rigs have no readable rest pose here. */
                if(solverOwned(slot,bone))return fallback;
                if(state.instance.cemAnimation!=null)return fallback;
                Matrix4f desired=anchor(slot,bone,binding,fallback);
                String key=constraint(slot,bone);
                if(blocked.contains(key) || inactive.contains(key))return fallback;
                if(desired.equals(fallback,1e-6f))return fallback;
                Matrix4f ancestor=group.parent==null?modelBase(slot):groupSpace(slot,group.parent);
                try {
                    var pose=AnchorMath.decompose(AnchorMath.parentLocal(ancestor,desired));
                    Override override=new Override(AnchorMath.offset(pose.position(),initial.channels().translate,group.initial.translate),pose.rotation(),pose.scale());
                    overrides.computeIfAbsent(slot,k->new LinkedHashMap<>()).put(bone,override);return desired;
                }catch(IllegalArgumentException ignored){return fallback;}
            });
        }
        private Matrix4f groupSpace(BindingScenes.Slot slot,ModelGroup group) {
            return pre(slot,group.id).rotateY((float)Math.PI).translate(new Vector3f(group.initial.translate).mul(-1f/16f));
        }
        private Matrix4f local(ModelGroup group,RigPose.Bone state,Override override) {
            var t=state.channels();Vector3f delta=new Vector3f(-(t.translate.x-group.initial.translate.x)/16f,
                (t.translate.y-group.initial.translate.y)/16f,(t.translate.z-group.initial.translate.z)/16f);
            Matrix4f local=new Matrix4f();Vector3f offset=override==null?state.offset():override.offset;
            if(offset!=null)local.translate(offset);local.translate(delta).translate(new Vector3f(group.initial.translate).mul(1f/16f));
            org.joml.Quaternionf rotation=override==null?state.orient():override.rotation;
            if(rotation==null)rotation=t.rotationMode==mchorse.bbs_mod.utils.pose.Transform.RotationMode.QUATERNION
                ?new org.joml.Quaternionf(t.quat):mchorse.bbs_mod.utils.joml.Matrices.toLocalRotationZYXDegrees(t.rotate);
            local.rotate(rotation).scale(override==null?(state.scale()==null?t.scale:state.scale()):override.scale);
            return local.translate(new Vector3f(group.initial.translate).mul(-1f/16f));
        }
        private boolean solverOwned(BindingScenes.Slot slot,String bone) {
            Fk state=fk(slot);if(state.instance==null || !(slot.form instanceof ModelForm form))return false;
            var source=form.bones.getBone(bone);
            if(source!=null && source.constraints.get().isActive()) return true;
            var chains=mchorse.bbs_mod.cubic.ik.ModelIKRuntime.getChains(state.instance.model,form);
            var controls=inputs(slot).controls;
            for(String id:state.instance.model.getHierarchyGroups(bone)) {
                for(var chain:chains.entrySet()) {
                    var control=form.bones.getBone(chain.getKey());
                    var ik=control==null?null:controls.getOrDefault(chain.getKey(),control.ik.get());
                    if(ik!=null && ik.enabled && ik.weight>0 && chain.getValue().contains(id))return true;
                }
                var control=form.bones.getBone(id);
                if(control!=null && control.hasPhysicsChain() && control.physics.get().enabled && control.physics.get().weight>0)return true;
            }
            return false;
        }
        public Matrix4f bone(BindingScenes.Slot slot,String bone) {
            MatrixCache rendered=live.get(slot);
            if(rendered!=null && rendered.has(bone))return modelBase(slot).mul(rendered.get(bone).matrix());
            return node(slot,"final",bone,()->{
                Fk state=fk(slot);
                if(state.instance==null || !(state.instance.model instanceof Model model) || !(slot.form instanceof ModelForm form))return pre(slot,bone);
                if(model.getGroup(bone)==null)return null;
                var chains=mchorse.bbs_mod.cubic.ik.ModelIKRuntime.getChains(model,form);
                Set<String> needed=new HashSet<>(model.getHierarchyGroups(bone)),selected=new HashSet<>();
                boolean more;
                do{more=false;for(var chain:chains.entrySet()) {
                    var control=form.bones.getBone(chain.getKey());
                    if(control!=null && inputs(slot).controls.getOrDefault(chain.getKey(),control.ik.get()).enabled && inputs(slot).controls.getOrDefault(chain.getKey(),control.ik.get()).weight>0
                        && chain.getValue().stream().anyMatch(needed::contains) && selected.add(chain.getKey())) {
                        needed.addAll(chain.getValue());more=true;
                    }
                }}while(more);
                if(state.fallback!=null)return pre(slot,bone);
                if(selected.isEmpty()) {
                    if(!hasHierarchyConstraint(form,model,bone))return pre(slot,bone);
                    try(RigPose.Scope scope=RigPose.scope(state.instance)) {
                        state.pose.apply(model);state.instance.form=form;
                        ModelConstraintsRuntime.apply(state.instance);
                        MatrixCache captured=new MatrixCache();state.instance.captureMatrices(captured);
                        return capturedBone(slot,bone,captured);
                    }
                }
                for(String tip:selected){var config=form.bones.getBone(tip);needed.addAll(model.getHierarchyGroups(config.ikTarget.get()));}
                for(String id:new ArrayList<>(needed))for(String ancestor:model.getHierarchyGroups(id))pre(slot,ancestor);
                Map<String,Vector3f> goals=goals(slot,selected);
                try(RigPose.Scope scope=RigPose.scope(state.instance)) {
                    state.pose.apply(model);state.instance.form=form;
                    Map<BaseValueBasic<?>,Object> controls=new IdentityHashMap<>();
                    for(var chain:chains.entrySet())if(!selected.contains(chain.getKey())) {
                        var control=form.bones.getBone(chain.getKey());if(control==null)continue;
                        controls.put(control.ik,control.ik.getRuntimeValue());
                        var disabled=control.ik.get().copy();disabled.enabled=false;control.ik.setRuntimeValue(disabled);
                    }
                    Query old=QUERY.get();QUERY.set(new Query(slot,overrides.getOrDefault(slot,Map.of()),goals));
                    try {
                        ((ModelFormRenderer)FormUtilsClient.getRenderer(form)).solveIK(state.instance,parent(slot),transition);
                        ModelConstraintsRuntime.apply(state.instance);
                        MatrixCache captured=new MatrixCache();state.instance.captureMatrices(captured);
                        return capturedBone(slot,bone,captured);
                    } finally {QUERY.set(old);controls.forEach((key,value)->setRuntime(key,value));}
                }
            });
        }
        private Matrix4f capturedBone(BindingScenes.Slot slot,String bone,MatrixCache captured) {
            Matrix4f local=captured.get(bone).matrix();
            if(local==null || !local.isFinite())return null;
            matrices.put(new Node(slot,"captured_local",bone),new Matrix4f(local));
            Matrix4f world=modelBase(slot).mul(local);
            return world.isFinite()?world:null;
        }
        private Matrix4f localBone(BindingScenes.Slot slot,String bone) {
            MatrixCache rendered=live.get(slot);
            if(rendered!=null && rendered.has(bone))return new Matrix4f(rendered.get(bone).matrix());
            return node(slot,"local",bone,()->{
                bone(slot,bone);
                Matrix4f captured=matrices.get(new Node(slot,"captured_local",bone));
                if(captured!=null)return new Matrix4f(captured);
                Fk state=fk(slot);
                if(state.fallback==null && state.instance!=null && state.instance.model instanceof Model model) {
                    // FK and binding overrides are already evaluated by bone(); capture before placement.
                    try(RigPose.Scope scope=RigPose.scope(state.instance)) {
                        state.pose.apply(model);apply(model,overrides.getOrDefault(slot,Map.of()));
                        MatrixCache local=new MatrixCache();state.instance.captureMatrices(local);
                        Matrix4f matrix=local.get(bone).matrix();return matrix==null?null:new Matrix4f(matrix);
                    }
                }
                // Mob, CEM and custom collectors supply native bone matrices without owner transforms.
                try(StateValues values=StateValues.of(slot,transition)) {
                    Matrix4f matrix=nativeMatrices(slot,true).get(bone).matrix();
                    if(matrix==null)return null;
                    return slot.form instanceof ModelForm?new Matrix4f().rotateY(-(float)Math.PI).mul(matrix):new Matrix4f(matrix);
                }
            });
        }
        private boolean hasHierarchyConstraint(ModelForm form,Model model,String bone) {
            for(String id:model.getHierarchyGroups(bone)) {
                var control=form.bones.getBone(id);
                if(control!=null && control.constraints.get().isActive())return true;
            }
            return false;
        }
        private Map<String,Vector3f> goals(BindingScenes.Slot slot,Set<String> selected) {
            Map<String,Vector3f> targets=new LinkedHashMap<>();
            if(slot.parent!=null || !(slot.form instanceof ModelForm form) || scene.controller.film==null)return targets;
            var replay=scene.controller.film.replays.getList().stream().filter(r->r.getId().equals(slot.actor)).findFirst().orElse(null);
            if(replay==null)return targets;
            var state=fk(slot);if(state.instance==null)return targets;
            var chains=mchorse.bbs_mod.cubic.ik.ModelIKRuntime.getChains(state.instance.model,form);
            for(var chain:chains.entrySet()) {
                if(selected!=null && !selected.contains(chain.getKey()))continue;
                var config=form.bones.getBone(chain.getKey());if(config==null || !config.ik.get().enabled)continue;
                String controller=config.ikTarget.get();
                var id=mchorse.bbs_mod.film.replays.tracks.TrackId.ikTarget(String.join("/",slot.path),controller);
                var channel=replay.properties.tracks.get(id);if(channel==null)continue;
                var segment=channel.find(replay.getTick(scene.controller.getTick())+transition);
                if(segment==null || !(segment.createInterpolated() instanceof Anchor anchor))continue;
                Matrix4f fallback=pre(slot,controller);if(fallback==null)continue;
                Vector3f resolved=goal(slot,controller,anchor,fallback);
                if(resolved!=null)targets.put(controller,resolved);
            }
            return targets;
        }
        /** TargetTrack contributes nothing when the endpoint it would bind to has no target. */
        private static Anchor binding(Anchor value) {
            if(value.previous!=null && value.isFadeIn())return value;
            if(value.previous!=null && value.isFadeOut())return value.previous;
            return value;
        }
        private Vector3f goal(BindingScenes.Slot slot,String controller,Anchor value,Matrix4f fallback) {
            if(!binding(value).hasTarget())return null;
            String key=slot.address()+"/ikTarget["+controller+"]";
            if(blocked.contains(key))return fallback.getTranslation(new Vector3f());
            constraints.push(key);
            try {
                Anchor a=AnchorReferences.evaluated(value);if(a==null)return fallback.getTranslation(new Vector3f());
                Vector3f current=goalEndpointOrFallback(slot,a,fallback);
                if(a.previous!=null)current=goalEndpointOrFallback(slot,a.previous,fallback).lerp(current,a.x);
                return blocked.contains(key)?fallback.getTranslation(new Vector3f()):current;
            }catch(Cycle|IllegalArgumentException ignored){return fallback.getTranslation(new Vector3f());}
            finally{constraints.pop();}
        }
        private Vector3f goalEndpointOrFallback(BindingScenes.Slot source,Anchor anchor,Matrix4f fallback) {
            try {
                Matrix4f target=fallback;
                if(anchor.hasTarget()) {
                    Address address=address(anchor);
                    if(address==null)throw new IllegalArgumentException("IK target does not exist: "+anchor.attachment);
                    checkSpace(source,address.slot);
                    target=address.bone==null?frame(address.slot):bone(address.slot,address.bone);
                    if(target==null)throw new IllegalArgumentException("IK target bone does not exist");
                }
                return new Matrix4f(target).mul(anchor.transform.createMatrix()).getTranslation(new Vector3f());
            } catch(IllegalArgumentException ignored) {
                return new Matrix4f(fallback).mul(anchor.transform.createMatrix()).getTranslation(new Vector3f());
            }
        }
        @SuppressWarnings({"rawtypes","unchecked"}) private static void setRuntime(BaseValueBasic key,Object value){key.setRuntimeValue(value);}
        private Matrix4f anchor(BindingScenes.Slot source,String bone,Anchor value,Matrix4f fallback) {
            if(value==null)return new Matrix4f(fallback);
            String key=constraint(source,bone);if(blocked.contains(key))return new Matrix4f(fallback);
            constraints.push(key);inactive.remove(key);
            try {
                value=mchorse.bbs_mod.api.client.events.FormPoseEvents.ANCHOR.invoker().resolve(
                    mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories.ANCHOR.copy(value));
                if(!BoneAnchors.contributes(value))return new Matrix4f(fallback);
                Anchor a=AnchorReferences.evaluated(value);if(a==null)return new Matrix4f(fallback);
                Matrix4f result=endpointOrFallback(source,bone,a,fallback);
                if(a.previous!=null)
                    result=mchorse.bbs_mod.utils.joml.Matrices.lerp(endpointOrFallback(source,bone,a.previous,fallback),result,a.x);
                return blocked.contains(key)?new Matrix4f(fallback):result;
            } catch(Cycle ignored){return new Matrix4f(fallback);}
            catch(IllegalArgumentException ignored){inactive.add(key);return new Matrix4f(fallback);}
            finally{constraints.pop();}
        }
        private Matrix4f endpointOrFallback(BindingScenes.Slot source,String bone,Anchor value,Matrix4f fallback) {
            try { return endpoint(source,bone,value,fallback); }
            catch(IllegalArgumentException ignored) {
                return new Matrix4f(fallback).mul(value.transform.createMatrix());
            }
        }
        private Matrix4f endpoint(BindingScenes.Slot source,String sourceBone,Anchor value,Matrix4f fallback) {
            Matrix4f target;
            if(!value.hasTarget())target=new Matrix4f(fallback);
            else {
                if(BindingAnchor.local(value)) {
                    BindingAnchor binding=(BindingAnchor)value;
                    var owner=scene.slot(value.replay,binding.targetPath);
                    if(owner==null)throw new IllegalArgumentException("Anchor target form does not exist: "+value.attachment);
                    checkSpace(source,owner);
                    return attachmentFrame(owner,binding.targetBone,value.inheritPosition,value.inheritRotation,value.inheritScale)
                        .mul(value.transform.createMatrix());
                }
                Address address=address(value);
                if(address==null) {
                    if(sourceBone==null && source.parent==null) {
                        var actor=scene.root(value.replay);
                        return new Matrix4f(actor==null?fallback:parent(actor)).mul(value.transform.createMatrix());
                    }
                    throw new IllegalArgumentException("Anchor target does not exist: "+value.replay+"/"+value.attachment);
                }
                target=address.bone==null?frame(address.slot):bone(address.slot,address.bone);
                if(target==null)throw new IllegalArgumentException("Anchor target bone does not exist: "+value.attachment);
                checkSpace(source,address.slot);
                target=sourceBone==null && source.parent==null?value.filterMatrix(target,fallback):
                    AnchorMath.filter(target,fallback,value.inheritPosition,value.inheritRotation,value.inheritScale);
            }
            return new Matrix4f(target).mul(value.transform.createMatrix());
        }
        public Matrix4f reference(BindingScenes.Slot slot,String bone,Anchor value) {
            Matrix4f fallback;
            if(bone==null)fallback=nativeBeforePart(slot);
            else {
                Fk state=fk(slot);if(state.instance==null || !(state.instance.model instanceof Model model))throw new IllegalArgumentException("Source rig is unavailable");
                ModelGroup group=model.getGroup(bone);if(group==null)throw new IllegalArgumentException("Source bone does not exist");
                if(solverOwned(slot,bone))throw new IllegalArgumentException("Bone is driven by IK, physics or a constraint; use a separate controller");
                Matrix4f ancestor=group.parent==null?modelBase(slot):groupSpace(slot,group.parent);
                fallback=ancestor.mul(local(group,state.pose.bones.get(bone),null))
                    .translate(new Vector3f(group.initial.translate).mul(1f/16f)).rotateY((float)Math.PI);
            }
            var clean=BindingAnchor.of(value);clean.transform.identity();
            return endpoint(slot,bone,clean,fallback);
        }
        public Matrix4f rootParent(BindingScenes.Slot slot,boolean full) {
            if(!full)return parent(slot);
            return node(slot,"parent_full",null,()->{
                Anchor copy=mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories.ANCHOR.copy(inputs(slot).header);
                copy.inheritPosition=copy.inheritRotation=copy.inheritScale=true;
                if(copy.previous!=null)copy.previous.inheritPosition=copy.previous.inheritRotation=copy.previous.inheritScale=true;
                return anchor(slot,null,copy,nativeBeforePart(slot));
            });
        }
        public boolean blockedHeader(BindingScenes.Slot slot){return blocked.contains(constraint(slot,null));}
        public void seedNativeParent(BindingScenes.Slot slot,Matrix4f matrix) {
            nativeParents.put(slot,new Matrix4f(matrix));matrices.clear();
        }
        private final Map<BindingScenes.Slot,Matrix4f> nativeParents=new IdentityHashMap<>();
        private final Map<BindingScenes.Slot,MatrixCache> live=new IdentityHashMap<>();
        public void ready(BindingScenes.Slot slot) {
            if(FormUtilsClient.getRenderer(slot.form) instanceof ModelFormRenderer renderer) {
                ModelInstance instance=renderer.getModel();
                if(instance!=null && instance.model instanceof Model) {
                    MatrixCache captured=new MatrixCache();instance.captureMatrices(captured);live.put(slot,captured);matrices.clear();
                }
            }
        }
        private void checkSpace(BindingScenes.Slot source,BindingScenes.Slot target) {
            if(scene.controller.film==null)return;
            var replays=scene.controller.film.replays.getList();
            var a=replays.stream().filter(r->r.getId().equals(source.actor)).findFirst().orElse(null);
            var b=replays.stream().filter(r->r.getId().equals(target.actor)).findFirst().orElse(null);
            boolean ar=a!=null&&a.relative.get(),br=b!=null&&b.relative.get();
            if(ar!=br || ar && !a.getRelativeOrigin().equals(b.getRelativeOrigin()))
                throw new IllegalArgumentException("Anchors cannot cross world and relative coordinate modes; align the actor coordinate modes");
        }
        public record Address(BindingScenes.Slot slot,String bone){}
        public Address address(Anchor value) {
            if(value instanceof BindingAnchor structured && structured.structured()) {
                var slot=scene.slot(value.replay,structured.targetPath);
                if(slot==null)return null;
                return structured.targetBone==null || declared(slot,structured.targetBone)?new Address(slot,structured.targetBone):null;
            }
            var root=scene.root(value.replay);if(root==null)return null;
            if(value.attachment.isEmpty())return new Address(root,null);
            var candidates=new ArrayList<>(scene.slots.values());candidates.sort(Comparator.comparingInt((BindingScenes.Slot slot)->slot.path.size()).reversed());
            for(var slot:candidates)if(slot.actor.equals(value.replay)) {
                String prefix=String.join("/",slot.path);
                if(!prefix.isEmpty() && value.attachment.equals(prefix))return new Address(slot,null);
                if(!prefix.isEmpty() && !value.attachment.startsWith(prefix+"/"))continue;
                String bone=prefix.isEmpty()?value.attachment:value.attachment.substring(prefix.length()+1);
                if(declared(slot,bone))return new Address(slot,bone);
            }
            return null;
        }
        private boolean declared(BindingScenes.Slot slot,String bone) {
            try(StateValues values=StateValues.of(slot,transition)) {
                var renderer=FormUtilsClient.getRenderer(slot.form);var hierarchy=renderer.getBoneHierarchy();
                return hierarchy==null?renderer.getBones().contains(bone):hierarchy.getGroupKeysInHierarchyOrder().contains(bone);
            }
        }
        private void collect(BindingScenes.Slot root,Matrix4f incoming,MatrixCache out,String prefix) {
            Matrix4f inverse=invertOrNull(nativeIncoming(root));
            if(inverse==null)inverse=new Matrix4f();
            Matrix4f base=incoming!=null && incoming.isFinite()?new Matrix4f(incoming):new Matrix4f();
            collectSubtree(root,base.mul(inverse),out,prefix);
        }
        private void collectSubtree(BindingScenes.Slot slot,Matrix4f toLocal,MatrixCache out,String prefix) {
            Matrix4f parent=parent(slot);
            mchorse.bbs_mod.api.client.events.FormPoseEvents.PARENT_FRAME.invoker().capture(
                slot.form,slot.entity,new Matrix4f(parent),prefix,transition);
            Matrix4f matrix=new Matrix4f(toLocal).mul(frame(slot));
            if(matrix.isFinite())out.put(prefix,matrix,new Matrix4f(matrix));
            FormRenderer<?> renderer=FormUtilsClient.getRenderer(slot.form);var hierarchy=renderer.getBoneHierarchy();
            Collection<String> bones=hierarchy==null?new ArrayList<>(renderer.getBones()):hierarchy.getGroupKeysInHierarchyOrder();
            for(String id:bones){Matrix4f world=bone(slot,id);if(world!=null&&world.isFinite()){Matrix4f local=new Matrix4f(toLocal).mul(world);if(local.isFinite())out.put(StringUtils.combinePaths(prefix,id),local,new Matrix4f(local));}}
            for(var child:slot.children)collectSubtree(child,toLocal,out,StringUtils.combinePaths(prefix,child.part.getId()));
        }
    }
    private BindingResolver(){}
}
