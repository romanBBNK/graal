import org.graalvm.collections.*;

import java.lang.management.*;
import java.util.HashMap;

public class MapSizeTest {
    //Usage MapSizeTest <economic> <iterations>
    //Economic: true: uses economic hash maps
    //Number of items to add to the application
    public static void main(String[] args) {
        if(args.length != 2){
            System.out.println("Incorrect arguments! Arguments: <economic:boolean> <iterations:integer>");
            return;
        }

        boolean economic;
        int iterations;

        economic = Boolean.parseBoolean(args[0]);
        iterations = Integer.parseInt(args[1]);

        Runtime runtime = Runtime.getRuntime();
        //System.gc();
        //long usedMemoryBefore = runtime.totalMemory() - runtime.freeMemory();
        long usedMemoryBefore = getReallyUsedMemory();

        if(economic){
            //System.out.println("Economic map. Adding " + iterations + " elements.");
            EconomicMap<Integer, String> map = EconomicMap.create();
            //HashMap<Integer, String> mapTemp = new HashMap<>();
            //EconomicMapWrap<Integer, String> map = new EconomicMapWrap<>(mapTemp);

            for(int i=0; i < iterations; i++){
                map.put(i, String.valueOf(i));
            }
        } else {
            //System.out.println("Standard hash map. Adding " + iterations + " elements.");
            HashMap<Integer, String> map = new HashMap<>();

            for(int i=0; i < iterations; i++){
                map.put(i, String.valueOf(i));
            }
        }

        //System.gc();
        //long usedMemoryAfter = runtime.totalMemory() - runtime.freeMemory();
        long usedMemoryAfter = getReallyUsedMemory();
        //System.out.println("Total memory used (bytes): " + (usedMemoryAfter-usedMemoryBefore));
        System.out.println((economic? "1 " : "2 ") + iterations + " " + (usedMemoryAfter-usedMemoryBefore));
    }

    static long getGcCount() {
        long sum = 0;
        for (GarbageCollectorMXBean b : ManagementFactory.getGarbageCollectorMXBeans()) {
            long count = b.getCollectionCount();
            if (count != -1) { sum +=  count; }
        }
        return sum;
    }
    static long getReallyUsedMemory() {
        long before = getGcCount();
        System.gc();
        while (getGcCount() == before);
        return getCurrentlyUsedMemory();
    }

    static long getCurrentlyUsedMemory() {
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();// +
                        //ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage().getUsed();
    }
}
