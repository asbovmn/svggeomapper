package de.asbbremen.svggeomapper;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class ListenerManager<T extends ListenerManager.Listener> {
    public static interface Listener {}

    private Set<T> listeners = new HashSet<>();

    public void addListener(T listener) {
        listeners.add(listener);
    }

    public void removeListener(T listener) {
        listeners.remove(listener);
    }

    public void notify(Consumer<? super T> action) {
        listeners.stream().forEach(action);
    }
}
