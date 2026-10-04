package net.minecraft.world.item;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenCustomHashSet;
import java.util.Objects;
import java.util.Set;

/** Fixture models the native item/tag hash and separate capability equality check. */
public final class ItemStackLinkedSet {
    public static final Hash.Strategy<ItemStack> TYPE_AND_TAG = new Hash.Strategy<>() {
        public int hashCode(ItemStack stack) {
            return stack == null ? 0 : 31 * (31 + (stack.isEmpty() ? "air" : stack.item).hashCode()) + Objects.hashCode(stack.tag);
        }
        public boolean equals(ItemStack a, ItemStack b) {
            return a == b || a != null && b != null && a.isEmpty() == b.isEmpty()
                    && (a.isEmpty() || a.item.equals(b.item)
                    && Objects.equals(a.tag, b.tag) && Objects.equals(a.capabilities, b.capabilities));
        }
    };

    public static Set<ItemStack> createTypeAndTagSet() {
        return new ObjectLinkedOpenCustomHashSet<>(TYPE_AND_TAG);
    }
}
