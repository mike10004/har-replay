package io.github.mike10004.harreplay.vhsimpl;

import com.google.common.net.MediaType;
import io.github.mike10004.vhs.HttpRespondable;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map.Entry;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

public class HttpRespondableWrapper implements HttpRespondable {

    protected final HttpRespondable delegate;

    public HttpRespondableWrapper(HttpRespondable delegate) {
        this.delegate = requireNonNull(delegate);
    }

    @Override
    public int getStatus() {
        return delegate.getStatus();
    }

    @Override
    public Stream<? extends Entry<String, String>> streamHeaders() {
        return delegate.streamHeaders();
    }

    @Override
    public MediaType writeBody(OutputStream outputStream) throws IOException {
        return delegate.writeBody(outputStream);
    }

    @Override
    @Nullable
    public MediaType previewContentType() {
        return delegate.previewContentType();
    }
}
