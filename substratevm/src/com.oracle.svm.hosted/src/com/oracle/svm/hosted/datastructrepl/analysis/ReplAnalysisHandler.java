package com.oracle.svm.hosted.datastructrepl.analysis;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.java.NewInstanceNode;

import java.util.ArrayList;
import java.util.regex.Pattern;

public class ReplAnalysisHandler {

    //TODO: Disabled due to being redundant for data structure tuning. Re-enable for structure replacement
    //private final ReplSafetyValidator safetyValidator = new ReplSafetyValidator();

    public ArrayList<String> findCandidateNodes(StructuredGraph graph, String packageNameRegex) {
        return findNodesOfInterest(graph, packageNameRegex);
    }

    private ArrayList<String> findNodesOfInterest(StructuredGraph graph, String packageNameRegex) {

        ArrayList<String> interestNodesIds = new ArrayList<>();

        //Checks for replacement opportunities
        for (Node n : graph.getNodes()){
            //TODO: this check might be redundant
            if (Pattern.matches(packageNameRegex, graph.method().getDeclaringClass().getName())) {
                if (n instanceof NewInstanceNode){
                    NewInstanceNode nin = (NewInstanceNode) n;
                    if (canBeHashMap(nin.instanceClass().getName())){
                        System.out.printf("[DataStructRepl] NewInstanceNode in %s:%s, id: %s \nClass: %s%n",
                                graph.method().getDeclaringClass().getName(),
                                graph.method().getName(),
                                n.toString(Verbosity.Id),
                                nin.instanceClass().toString());
                        interestNodesIds.add(nin.toString(Verbosity.Id));
                    }
                }
            }
        }

        return interestNodesIds;
    }

    /*Returns whether the typename is in the hashmap family*/
    private boolean canBeHashMap(String typename) {
        switch (typename) {
            case "Ljava/util/HashMap;":
            case "Ljava/util/concurrent/ConcurrentHashMap;":
            case "Ljava/util/concurrent/ConcurrentMap;":
            case "Ljava/util/AbstractMap;":
            case "Ljava/util/Map;":
                return true;
            default:
                return false;
        }
    }
}
