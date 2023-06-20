package com.oracle.svm.hosted.datastructrepl.utils;

import com.oracle.svm.hosted.datastructrepl.DataStructProfilerSnippets;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReplFileHandler {

    public void generateMapObject(Map<Integer, ArrayList<DataStructProfilerSnippets.MapObject>> methodProfilingMap, String text){

        int mapId;
        int methodHash;
        int finalEntries;
        int maxEntries;
        int avgEntriesPerOp;
        float loadFactor;
        int initialCap;
        int resizes;
        double allocatedSpace;
        double utilization = 0;

        //Get the component variables
        mapId = Integer.parseInt(genPartial("mapId=\\d+[,]", text, "=", ","));
        methodHash = Integer.parseInt(genPartial("methodHash=-?\\d+[,]", text, "=", ","));
        finalEntries = Integer.parseInt(genPartial("finalEntries=\\d+[,]", text, "=", ","));
        maxEntries = Integer.parseInt(genPartial("maxEntries=\\d+[,]", text, "=", ","));
        avgEntriesPerOp = Integer.parseInt(genPartial("avgEntriesPerOp=\\d+[,]", text, "=", ","));
        loadFactor = Float.parseFloat(genPartial("loadFactor=\\d+[.]\\d+[,]", text, "=", ","));
        initialCap = Integer.parseInt(genPartial("initialCap=\\d+[,]", text, "=", ","));
        resizes = Integer.parseInt(genPartial("resizes=\\d+[,]", text, "=", ","));
        allocatedSpace = Double.parseDouble(genPartial("allocatedSpace=\\d+[.]\\d+E?\\d*[,]", text, "=", ","));
        String naNstringVerifier = genPartial("utilization=\\d+[.]\\d+[}]", text, "=", "}");
        if (!naNstringVerifier.isEmpty()){
            utilization = Double.parseDouble(naNstringVerifier);
            maxEntries = 0;
        }

        //Creates and stores the map info object
        DataStructProfilerSnippets.MapObject generated = new DataStructProfilerSnippets.MapObject(mapId, finalEntries,
                maxEntries, avgEntriesPerOp, loadFactor, initialCap, resizes, allocatedSpace, utilization);

        if (!methodProfilingMap.containsKey(methodHash)) {
            ArrayList<DataStructProfilerSnippets.MapObject> profiledMaps  = new ArrayList<>();
            methodProfilingMap.put(methodHash, profiledMaps);
        }
        methodProfilingMap.get(methodHash).add(generated);
    }

    //Take regex and strings as input, returns the section between delimiters if it exists
    public String genPartial(String regex, String text, String start, String finish){
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            if(finish.equals(""))
                return matcher.group().substring(
                        matcher.group().indexOf(start) + start.length());
            else
                return matcher.group().substring(
                        matcher.group().indexOf(start) + start.length(),
                        matcher.group().indexOf(finish));
        }
        return "";
    }

    public Map<Integer, ArrayList<DataStructProfilerSnippets.MapObject>> readProfilingData(File instancesFile){

        Map<Integer, ArrayList<DataStructProfilerSnippets.MapObject>> methodProfilingMap = new ConcurrentHashMap<>();

        try {
            //Reads contents of file into string
            FileReader fr=new FileReader(instancesFile);
            BufferedReader br=new BufferedReader(fr);
            StringBuilder sb = new StringBuilder();
            String line;
            while((line=br.readLine())!=null){
                sb.append(line);
            }
            String fullContents = sb.toString();
            fr.close();

            //Recovers info from string
            String instance;

            //TODO: There's an assumption file was written correctly. If it wasn't/been corrupted, there will be a crash

            //Gets the number of total reports in the file
            int totalInstances = Integer.parseInt(genPartial("Instances=\\d+;", fullContents, "=", ";"));
            System.out.println("Total instances profiled: "+ totalInstances);
            //Gets each map info and creates the respective info object
            for(int i = 0; i < totalInstances; i++){
                instance = genPartial("Instance" + i +"\\{.+?\\}", fullContents, "Instance" + i + "{", "");
                generateMapObject(methodProfilingMap, instance); //Gets an individual report string and processes it
            }

            //Deletes the profiling info file. This is to force the creation of a new one for every compilation,
            // for safety against code changes.
            /*if (!instancesFile.delete()){
                System.out.println("Failed to delete profiling data file.");
            }*/

        } catch (FileNotFoundException e) {
            System.out.println("Failed to open profiling data file.");
        } catch (IOException e) {
            System.out.println("Failed to read profiling data file.");
        }
        return methodProfilingMap;
    }

    public String generateRegexPackageName(String packageName) {
        return ".*" + packageName.replace(".", "\\.") + ".*";
    }

    public String generateFilepathPackageName(String packageName) {
        String[] parts = packageName.split("\\.");
        return parts.length == 1 ? packageName : parts[parts.length-1];
    }
}
