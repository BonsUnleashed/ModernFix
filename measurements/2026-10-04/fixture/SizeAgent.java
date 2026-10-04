package bench;
import java.lang.instrument.Instrumentation;
public final class SizeAgent {
    public static void premain(String args,Instrumentation inst) {System.getProperties().put("modernfix.nativebench.instrumentation",inst);}
}
