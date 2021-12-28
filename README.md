# Project Loom Lab

Experiments with Project Loom's features based on these JEP(draft)s:

* [Structured Concurrency](https://openjdk.java.net/jeps/8277129)
* [Virtual Threads](https://openjdk.java.net/jeps/8277131)

## Experiments

For these experiments, you need a current [Project Loom EA build](https://jdk.java.net/loom/) and Maven.

If you have SDKMan installed you can install the java version with `lm` in its name, i.e. `sdk install java 19.ea.1.lm-open`.

To run it:

```
jbang $EXPERIMENT.java $ARGUMENTS
```

Where:

* `$EXPERIMENT` selects one of the experiments by name
* `$ARGUMENTS` configures the experiment

Example:
```
jbang DiskStats.java VIRTUAL .
```

For details on these, see specific experiments below.

### Disk Stats

Walks over all folders and files in a given directory to gather their respective sizes.
Can be configured to either run as a single thread or with one virtual thread for each file/folder.

* name: `DiskStats`
* arguments: see [`DiskStats.java`](DiskStats.java)

### Echo Client & Server

A client and server that exchange messages via sockets on localhost:8080.
Client protocol:

* sends a single line, terminated by a newline
* waits for a single line (i.e. a string terminated by a newline) to be received

Server protocol:

* reads a single line (i.e. a string terminated by a newline) from that socket
* waits a predetermined amount of time
* replies with the same string, including the newline

To try this out, run the client and the server in different shells.

* server
    * name: `EchoServer`
    * arguments: see [`Echo.java`.](Echo.java)
* client
    * name: `EchoClient`
    * arguments: see [`Send.java`.](Send.java), 

## Edit 

To edit one of the examples in an IDE use `jbang edit`, i.e. `jbang edit Echo.java`.

