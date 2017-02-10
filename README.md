har-replay
==========

Java library for serving recorded HTTP responses from a HAR file. The library
uses the [Stuk/server-replay](https://github.com/Stuk/server-replay) Node
module. See that project for details, but to summarize, it's a program that
acts as an HTTP proxy, and when it receives a request, it responds with a 
pre-recorded responses from a HAR.

Quick Start
-----------

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


Interrogatives
--------------

### How do I create a HAR?

You can use the DevTools in Chrome. See this unofficial tech support posting:
[Generating a HAR file for troubleshooting][har-howto]. 

### How does it handle HTTPS?

It doesn't, really, but there is an imperfect workaround. The usual way you
configure the server is to be flexible about matching against https entries
in the HAR, meaning when searching for a matching entry in the HAR, it will
accept an entry whose request URL matches *except* for the protocol, where a
canned HTTPS response will be returned for an HTTP request.

Don't try to visit HTTPS URLs through the proxy, which does not support TLS
connections. Instead, modify all of your requests to use HTTP as the protocol.
If you're using Chrome, you can use the [Switcheroo extension][switcheroo].
If you're using Chrome through a WebDriver and can't perform the manual 
configuration required by that extension, you can use a modified version of
the extension included with the library. Check out the ModifiedSwitcheroo
class, which you can use to create a CRX file (Chrome extension file). You can
configure a `ChromeOptions` instance with that CRX file and pass it to the
`ChromeDriver` constructor, and your webdriver instance will be started with
the extension, which intercepts requests to HTTPS URLs and modifies the URL to
use HTTP instead.

[switcheroo]: https://chrome.google.com/webstore/detail/switcheroo-redirector/cnmciclhnghalnpfhhleggldniplelbg
[har-howto]: https://support.zendesk.com/hc/en-us/articles/204410413-Generating-a-HAR-file-for-troubleshooting
