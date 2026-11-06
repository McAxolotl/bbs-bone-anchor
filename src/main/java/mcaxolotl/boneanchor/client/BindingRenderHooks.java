package mcaxolotl.boneanchor.client;

import java.util.*;
import mcaxolotl.boneanchor.*;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import org.joml.Matrix4f;

/** Borrowed render contexts remain on the synchronous BEFORE/AFTER stack only. */
public final class BindingRenderHooks {
    private static final ThreadLocal<Deque<Scope>> STACK=ThreadLocal.withInitial(ArrayDeque::new);
    private static final class Scope {
        final Form form; final FormRenderingContext context; final BindingScenes.Slot slot;
        final BindingResolver.Request request; boolean prepared;
        Scope(Form form,FormRenderingContext context,BindingScenes.Slot slot,BindingResolver.Request request) {
            this.form=form;this.context=context;this.slot=slot;this.request=request;
        }
    }
    static boolean statesApplied(Form form) {
        for(Scope scope:STACK.get())if(scope.form==form && scope.prepared)return true;
        return false;
    }
    public static void before(Form form,FormRenderingContext context) {
        BindingScenes.Slot slot=BindingScenes.find(form);BindingResolver.Request request=null;
        if(slot!=null && slot.scene.enabled()) {
            request=BindingResolver.request(slot.scene,context.getTransition());
            Scope parent=STACK.get().peek();if(parent!=null && parent.slot!=null)request.session.ready(parent.slot);
        }
        STACK.get().push(new Scope(form,context,slot,request));
    }
    public static void prepareIfDrawing(Form form,net.minecraft.client.util.math.MatrixStack stack,boolean origin) {
        Scope scope=STACK.get().peek();
        if(scope!=null && scope.form==form && scope.context.stack==stack && !origin)prepare(form,scope.context);
    }
    private static void prepare(Form form,FormRenderingContext context) {
        Scope scope=STACK.get().peek();
        if(scope==null || scope.form!=form || scope.context!=context || scope.prepared || scope.request==null)return;
        scope.prepared=true;
        BoneAnchorRuntime.sample(form,context.getTransition());
        if(context.world==null)return;
        Matrix4f incoming=new Matrix4f(context.world.peek().getPositionMatrix());
        scope.request.session.seedNativeParent(scope.slot,incoming);
    }
    public static void after(Form form,FormRenderingContext context) {
        Scope scope=STACK.get().poll();if(scope!=null && scope.request!=null)scope.request.close();
        if(STACK.get().isEmpty())STACK.remove();
    }
    private BindingRenderHooks(){}
}
