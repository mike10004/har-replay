[![Travis build status](https://img.shields.io/travis/mike10004/har-replay.svg)](https://travis-ci.org/mike10004/har-replay)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.mike10004/har-replay.svg)](https://repo1.maven.org/maven2/com/github/mike10004/har-replay/)

har-replay
==========

Java library for serving recorded HTTP responses from a HAR file. The library
uses a Node module called [har-replay-proxy] that acts as an HTTP proxy. The
proxy intercepts each request and responds with a pre-recorded response from 
a HAR.

Quick Start
-----------

Maven dependency:

    <dependency>
        <groupId>com.github.mike10004</groupId>
        <artifactId>har-replay</artifactId>
        <version>0.5</version>
    </dependency>

See Maven badge above for the actual latest version.

If you have a HAR file handy, you can replay it as shown here:

    public static void example(File harFile) throws IOException {
        ReplayManagerConfig replayManagerConfig = ReplayManagerConfig.auto();
        ReplayManager replayManager = new ReplayManager(replayManagerConfig);
        ReplaySessionConfig sessionConfig = ReplaySessionConfig.usingTempDir().build(harFile);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            Future<?> server = replayManager.startAsync(executorService, sessionConfig);
            doSomethingWithProxy("localhost", sessionConfig.port);
            server.cancel(true);
        } finally {
            executorService.shutdownNow();
        }
    }

The unit tests contain some examples of usage with an Apache HTTP client and a
Chrome WebDriver client. 

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

[har-replay-proxy]: https://github.com/mike10004/har-replay-proxy
[switcheroo]: https://chrome.google.com/webstore/detail/switcheroo-redirector/cnmciclhnghalnpfhhleggldniplelbg
[har-howto]: https://support.zendesk.com/hc/en-us/articles/204410413-Generating-a-HAR-file-for-troubleshooting
