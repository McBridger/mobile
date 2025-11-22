package expo.modules.connector;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class Observable<T> {
    private final List<WeakReference<T>> listeners = new CopyOnWriteArrayList<>();

    public void add(T listener) {
        if (listener == null) return;
        
        // Check for duplicates to avoid adding the same listener twice
        for (WeakReference<T> ref : listeners) {
            if (listener.equals(ref.get())) return;
        }
        
        listeners.add(new WeakReference<>(listener));
    }

    public void remove(T listener) {
        if (listener == null) return;
        // Remove the specific listener or dead references
        listeners.removeIf(ref -> {
            T referent = ref.get();
            return referent == null || referent.equals(listener);
        });
    }

    public void notify(Consumer<T> action) {
        for (WeakReference<T> ref : listeners) {
            T listener = ref.get();
            
            if (listener == null) listeners.remove(ref); // Clean up dead reference    
            else action.accept(listener); // Execute the action on the listener
        }
    }
}
