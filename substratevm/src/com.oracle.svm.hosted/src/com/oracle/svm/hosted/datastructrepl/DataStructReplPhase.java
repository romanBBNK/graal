package com.oracle.svm.hosted.datastructrepl;

import com.oracle.svm.hosted.datastructrepl.analysis.ReplAnalysisHandler;
import com.oracle.svm.hosted.datastructrepl.profiling.ReplProfilingHandler;
import com.oracle.svm.hosted.datastructrepl.replacement.ReplacementHandler;
import com.oracle.svm.hosted.datastructrepl.utils.ReplFileHandler;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.phases.Phase;

import java.io.File;
import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Pattern;

public class DataStructReplPhase extends Phase {

    private final ReplFileHandler replFileHandler = new ReplFileHandler();
    private final ReplAnalysisHandler replAnalysisHandler = new ReplAnalysisHandler();
    private final ReplProfilingHandler replProfilingHandler = new ReplProfilingHandler();
    private final ReplacementHandler replacementHandler = new ReplacementHandler();
    private String packageName;

    @Override
    protected void run(StructuredGraph graph){

        packageName = System.getenv("DataStructScannerPackage");

        String dataPath = "ProfilingData/";
        dataPath = dataPath.concat("DataStructProfilingInfo.txt");
        File instancesFile = new File(dataPath);

        /*System.out.println("DataStructTuning: Started main phase for method " +
                graph.method().getDeclaringClass().getName() + " in package " +
                graph.method().getDeclaringClass().getName() + ", attempting to match against package name " +
                packageName);*/
        if (Pattern.matches(replFileHandler.generateRegexPackageName(packageName),
                graph.method().getDeclaringClass().getName())) {
            System.out.println("Pattern matches " + graph.method().getDeclaringClass().getName());

            //Check whether profiling data exists.
            if (!instancesFile.exists()) {
                //No profiling data. Will try to find replacement candidates and append profiling instructions
                System.out.println("No profiling data to use for replacement. Appending profiling instructions.");
                ArrayList<String> safeNodesIds = replAnalysisHandler.findCandidateNodes(graph,
                        replFileHandler.generateRegexPackageName(packageName));
                replProfilingHandler.appendProfilerInfo(graph, safeNodesIds,
                        replFileHandler.generateRegexPackageName(packageName));

            } else {
                System.out.println("Profiling data found. Replacing.");
                Map<Integer, ArrayList<DataStructProfilerSnippets.MapObject>> profiledMethods = replFileHandler.readProfilingData(instancesFile);

                //Replacement main algorithm
                replacementHandler.structureReplacement(graph, profiledMethods);

                //DEBUG: Append profiling instructions to replaced/tuned nodes
                /*ArrayList<String> safeNodesIds = replAnalysisHandler.findCandidateNodes(graph,
                        replFileHandler.generateRegexPackageName(packageName));
                replProfilingHandler.appendProfilerInfo(graph, safeNodesIds,
                        replFileHandler.generateRegexPackageName(packageName));*/
            }
        }
    }

}