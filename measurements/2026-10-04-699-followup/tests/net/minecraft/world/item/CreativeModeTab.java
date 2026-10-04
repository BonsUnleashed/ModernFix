package net.minecraft.world.item;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Minimal native getter surface with read tracing to check fallback ordering. */
public class CreativeModeTab {
    public static final List<String> TRACE = new ArrayList<>();
    public final String name;
    public Collection<ItemStack> contents;
    public int reads;

    public CreativeModeTab(String name, Collection<ItemStack> contents) {
        this.name = name;
        this.contents = contents;
    }
    public Collection<ItemStack> getDisplayItems() {
        TRACE.add("display:" + name);
        return contents;
    }
    public Collection<ItemStack> getSearchTabDisplayItems() {
        reads++;
        TRACE.add("search:" + name);
        return contents;
    }
}
