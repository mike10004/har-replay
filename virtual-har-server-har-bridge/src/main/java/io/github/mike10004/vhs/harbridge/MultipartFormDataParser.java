package io.github.mike10004.vhs.harbridge;

import com.google.common.net.MediaType;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface MultipartFormDataParser {
    int HTTP_ERROR_BAD_REQUEST = 400;

    static String getBoundaryOrDie(MediaType contentType) throws BadMultipartFormDataException {
        @Nullable String boundary = contentType.parameters().entries().stream()
                .filter(entry -> "boundary".equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst().orElse(null);
        if (boundary == null) {
            throw new BadMultipartFormDataException("'boundary' parameter not present in Content-Type: " + contentType.toString());
        }
        return boundary;
    }

    /**
     * Parse multipart/form-data.
     * See https://www.iana.org/assignments/media-types/multipart/form-data.
     * @param contentType content type (must have boundary parameter, should probably be {@code multipart/form-data}
     * @param data the data
     * @return
     * @throws MultipartFormData.BadMultipartFormDataException
     * @throws NanohttpdFormDataParser.RuntimeIOException
     */
    List<FormDataPart> decodeMultipartFormData(MediaType contentType, byte[] data) throws BadMultipartFormDataException;

    class BadMultipartFormDataException extends RuntimeException {

        public static final int STATUS_CODE = HTTP_ERROR_BAD_REQUEST;

        @SuppressWarnings("unused")
        public BadMultipartFormDataException(String message) {
            super(message);
        }

        @SuppressWarnings("unused")
        public BadMultipartFormDataException(String message, Throwable cause) {
            super(message, cause);
        }

        @SuppressWarnings("unused")
        public BadMultipartFormDataException(Throwable cause) {
            super(cause);
        }
    }

    class MalformedMultipartFormDataException extends BadMultipartFormDataException {

        public MalformedMultipartFormDataException(String message) {
            super(message);
        }
    }

    class RuntimeIOException extends RuntimeException {
        public RuntimeIOException(String message, IOException cause) {
            super(message, cause);
        }
    }
}
