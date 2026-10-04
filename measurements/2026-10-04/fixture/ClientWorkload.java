package org.embeddedt.modernfix.benchmark;

import java.lang.management.ManagementFactory;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

/** Exercises normal client paths in the real loaded pack; never repeats bake/search internals manually. */
public final class ClientWorkload {
    private static FrameSample frames;
    private static boolean registered;
    private ClientWorkload() {}
    public static BooleanSupplier start(String action) {
        String[] words=action.split(" ");Minecraft mc=Minecraft.getInstance();
        switch(words[0]) {
            case "heap": {
                String label=words.length>1?words[1]:"heap";
                long count=gcCount();System.gc();long start=System.nanoTime();
                return () -> {
                    if(System.nanoTime()-start<2_000_000_000L)return false;
                    System.out.printf(Locale.ROOT,"MF_NATIVE kind=post_gc_heap label=%s used_bytes=%d gc_before=%d gc_after=%d%n",label,ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed(),count,gcCount());
                    return true;
                };
            }
            case "search": {
                long[] timing=BenchMetrics.begin();
                CreativeModeTab chosen=CreativeModeTabs.allTabs().stream().filter(t->t.getType()!=CreativeModeTab.Type.SEARCH&&t.hasSearchBar()).findFirst().orElse(null);
                if(chosen==null) {System.out.println("MF_NATIVE kind=search_unexercised reason=no_installed_tab_with_search_bar");return ()->true;}
                try {
                    CreativeModeInventoryScreen screen=new CreativeModeInventoryScreen(mc.player,mc.level.enabledFeatures(),false);mc.setScreen(screen);
                    Method select=Arrays.stream(CreativeModeInventoryScreen.class.getDeclaredMethods()).filter(m->m.getReturnType()==void.class&&Arrays.equals(m.getParameterTypes(),new Class<?>[]{CreativeModeTab.class})).findFirst().orElseThrow();
                    select.setAccessible(true);select.invoke(screen,chosen);
                    Field search=Arrays.stream(CreativeModeInventoryScreen.class.getDeclaredFields()).filter(f->f.getType()==EditBox.class).findFirst().orElseThrow();search.setAccessible(true);
                    ((EditBox)search.get(screen)).setValue("stone");
                    BenchMetrics.end("creative_search_ui",String.valueOf(BuiltInRegistries.CREATIVE_MODE_TAB.getKey(chosen)),timing);
                    return ()->true;
                }catch(ReflectiveOperationException e) {throw new IllegalStateException(e);}
            }
            case "camera":mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);return ()->true;
            case "frames": {
                mc.setScreen(null);
                if(!registered) {MinecraftForge.EVENT_BUS.addListener(ClientWorkload::render);registered=true;}
                frames=new FrameSample(Integer.parseInt(words[1]));return ()->frames.finished;
            }
            case "cubes":System.out.printf("MF_NATIVE kind=cube_totals hits=%d misses=%d%n",BenchMetrics.CUBE_HITS.sum(),BenchMetrics.CUBE_MISSES.sum());return ()->true;
            default:throw new IllegalArgumentException(action);
        }
    }
    private static long gcCount() {return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b->Math.max(0,b.getCollectionCount())).sum();}
    private static void render(TickEvent.RenderTickEvent e) {
        FrameSample sample=frames;if(sample==null||sample.finished)return;
        if(e.phase==TickEvent.Phase.START) {sample.wall=System.nanoTime();sample.cpu=BenchMetrics.cpu();sample.bytes=BenchMetrics.allocated();return;}
        long wall=System.nanoTime()-sample.wall,cpu=BenchMetrics.cpu()-sample.cpu,bytes=BenchMetrics.allocated()-sample.bytes;
        sample.times.add(wall);sample.wallSum+=wall;sample.cpuSum+=cpu;sample.byteSum+=bytes;
        if(System.nanoTime()>=sample.until) {
            sample.finished=true;sample.times.sort(null);int n=sample.times.size();
            System.out.printf(Locale.ROOT,"MF_NATIVE kind=actual_frames frames=%d wall_total_ns=%d render_cpu_total_ns=%d allocated_bytes=%d wall_p50_ns=%d wall_p95_ns=%d cube_hits=%d cube_misses=%d%n",n,sample.wallSum,sample.cpuSum,sample.byteSum,sample.times.get(n/2),sample.times.get(Math.min(n-1,(int)(n*.95))),BenchMetrics.CUBE_HITS.sum()-sample.hits,BenchMetrics.CUBE_MISSES.sum()-sample.misses);
        }
    }
    private static final class FrameSample {
        final long until,hits=BenchMetrics.CUBE_HITS.sum(),misses=BenchMetrics.CUBE_MISSES.sum();
        final List<Long> times=new ArrayList<>();long wall,cpu,bytes,wallSum,cpuSum,byteSum;boolean finished;
        FrameSample(int seconds) {until=System.nanoTime()+seconds*1_000_000_000L;}
    }
}
