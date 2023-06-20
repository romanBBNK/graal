package com.oracle.svm.hosted.datastructrepl.replacement;

import com.oracle.svm.hosted.datastructrepl.DataStructProfilerSnippets;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.StructuredGraph;

import java.util.ArrayList;
import java.util.Map;

public class ReplacementHandler {

    public void structureReplacement(StructuredGraph graph, Map<Integer, ArrayList<DataStructProfilerSnippets.MapObject>> profiledMethods){

        boolean doArgumentMod;
        String packageAndMethod = graph.method().getDeclaringClass().getName()
                + graph.method().getName();
        int packageAndMethodHash = packageAndMethod.hashCode();

        if (profiledMethods.containsKey(packageAndMethodHash)) {
            ArrayList<DataStructProfilerSnippets.MapObject> profiledMaps = profiledMethods.get(packageAndMethodHash);
            for(DataStructProfilerSnippets.MapObject map: profiledMaps){
                //TODO: Debug print, remove
                System.out.println(map.toString());
                doArgumentMod = false;

                if(map.maxEntries >= 12) {
                    if(map.avgEntriesPerOp * 1.25 >= map.maxEntries){ //Ensures load is consistent and without major spikes
                        if(map.maxEntries > (map.allocatedSpace / 2) && map.resizes > 0){
                            //Max load higher than previous size, but within load factor of current size
                            if(map.initialCap == 16 && map.loadFactor == 0.75){
                                //Initial capacity and load factor are default, safe to change
                                map.initialCap = Math.round(map.maxEntries / map.loadFactor);
                                map.loadFactor = 0.8f;
                                doArgumentMod = true;
                            }
                        } else if(map.maxEntries <= (map.allocatedSpace / 2) && map.resizes > 0){
                            //Max load higher than previous real capacity, but lower or equal to allocated size
                            if(map.maxEntries == (map.allocatedSpace / 2)){
                                //Max load equal to allocated space before resize
                                if(map.initialCap == 16 && map.loadFactor == 0.75){
                                    //Load factor would be 1. Would lead to low performance and
                                    //zero tolerance for even one additional entry. This method adds tolerance
                                    //with a still respectable 33% space optimization
                                    map.initialCap = Math.round(map.maxEntries / map.loadFactor);
                                    map.loadFactor = 0.8f;
                                    doArgumentMod = true;
                                }
                            } else { //Max load lower than allocated space before resize
                                if(map.initialCap == 16 && map.loadFactor == 0.75){
                                    int newUsableSpace = Math.min(
                                            (int) (Math.max(Math.round(map.maxEntries * 1.05), map.maxEntries + 1)),
                                            (int) map.allocatedSpace / 2);
                                    if (newUsableSpace == (map.allocatedSpace /2)){
                                        //Load factor would be 1. Would lead to low performance and
                                        //zero tolerance for even one additional entry. This method adds tolerance
                                        //with a still respectable 33% space optimization
                                        map.initialCap = Math.round(newUsableSpace / map.loadFactor);
                                        map.loadFactor = 0.8f;
                                    } else {
                                        //New usable space is less than the pre-resize allocated space.
                                        // We set only the load factor. Better for slow growth, while keeping same scaling
                                        map.loadFactor = Double.valueOf(newUsableSpace / (map.allocatedSpace / 2)).floatValue();
                                    }
                                    //map.initialCap = (int) (map.allocatedSpace / 2);
                                    doArgumentMod = true;
                                }
                            }
                        }
                    }
                    if(doArgumentMod){
                        System.out.println(
                                String.format("Changing allocation arguments. Load factor: %s. Initial capacity: %s",
                                        map.loadFactor, map.initialCap));
                        //TODO:Currently only working for init calls. Need to find a way to work with non-init NewInstances

                        for (Node n : graph.getNodes()){
                            if (n instanceof InvokeWithExceptionNode){
                                InvokeWithExceptionNode in = (InvokeWithExceptionNode) n;
                                String targetId = in.callTarget().arguments().get(0).toString(Verbosity.Id);
                                if (in.callTarget().targetMethod().getName().equals("<init>")){
                                    System.out.println("Found init node, Id: " + targetId + ". Map Id: " + map.mapId);
                                    if(map.mapId==Integer.parseInt(targetId)){
                                        if (in.callTarget().arguments().size() == 3
                                                && in.callTarget().arguments().get(1).isConstant()
                                                && in.callTarget().arguments().get(2).isConstant()) {
                                            System.out.println("Modifying init for node Id: " + targetId);
                                            in.callTarget().arguments().set(1, ConstantNode.forInt(map.initialCap, graph));
                                            in.callTarget().arguments().set(2, ConstantNode.forFloat(map.loadFactor, graph));
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
