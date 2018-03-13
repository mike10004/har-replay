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
a Node module called [har-replay-proxy].

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
        <version>0.11</version>
    </dependency>

See Maven badge above for the actual latest version.

The unit tests contain some examples of using the library with an Apache HTTP 
client and a Chrome WebDriver client. 

Interrogatives
--------------

### How do I create a HAR?

You can use the DevTools in Chrome. See this unofficial tech support posting:
[Generating a HAR file for troubleshooting][har-howto]. 

### How does it handle HTTPS?

It doesn't, really, but there is an imperfect workaround. The server will match
HTTP requests to HTTPS entries in the HAR (for better or worse), so you only have
to make sure that on the client side you make HTTP requests instead of HTTPS 
requests. The proxy does not support TLS connections, so HTTPS requests will not
go through and will probably not even return an HTTP response, because they 
won't even make it to the replay request handler. So you have to intercept HTTPS 
requests and replace the protocol with `http://`.

If you're using Chrome, you can use the [Switcheroo extension][switcheroo].
If you're using Chrome through a WebDriver and can't perform the manual 
configuration required by that extension, you can use a modified version of
the extension included with this library. Check out the ModifiedSwitcheroo
class, which you can use to create a CRX file (Chrome extension file). You can
configure a `ChromeOptions` instance with that CRX file and pass it to the
`ChromeDriver` constructor, and your webdriver instance will be started with
the extension, which intercepts requests to HTTPS URLs and modifies the URL to
use HTTP instead. See the unit tests for an example of this.

Note that the extension will not rewrite URLs that are visited as a result of
redirects. To swap HTTPS for HTTP in those URLs, you have to add a response
header transform to the `ReplaySessionConfig` object.

Dependency on har-replay-proxy
-------------------------------

The current **har-replay-proxy** version is 0.0.1. If an update is available, then
the packaged zip in `src/main/resources` needs to be updated as well. To create
a new packaged zip:

* clone the **har-replay-proxy** repository, 
* run `npm install` in that directory so that its `node_modules` subdirectory 
  is populated,
* delete the `.git` folder from that cloned repo directory, and
* zip the folder so that the zip contains a `har-replay-proxy` directory at
  its root. 

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
