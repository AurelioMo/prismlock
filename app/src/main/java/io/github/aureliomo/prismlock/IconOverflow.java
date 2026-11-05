package io.github.aureliomo.prismlock;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Allows custom icon glows outside the 288-unit artboard without resizing sensor/touch bounds. */
final class IconOverflow {
    private static final Map<View, State> VIEWS = new WeakHashMap<>();
    private static final Map<ViewGroup, ParentState> PARENTS = new WeakHashMap<>();
    private IconOverflow() {}

    static void allow(Object target) {
        if (!(target instanceof View)) return;
        View view = (View) target;
        if (VIEWS.containsKey(view)) return;
        try {
            Method get = target.getClass().getMethod("getClipToCompositionBounds");
            Method set = target.getClass().getMethod("setClipToCompositionBounds", boolean.class);
            State state = new State((Boolean) get.invoke(target), view.getClipToOutline(), view.getClipBounds());
            set.invoke(target, false);
            view.setClipToOutline(false);
            view.setClipBounds(null);
            for (ViewParent parent = view.getParent(); parent instanceof ViewGroup; parent = parent.getParent()) {
                ViewGroup group = (ViewGroup) parent;
                ParentState saved = PARENTS.get(group);
                if (saved == null) {
                    saved = new ParentState(group.getClipChildren(), group.getClipToPadding());
                    PARENTS.put(group, saved);
                }
                saved.users++;
                group.setClipChildren(false);
                group.setClipToPadding(false);
                state.parents.add(new WeakReference<>(group));
            }
            VIEWS.put(view, state);
        } catch (ReflectiveOperationException ignored) {
            // Older Lottie versions keep their original behavior if this optional API is absent.
        }
    }

    static void restore(Object target) {
        if (!(target instanceof View)) return;
        View view = (View) target;
        State state = VIEWS.remove(view);
        if (state == null) return;
        try { target.getClass().getMethod("setClipToCompositionBounds", boolean.class).invoke(target, state.composition); }
        catch (ReflectiveOperationException ignored) { }
        view.setClipToOutline(state.outline);
        view.setClipBounds(state.bounds);
        for (WeakReference<ViewGroup> parent : state.parents) {
            ViewGroup group = parent.get();
            ParentState saved = PARENTS.get(group);
            if (saved == null) continue;
            if (--saved.users == 0) {
                group.setClipChildren(saved.children);
                group.setClipToPadding(saved.padding);
                PARENTS.remove(group);
            }
        }
    }

    private static final class State {
        final boolean composition, outline;
        final Rect bounds;
        final List<WeakReference<ViewGroup>> parents = new ArrayList<>();
        State(boolean composition, boolean outline, Rect bounds) {
            this.composition = composition; this.outline = outline; this.bounds = bounds;
        }
    }
    private static final class ParentState {
        final boolean children, padding;
        int users;
        ParentState(boolean children, boolean padding) { this.children = children; this.padding = padding; }
    }
}
