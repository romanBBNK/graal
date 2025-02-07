#!/bin/bash

JAVA_HOME=/home/romanb/MEGAsync/MSc/Thesis/Code/graalvm-ce-java17-21.3.0
GRAALVM_HOME=~/MEGAsync/MSc/Thesis/Code/graal/vm/latest_graalvm_home

#ENV=ni-ce
ENV=svm

function build_graal {
        mx --java-home $JAVA_HOME -p vm --env $ENV clean --all
        mx --java-home $JAVA_HOME -p vm --env $ENV build
}

function run_benchmark {
	# Run the NI profiler which counts the number of allocated maps and the number of map entries.
	mx --java-home $JAVA_HOME -p vm --env $ENV benchmark shopcart-wrk:mixed-small  -- --jvm=native-image -Dnative-image.benchmark.stages=image,run | tee ni-shopcart-wrk-mixed-small.log
	mx --java-home $JAVA_HOME -p vm --env $ENV benchmark petclinic-wrk:mixed-small -- --jvm=native-image -Dnative-image.benchmark.stages=image,run | tee ni-petclinic-wrk-mixed-small.log
	# Run the JVMCI profiler which periodically counts the maps in the heap.
	mx --java-home $JAVA_HOME -p vm --env $ENV benchmark shopcart-wrk:mixed-small  -- -agentpath:object_demographics/liboiprofiler.so | tee hotspot-shopcart-wrk-mixed-small.log
	mv profile_output.txt hotspot-shopcart-wrk-mixed-small.profile
	mx --java-home $JAVA_HOME -p vm --env $ENV benchmark petclinic-wrk:mixed-small  -- -agentpath:object_demographics/liboiprofiler.so | tee hotspot-petclinic-wrk-mixed-small.log
	mv profile_output.txt hotspot-petclinic-wrk-mixed-small.profile
}

function build_image {
	SHOPCART_JAR=/home/rbruno/.mx/cache/SHOPCART_0.3.5_da961b7b81c161fda51ac1939a983cbfc95a5b28/shopcart-0.3.5.extracted/shopcart-0.3.5/shopcart-0.3.5-all.jar
#	IGV="$IGV,io.micronaut.discovery.cloud.ComputeInstanceMetadataResolverUtils.populateMetadata"
#	IGV="$IGV,io.micronaut.aop.chain.InterceptorChain.resolveInterceptorsInternal"
#	IGV="$IGV,io.micronaut.core.beans.ReflectionBeanMap.containsKey"
#	IGV="$IGV -H:Dump=:2 -H:-PrintGraphFile -H:MethodFilter=$methodfilter"
	$GRAALVM_HOME/bin/native-image $IGV -H:Name=shopcart-0.3.5-all -jar $SHOPCART_JAR 2>&1 | tee image.log
}

build_graal
#build_image
#run_benchmark
#beep