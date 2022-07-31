package com.oracle.svm.hosted.datastructrepl;

import com.oracle.graal.pointsto.flow.SourceTypeFlow;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.svm.core.graal.nodes.SubstrateReflectionGetCallerClassNode;
import com.oracle.svm.core.nodes.SubstrateMethodCallTargetNode;
import com.oracle.svm.hosted.meta.HostedField;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.hotspot.replacements.HotSpotReflectionGetCallerClassNode;
import org.graalvm.compiler.hotspot.replacements.HubGetClassNode;
import org.graalvm.compiler.hotspot.replacements.arraycopy.CheckcastArrayCopyCallNode;
import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.compiler.nodes.BeginNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.ObjectEqualsNode;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.extended.GetClassNode;
import org.graalvm.compiler.nodes.java.InstanceOfDynamicNode;
import org.graalvm.compiler.nodes.java.InstanceOfNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.java.NewInstanceNode;
import org.graalvm.compiler.nodes.java.StoreFieldNode;
import org.graalvm.compiler.phases.Phase;
import org.graalvm.compiler.replacements.IntrinsicGraphBuilder;
import org.graalvm.compiler.replacements.nodes.ReflectionGetCallerClassNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Objects;
import java.util.regex.Pattern;

public class DataStructReplPhase extends Phase {

    private ArrayList<String> safeNodesIds = new ArrayList<>();
    private ArrayList<String> interestNodesIds = new ArrayList<>();
    private ArrayList<String> unsafeNodeTypes;

