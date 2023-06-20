package com.oracle.svm.hosted.datastructrepl.analysis;

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
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.java.NewInstanceNode;
import org.graalvm.compiler.nodes.java.StoreFieldNode;
import org.graalvm.compiler.replacements.nodes.ReflectionGetCallerClassNode;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.regex.Pattern;

public class ReplSafetyValidator {

    private final ArrayList<String> unsafeNodeTypes;


    public ReplSafetyValidator() {
        //TODO: Need to get the stringnames of the node classes to avoid these calls
        ArrayList<String> typeList = new ArrayList<>();
        typeList.add(HotSpotReflectionGetCallerClassNode.class.toString()); //sun.reflect.Reflection, which was the invocation for this
        typeList.add(SubstrateReflectionGetCallerClassNode.class.toString()); //is now deprecated. Shall I keep this for legacy problems? Also, hard to trace where it was called, what do?
        typeList.add(HubGetClassNode.class.toString());
        typeList.add(CheckcastArrayCopyCallNode.class.toString());
        typeList.add(MethodCallTargetNode.class.toString()); //TODO: For now
        typeList.add(ReturnNode.class.toString()); //TODO: For now
        typeList.add(GetClassNode.class.toString());
        typeList.add(InstanceOfNode.class.toString());
        typeList.add(InstanceOfDynamicNode.class.toString());
        typeList.add(ObjectEqualsNode.class.toString());
        this.unsafeNodeTypes = typeList;
    }

    public boolean verifySafe(Node usageNode){
        /*TODO: Find all reflection methods to add*/
        return !unsafeNodeTypes.contains(usageNode.getNodeClass().getJavaClass().toString());
    }

    public ArrayList<String> filterSafeNodes(StructuredGraph graph, ArrayList<String> interestNodesIds, String packageNameRegex){

        ArrayList<String> safeNodesIds = new ArrayList<>();

        //Iterate through all graph nodes
        for (Node n: graph.getNodes()) {
            if (Pattern.matches(packageNameRegex, graph.method().getDeclaringClass().getName())) {
                if(unsafeNodeTypes.contains(n.getNodeClass().getJavaClass().toString())){
                    if(n instanceof ReflectionGetCallerClassNode) {
                        String id = ((ReflectionGetCallerClassNode) n).getArgument(0).toString(Verbosity.Id);
                        interestNodesIds.remove(id);
                    } else if(n instanceof CheckcastArrayCopyCallNode){ //TODO: See result
                        System.out.println(((CheckcastArrayCopyCallNode) n).getSource().toString());
                    } else if(n instanceof MethodCallTargetNode){ //TODO: See result
                        System.out.println(((MethodCallTargetNode) n).toString());
                    } else if(n instanceof HubGetClassNode){ //TODO: See result
                        System.out.println(((HubGetClassNode) n).toString());
                    } else if(n instanceof ReturnNode){ //TODO: See result
                        System.out.println(((ReturnNode) n).toString());
                    } else if(n instanceof GetClassNode){ //TODO: See result
                        System.out.println(((GetClassNode) n).toString());
                    } else if(n instanceof InstanceOfNode){ //TODO: See result
                        System.out.println(((InstanceOfNode) n).toString());
                    } else if(n instanceof InstanceOfDynamicNode){ //TODO: See result
                        System.out.println(((InstanceOfDynamicNode) n).toString());
                    } else if(n instanceof ObjectEqualsNode){ //TODO: See result
                        System.out.println(((ObjectEqualsNode) n).toString());
                    }
                }
            }
        }
        safeNodesIds.addAll(interestNodesIds);
        return safeNodesIds;
    }

    private boolean traceReference(NewInstanceNode source){

        //FIFO queue for BFS reference tracing
        LinkedList<Node> nodeQueue = new LinkedList<>();
        ArrayList<Integer> visitedNodes = new ArrayList<>();
        for (Node usage : source.usages()){
            //TODO: See about tagging nodes rather than this inefficient list
            //TODO: Consider using printUsage function from profiler example for debug
            if (!visitedNodes.contains(Integer.getInteger(usage.toString(Verbosity.Id)))) {
                nodeQueue.addLast(usage);
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
}
