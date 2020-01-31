![](https://github.com/voutilad/paysim-demo/workflows/Java%20CI/badge.svg)

# Modeling 1st & 3rd Party Fraud with PaySim 2.1
Create your own fraud network in your local Neo4j instance and explore a virtual mobile money network!

![](./paysim-2.1.0.png?raw=true)

## Requirements
To build and run this demo, you'll need:
* [Java 8 or 11 JDK](https://adoptopenjdk.net) (development is being done in Java 11)
* [Neo4j](https://neo4j.com/download) v3.5 (community or enterprise)

### Known Issues ⚠️
Before you get started, keep in mind the following caveats:
  * Large simulations generate a lot of Neo4j write transactions. I've done little tuning of batch sizing, so make sure to allocate at least 2GB of JVM heap space as there's no error handling in the loader currently.
  * Currently assumes your Neo4j 3.5 instance is running locally with:
    - bolt uri: `bolt://localhost:7687"
    - neo4j password is `password`
  * You may get `org.neo4j.driver.exceptions.TransientException`s spit out into the console...don't panic and just ignore those for now 😉 it's due to my sloppy coding.

## Building & Running 🛠
This project uses [Gradle](https://gradle.org/) and should work with your favorite Java IDE (if so desired) or you can just use the provided gradle wrapper scripts. They will download the appropriate version of Gradle for your platform and orchestrate things for you.

On Linux/*BSD/macOS:
```shell script
$ ./gradlew run
```

On Windows:
```shell script
> .\gradlew.bat run
```

Currently the project doesn't have a build task for packaging an uber jar or bundle of jars.

## Querying the Graph

...queries TBA :-)

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