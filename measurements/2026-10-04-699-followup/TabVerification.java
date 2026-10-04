package org.embeddedt.modernfix.benchmark;

import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import org.embeddedt.modernfix.searchtree.JEIRuntimeCapturer;

/** Fixture only: replay original membership once, after the ordinary measured search. */
public final class TabVerification {
    private static Collection<ItemStack> input;
    private static List<CreativeModeTab> tabs;
    private static Reference2IntOpenHashMap<CreativeModeTab> actual;
    private static int invocations;
    private static int indexedInvocations;

    public static void record(Collection<ItemStack> all, List<CreativeModeTab> allTabs,
                              Reference2IntOpenHashMap<CreativeModeTab> counts) {
        input = all;
        tabs = allTabs;
        actual = counts;
        invocations++;
    }

    public static void indexed() {
        indexedInvocations++;
    }

    public static void verify() {
        if (input == null || tabs == null || actual == null) {
            throw new IllegalStateException("ordinary search did not invoke the tab index helper");
        }
        if (indexedInvocations == 0) {
            throw new IllegalStateException("ordinary search never entered the indexed counting path");
        }
        Reference2IntOpenHashMap<CreativeModeTab> expected = new Reference2IntOpenHashMap<>();
        // This correctness check is deliberately outside both operation and typed-input timers.
        for (ItemStack stack : input) {
            for (int i = 0; i < tabs.size(); i++) {
                CreativeModeTab tab = tabs.get(i);
                if (tab.getSearchTabDisplayItems().contains(stack)) expected.addTo(tab, 1);
            }
        }
        if (!actual.equals(expected)) throw new IllegalStateException("real per-tab counts differ from original loop");
        Set<CreativeModeTab> represented = new HashSet<>();
        represented.add(CreativeModeTabs.searchTab());
        for (CreativeModeTab tab : tabs) {
            int size = tab.getSearchTabDisplayItems().size();
            if (expected.getInt(tab) >= size / 4) represented.add(tab);
            System.out.printf("MF_NATIVE kind=tab_verify_row tab=%s expected_count=%d actual_count=%d search_items=%d%n",
                    BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab), expected.getInt(tab), actual.getInt(tab), size);
        }
        if (!represented.equals(JEIRuntimeCapturer.getRepresentedTabs())) {
            throw new IllegalStateException("real represented tab identities differ from original loop");
        }
        System.out.printf("MF_NATIVE kind=tab_verification result=passed helper_invocations=%d indexed_invocations=%d tab_entries=%d ingredients=%d represented_tabs=%d%n",
                invocations, indexedInvocations, tabs.size(), input.size(), represented.size());
        System.out.println("MF_NATIVE kind=represented_tab_ids values=" + represented.stream()
                .map(t -> String.valueOf(BuiltInRegistries.CREATIVE_MODE_TAB.getKey(t))).sorted().toList());
    }
}
