har-replay-node
===============

Implementation of HAR replay manager using a Node-based back end. This implementation
uses a Node module called **har-replay-proxy** to provide a proxy server.

The current **har-replay-proxy** version is 0.0.1. If an update is available, then
the packaged zip in `src/main/resources` needs to be updated as well. To create
a new packaged zip:

* clone the **har-replay-proxy** repository, 
* run `npm install` in that directory so that its `node_modules` subdirectory 
  is populated,
* delete the `.git` folder from that cloned repo directory, and
* zip the folder so that the zip contains a `har-replay-proxy` directory at
  its root. 

## Handling HTTPS requests

The server will match HTTP requests to HTTPS entries in the HAR, so you have
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

