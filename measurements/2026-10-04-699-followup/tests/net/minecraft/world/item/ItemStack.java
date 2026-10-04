package net.minecraft.world.item;

/** Minimal fixture API, not a Minecraft implementation or native runtime test. */
public final class ItemStack {
    public final String item;
    public final String tag;
    public final int count;
    public final String capabilities;

    public ItemStack(String item, String tag, int count, String capabilities) {
        this.item = item;
        this.tag = tag;
        this.count = count;
        this.capabilities = capabilities;
    }

    public boolean isEmpty() {
        return count <= 0 || item.equals("air");
    }
}
