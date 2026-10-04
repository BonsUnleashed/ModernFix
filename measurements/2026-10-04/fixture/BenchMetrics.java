package org.embeddedt.modernfix.benchmark;

import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.atomic.LongAdder;

/** Fixture-only measurements, applied identically to every native benchmark arm. */
public final class BenchMetrics {
    private static final com.sun.management.ThreadMXBean THREADS=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
    public static final LongAdder CUBE_HITS=new LongAdder(), CUBE_MISSES=new LongAdder();
    private BenchMetrics() {}
    public static long allocated() { return THREADS.isThreadAllocatedMemorySupported()?THREADS.getThreadAllocatedBytes(Thread.currentThread().getId()):-1; }
    public static long cpu() { return THREADS.isCurrentThreadCpuTimeSupported()?THREADS.getCurrentThreadCpuTime():-1; }
    public static long[] begin() { long[] s=new long[2];s[0]=System.nanoTime();s[1]=allocated();return s; }
    public static void end(String kind,String name,long[] s) {
        long bytes=allocated(),ns=System.nanoTime()-s[0];
        System.out.printf(Locale.ROOT,"MF_NATIVE kind=%s name=%s elapsed_ns=%d allocated_bytes=%d%n",kind,name,ns,s[1]<0?-1:bytes-s[1]);
    }
    public static void cube(boolean hit) { (hit?CUBE_HITS:CUBE_MISSES).increment(); }
    public static void storage(Object helper) {
        try {
            Field f=helper.getClass().getDeclaredField("topLevelModelLocations");f.setAccessible(true);Object set=f.get(helper);
            Instrumentation inst=(Instrumentation)System.getProperties().get("modernfix.nativebench.instrumentation");
            if(inst==null)throw new IllegalStateException("measurement agent missing");
            Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());seen.add(set);
            long retained=inst.getObjectSize(set);StringBuilder arrays=new StringBuilder();
            for(Class<?> c=set.getClass();c!=null;c=c.getSuperclass())for(Field field:c.getDeclaredFields()) {
                if(Modifier.isStatic(field.getModifiers())||field.getType().isPrimitive())continue;
                field.setAccessible(true);Object value=field.get(set);
                if(value!=null&&value.getClass().isArray()&&seen.add(value)) {
                    long bytes=inst.getObjectSize(value);retained+=bytes;
                    arrays.append(field.getName()).append(':').append(java.lang.reflect.Array.getLength(value)).append(':').append(bytes).append(',');
                }
            }
            System.out.printf(Locale.ROOT,"MF_NATIVE kind=set_storage class=%s entries=%d retained_bytes_excluding_elements=%d arrays=%s%n",set.getClass().getName(),((Set<?>)set).size(),retained,arrays);
        } catch(ReflectiveOperationException e) { throw new IllegalStateException("cannot measure native set storage",e); }
    }
    public static void tabs(List<?> tabs) {
        Map<String,Integer> classes=new TreeMap<>();for(Object tab:tabs)classes.merge(tab.getClass().getName(),1,Integer::sum);
        System.out.println("MF_NATIVE kind=tab_classes values="+classes);
    }
}
