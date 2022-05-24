package com.oracle.svm.hosted.datastructrepl;

import com.oracle.svm.core.graal.nodes.SubstrateReflectionGetCallerClassNode;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.hotspot.replacements.HotSpotReflectionGetCallerClassNode;
import org.graalvm.compiler.hotspot.replacements.HubGetClassNode;
import org.graalvm.compiler.hotspot.replacements.arraycopy.CheckcastArrayCopyCallNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.extended.ClassIsArrayNode;
import org.graalvm.compiler.nodes.extended.GetClassNode;
import org.graalvm.compiler.nodes.java.InstanceOfDynamicNode;
import org.graalvm.compiler.nodes.java.InstanceOfNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.java.NewInstanceNode;
import org.graalvm.compiler.nodes.java.StoreFieldNode;
import org.graalvm.compiler.phases.Phase;

import java.util.ArrayList;
import java.util.LinkedList;

public class DataStructReplPhase extends Phase {

    private ArrayList<NewInstanceNode> safeNodes = new ArrayList<>();
    private ArrayList<String> unsafeNodeTypes;

    private void setUnsafeNodeTypes() {
        //TODO: Need to get the stringnames of the node classes to avoid these calls
        ArrayList<String> typeList = new ArrayList<>();
        typeList.add(HotSpotReflectionGetCallerClassNode.class.toString());
        typeList.add(SubstrateReflectionGetCallerClassNode.class.toString());
        typeList.add(HubGetClassNode.class.toString());
        typeList.add(CheckcastArrayCopyCallNode.class.toString());
        typeList.add(CheckcastArrayCopyCallNode.class.toString());
        typeList.add(MethodCallTargetNode.class.toString()); //TODO: For now
        typeList.add(ReturnNode.class.toString()); //TODO: For now
        typeList.add(ClassIsArrayNode.class.toString()); //TODO: more investigation needed
        typeList.add(GetClassNode.class.toString());
        typeList.add(InstanceOfNode.class.toString());
        typeList.add(InstanceOfDynamicNode.class.toString());
        this.unsafeNodeTypes = typeList;
    }

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

            for (Node consumer : currentNode.usages()){
                nodeQueue.addLast(consumer);
            }

            if(!verifySafe(currentNode))
                return false;

            nodeQueue.removeFirst();
        }

        return true;
    }

    private boolean verifySafe(Node usageNode){
        if(unsafeNodeTypes.contains(usageNode.getNodeClass().toString())) { //.toiString()
            /*TODO: Distinguish: am I sending the struct's reference to a method,
             *   or calling a method OF the reference's struct .class() method
             *   Find all reflection methods to add*/
            return false;
        }
        return true;
    }

    /*
        /home/romanb/MEGAsync/MSc/Thesis/Code/graal/sdk/latest_graalvm_home/bin/native-image -H:Dump=:2 -H:-PrintGraphFile -H:MethodFilter=main HelloWorld
     */

    @Override
    protected void run(StructuredGraph graph){

        setUnsafeNodeTypes();

        /*TODO: Optimization possibility: Graph.java L.999
        *  can we use getNodes(nodeClass) where nodeClass is NewInstanceNode?
        * This substantially reduces iteration cycles.*/
        for (Node n : graph.getNodes()){
            //Check if allocation
            if (n instanceof NewInstanceNode){
                NewInstanceNode nin = (NewInstanceNode) n;
                if (canBeHashMap(nin.instanceClass().getName())){
                    //TODO: Debug print, remove
                    System.out.println(String.format("[DataStructRepl] NewInstanceNode in %s:%s",
                            graph.method().getDeclaringClass().getName(),
                            graph.method().getName()));
                    if(traceReference(nin)){
                        safeNodes.add(nin);
                        System.out.println(String.format("[DataStructRepl] Found safe node in %s:%s",
                                graph.method().getDeclaringClass().getName(),
                                graph.method().getName()));
                    }
                }
            }
        }
        //TODO: Remove
        //System.out.println("Finished checking for replacement opportunities.");
    }

}