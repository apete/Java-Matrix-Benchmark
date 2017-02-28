/*
 * Copyright (c) 2009-2015, Peter Abeles. All Rights Reserved.
 *
 * This file is part of JMatrixBenchmark.
 *
 * JMatrixBenchmark is free software: you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * JMatrixBenchmark is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with JMatrixBenchmark.  If not, see <http://www.gnu.org/licenses/>.
 */

package jmbench.tools.runtime;

import jmbench.impl.LibraryDescription;
import jmbench.impl.LibraryManager;
import jmbench.tools.MiscTools;
import jmbench.tools.SystemInfo;
import jmbench.tools.stability.UtilXmlSerialization;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;


/**
 * <p>
 * RuntimeBenchmarkMaster runs a series of benchmark tests against a set of linear algebra libraries.
 * These libraries are evaluated using {@link RuntimeBenchmarkLibrary}.  The results are
 * put into a directory that is in the results directory whose name is set to the integer value
 * returned by System.currentTimeMillis().  In addition information on the system the test was run on
 * and error logs are all saved.
 * </p>
 * 
 * @author Peter Abeles
 */
public class RuntimeBenchmark {

    // where should the results be saved to
    private String directorySave;

    public RuntimeBenchmark() {
        directorySave = MiscTools.selectDirectoryName("runtime");
    }

    public RuntimeBenchmark(String directory ) {
        this.directorySave = directory;
    }

    /**
     * Perform the benchmark tests against all the different algortihms
     */
    public void performBenchmark( RuntimeBenchmarkConfig config ) {

        saveSystemInfo(config);

        long startTime = System.currentTimeMillis();
        processLibraries(config.getTargets(),config);

        System.out.println("Elapsed time "+MiscTools.milliToHuman(System.currentTimeMillis()-startTime)+"\n");
    }

