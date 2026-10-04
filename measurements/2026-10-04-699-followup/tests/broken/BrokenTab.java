package broken;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStackLinkedSet;

public class BrokenTab extends CreativeModeTab {
    public BrokenTab() {
        super("broken", ItemStackLinkedSet.createTypeAndTagSet());
    }
    public MissingCollection unrelatedCollection() {
        return null;
    }
}
