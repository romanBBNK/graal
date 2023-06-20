# GraalVM Ideal Graph Visualizer (IGV)

## Prerequisites

- GraalVM

To download GraalVM Community Edition (CE) or Enterprise Edition (EE) see our [website](www.graalvm.org).

- Or JDK 8, JDK 10 can be used as Java runtime platform for IGV.

## Settings

- During IGV first run, you will be prompted to download and install JavaScript dependencies for IGV's Scripting shell.
 - Without JavaScript modules the Scripting shell will have less fetures (highlighting will be missing among other features).
- It is recomended to run IGV on GraalVM.
 - To run with GraalVM use flag `--jdkhome <path>` or setup your `PATH` environment variable to point to GraalVM installation directory.
  - This will enable the use of Scripting shell with GraalVM scripting languages.

## Running IGV

### Linux

Run `>$ idealgraphvisualizer/bin/idealgraphvisualizer`

### MacOS

Run `>$ idealgraphvisualizer/bin/idealgraphvisualizer`

### Windows

Execute `idealgraphvisualizer/bin/idealgraphvisualizer64.exe` or `idealgraphvisualizer/bin/idealgraphvisualizer.exe`

### Command Line Options
- `--jdkhome <path>` sets path to jdk to run IGV with (IGV runtime Java platform).
- `--open <file>` or `<file>` (*.bgv) opens specified file immediately after IGV is started.

## Additional Informations
- IGV won't show GraalVM EE compilation phases graphs. Graphs specific to GraalVM EE are not supported in this IGV distribution. 