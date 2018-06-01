package io.github.mike10004.vhs.bmp;

import com.google.common.base.Suppliers;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;

import java.util.function.Supplier;

public class HeaderAddingFilter implements BmpResponseListener {

    private final Supplier<String> nameSupplier;
    private final Supplier<String> valueSupplier;

    public HeaderAddingFilter(String headerName, Supplier<String> valueSupplier) {
        this(Suppliers.ofInstance(headerName), valueSupplier);
    }

    public HeaderAddingFilter(Supplier<String> nameSupplier, Supplier<String> valueSupplier) {
        this.nameSupplier = nameSupplier;
        this.valueSupplier = valueSupplier;
    }

    @Override
    public void responding(RequestCapture requestCapture, ResponseCapture responseCapture) {
        filter(responseCapture.response);
    }

    private void filter(HttpResponse response) {
        HttpHeaders headers = response.headers();
        String headerName = nameSupplier.get();
        String value = valueSupplier.get();
        headers.set(headerName, value);
    }
}
