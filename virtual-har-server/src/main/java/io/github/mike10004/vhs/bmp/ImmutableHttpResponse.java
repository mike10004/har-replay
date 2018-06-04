package io.github.mike10004.vhs.bmp;

public class ImmutableHttpResponse extends ImmutableHttpMessage {

    public final int status;

    private ImmutableHttpResponse(Builder builder) {
        super(builder);
        status = builder.status;
    }

    @Override
    public String toString() {
        return toStringHelper().add("status", status).toString();
    }

    public static Builder builder(int status) {
        return new Builder(status);
    }

    public static final class Builder extends MessageBuilder<Builder> {

        private final int status;

        private Builder(int status) {
            this.status = status;
        }

        public ImmutableHttpResponse build() {
            return new ImmutableHttpResponse(this);
        }
    }

}
