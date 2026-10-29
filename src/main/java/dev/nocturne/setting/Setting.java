package dev.nocturne.setting;

import java.util.function.BooleanSupplier;

public abstract class Setting<T> {
    public final String name;
    protected T value;
    private BooleanSupplier visible = () -> true;

    protected Setting(String name, T value) { this.name = name; this.value = value; }

    public T get() { return value; }
    public void set(T v) { value = v; }
    public void showIf(BooleanSupplier s) { visible = s; }
    public boolean visible() { return visible.getAsBoolean(); }

    public abstract String display();
    public abstract Object save();
    public abstract void load(Object o);
}
