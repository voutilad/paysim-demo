![](https://github.com/voutilad/paysim-demo/workflows/Java%20CI/badge.svg)

# Modeling 1st & 3rd Party Fraud with PaySim 2.x
Generate your own fraud network dataset with PaySim. Supports either directly building a Neo4j graph or dumping out representative CSV files for use in any system.

![](./paysim-2.1.0.png?raw=true)

## Requirements
To build and run this demo, you'll need:
* [Java 11 JDK](https://adoptopenjdk.net)
* [Neo4j](https://neo4j.com/download) v4.2 (or newer) if directly loading a graph

### Known Issues ⚠️
Before you get started, keep in mind the following caveats:
  * There's a `WARNING: An illegal reflective access operation has occurred` because of a 3rd party dependency in use

## Building & Running 🛠
This project uses [Gradle](https://gradle.org/) and should work with your favorite Java IDE (if so desired) or you can just use the provided gradle wrapper scripts. They will download the appropriate version of Gradle for your platform and orchestrate things for you.

### Quick & Dirty with Gradle Run 🏃
If you want to use all the following defaults, this is the easiest way to run the simulation.

#### Populating a local Neo4j database
Assuming:
- bolt uri: `neo4j://localhost:7687`
- neo4j admin user is `neo4j` and password is `password`

On Linux/*BSD/macOS:
```shell script
$ ./gradlew runBolt
```

On Windows:
```shell script
> .\gradlew.bat runBolt
```

#### Dumping out local CSV files
Assuming:
- an output directory of `./csv-output/`

On Linux/*BSD/macOS:
```shell script
$ ./gradlew runCsv
```

On Windows:
```shell script
> .\gradlew.bat runCsv
```

### Packaging to Run without Gradle
First, build a distribution.

On Linux/*BSD/macOS:
```shell script
$ ./gradlew distTar
```

On Windows:
```shell script
> .\gradlew.bat distZip
```

In `./build/distributions` you'll find either the `.tar` or `.zip` file. You can unpack the contents wherever you want to "install" the demo app.

```
burritogrande[paysim-demo-0.8.0]$ ls -alFh
total 8
drwxr-xr-x  6 dave  staff   192B Feb  3 09:22 ./
drwxr-xr-x  4 dave  staff   128B Feb  3 09:22 ../
-rw-r--r--  1 dave  staff   671B Feb  3 09:22 PaySim.properties
drwxr-xr-x  4 dave  staff   128B Feb  3 09:22 bin/
drwxr-xr-x  9 dave  staff   288B Feb  3 09:22 lib/
drwxr-xr-x  9 dave  staff   288B Feb  3 09:22 paramFiles/
```

Inside `bin/` you'll find a shell script and batch file for easily running the demo:

```
$ bin/paysim-demo -h
usage: paysim-demo [command] [args]
valid commands:
        bolt -- directly populate a remote database
         csv -- dump data out into local csv files
```

Each command has slightly different, but some common, optional arguments:

#### Bolt Mode
```
$ bin/paysim-demo bolt -h
usage: paysim-demo bolt [-h] [--properties PROPERTIES] [--uri URI]
                        [--username USERNAME] [--password PASSWORD]
                        [--tls TLS] [--batchSize BATCHSIZE]
                        [--queueDepth QUEUEDEPTH]

Builds a virtual mobile money network graph in CSV

named arguments:
  -h, --help             show this help message and exit
  --properties PROPERTIES
                         PaySim properties file  (with  paramFiles adjacent
                         in same dir) (default: PaySim.properties)
  --uri URI              [Bolt]  Bolt   URI   to   target   Neo4j  database
                         (default: bolt://localhost:7687)
  --username USERNAME    [Bolt] neo4j username (default: neo4j)
  --password PASSWORD    [Bolt] neo4j password (default: password)
  --tls TLS              [Bolt] Use a TLS Bolt connection? (default: false)
  --batchSize BATCHSIZE  transaction batch size (default: 5000)
  --queueDepth QUEUEDEPTH
                         PaySim queue depth (default: 50000)
```

#### CSV Mode
```
$ bin/paysim-demo csv -h
usage: paysim-demo csv [-h] [--properties PROPERTIES]
                       [--batchSize BATCHSIZE] [--queueDepth QUEUEDEPTH]
                       [--outputDir OUTPUTDIR]

Builds a virtual mobile money network graph in Neo4j

named arguments:
  -h, --help             show this help message and exit
  --properties PROPERTIES
                         PaySim properties file  (with  paramFiles adjacent
                         in same dir) (default: PaySim.properties)
  --batchSize BATCHSIZE  transaction batch size (default: 5000)
  --queueDepth QUEUEDEPTH
                         PaySim queue depth (default: 50000)
  --outputDir OUTPUTDIR  [Csv] Output directory (default: .)
```

## Querying the Graph
For some examples on how to interact with the PaySim graph in Neo4j, see [this post](https://www.sisu.io/posts/paysim-part3/) on my blog.

## License ⚖️
The demo code and any packaged releases are provided under the GPLv3 (see [LICENSE](./LICENSE)). Copyright for my contributions most likely should be attributed to my employer, Neo4j Inc.

This project requires 3rd party dependencies under various licenses, which may or may not be compatible with the GPLv3. As such, 
* [PaySim](https://github.com/voutilad/paysim)
  - GPLv3
  - E. A. Lopez-Rojas , A. Elmir, and S. Axelsson. "PaySim: A financial mobile money simulator for fraud detection". In: The 28th European Modeling and Simulation Symposium-EMSS, Larnaca, Cyprus. 2016
* [MASON](https://github.com/voutilad/mason)
  - Academic Free License v3 (with [exceptions](https://github.com/voutilad/mason/blob/master/LICENSE))
  - [According to the FSF](https://www.gnu.org/licenses/license-list.en.html#AcademicFreeLicense), the AFLv3 is *NOT* GPL compatible.
  - See the [MASON project homepage](https://cs.gmu.edu/~eclab/projects/mason/)
* [jFairy](https://github.com/Devskiller/jfairy)
  - Apache v2.0
  - See the [jFairy project homepage](https://devskiller.github.io/jfairy/)
  
Due to the unfortunate combination of the Academic Free License v3 and the GPLv3 (the original PaySim license), you cannot distribute a complete standalone build of this application and its dependencies (i.e. MASON).

> To overcome this, one would have to replace the MASON framework with something GPL compatible. 🤷‍