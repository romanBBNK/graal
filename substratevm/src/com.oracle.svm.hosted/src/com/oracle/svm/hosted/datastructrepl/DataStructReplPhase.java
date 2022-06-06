package com.oracle.svm.hosted.datastructrepl;

import com.oracle.svm.core.graal.nodes.SubstrateReflectionGetCallerClassNode;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.hotspot.replacements.HotSpotReflectionGetCallerClassNode;
import org.graalvm.compiler.hotspot.replacements.HubGetClassNode;
import org.graalvm.compiler.hotspot.replacements.arraycopy.CheckcastArrayCopyCallNode;
import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.calc.ObjectEqualsNode;
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
import java.util.regex.Pattern;

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
        typeList.add(GetClassNode.class.toString());
        typeList.add(InstanceOfNode.class.toString());
        typeList.add(InstanceOfDynamicNode.class.toString());
        typeList.add(ObjectEqualsNode.class.toString());
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
        ArrayList<Integer> visitedNodes = new ArrayList<>();
        for (Node usage : source.usages()){
            //TODO: See about tagging nodes rather than this inefficient list
            if (!visitedNodes.contains(Integer.getInteger(usage.toString(Verbosity.Id)))) {
                nodeQueue.addLast(usage);
                visitedNodes.add(Integer.getInteger(usage.toString(Verbosity.Id)));
            }
        }

        while(!nodeQueue.isEmpty()){
            Node currentNode = nodeQueue.peekFirst();

            // Skip if node is used in store but not as a value.
            // If it is, false for now
            if (currentNode instanceof StoreFieldNode && ((StoreFieldNode) currentNode).value() != source) {
                return false;
            }

            //Removed same check for LoadFieldNode, if it's being loaded it should be safe

            System.out.println("In while");

            if(currentNode instanceof InvokeNode) {
                //Direct#HashMap.getNode
                System.out.println(((InvokeNode) currentNode).getTargetMethod());
                return false; //TODO: Deeper comparison of InvokeNode
            }

            if(!verifySafe(currentNode))
                return false;

            for (Node consumer : currentNode.usages()){
                if (!visitedNodes.contains(Integer.getInteger(consumer.toString(Verbosity.Id)))) {
                    nodeQueue.addLast(consumer);
                    visitedNodes.add(Integer.getInteger(consumer.toString(Verbosity.Id)));
                }
            }

            nodeQueue.removeFirst();
        }

        return true;
    }

    private boolean verifySafe(Node usageNode){
        if(unsafeNodeTypes.contains(usageNode.getNodeClass().getJavaClass().toString())) {
            /*TODO: Distinguish: am I sending the struct's reference to a method,
             *   or calling a method OF the reference's struct .class() method?
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

        for (Node n : graph.getNodes()){
            //"^Ljava|^Ljdk|^Lsun|^Lcom" lets through things it shouldn't
            if (Pattern.matches("main", graph.method().getName())) {
                if (n instanceof NewInstanceNode){
                    NewInstanceNode nin = (NewInstanceNode) n;
                    if (canBeHashMap(nin.instanceClass().getName())){
                        System.out.println(String.format("[DataStructRepl] NewInstanceNode in %s:%s, id: %s \nClass: %s",
                                graph.method().getDeclaringClass().getName(),
                                graph.method().getName(),
                                n.toString(Verbosity.Id),
                                nin.instanceClass().toString()));
                        if(traceReference(nin)){
                            safeNodes.add(nin);
                            System.out.println(String.format("[DataStructRepl] Found safe node in %s:%s, id: %s \nClass: %s",
                                    graph.method().getDeclaringClass().getName(),
                                    graph.method().getName(),
                                    n.toString(Verbosity.Id),
                                    nin.instanceClass().toString()));
                        }
                    }
                }
            }
        }
    }

}