    private void setUnsafeNodeTypes() {
        //TODO: Need to get the stringnames of the node classes to avoid these calls
        ArrayList<String> typeList = new ArrayList<>();
        typeList.add(HotSpotReflectionGetCallerClassNode.class.toString());
        typeList.add(SubstrateReflectionGetCallerClassNode.class.toString());
        typeList.add(HubGetClassNode.class.toString()); //TODO: What's a hub and how do I get reference to it? Also, what is klass/clazz
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
            //TODO: Consider using printUsage function from profiler example for debug
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

    private void checkInstanceSafety(StructuredGraph graph){

        //Iterate through all graph nodes
        /*for (Node n: graph.getNodes()) {
            if (Pattern.matches("main", graph.method().getName())) {
                if(unsafeNodeTypes.contains(n.getNodeClass().getJavaClass().toString())){
                    if(n instanceof ReflectionGetCallerClassNode) {
                        String id = ((ReflectionGetCallerClassNode) n).getArgument(0).toString(Verbosity.Id);
                        interestNodesIds.remove(id);
                    } else if(n instanceof CheckcastArrayCopyCallNode){ //TODO: See result
                        System.out.println(((CheckcastArrayCopyCallNode) n).getSource().toString());
                    } else if(n instanceof MethodCallTargetNode){ //TODO: See result
                        System.out.println(((MethodCallTargetNode) n).toString());
                    }
                }
            }
        }*/
        safeNodesIds.addAll(interestNodesIds);
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

    private void appendProfilerInfo(StructuredGraph graph){

        for (Node n : graph.getNodes()){
            if (Pattern.matches("main", graph.method().getName())) {

                //Append profiling to new instances
                if(n instanceof NewInstanceNode){
                    if(safeNodesIds.contains(n.toString(Verbosity.Id))){
                        NewInstanceNode nin = (NewInstanceNode) n;
                        ForeignCallNode fcn = graph.add(new ForeignCallNode(DataStructProfilerSnippets.PROFILE_NEW_INSTANCE, new ValueNode[]{}));
                        graph.addBeforeFixed(nin, fcn);
                    }
                }

                //Append profiling to usages of new instances
                if (n instanceof InvokeWithExceptionNode){
                    InvokeWithExceptionNode in = (InvokeWithExceptionNode) n;
                    String targetId = in.callTarget().arguments().get(0).toString(Verbosity.Id);
                    //System.out.println("Target method: " + in.callTarget().targetMethod().getName());
                    //System.out.println(in.callTarget().arguments().toString());

                    if (safeNodesIds.contains(targetId)) {
                        //TODO: Other methods to profile:
                        //compute (what does it do?), putAll (need to check args), putIfAbsent (get result of check?), remove(k, v)(how to check result?)
                        //TODO: Get HashMap object/node to pass to profiler code
                        if (in.callTarget().targetMethod().getName().equals("put")) {
                            NewInstanceNode nin = (NewInstanceNode) graph.getNode(Integer.parseInt(targetId));
                            ValueNode[] args = new ValueNode[]{nin, ConstantNode.forInt(Integer.parseInt(targetId), graph)};
                            ForeignCallNode fcn = graph.add(new ForeignCallNode(DataStructProfilerSnippets.PROFILE_MAP_PUT, args));
                            if(in.successors().first() instanceof BeginNode){
                                BeginNode bn = (BeginNode) in.successors().first(); //Appends to begin, not the exception
                                graph.addAfterFixed(bn, fcn); //Must be added after the method call to profile the map after it is modified
                                //Needs to be after 'Begin' node because Call node isn't FixedWithNext as is needed
                            }
                        }else if (in.callTarget().targetMethod().getName().equals("clear")){
                            ValueNode[] args = new ValueNode[]{ConstantNode.forInt(Integer.parseInt(targetId), graph)};
                            ForeignCallNode fcn = graph.add(new ForeignCallNode(DataStructProfilerSnippets.PROFILE_MAP_CLEAR, args));
                            graph.addBeforeFixed(in, fcn);
                        }else if (in.callTarget().targetMethod().getName().equals("remove")){
                            NewInstanceNode nin = (NewInstanceNode) graph.getNode(Integer.parseInt(targetId));
                            ValueNode[] args = new ValueNode[]{nin, ConstantNode.forInt(Integer.parseInt(targetId), graph)};
                            ForeignCallNode fcn = graph.add(new ForeignCallNode(DataStructProfilerSnippets.PROFILE_MAP_REMOVE, args));
                            if(in.successors().first() instanceof BeginNode){
                                BeginNode bn = (BeginNode) in.successors().first();
                                graph.addAfterFixed(bn, fcn);
                            }
                        } else if (in.callTarget().targetMethod().getName().equals("<init>")){
                            int initialCap = Objects.requireNonNull(in.callTarget().arguments().get(1).asJavaConstant()).asInt();
                            float initialFactor = Objects.requireNonNull(in.callTarget().arguments().get(2).asJavaConstant()).asFloat();
                            ValueNode[] args = new ValueNode[]{ConstantNode.forInt(Integer.parseInt(targetId), graph),
                                    ConstantNode.forInt(initialCap, graph),
                                    ConstantNode.forFloat(initialFactor, graph)};
                            ForeignCallNode fcn = graph.add(new ForeignCallNode(DataStructProfilerSnippets.PROFILE_MAP_INIT, args));
                            graph.addBeforeFixed(in, fcn);
                        } else {
                            NewInstanceNode nin = (NewInstanceNode) graph.getNode(Integer.parseInt(targetId));
                            ValueNode[] args = new ValueNode[]{nin, ConstantNode.forInt(Integer.parseInt(targetId), graph)};
                            ForeignCallNode fcn = graph.add(new ForeignCallNode(DataStructProfilerSnippets.PROFILE_MAP_OTHER, args));
                            if(in.successors().first() instanceof BeginNode){
                                BeginNode bn = (BeginNode) in.successors().first(); //Appends to begin, not the exception
                                graph.addAfterFixed(bn, fcn); //Must be added after the method call to profile the map after it is modified
                                //Needs to be after 'Begin' node because Call node isn't FixedWithNext as is needed
                            }
                        }
                    }
                }
            }
        }
    }

    /*
        /home/romanb/MEGAsync/MSc/Thesis/Code/graal/sdk/latest_graalvm_home/bin/native-image -H:Dump=:2 -H:-PrintGraphFile -H:MethodFilter=main HelloWorld
     */

    @Override
    protected void run(StructuredGraph graph){

        setUnsafeNodeTypes();

        //Checks for replacement opportunities
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
                        /*if(traceReference(nin)){
                            //TODO: Workaround for getId() deprecation. Optimization possible?
                            safeNodesIds.add(nin.toString(Verbosity.Id));
                            System.out.println(String.format("[DataStructRepl] Found safe node in %s:%s, id: %s \nClass: %s",
                                    graph.method().getDeclaringClass().getName(),
                                    graph.method().getName(),
                                    n.toString(Verbosity.Id),
                                    nin.instanceClass().toString()));
                        }*/
                        interestNodesIds.add(nin.toString(Verbosity.Id));
                    }
                }
            }
        }
        checkInstanceSafety(graph);

        //Appends profiling tools to candidate nodes deemed safe

        appendProfilerInfo(graph);
    }

}