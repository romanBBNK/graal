package com.oracle.svm.hosted.datastructrepl;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.phases.tiers.Suites;
import org.graalvm.compiler.phases.util.Providers;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.graal.GraalFeature;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;

@AutomaticFeature
public class DataStructReplFeature implements GraalFeature {

    public static class Options {
        @Option(help = "Enable data structure scanner.")//
        public static final HostedOptionKey<Boolean> DataStructScanner = new HostedOptionKey<>(true);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {
        BeforeAnalysisAccessImpl access = (BeforeAnalysisAccessImpl) a;

        if (!Options.DataStructScanner.getValue()) {
            return;
        }
    }

    @Override
    public void registerGraalPhases(Providers providers, SnippetReflectionProvider snippetReflection, Suites suites, boolean hosted) {
        if (Options.DataStructScanner.getValue()) {
            //suites.getHighTier().prependPhase(new DataStructReplPhase());
            suites.getHighTier().appendPhase(new DataStructReplPhase());
        }
    }

}