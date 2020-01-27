![](https://github.com/voutilad/paysim-demo/workflows/Java%20CI/badge.svg)

# Modeling 1st & 3rd Party Fraud with PaySim
More documentation details are coming...for now this is bare bones. If you need more background, see the archived original demo writeup at https://github.com/voutilad/paysim-demo-clj for more details.
## Requirements
To build and run this demo, you'll need:
* [Java 8 or 11 JDK](https://adoptopenjdk.net) (development is being done in Java 11)
* [Neo4j](https://neo4j.com/download) v3.5 (community or enterprise)

> Known caveats: configurable Neo4j targets aren't yet supported, so it assumes it can reach a database on localhost at the default Bolt port with username `neo4j` and password `password`.

### Building
This project uses [Gradle](https://gradle.org/) and should work with your favorite Java IDE (if so desired) or you can just use the provided gradle wrapper scripts.

On Linux/*BSD/macOS:
```shell script
$ ./gradlew build
```

On Windows:
```shell script
> gradlew.bat build
```

### Running
I haven't implemented an easily portable uberjar build task yet, so the easiest way to run is via the `run` task:

```shell script
$ ./gradlew run
```

## License
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