    private void processLibraries( List<LibraryDescription> libs, RuntimeBenchmarkConfig config ) {


        for( LibraryDescription desc : libs ) {

            String libOutputDir = directorySave+"/"+desc.info.nameShort;

            // save the description so that where this came from can be easily extracted
            String outputFile = libOutputDir+".xml";
            UtilXmlSerialization.serializeXml(desc,outputFile);

            RuntimeBenchmarkLibrary benchmark = new RuntimeBenchmarkLibrary(libOutputDir,desc,config);

            try {
                benchmark.performBenchmark();
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Collects information of the system that this is being run on.  Allows for a better understanding
     * of the results.  Not all relevant information can be gathered since this is java.
     *
     * The benchmark config is also saved here.
     */
    private void saveSystemInfo(RuntimeBenchmarkConfig config) {
        SystemInfo info = new SystemInfo();
        info.grabCurrentInfo();

        File dir = new File(directorySave);
        if( !dir.exists() ) {
            if( !dir.mkdirs() ) {
                throw new IllegalArgumentException("Can't make directories to save results.");
            }
        }

        UtilXmlSerialization.serializeXml(info,directorySave+"/info.xml");
        UtilXmlSerialization.serializeXml(config,directorySave+"/config.xml");
    }

    public static void printHelp() {
        System.out.println("The following options are valid for runtime benchmark:");
        System.out.println("  --Config=<file>          |  Configure using the specified xml file.");
        System.out.println("  --Size=min:max           |  Test matrices from the specified minimum size to the specified maximum size.");
        System.out.println("  --Quick                  |  Generate results much faster by sacrificing accuracy/stability of the results.");
        System.out.println("  --Library=<lib>          |  To run a specific library only.  --Library=? will print a list");
        System.out.println("                           |  Use a comma to specify multiple libraries, e.g. 'ejml,ojalgo'");
        System.out.println("  --Seed=<number>          |  used to set the random seed to the specified value.");
        System.out.println("  --TrailTime=<ms>         |  The minimum amount of time spent in each trial.  Typical is 3000 ms.");
        System.out.println("  --MaxTime=<ms>           |  "+MiscTools.stringTimeArgumentHelp());
        System.out.println("  --Resume=<directory>     |  It will resume an unfinished benchmark at the specified directory.");
        System.out.println("  --Memory=<MB>            |  Sets the amount of memory allocated to java for each trial in megabytes.  This number should be");
        System.out.println("                           |  as large as possible with out exceeding the amount of physical memory on the system.");
        System.out.println("                           |  specified since the dynamic algorithm will slow down the benchmark and has some known issues.");
        System.out.println();
        System.out.println("The only option which must be specified is \"FixedMemory\".  If no other options are specified " +
                "then a default configuration will be used and the results" +
                "will be saved to a directory in results with the name of the current system time in milliseconds.");
        System.out.println();
        System.out.println("Example: java -jar benchmark.jar runtime --Size=2:40000 --MaxTime=10m --Memory=25600");
    }

    public static void main( String args[] ) throws IOException, InterruptedException {
        if( args.length == 0 ) {
            printHelp();
            System.exit(0);
        }

        LibraryManager manager = new LibraryManager();

        boolean memorySpecified = false;
        boolean failed = false;
        boolean configFileSpecified = false;

        RuntimeBenchmarkConfig config = RuntimeBenchmarkConfig.createAllConfig(manager.getDefaults());

        System.out.println("** Parsing Command Line **");
        System.out.println();
        for( int i = 0; i < args.length; i++ ) {
            String splits[] = args[i].split("=");

            String flag = splits[0];

            if( flag.length() < 2 || flag.charAt(0) != '-' || flag.charAt(0) != '-') {
                failed = true;
                break;
            }

            flag = flag.substring(2);

            if( flag.compareTo("Config") == 0 ) {
                if( splits.length != 2 ) {failed = true; break;}
                configFileSpecified = true;
                System.out.println("Loading config: "+splits[1]);
                config = UtilXmlSerialization.deserializeXml(splits[1]);
            } else if( flag.compareTo("Size") == 0 ) {
                if( splits.length != 2 ) {failed = true; break;}
                String rangeStr[] = splits[1].split(":");
                if( rangeStr.length != 2 ) {failed = true; break;}
                config.minMatrixSize = Integer.parseInt(rangeStr[0]);
                config.maxMatrixSize = Integer.parseInt(rangeStr[1]);
                System.out.println("Set min/max matrix size to: "+config.minMatrixSize+" "+config.maxMatrixSize);
            } else if( flag.compareTo("Quick") == 0 ) {
                if( i != 0 ) {
                    System.out.println("quick must be the first argument specified.");
                    failed = true; break;
                }
                if( splits.length != 1 ) {failed = true; break;}
                config.totalTests = 2;
                config.numTestsPerBlock = 2;
                config.minimumTimePerTestMS = 1000;
                System.out.println("Using quick and dirty config.");
            } else if( flag.compareTo("Library") == 0 ) {
                if( splits.length != 2 ) {failed = true; break;}
                String[] libs = splits[1].split(",");

                config.targets.clear();

                for (int j = 0; j < libs.length; j++) {
                    LibraryDescription match = manager.lookup(libs[j]);
                    if( match == null ) {
                        failed = true;
                        manager.printAllNames();
                        break;
                    }
                    config.targets.add(match);
                }
            } else if( flag.compareTo("Seed") == 0 ) {
                if( splits.length != 2 ) {failed = true; break;}
                config.seed = Long.parseLong(splits[1]);
                System.out.println("Random seed set to "+config.seed);
            } else if( flag.compareTo("TrailTime") == 0 ) {
                if( splits.length != 2 ) {failed = true; break;}
                config.minimumTimePerTestMS = Integer.parseInt(splits[1]);
                System.out.println("Time per trial set to "+config.minimumTimePerTestMS +" (ms).");
            } else if( flag.compareTo("MaxTime") == 0 ) {
                if( splits.length != 2 ) {failed = true; break;}
                config.maxTimePerTestMS = (int)MiscTools.parseTime(splits[1]);
                System.out.println("Max time per test set to "+config.maxTimePerTestMS +" (ms).");
            }else if( flag.compareTo("Resume") == 0 ) {
                if( splits.length != 2 || args.length != 1 ) {failed = true; break;}
                System.out.println("Resuming a benchmark in dir "+splits[1]);
                RuntimeBenchmark master = new RuntimeBenchmark(splits[1]);
                config = UtilXmlSerialization.deserializeXml(splits[1]+"/config.xml");
                master.performBenchmark(config);
                return;
            } else if( flag.compareTo("Memory") == 0 ) {
                if( splits.length != 2 ) {failed = true; break;}
                memorySpecified = true;
                config.memoryMB = Integer.parseInt(splits[1]);
                if( config.memoryMB <= 0 )
                    System.out.println("Memory must be set to a value greater than zero");
                else
                    System.out.println("Memory used in each test will be "+config.memoryMB +" (MB).");
            } else {
                System.out.println("Unknown flag: "+flag);
                printHelp();
                failed = true;
                break;
            }
        }
        System.out.println("\n** Done parsing command line **\n");
        System.out.println(" To safely quit the benchmark process 'q' and enter.");
        System.out.println();

        if( failed ) {
            System.out.println("Unable to parse command.  Try --Help");
        } else {
            if( !configFileSpecified && !memorySpecified ) {
                System.out.println("The amount of memory must be specified using \"--Memory=<MB>\"!");
            } else {
                RuntimeBenchmark master = new RuntimeBenchmark();
                master.performBenchmark(config);
            }
        }
    }
}
