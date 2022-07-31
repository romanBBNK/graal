package com.oracle.svm.hosted.datastructrepl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;

public class DataStructProfilerSnippets {

    static class AverageCalculator {

        private int samples;
        private long sum;

        public void update(int partial) {
            sum = sum + partial;
            samples = samples + 1;
        }

        public int average() {
            return samples == 0 ? 0 : (int) sum / samples;
        }

        @Override
        public String toString() {
            return String.format("%s", average());
        }
    }

    static class AverageCalculatorComparator implements Comparator<AverageCalculator> {

        @Override
        public int compare(AverageCalculator o1, AverageCalculator o2) {
            return o1.average() - o2.average();
        }

    }

    static class MapCalculatorComparator implements Comparator<MapCalculator> {

        @Override
        public int compare(MapCalculator o1, MapCalculator o2) {
            return o1.getAverage() - o2.getAverage();
        }

    }

    static class MapCalculator {

        private int entries;
        private int maxEntries; //Maximum number of samples map has had in its lifetime
        private long sumEntries;
        private int accesses;
        private int initialCap=16;
        private int resizes;
        private float loadFactor = 0.75f;

        //TODO: Instead of replacement from hash to another type, replace load factor (if map only ever uses 31 elements)

        public void update(boolean addition) {
            if(addition){
                if(entries == maxEntries)
                    maxEntries = maxEntries + 1;
                entries = entries + 1;
            } else {
                if(entries>0)
                    entries = entries - 1;
            }
            updateAverage();
        }

        public void update(int size){
            entries = entries + size;
            if(maxEntries < entries)
                maxEntries = entries;
            updateAverage();
        }

        public void updateAverage(){
            sumEntries = sumEntries + entries;
            accesses = accesses + 1;
        }

        public void mapClear(){
            entries = 0;
            accesses = accesses + 1;
        }

        public void checkResize(){
            if(entries >= (getAllocatedSpace() * loadFactor)){
                resizes = resizes + 1;
            }
        }

        public int getAverage(){
            return accesses == 0 ? 0 : (int) sumEntries / accesses;
        }

        public int getEntries(){
            return entries;
        }

        public int getInitialCap(){return  initialCap;}

        public void setInitialCap(int newCap){initialCap = newCap;}

        public float getLoadFactor(){return  loadFactor;}

        public void setLoadFactor(float newFactor){loadFactor = newFactor;}

        public double getAllocatedSpace(){ return initialCap * (int) Math.pow(2, resizes);}

        public double getUtilization(){ return entries / getAllocatedSpace();}

        @Override
        public String toString() {
            return String.format("Current entries: %s. Max entries: %s. Average entries after op: %s\n" +
                    "Load Factor: %s. Initial Size: %s. Total used space: %s. Utilization: %s.",
                    entries, maxEntries, getAverage(),
                    loadFactor, initialCap, getAllocatedSpace(), getUtilization());
        }
    }

    public static final SnippetRuntime.SubstrateForeignCallDescriptor PROFILE_NEW_INSTANCE = SnippetRuntime.findForeignCall(DataStructProfilerSnippets.class, "profileNewInstance", false);
    //TODO: Deprecated descriptor
    public static final SnippetRuntime.SubstrateForeignCallDescriptor PROFILE_FIELD_LOAD = SnippetRuntime.findForeignCall(DataStructProfilerSnippets.class, "profileFieldLoad", false);
    public static final SnippetRuntime.SubstrateForeignCallDescriptor PROFILE_MAP_INIT = SnippetRuntime.findForeignCall(DataStructProfilerSnippets.class, "profileMapInit", false);
    public static final SnippetRuntime.SubstrateForeignCallDescriptor PROFILE_MAP_PUT = SnippetRuntime.findForeignCall(DataStructProfilerSnippets.class, "profileMapPut", false);
    public static final SnippetRuntime.SubstrateForeignCallDescriptor PROFILE_MAP_REMOVE = SnippetRuntime.findForeignCall(DataStructProfilerSnippets.class, "profileMapRemove", false);
    public static final SnippetRuntime.SubstrateForeignCallDescriptor PROFILE_MAP_CLEAR = SnippetRuntime.findForeignCall(DataStructProfilerSnippets.class, "profileMapClear", false);
    public static final SnippetRuntime.SubstrateForeignCallDescriptor PROFILE_MAP_OTHER = SnippetRuntime.findForeignCall(DataStructProfilerSnippets.class, "profileOtherOp", false);
    public static final SnippetRuntime.SubstrateForeignCallDescriptor[] FOREIGN_CALLS = new SnippetRuntime.SubstrateForeignCallDescriptor[]{
            PROFILE_NEW_INSTANCE,
            PROFILE_FIELD_LOAD, //TODO: Remove when not needed
            PROFILE_MAP_INIT,
            PROFILE_MAP_PUT,
            PROFILE_MAP_REMOVE,
            PROFILE_MAP_CLEAR,
            PROFILE_MAP_OTHER};

