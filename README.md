[![Travis build status](https://travis-ci.org/mike10004/chrome-cookie-implant.svg?branch=master)](https://travis-ci.org/mike10004/chrome-cookie-implant)
[![AppVeyor build status](https://ci.appveyor.com/api/projects/status/tfhj96elsi8ytf82?svg=true)](https://ci.appveyor.com/project/mike10004/har-replay)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.mike10004/har-replay.svg)](https://repo1.maven.org/maven2/com/github/mike10004/har-replay/)

har-replay
==========

Java library and executable for serving recorded HTTP responses from a HAR 
file. To use it, you construct a server from a HAR file and configure your 
web browser to use the server as an HTTP proxy. The proxy intercepts each 
request and responds with the corresponding pre-recorded response from the 
HAR.

Two implementations are available, one that is pure Java and one that uses
a Node module under the hood.

Quick Start
-----------

### Executable

If you have a HAR file handy, build the `har-replay-exec` module and run the
executable jar: 

    java -jar har-replay-exec.jar --port 56789 /path/to/my.har

That starts an HTTP proxy on port 56789 serving responses from `/path/to/my.har`.

### Library

Maven dependency:

    <dependency>
        <groupId>com.github.mike10004</groupId>
        <artifactId>har-replay-vhs</artifactId> <!-- or har-replay-node -->
        <version>0.12</version>
    </dependency>

See Maven badge above for the actual latest version.

    File harFile = new File("my-session.har");
    ReplaySessionConfig sessionConfig = ReplaySessionConfig.usingTempDir().build(harFile);
    VhsReplayManagerConfig config = VhsReplayManagerConfig.getDefault();
    ReplayManager replayManager = new VhsReplayManager(config);
    try (ReplaySessionControl sessionControl = replayManager.start(sessionConfig)) {
        String proxySocketAddress = "localhost:" + sessionControl.getListeningPort();
        URL url = new URL("http://www.example.com/");
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
        HttpURLConnection conn = (HttpURLConnection) url.openConnection(proxy);
        try {
            System.out.format("served from HAR: %s %s%n", conn.getResponseCode(), conn.getResponseMessage());
            // do something with the connection...
        } finally {
            conn.disconnect();
        }
    }

The unit tests contain some examples of using the library with an Apache HTTP 
client and a Chrome WebDriver client. 

FAQ
---

### How do I create a HAR?

You can use the DevTools in Chrome. See this unofficial tech support posting:
[Generating a HAR file for troubleshooting][har-howto]. Another option is to
use [browsermob-proxy](https://github.com/lightbody/browsermob-proxy) to 
capture a HAR.

### What's the difference between har-replay-vhs and har-replay-node?

They are both implementations of a HAR replay manager, but **har-replay-vhs**
(*vhs* stands for Virtual HAR Server) is a pure Java implementation based on
[virtual-har-server] and **har-replay-node** is a JavaScript implementation 
based on the [har-replay-proxy] Node module.

It is likely that support for the **har-replay-node** implementation will be 
discontinued in the future, so you should probably prefer the pure Java 
implementation.   

Debugging Travis Builds
-----------------------

If the Travis build is failing, you can test locally with Docker by running 
`./travis-debug.sh`. However, the `mvn verify` command appears to exit early 
but does not report a nonzero exit code, so it's not clear whether it's 
actually succeeding or something funky is going on. It should be useful for 
debugging failures that happen earlier, though. If the failure you see on 
Travis happens later on in `mvn verify`, you can follow the Travis
[Troubleshooting in a local container][troubleshooting] instructions, which 
say to execute:

    $ docker run --name travis-debug -dit $TRAVIS_IMAGE /sbin/init
    $ docker exec -it travis-debug bash -l 

This puts you inside the container, where you can `su -l travis`, clone the 
repo, and proceed manually from there.

[har-replay-proxy]: https://github.com/mike10004/har-replay-proxy
[switcheroo]: https://chrome.google.com/webstore/detail/switcheroo-redirector/cnmciclhnghalnpfhhleggldniplelbg
[har-howto]: https://support.zendesk.com/hc/en-us/articles/204410413-Generating-a-HAR-file-for-troubleshooting
[troubleshooting]: https://docs.travis-ci.com/user/common-build-problems/
[virtual-har-server]: https://github.com/mike10004/virtual-har-server