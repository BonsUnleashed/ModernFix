package org.embeddedt.modernfix.searchtree;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenCustomHashSet;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;

public final class RepresentedTabsCheck {
    private static int assertions;
    private static int cases;
    private static final ItemStack STONE = stack("stone", "", 1, "");
    private static final ItemStack DIRT = stack("dirt", "", 1, "");

    private static ItemStack stack(String item, String tag, int count, String caps) {
        return new ItemStack(item, tag, count, caps);
    }
    private static Set<ItemStack> set(ItemStack... items) {
        Set<ItemStack> result = ItemStackLinkedSet.createTypeAndTagSet();
        Collections.addAll(result, items);
        return result;
    }
    private static void require(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
    private static Reference2IntOpenHashMap<CreativeModeTab> original(Collection<ItemStack> all, List<CreativeModeTab> tabs) {
        Reference2IntOpenHashMap<CreativeModeTab> counts = new Reference2IntOpenHashMap<>();
        originalInto(all, tabs, counts);
        return counts;
    }
    private static void originalInto(Collection<ItemStack> all, List<CreativeModeTab> tabs, Reference2IntOpenHashMap<CreativeModeTab> counts) {
        for (ItemStack stack : all) {
            for (int i = 0; i < tabs.size(); i++) {
                CreativeModeTab tab = tabs.get(i);
                if (tab.getSearchTabDisplayItems().contains(stack)) counts.addTo(tab, 1);
            }
        }
    }
    private static void indexed(String name, Collection<ItemStack> all, List<CreativeModeTab> tabs) {
        Reference2IntOpenHashMap<CreativeModeTab> expected = original(all, tabs);
        Reference2IntOpenHashMap<CreativeModeTab> actual = new Reference2IntOpenHashMap<>();
        Collection<ItemStack> remainder = RepresentedTabsIndex.count(all, tabs, actual);
        require(remainder != all && remainder.isEmpty(), name + " enters indexed path");
        require(actual.equals(expected), name + " counts equal original loop");
        cases++;
    }
    private static void fallback(String name, Supplier<List<CreativeModeTab>> factory) {
        List<ItemStack> all = Arrays.asList(STONE, DIRT, STONE);
        List<CreativeModeTab> candidate = factory.get();
        Reference2IntOpenHashMap<CreativeModeTab> counts = new Reference2IntOpenHashMap<>();
        CreativeModeTab.TRACE.clear();
        Collection<ItemStack> remainder = RepresentedTabsIndex.count(all, candidate, counts);
        require(remainder == all, name + " retains original input");
        require(counts.isEmpty(), name + " does not change counts before fallback");
        require(CreativeModeTab.TRACE.isEmpty(), name + " invokes no getter before fallback");
        originalInto(remainder, candidate, counts);
        List<String> actualTrace = List.copyOf(CreativeModeTab.TRACE);
        List<CreativeModeTab> baseline = factory.get();
        CreativeModeTab.TRACE.clear();
        Reference2IntOpenHashMap<CreativeModeTab> expected = original(all, baseline);
        require(actualTrace.equals(CreativeModeTab.TRACE), name + " getter call order preserved");
        for (int i = 0; i < candidate.size(); i++) {
            require(counts.getInt(candidate.get(i)) == expected.getInt(baseline.get(i)), name + " counts at tab " + i);
        }
        cases++;
    }
    private static class DecoratedTab extends CreativeModeTab {
        DecoratedTab(String name, Collection<ItemStack> contents) { super(name, contents); }
        public int getLabelColor() { return 0xffffff; }
    }
    private static final class InheritedDecoratedTab extends DecoratedTab {
        InheritedDecoratedTab(String name, Collection<ItemStack> contents) { super(name, contents); }
    }
    private static class SearchOverride extends CreativeModeTab {
        SearchOverride() { super("search-override", set(STONE)); }
        @Override public Collection<ItemStack> getSearchTabDisplayItems() {
            TRACE.add("custom-search:" + name);
            return super.getSearchTabDisplayItems();
        }
    }
    private static final class InheritedSearchOverride extends SearchOverride {}
    private static final class DisplayOverride extends CreativeModeTab {
        DisplayOverride() { super("display-override", set(STONE)); }
        @Override public Collection<ItemStack> getDisplayItems() { return super.getDisplayItems(); }
    }
    private static final class CovariantOverride extends CreativeModeTab {
        CovariantOverride() { super("covariant", set(STONE)); }
        @Override public Set<ItemStack> getSearchTabDisplayItems() {
            return (Set<ItemStack>) super.getSearchTabDisplayItems();
        }
    }
    private static final class MutatingOverride extends CreativeModeTab {
        MutatingOverride() { super("mutating", set(STONE)); }
        @Override public Collection<ItemStack> getSearchTabDisplayItems() {
            if (reads % 2 == 0) contents.add(DIRT); else contents.remove(DIRT);
            return super.getSearchTabDisplayItems();
        }
    }
    private static final class UnrelatedCollection extends CreativeModeTab {
        UnrelatedCollection() { super("unrelated", set(STONE)); }
        private List<String> unrelated(int ignored) { return List.of(); }
    }
    private static final class ThrowingOverride extends CreativeModeTab {
        ThrowingOverride() { super("throws", set()); }
        @Override public Collection<ItemStack> getSearchTabDisplayItems() {
            throw new IllegalStateException("original failure");
        }
    }
    private static final class SetSubclass extends ObjectLinkedOpenCustomHashSet<ItemStack> {
        SetSubclass() { super(ItemStackLinkedSet.TYPE_AND_TAG); add(STONE); }
    }
    private static void collectionFallback(String name, Collection<ItemStack> contents) {
        CreativeModeTab tab = new DecoratedTab(name, contents);
        List<ItemStack> all = List.of(STONE, DIRT);
        Reference2IntOpenHashMap<CreativeModeTab> actual = new Reference2IntOpenHashMap<>();
        Collection<ItemStack> result = RepresentedTabsIndex.count(all, List.of(tab), actual);
        require(result == all && actual.isEmpty(), name + " collection guard retains original loop");
        originalInto(result, List.of(tab), actual);
        require(actual.equals(original(all, List.of(tab))), name + " original membership");
        cases++;
    }
    private static void missingReturnType(Path classes) throws Exception {
        ClassLoader loader = new ClassLoader(RepresentedTabsCheck.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals("broken.MissingCollection")) throw new ClassNotFoundException(name);
                if (!name.equals("broken.BrokenTab")) return super.loadClass(name, resolve);
                Class<?> value = findLoadedClass(name);
                if (value == null) {
                    try {
                        byte[] bytes = Files.readAllBytes(classes.resolve("broken/BrokenTab.class"));
                        value = defineClass(name, bytes, 0, bytes.length);
                    } catch (IOException e) { throw new ClassNotFoundException(name, e); }
                }
                if (resolve) resolveClass(value);
                return value;
            }
        };
        CreativeModeTab tab = (CreativeModeTab) loader.loadClass("broken.BrokenTab").getConstructor().newInstance();
        boolean linkageFailed = false;
        try { tab.getClass().getDeclaredMethods(); } catch (NoClassDefFoundError expected) { linkageFailed = true; }
        require(linkageFailed, "fixture really has an unavailable method return type");
        List<ItemStack> all = List.of(STONE);
        CreativeModeTab.TRACE.clear();
        require(RepresentedTabsIndex.count(all, List.of(tab), new Reference2IntOpenHashMap<>()) == all, "linkage failure falls back");
        require(CreativeModeTab.TRACE.isEmpty(), "linkage fallback invokes no getters");
        cases++;
    }
    public static void main(String[] args) throws Exception {
        indexed("stock", List.of(STONE, DIRT), List.of(new CreativeModeTab("stock", set(STONE))));
        indexed("decorated", List.of(STONE, DIRT), List.of(new DecoratedTab("decorated", set(STONE))));
        indexed("inherited decorated", List.of(STONE, DIRT), List.of(new InheritedDecoratedTab("inherited", set(STONE))));
        fallback("search override", () -> List.of(new CreativeModeTab("first", set(STONE)), new SearchOverride()));
        fallback("display override", () -> List.of(new DisplayOverride()));
        fallback("inherited override", () -> List.of(new InheritedSearchOverride()));
        fallback("covariant override", () -> List.of(new CovariantOverride()));
        require(Arrays.stream(CovariantOverride.class.getDeclaredMethods()).anyMatch(Method::isBridge), "covariant fixture includes compiler bridge");
        fallback("same collection mutation", () -> List.of(new MutatingOverride()));
        fallback("unrelated private collection method", () -> List.of(new UnrelatedCollection()));
        List<ItemStack> one = List.of(STONE);
        CreativeModeTab throwing = new ThrowingOverride();
        require(RepresentedTabsIndex.count(one, List.of(throwing), new Reference2IntOpenHashMap<>()) == one, "throwing getter not eagerly invoked");
        try { original(one, List.of(throwing)); throw new AssertionError("expected original error"); }
        catch (IllegalStateException expected) { require(expected.getMessage().equals("original failure"), "original getter error remains"); }
        cases++;

        Hash.Strategy<ItemStack> equivalent = new Hash.Strategy<>() {
            public int hashCode(ItemStack value) { return ItemStackLinkedSet.TYPE_AND_TAG.hashCode(value); }
            public boolean equals(ItemStack a, ItemStack b) { return ItemStackLinkedSet.TYPE_AND_TAG.equals(a,b); }
        };
        ObjectLinkedOpenCustomHashSet<ItemStack> otherStrategy = new ObjectLinkedOpenCustomHashSet<>(equivalent);
        otherStrategy.add(STONE);
        collectionFallback("different strategy identity", otherStrategy);
        collectionFallback("set subclass", new SetSubclass());
        collectionFallback("list", new ArrayList<>(List.of(STONE)));
        collectionFallback("hash set", new HashSet<>(List.of(STONE)));

        CreativeModeTab duplicate = new DecoratedTab("duplicate", set(STONE));
        indexed("duplicate tab identity", List.of(STONE, DIRT, STONE), List.of(duplicate, duplicate));
        ItemStack collisionA = stack("stone", "Aa", 1, "a");
        ItemStack collisionB = stack("stone", "BB", 64, "a");
        ItemStack incompatible = stack("stone", "Aa", 1, "b");
        require(ItemStackLinkedSet.TYPE_AND_TAG.hashCode(collisionA) == ItemStackLinkedSet.TYPE_AND_TAG.hashCode(collisionB), "real hash collision fixture");
        indexed("collision and capabilities", List.of(collisionA, collisionB, incompatible, stack("stone", "Aa", 64, "a")),
                List.of(new DecoratedTab("collision", set(collisionA)), new CreativeModeTab("other", set(collisionB))));
        ItemStack emptyA = stack("stone", "a", 0, "a");
        ItemStack emptyB = stack("stone", "b", 0, "b");
        indexed("null and differently hashed empty", Arrays.asList(null, emptyA, emptyB, STONE),
                List.of(new DecoratedTab("null", set((ItemStack) null)), new DecoratedTab("empty", set(emptyA)), new CreativeModeTab("ordinary", set(STONE))));

        CreativeModeTab reusable = new InheritedDecoratedTab("reused", set(STONE));
        indexed("before contents change", List.of(STONE, DIRT), List.of(reusable));
        reusable.contents.add(DIRT);
        indexed("same set after contents change", List.of(STONE, DIRT), List.of(reusable));
        reusable.contents = set(DIRT);
        indexed("replaced set", List.of(STONE, DIRT), List.of(reusable));
        missingReturnType(Path.of(args[0]));

        Random random = new Random(699);
        List<ItemStack> pool = new ArrayList<>();
        pool.add(null);
        for (int i=0; i<180; i++) pool.add(stack("item"+random.nextInt(12), "tag"+random.nextInt(5), random.nextInt(4), "cap"+random.nextInt(3)));
        for (int sample=0; sample<300; sample++) {
            List<CreativeModeTab> tabs = new ArrayList<>();
            for (int t=0; t<1+random.nextInt(18); t++) {
                Set<ItemStack> contents = set();
                for (int i=0; i<random.nextInt(35); i++) contents.add(pool.get(random.nextInt(pool.size())));
                tabs.add(t%2==0 ? new DecoratedTab("t"+t, contents) : new CreativeModeTab("t"+t, contents));
            }
            if (sample%3==0) tabs.add(tabs.get(0));
            List<ItemStack> all = new ArrayList<>();
            for (int i=0; i<60; i++) all.add(pool.get(random.nextInt(pool.size())));
            indexed("random differential " + sample, all, tabs);
        }
        System.out.println("PASS cases=" + cases + " assertions=" + assertions);
    }
}
