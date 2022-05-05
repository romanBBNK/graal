package com.oracle.svm.hosted.datastructrepl;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.IsNullNode;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.java.FinalFieldBarrierNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.NewInstanceNode;
import org.graalvm.compiler.nodes.java.StoreFieldNode;
import org.graalvm.compiler.phases.Phase;

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.svm.core.nodes.SubstrateMethodCallTargetNode;
import com.oracle.svm.hosted.meta.HostedField;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class DataStructReplPhase extends Phase {

    private ArrayList<NewInstanceNode> safeNodes = new ArrayList<>();

    /*Returns whether the typename is in the hashmap family*/
    private static boolean canBeHashMap(String typename) {
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

    private boolean traceReference(NewInstanceNode source){

        //FIFO queue for BFS reference tracing
        LinkedList<Node> nodeQueue = new LinkedList<>();
        for (Node usage : source.usages()){
            //TODO: Check if worth using the 2 skips in the ProfilerPhase code
            nodeQueue.addLast(usage);
        }

        while(!nodeQueue.isEmpty()){
            Node currentNode = nodeQueue.peekFirst();

            //TODO: Field load/store marks fields as interesting rather than flat denial
            if(currentNode instanceof StoreFieldNode)
                return false;

            if(currentNode instanceof LoadFieldNode)
                return false;

            for (Node consumer : source.usages()){
                nodeQueue.addLast(consumer);
            }

            if(!verifySafe(currentNode))
                return false;

            nodeQueue.removeFirst();
        }

        return true;
    }

    private boolean verifySafe(Node usageNode){
        switch(usageNode.getNodeClass().toString()){
            //TODO: Verify correct names for comparison
            /*TODO: Where to get correct names for each of these?
            *  areturn
            *  checkcast
            *  instanceof
            *  invokeinterface
            *  invokespecial
            *  invokevirtual
            * 	//Will need to expand checking for specific methods, as it stands this would
            *   //block all methods. Need to make it more "granular" by researching dangerous
            *   //methods for each type of data structure we want to replace.
            *   //Distinguish: am I sending the struct's reference to a method,
            *   or calling a method OF the reference's struct
            *   .class() method
            *   All reflection-related methods*/
            case "Case1":
            case "Case2":
                return false;
            default:
                return true;
        }
    }

    @Override
    protected void run(StructuredGraph graph){

        /*TODO: Optimization possibility: Graph.java L.999
        *  can we use getNodes(nodeClass) where nodeClass is NewInstanceNode?
        * This substantially reduces iteration cycles.*/
        for (Node n : graph.getNodes()){
            //Check if allocation
            if (n instanceof NewInstanceNode){
                NewInstanceNode nin = (NewInstanceNode) n; //TODO: Why cast?
                if (canBeHashMap(nin.instanceClass().getName())){
                    //TODO: Debug print, remove
                    System.out.println(String.format("[DataStructRepl] NewInstanceNode in %s:%s",
                            graph.method().getDeclaringClass().getName(),
                            graph.method().getName()));
                    if(traceReference(nin))
                        safeNodes.add(nin);
                }
            }
        }
        //TODO: Remove?
        System.out.println("Finished checking for replacement opportunities.");
    }
}