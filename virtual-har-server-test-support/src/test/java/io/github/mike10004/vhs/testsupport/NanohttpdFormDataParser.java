/*
 * SOME METHODS COPIED FROM nanohttpd AND INCLUDED UNDER THIS LICENSE:
 * #%L
 * NanoHttpd-Core
 * %%
 * Copyright (C) 2012 - 2015 nanohttpd
 * %%
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the nanohttpd nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package io.github.mike10004.vhs.testsupport;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.ByteSource;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import io.github.mike10004.vhs.harbridge.ContentDisposition;
import io.github.mike10004.vhs.harbridge.FormDataPart;
import io.github.mike10004.vhs.harbridge.MultipartFormDataParser;
import io.github.mike10004.vhs.harbridge.TypedContent;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class NanohttpdFormDataParser implements MultipartFormDataParser {

    static final Charset BOUNDARY_ENCODING = StandardCharsets.US_ASCII; // assume header value contains only ASCII
    static final Charset DEFAULT_MULTIPART_FORM_DATA_ENCODING = StandardCharsets.UTF_8;
    private static final MediaType DEFAULT_FORM_DATA_PART_CONTENT_TYPE = MediaType.FORM_DATA;

    private static final int MAX_HEADER_SIZE = 1024;

    private static final String CONTENT_DISPOSITION_REGEX = "([ |\t]*Content-Disposition[ |\t]*:)(.*)";

    private static final Pattern CONTENT_DISPOSITION_PATTERN = Pattern.compile(CONTENT_DISPOSITION_REGEX, Pattern.CASE_INSENSITIVE);

    private static final String CONTENT_TYPE_REGEX = "([ |\t]*content-type[ |\t]*:)(.*)";

    private static final Pattern CONTENT_TYPE_PATTERN = Pattern.compile(CONTENT_TYPE_REGEX, Pattern.CASE_INSENSITIVE);

    private static int skipOverNewLine(byte[] partHeaderBuff, int index) {
        while (partHeaderBuff[index] != '\n') {
            index++;
        }
        return ++index;
    }

    /**
     * Copies a region of a buffer to a new byte array.
     */
    private static byte[] copyRegion(ByteBuffer srcBuffer, int offset, int len) {
        if (len > 0) {
            final ByteArrayOutputStream destBuffer = new ByteArrayOutputStream(256);
            ByteBuffer srcDuplicate = srcBuffer.duplicate();
            srcDuplicate.position(offset).limit(offset + len);
            WritableByteChannel channel = Channels.newChannel(destBuffer);
            try {
                channel.write(srcDuplicate.slice());
                destBuffer.flush();
            } catch (IOException e) {
                throw new RuntimeException("operation on memory-only buffers should not throw IOException, but alas", e);
            }
            return destBuffer.toByteArray();
        }
        return new byte[0];
    }

    /**
     * Find the byte positions where multipart boundaries start. This reads
     * a large block at a time and uses a temporary buffer to optimize
     * (memory mapped) file access.
     */
    @SuppressWarnings("Duplicates")
    static int[] getBoundaryPositions(ByteBuffer b, byte[] boundary) {
        int[] res = new int[0];
        if (b.remaining() < boundary.length) {
            return res;
        }

        int search_window_pos = 0;
        byte[] search_window = new byte[4 * 1024 + boundary.length];

        int first_fill = (b.remaining() < search_window.length) ? b.remaining() : search_window.length;
        b.get(search_window, 0, first_fill);
        int new_bytes = first_fill - boundary.length;

        do {
            // Search the search_window
            for (int j = 0; j < new_bytes; j++) {
                for (int i = 0; i < boundary.length; i++) {
                    if (search_window[j + i] != boundary[i])
                        break;
                    if (i == boundary.length - 1) {
                        // Match found, add it to results
                        int[] new_res = new int[res.length + 1];
                        System.arraycopy(res, 0, new_res, 0, res.length);
                        new_res[res.length] = search_window_pos + j;
                        res = new_res;
                    }
                }
            }
            search_window_pos += new_bytes;

            // Copy the end of the buffer to the start
            System.arraycopy(search_window, search_window.length - boundary.length, search_window, 0, boundary.length);

            // Refill search_window
            new_bytes = search_window.length - boundary.length;
            new_bytes = (b.remaining() < new_bytes) ? b.remaining() : new_bytes;
            b.get(search_window, boundary.length, new_bytes);
        } while (new_bytes > 0);
        return res;
    }

    /**
     * Decodes the Multipart Body data and put it into Key/Value pairs.
     */
    @Override
    public List<FormDataPart> decodeMultipartFormData(MediaType contentType, byte[] data) throws BadMultipartFormDataException {
        Charset parentTypeEncoding = contentType.charset().or(DEFAULT_MULTIPART_FORM_DATA_ENCODING);
        String boundary = MultipartFormDataParser.getBoundaryOrDie(contentType);
        byte[] boundaryBytes = boundary.getBytes(BOUNDARY_ENCODING);
        ByteBuffer fbuf = ByteBuffer.wrap(data);
        int[] boundaryIdxs = getBoundaryPositions(fbuf, boundaryBytes);
        List<FormDataPart> parts = new ArrayList<>(boundaryIdxs.length);
        byte[] partHeaderBuff = new byte[MAX_HEADER_SIZE];
        for (int boundaryIdx = 0; boundaryIdx < boundaryIdxs.length - 1; boundaryIdx++) {
            Multimap<String, String> headers = ArrayListMultimap.create();
            fbuf.position(boundaryIdxs[boundaryIdx]);
            int len = (fbuf.remaining() < MAX_HEADER_SIZE) ? fbuf.remaining() : MAX_HEADER_SIZE;
            fbuf.get(partHeaderBuff, 0, len);
            MemoryBufferedReader in = new MemoryBufferedReader(partHeaderBuff, 0, len, parentTypeEncoding, len);
            int headerLines = 0;
            // First line is boundary string
            String mpline = in.readLine();
            headerLines++;
            if (mpline == null || !mpline.contains(boundary)) {
                throw new MalformedMultipartFormDataException("BAD REQUEST: Content type is multipart/form-data but chunk does not start with boundary.");
            }

            // Parse the reset of the header lines
            mpline = in.readLine();
            headerLines++;
            @Nullable String partContentType = null;
            @Nullable ContentDisposition disposition = null;
            while (mpline != null && mpline.trim().length() > 0) {
                Matcher matcher = CONTENT_DISPOSITION_PATTERN.matcher(mpline);
                if (matcher.matches()) {
                    String attributeString = matcher.group(2);
                    headers.put(HttpHeaders.CONTENT_DISPOSITION, attributeString);
                    disposition = ContentDisposition.parse(attributeString);
                }
                matcher = CONTENT_TYPE_PATTERN.matcher(mpline);
                if (matcher.matches()) {
                    partContentType = matcher.group(2).trim();
                    headers.put(HttpHeaders.CONTENT_TYPE, partContentType);
                }
                mpline = in.readLine();
                headerLines++;
            }
            int partHeaderLength = 0;
            while (headerLines-- > 0) {
                partHeaderLength = skipOverNewLine(partHeaderBuff, partHeaderLength);
            }
            // Read the part data
            if (partHeaderLength >= len - 4) {
                throw new BadMultipartFormDataException("Multipart header size exceeds MAX_HEADER_SIZE.");
            }
            int partDataStart = boundaryIdxs[boundaryIdx] + partHeaderLength;
            int partDataEnd = boundaryIdxs[boundaryIdx + 1] - 4;

            fbuf.position(partDataStart);

            if (partContentType == null) {
                partContentType = DEFAULT_FORM_DATA_PART_CONTENT_TYPE.toString();
            }
            // Read it into a file
            byte[] fileData = copyRegion(fbuf, partDataStart, partDataEnd - partDataStart);
            TypedContent file = TypedContent.identity(ByteSource.wrap(fileData), MediaType.parse(partContentType));
            parts.add(new FormDataPart(headers, disposition, file));
        }
        return parts;
    }

    private static class MemoryBufferedReader extends BufferedReader {

        public MemoryBufferedReader(byte[] data, int offset, int len, Charset charset, int sz) {
            super(new InputStreamReader(new ByteArrayInputStream(data, offset, len), charset), sz);
        }

        @Override
        public String readLine() {
            try {
                return super.readLine();
            } catch (IOException e) {
                throw new RuntimeIOException("readLine() on memory buffer should not throw exception, but alas", e);
            }
        }
    }
}
