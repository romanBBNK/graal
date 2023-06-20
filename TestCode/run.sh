# Function declarations----------------------------------------------------------------------------------
function cleanFunction {
  rm -f *.build_artifacts.txt;
  rm -f *.class;
  rm -f output*.txt;
  rm -f helloworld;
  rm -f dangerclass;
  rm -rf ProfilingData;
}

function cleanAllFunction {
  cleanFunction;
  rm -r graal_dumps/*;
}

function igvFunction {
  /home/romanb/MEGAsync/MSc/Thesis/Code/idealgraphvisualizer/bin/idealgraphvisualizer &
}

function buildNativeImageFunction {
  cd /home/romanb/MEGAsync/MSc/Thesis/Code/graal/substratevm && mx build;
}


# Runnable commands----------------------------------------------------------------------------------

clean() {
  cleanFunction;
}
cleanAll() {
  cleanAllFunction;
}

igv() {
  igvFunction;
}
buildNativeImage() {
  buildNativeImageFunction;
}

# End to end execution with all steps
fullExecution() {
  cleanFunction;
  javac "$1".java;
  echo "Running first native image compilation";
  /home/romanb/MEGAsync/MSc/Thesis/Code/graal/sdk/latest_graalvm_home/bin/native-image "$1";
  echo "Running first execution";
  ./"$2" "$3";
  echo "Running second native image compilation";
  /home/romanb/MEGAsync/MSc/Thesis/Code/graal/sdk/latest_graalvm_home/bin/native-image -H:-DataStructScannerHook "$1";
  echo "Running second execution";
  ./"$2" "$3";
}
fullExecutionWithMassif() {
  cleanFunction;
  javac "$1".java;
  echo "Running first native image compilation";
  /home/romanb/MEGAsync/MSc/Thesis/Code/graal/sdk/latest_graalvm_home/bin/native-image "$1";
  echo "Running first execution";
  valgrind --tool=massif ./"$2";
  echo "Running second native image compilation";
  /home/romanb/MEGAsync/MSc/Thesis/Code/graal/sdk/latest_graalvm_home/bin/native-image -H:-DataStructScannerHook "$1";
  echo "Running second execution";
  valgrind --tool=massif ./"$2";
}
fullExecutionWithTsTime() {
  cleanFunction;
  javac "$1".java;
  echo "Running first native image compilation";
  /home/romanb/MEGAsync/MSc/Thesis/Code/graal/sdk/latest_graalvm_home/bin/native-image "$1";
  echo "Running first execution";
  sudo /home/romanb/MEGAsync/MSc/Thesis/Code/TestCode/tstime ./"$2" "$3" > output_"$2".txt;
  sudo chmod 777 ProfilingData;
  echo "Running second native image compilation";
  /home/romanb/MEGAsync/MSc/Thesis/Code/graal/sdk/latest_graalvm_home/bin/native-image -H:-DataStructScannerHook "$1";
  echo "Running second execution";
  sudo /home/romanb/MEGAsync/MSc/Thesis/Code/TestCode/tstime ./"$2" "$3" > output2_"$2".txt;
}

performanceTest() { #Runs vanilla execution to get base execution value, then performs optimization and runs again
  cleanFunction;
  javac "$1".java;
  #echo "Running vanilla compilation";
  #/home/romanb/MEGAsync/MSc/Thesis/Code/graal/sdk/latest_graalvm_home/bin/native-image -H:-DataStructScannerHook -H:-DataStructScanner -H:-DataStructScannerProfiler "$1";
  echo "Running vanilla execution";
  #sudo /home/romanb/MEGAsync/MSc/Thesis/Code/TestCode/tstime ./"$2"_vanilla "$3" > output1_"$2".txt;
  sudo /home/romanb/MEGAsync/MSc/Thesis/Code/TestCode/tstime ./"$2"_vanilla "$3";
  echo "Running first modified compilation";
  /home/romanb/MEGAsync/MSc/Thesis/Code/graal/sdk/latest_graalvm_home/bin/native-image "$1";
  echo "Running profiled execution";
  ./"$2" "$3" > output0_"$2".txt;
  echo "Running second modified compilation";
  /home/romanb/MEGAsync/MSc/Thesis/Code/graal/sdk/latest_graalvm_home/bin/native-image -H:-DataStructScannerHook "$1";
  echo "Running optimized execution";
  #sudo /home/romanb/MEGAsync/MSc/Thesis/Code/TestCode/tstime ./"$2" "$3" > output2_"$2".txt;
  sudo /home/romanb/MEGAsync/MSc/Thesis/Code/TestCode/tstime ./"$2" "$3";
}


# Native Image build - NO IGV Dump
nativeImageVanilla() {
  # Runs Native Image with all of the dataStruct features off
  /home/romanb/MEGAsync/MSc/Thesis/Code/graal/sdk/latest_graalvm_home/bin/native-image -H:-DataStructScannerHook -H:-DataStructScanner -H:-DataStructScannerProfiler "$1";
}
nativeImageFirstRun() {
  # First run, analyzes and adds profiling instructions
  export DataStructScannerPackage="$2";
  /home/romanb/MEGAsync/MSc/Thesis/Code/graal/sdk/latest_graalvm_home/bin/native-image "$1";
}
nativeImageSecondRun() {
  # Second run, performs replacement
    export DataStructScannerPackage="$2";
  /home/romanb/MEGAsync/MSc/Thesis/Code/graal/sdk/latest_graalvm_home/bin/native-image -H:-DataStructScannerHook "$1";
}

# Native Image JAR build - NO IGV Dump
nativeImageVanillaJar() {
  # Runs Native Image with all of the dataStruct features off
  /home/romanb/MEGAsync/MSc/Thesis/Code/graal/sdk/latest_graalvm_home/bin/native-image -H:-DataStructScannerHook -H:-DataStructScanner -H:-DataStructScannerProfiler -jar "$1";
}
nativeImageFirstRunJar() {
  # First run, analyzes and adds profiling instructions
  /home/romanb/MEGAsync/MSc/Thesis/Code/graal/sdk/latest_graalvm_home/bin/native-image -H:DataStructScannerPackage=:"$1" -jar "$2";
}
nativeImageSecondRunJar() {
  # Second run, performs replacement
  /home/romanb/MEGAsync/MSc/Thesis/Code/graal/sdk/latest_graalvm_home/bin/native-image -H:-DataStructScannerHook -H:DataStructScannerPackage="$2" -jar "$1";
}


# Native Image build - WITH IGV Dump
igvNativeImageVanilla() {
  # Runs Native Image with all of the dataStruct features off
  /home/romanb/MEGAsync/MSc/Thesis/Code/graal/sdk/latest_graalvm_home/bin/native-image -H:Dump=:2 -H:-PrintGraphFile -H:MethodFilter=main -H:-DataStructScannerHook -H:-DataStructScanner -H:-DataStructScannerProfiler "$1";
}
igvNativeImageFirstRun() {
  # First run, analyzes and adds profiling instructions
  /home/romanb/MEGAsync/MSc/Thesis/Code/graal/sdk/latest_graalvm_home/bin/native-image -H:Dump=:2 -H:-PrintGraphFile -H:MethodFilter=main -H:DataStructScannerPackage=HelloWorld "$1";
}
igvNativeImageSecondRun() {
  # Second run, performs replacement
  /home/romanb/MEGAsync/MSc/Thesis/Code/graal/sdk/latest_graalvm_home/bin/native-image -H:Dump=:2 -H:-PrintGraphFile -H:MethodFilter=main -H:-DataStructScannerHook "$1";
}

# Execution------------------------------------------------------------------------------------------
# Check if the function exists (bash specific)
if declare -f "$1" > /dev/null
then
  # call arguments verbatim
  "$@"
else
  # Show a helpful error
  echo "'$1' is not a known function name" >&2
  exit 1
fi

#HOW TO USE
#EXPORT THE PACKAGE/MAIN CLASS NAME TO THE SHELL AS AN ENVIRONMENT VARIABLE BEFORE DOING ANYTHING ELSE
#This may be the full package name, or part of one to allow optimization to affect multiple classes/subpackages.
#export DataStructScannerPackage=<packageName>
#export DataStructScannerPackage=HelloWorld

#PERFORMANCE TEST:
#/home/romanb/MEGAsync/MSc/Thesis/Code/TestCode/run.sh performanceTest <mainClass.class> <expectedExecutableName> <input>
#/home/romanb/MEGAsync/MSc/Thesis/Code/TestCode/run.sh performanceTest HelloWorld helloworld 786432
#
#INDIVIDUAL COMPILE
#FIRST RUN:
#/home/romanb/MEGAsync/MSc/Thesis/Code/TestCode/run.sh nativeImageFirstRun <mainClass.class> <packageName>
#SECOND RUN:
#/home/romanb/MEGAsync/MSc/Thesis/Code/TestCode/run.sh nativeImageSecondRun <mainClass.class> <packageName>
#
#JAR INDIVIDUAL
#FIRST:
#/home/romanb/MEGAsync/MSc/Thesis/Code/TestCode/run.sh nativeImageFirstRunJar <mainClass.class> <packageName>
# SECOND:
#/home/romanb/MEGAsync/MSc/Thesis/Code/TestCode/run.sh nativeImageSecondRunJar <mainClass.class> <packageName>
#
#
#
#
