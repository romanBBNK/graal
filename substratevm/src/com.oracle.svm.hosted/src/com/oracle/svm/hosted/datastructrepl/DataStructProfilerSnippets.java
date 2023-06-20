package com.oracle.svm.hosted.datastructrepl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
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

    public static class MapObject{
        public int mapId;
        public int finalEntries;
        public int maxEntries;
        public int avgEntriesPerOp;
        public float loadFactor;
        public int initialCap;
        public int resizes;
        public double allocatedSpace;
        public double utilization;

        public MapObject(int mapId, int finalEntries, int maxEntries, int avgEntriesPerOp, float loadFactor, int initialCap, int resizes, double allocatedSpace, double utilization) {
            this.mapId = mapId;
            this.finalEntries = finalEntries;
            this.maxEntries = maxEntries;
            this.avgEntriesPerOp = avgEntriesPerOp;
            this.loadFactor = loadFactor;
            this.initialCap = initialCap;
            this.resizes = resizes;
            this.allocatedSpace = allocatedSpace;
            this.utilization = utilization;
        }

        @Override
        public String toString() {
            return String.format("Map id: %s. Current entries: %s. Max entries: %s. Average entries after op: %s\n" +
                            "Load Factor: %s. Initial Size: %s. Total used space: %s. Utilization: %s.",
                    mapId, finalEntries, maxEntries, avgEntriesPerOp,
                    loadFactor, initialCap, allocatedSpace, utilization);
        }
    }

    public static class MapCalculator {

        private int entries;
        private int maxEntries; //Maximum number of samples map has had in its lifetime
        //private long sumEntries;
        //private int accesses;
        private int initialCap=16;
        private int resizes=0;
        private float loadFactor = 0.75f;
        private int average = 0;
        private Queue<Integer> history = new LinkedList<>();

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
            if (history.size() >= 10) {
                history.remove();
            }
            history.add(entries);
        }

        public void mapClear(){
            entries = 0;
            updateAverage();
        }

        public void checkResize(){
            if(entries >= (getAllocatedSpace() * loadFactor)){
                resizes = resizes + 1;
            }
        }

        public int getAverage(){
            if (average==0) {
                long sum = 0;
                int size = history.size();
                Integer entry = history.poll();
                while (entry != null) {
                    sum = sum + entry;
                    entry = history.poll();
                }

                average = size == 0 ? 0 : (int) Math.ceil((double) sum / size);
            }
            return average;
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

        public String export(int number, int mapId, int methodHash){

            String instance = "Instance" + number + "{" +
                    "mapId=" + mapId +
                    ", methodHash=" + methodHash +
                    ", finalEntries=" + this.entries +
                    ", maxEntries=" + this.maxEntries +
                    ", avgEntriesPerOp=" + getAverage() +
                    ", loadFactor=" + this.loadFactor +
                    ", initialCap=" + this.initialCap +
                    ", resizes=" + this.resizes +
                    ", allocatedSpace=" + getAllocatedSpace() +
                    ", utilization=" + getUtilization() +
                    "}";

            return instance;
        }

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
    //private static Map<Integer, MapCalculator> entriesPerObject = new ConcurrentHashMap<>();
    //Key of map: hash code of package+method
    //Key of value map: id of node
    private static Map<Integer, Map<Integer, MapCalculator>> methodProfilingMap = new ConcurrentHashMap<>();
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
    public static void profileMapInit(int fieldId, int methodHash, int initialCap, float initialFactor) {

        if (!methodProfilingMap.containsKey(methodHash)) {
            methodProfilingMap.put(methodHash, new ConcurrentHashMap<>());
        }
        Map<Integer, MapCalculator> entriesPerObject = methodProfilingMap.get(methodHash);

        if (!entriesPerObject.containsKey(fieldId)) {
            entriesPerObject.put(fieldId, new MapCalculator());
        }
        entriesPerObject.get(fieldId).setInitialCap(initialCap);
        entriesPerObject.get(fieldId).setLoadFactor(initialFactor);
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    public static void profileMapPut(Object object, int fieldId, int methodHash) {

        if (!methodProfilingMap.containsKey(methodHash)) {
            methodProfilingMap.put(methodHash, new ConcurrentHashMap<>());
        }
        Map<Integer, MapCalculator> entriesPerObject = methodProfilingMap.get(methodHash);

        if ((object instanceof HashMap || object instanceof ConcurrentHashMap)) {

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
    public static void profileMapRemove(Object object, int fieldId, int methodHash) {

        /*if (!entriesPerObject.containsKey(fieldId)) {
            entriesPerObject.put(fieldId, new MapCalculator());
        }
        entriesPerObject.get(fieldId).update(false);*/

        if (!methodProfilingMap.containsKey(methodHash)) {
            methodProfilingMap.put(methodHash, new ConcurrentHashMap<>());
        }
        Map<Integer, MapCalculator> entriesPerObject = methodProfilingMap.get(methodHash);

        if ((object instanceof HashMap || object instanceof ConcurrentHashMap)) {

            if (!entriesPerObject.containsKey(fieldId)) {
                entriesPerObject.put(fieldId, new MapCalculator());
            }
            int size = ((Map) object).size();
            int diff = size - entriesPerObject.get(fieldId).getEntries();
            entriesPerObject.get(fieldId).update(diff);
        }
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    public static void profileMapClear(int fieldId, int methodHash) {

        if (!methodProfilingMap.containsKey(methodHash)) {
            methodProfilingMap.put(methodHash, new ConcurrentHashMap<>());
        }
        Map<Integer, MapCalculator> entriesPerObject = methodProfilingMap.get(methodHash);

        if (!entriesPerObject.containsKey(fieldId)) {
            entriesPerObject.put(fieldId, new MapCalculator());
        }
        entriesPerObject.get(fieldId).mapClear();
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    public static void profileOtherOp(Object object, int fieldId, int methodHash) {
        //Same code as put profiling. Accounts for profiling of other operations by getting the map's size after
        //whatever operation was run on it finishes.

        if (!methodProfilingMap.containsKey(methodHash)) {
            methodProfilingMap.put(methodHash, new ConcurrentHashMap<>());
        }
        Map<Integer, MapCalculator> entriesPerObject = methodProfilingMap.get(methodHash);

        if ((object instanceof HashMap || object instanceof ConcurrentHashMap)) {

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
        System.out.println(String.format("[DataStructProfilerSnippets:%s] Found %s profiled methods.", tag, methodProfilingMap.size()));

        List<Integer> sortedMethodIds = new ArrayList<>(methodProfilingMap.keySet());
        Collections.sort(sortedMethodIds);
        for (Integer methodId : sortedMethodIds) {
            Map<Integer, MapCalculator> entriesPerObject = methodProfilingMap.get(methodId);
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
    }

    private static void dumpMapsAsFile(){

        //Generate file path
        String dataPath = "ProfilingData/";
        if(checkDataDir(dataPath) != 0)
            return;

        dataPath = dataPath.concat("DataStructProfilingInfo.txt");

        try {
            File writeFile = new File(dataPath);
            FileOutputStream fos = new FileOutputStream(writeFile, false);
            String toWrite = "";

            List<Integer> sortedMethodIds = new ArrayList<>(methodProfilingMap.keySet());
            Collections.sort(sortedMethodIds);
            int totalMaps=0;
            for (Integer methodId : sortedMethodIds) {
                totalMaps = totalMaps + methodProfilingMap.get(methodId).size();
            }
            int totalCounter=0;

            toWrite = toWrite.concat("Instances="+totalMaps+";");
            for (Integer methodId : sortedMethodIds) {
                Map<Integer, MapCalculator> entriesPerObject = methodProfilingMap.get(methodId);

                List<Integer> sortedIds = new ArrayList<>(entriesPerObject.keySet());
                Collections.sort(sortedIds);
                int size = sortedIds.size();

                for (int i = 0; i < size; i++) {
                    toWrite = toWrite.concat("\n");
                    toWrite = toWrite.concat(entriesPerObject.get(sortedIds.get(i)).export(totalCounter, sortedIds.get(i), methodId));
                    totalCounter++;
                }
            }

            fos.write(toWrite.getBytes(StandardCharsets.UTF_8));
            fos.getFD().sync();
            fos.close();

        } catch (IOException e) {
            System.out.println("Failed to dump profiling info as file");
        }

    }

    public static Runnable dumpProfileResults() {
        return () -> {
            dumpNewInstanceCounter();
            dumpEntriesPerObject();
            dumpMapsAsFile();
        };
    }

    //Utility for profiling data dump
    private static int checkDataDir(String path){
        File dataDir = new File(path);
        if(!dataDir.exists()){
            // attempt to create the directory here
            if (!dataDir.mkdir()){
                System.out.println("Failed to create the profiling data storage directory");
                return -1;}
        }
        return 0;
    }

}
