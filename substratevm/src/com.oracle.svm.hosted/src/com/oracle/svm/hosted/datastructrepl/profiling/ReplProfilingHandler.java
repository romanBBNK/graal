package com.oracle.svm.hosted.datastructrepl.profiling;

import com.oracle.svm.hosted.datastructrepl.DataStructProfilerSnippets;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.compiler.nodes.BeginNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.java.NewInstanceNode;

import java.util.ArrayList;
import java.util.Objects;
import java.util.regex.Pattern;

public class ReplProfilingHandler {

    public void appendProfilerInfo(StructuredGraph graph, ArrayList<String> safeNodesIds, String packageNameRegex){

        String packageAndMethod = graph.method().getDeclaringClass().getName()
                + graph.method().getName();
        int packageAndMethodHash = packageAndMethod.hashCode();

        for (Node n : graph.getNodes()){
            if (Pattern.matches(packageNameRegex, graph.method().getDeclaringClass().getName())) {

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
                    String targetId;
                    if(in.callTarget().arguments().size() >= 1)
                        targetId = in.callTarget().arguments().get(0).toString(Verbosity.Id);
                    else
                        targetId = "0";

                    if (safeNodesIds.contains(targetId)) {
                        //TODO: Other methods to profile:
                        //compute (what does it do?), putAll (need to check args), putIfAbsent (get result of check?), remove(k, v)(how to check result?)
                        //TODO: Get HashMap object/node to pass to profiler code
                        if (in.callTarget().targetMethod().getName().equals("put")) {
                            NewInstanceNode nin = (NewInstanceNode) graph.getNode(Integer.parseInt(targetId));
                            ValueNode[] args = new ValueNode[]{nin,
                                    ConstantNode.forInt(Integer.parseInt(targetId), graph),
                                    ConstantNode.forInt(packageAndMethodHash, graph)};
                            ForeignCallNode fcn = graph.add(new ForeignCallNode(DataStructProfilerSnippets.PROFILE_MAP_PUT, args));
                            if(in.successors().first() instanceof BeginNode){
                                BeginNode bn = (BeginNode) in.successors().first(); //Appends to begin, not the exception
                                graph.addAfterFixed(bn, fcn); //Must be added after the method call to profile the map after it is modified
                                //Needs to be after 'Begin' node because Call node isn't FixedWithNext as is needed
                            }
                        }
                        else if (in.callTarget().targetMethod().getName().equals("clear")){
                            ValueNode[] args = new ValueNode[]{ConstantNode.forInt(Integer.parseInt(targetId), graph),
                                    ConstantNode.forInt(packageAndMethodHash, graph)};
                            ForeignCallNode fcn = graph.add(new ForeignCallNode(DataStructProfilerSnippets.PROFILE_MAP_CLEAR, args));
                            graph.addBeforeFixed(in, fcn);
                        }
                        else if (in.callTarget().targetMethod().getName().equals("remove")){
                            NewInstanceNode nin = (NewInstanceNode) graph.getNode(Integer.parseInt(targetId));
                            ValueNode[] args = new ValueNode[]{nin, ConstantNode.forInt(Integer.parseInt(targetId), graph),
                                    ConstantNode.forInt(packageAndMethodHash, graph)};
                            ForeignCallNode fcn = graph.add(new ForeignCallNode(DataStructProfilerSnippets.PROFILE_MAP_REMOVE, args));
                            if(in.successors().first() instanceof BeginNode){
                                BeginNode bn = (BeginNode) in.successors().first();
                                graph.addAfterFixed(bn, fcn);
                            }
                        }
                        else if (in.callTarget().targetMethod().getName().equals("<init>")){
                            if (in.callTarget().arguments().size() == 3
                                    && in.callTarget().arguments().get(1).isConstant()
                                    && in.callTarget().arguments().get(2).isConstant()) {
                                System.out.println("Adding profiling instructions to init");
                                    int initialCap = Objects.requireNonNull(in.callTarget().arguments().get(1).asJavaConstant()).asInt();
                                    float initialFactor = Objects.requireNonNull(in.callTarget().arguments().get(2).asJavaConstant()).asFloat();
                                    ValueNode[] args = new ValueNode[]{ConstantNode.forInt(Integer.parseInt(targetId), graph),
                                            ConstantNode.forInt(packageAndMethodHash, graph),
                                            ConstantNode.forInt(initialCap, graph),
                                            ConstantNode.forFloat(initialFactor, graph)};
                                    ForeignCallNode fcn = graph.add(new ForeignCallNode(DataStructProfilerSnippets.PROFILE_MAP_INIT, args));
                                    graph.addBeforeFixed(in, fcn);
                            }
                        }
                        else {
                            NewInstanceNode nin = (NewInstanceNode) graph.getNode(Integer.parseInt(targetId));
                            ValueNode[] args = new ValueNode[]{nin, ConstantNode.forInt(Integer.parseInt(targetId), graph),
                                    ConstantNode.forInt(packageAndMethodHash, graph)};
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
}