    private static int newInstanceCounter = 0;
    private static Map<Integer, Integer> sizesPerObjectHashcode = new ConcurrentHashMap<>();
    private static Map<Integer, MapCalculator> entriesPerObject = new ConcurrentHashMap<>();
    private static Map<Integer, AverageCalculator> sizesPerObjectFieldId = new ConcurrentHashMap<>();

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    public static void profileNewInstance() {
        newInstanceCounter += 1;
    }

    //TODO: DEPRECATED: REMOVE AFTER TESTING
    @SubstrateForeignCallTarget(stubCallingConvention = false)
    public static void profileFieldLoad(Object object, int fieldId) {

        if (object != null && (object instanceof HashMap || object instanceof ConcurrentHashMap)) {
            Integer hashcode = System.identityHashCode(object);
            Integer size = ((Map) object).size();
            sizesPerObjectHashcode.put(hashcode, size);
            if (!sizesPerObjectFieldId.containsKey(fieldId)) {
                sizesPerObjectFieldId.put(fieldId, new AverageCalculator());
            }
            sizesPerObjectFieldId.get(fieldId).update(size);
        }
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    public static void profileMapInit(int fieldId, int initialCap, float initialFactor) {

        if (!entriesPerObject.containsKey(fieldId)) {
            entriesPerObject.put(fieldId, new MapCalculator());
        }
        entriesPerObject.get(fieldId).setInitialCap(initialCap);
        entriesPerObject.get(fieldId).setLoadFactor(initialFactor);
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    public static void profileMapPut(Object object, int fieldId) {

        /*if (!entriesPerObject.containsKey(fieldId)) {
            entriesPerObject.put(fieldId, new MapCalculator());
        }
        entriesPerObject.get(fieldId).update(true);
        entriesPerObject.get(fieldId).checkResize();*/

        if (object != null && (object instanceof HashMap || object instanceof ConcurrentHashMap)) {

            if (!entriesPerObject.containsKey(fieldId)) {
                entriesPerObject.put(fieldId, new MapCalculator());
            }
            int size = ((Map) object).size();
            int diff = size - entriesPerObject.get(fieldId).getEntries();
            entriesPerObject.get(fieldId).update(diff);
            entriesPerObject.get(fieldId).checkResize();
        }
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    public static void profileMapRemove(Object object, int fieldId) {

        /*if (!entriesPerObject.containsKey(fieldId)) {
            entriesPerObject.put(fieldId, new MapCalculator());
        }
        entriesPerObject.get(fieldId).update(false);*/

        if (object != null && (object instanceof HashMap || object instanceof ConcurrentHashMap)) {

            if (!entriesPerObject.containsKey(fieldId)) {
                entriesPerObject.put(fieldId, new MapCalculator());
            }
            int size = ((Map) object).size();
            int diff = size - entriesPerObject.get(fieldId).getEntries();
            entriesPerObject.get(fieldId).update(diff);
        }
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    public static void profileMapClear(int fieldId) {

        if (!entriesPerObject.containsKey(fieldId)) {
            entriesPerObject.put(fieldId, new MapCalculator());
        }
        entriesPerObject.get(fieldId).mapClear();
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    public static void profileOtherOp(Object object, int fieldId) {
        //Same code as put profiling. Accounts for profiling of other operations by getting the map's size after
        //whatever operation was run on it finishes.

        if (object != null && (object instanceof HashMap || object instanceof ConcurrentHashMap)) {

            if (!entriesPerObject.containsKey(fieldId)) {
                entriesPerObject.put(fieldId, new MapCalculator());
            }
            int size = ((Map) object).size();
            int diff = size - entriesPerObject.get(fieldId).getEntries();
            entriesPerObject.get(fieldId).update(diff);
            entriesPerObject.get(fieldId).checkResize();
        }
    }

    private static void dumpNewInstanceCounter() {
        System.out.println(String.format("[DataStructProfilerSnippets:dumpNewInstanceCounter] Found %s instancesof Map.", newInstanceCounter));
    }

    private static void dumpEntriesPerObject() {
        String tag = "dumpEntriesPerObject";
        System.out.println(String.format("[DataStructProfilerSnippets:%s] Found %s profiled Map objects.", tag, entriesPerObject.size()));

        List<Integer> sortedIds = new ArrayList<>(entriesPerObject.keySet());
        Collections.sort(sortedIds);
        for (Integer id : sortedIds){
            System.out.println(String.format(
                    "[DataStructProfilerSnippets:%s] Profiled Map with id: %s. %s",
                    tag,
                    id,
                    entriesPerObject.get(id).toString()));
        }
    }

    private static void dumpSizesPerObjectHashcode() {
        String tag = "dumpSizesPerObjectHashcode";
        System.out.println(String.format("[DataStructProfilerSnippets:%s] Found %s Map objects.", tag, sizesPerObjectHashcode.size()));

        HashMap<Integer, Integer> cardinalityPerSize = new HashMap<>();
        for (Integer size : sizesPerObjectHashcode.values()) {
            if (!cardinalityPerSize.containsKey(size)) {
                cardinalityPerSize.put(size, 1);
            } else {
                cardinalityPerSize.put(size, cardinalityPerSize.get(size) + 1);
            }
        }

        List<Integer> sortedSizes = new ArrayList<>(cardinalityPerSize.keySet());
        Collections.sort(sortedSizes);
        for (Integer size : sortedSizes) {
            System.out.println(String.format("[DataStructProfilerSnippets:%s] Found %s Maps with %s elements.", tag, cardinalityPerSize.get(size), size));
        }
    }

    private static void dumpSizesPerObjectFieldId() {
        String tag = "dumpSizesPerObjectFieldId";
        System.out.println(String.format("[DataStructProfilerSnippets:%s] Found %s Map fields.", tag, sizesPerObjectFieldId.size()));

        HashMap<Integer, Integer> cardinalityPerSize = new HashMap<>();
        for (AverageCalculator size : sizesPerObjectFieldId.values()) {
            int average = size.average();
            if (!cardinalityPerSize.containsKey(average)) {
                cardinalityPerSize.put(average, 1);
            } else {
                cardinalityPerSize.put(average, cardinalityPerSize.get(average) + 1);
            }
        }

        List<Integer> sortedSizes = new ArrayList<>(cardinalityPerSize.keySet());
        Collections.sort(sortedSizes);
        for (Integer size : sortedSizes) {
            System.out.println(String.format("[DataStructProfilerSnippets:%s] Found %s Maps with %s elements.", tag, cardinalityPerSize.get(size), size));
        }
    }

    public static Runnable dumpProfileResults() {
        return () -> {
            dumpNewInstanceCounter();
            dumpEntriesPerObject();
            //TODO: Remove unused
            //dumpSizesPerObjectHashcode();
            //dumpSizesPerObjectFieldId();
        };
    }
}
