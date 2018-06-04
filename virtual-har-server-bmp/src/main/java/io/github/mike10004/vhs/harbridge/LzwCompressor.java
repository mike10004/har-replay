/*
The MIT License (MIT)

Copyright (c) 2016 Saul Johnson

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */

package io.github.mike10004.vhs.harbridge;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import javax.xml.bind.DatatypeConverter;

/**
 * A compressor that uses LZW coding to compress a set of bytes.
 *
 * @version 1.0 25 May 2016
 * @author Saul Johnson, Alex Mullen, Lee Oliver
 */
public class LzwCompressor {

    /**
     * Returns the specified integer as a hex string at least two characters long.
     * @param data  the integer to convert
     * @return      the specified integer as a hex string
     */
    private String toHex(int data) {
        final StringBuilder sb = new StringBuilder(Integer.toHexString(data));
        while (sb.length() < 2) {
            sb.insert(0, '0');
        }
        return sb.toString();
    }

    /**
     * Returns the specified 16-bit integer as a byte array.
     * @param value the 16-bit integer to convert
     * @return      the specified 16-bit integer as a byte array
     */
    private byte[] shortToByteArray(short value) {
        return ByteBuffer.allocate(2).putShort(value).array();
    }

    /**
     * Returns the specified byte array as a 16-bit integer.
     * @param data  the byte array integer to convert
     * @return      the specified byte array integer as a 16-bit integer
     */
    private short byteArrayToShort(byte[] data) {
        return ByteBuffer.allocate(2).put(data).getShort(0);
    }

    /**
     * Returns a dictionary initialised with entries for single byte values.
     * @return  a dictionary initialised with entries for single byte values
     */
    private LzwDictionary getInitializedDictionary() {
        final LzwDictionary dictionary = new LzwDictionary();
        for (int i = 0; i < 256; i++) {
            dictionary.addData(toHex(i));
        }
        return dictionary;
    }

    /**
     * Converts a hexadecimal string to a byte array and returns it.
     * @param str   the hexadecimal string to convert
     * @return      a byte array representation of the hexadecimal string
     */
    private byte[] fromHexString(String str) {
        return DatatypeConverter.parseHexBinary(str);
    }

    /**
     * Returns the first byte of a hexadecimal string.
     * @param str   the hexadecimal string
     * @return      the first byte (i.e. first two characters) of the hex string
     */
    private String firstByte(String str) {
        return str.substring(0, 2);
    }

    /**
     * Compresses the given data using LZW encoding.
     * @param data          the data to compress
     * @return              the compressed data
     * @throws IOException
     */
    public byte[] compress(byte[] data) throws IOException {
        // Initialize input stream on data.
        ByteArrayInputStream inputStream = new ByteArrayInputStream(data);

        // Initialise dictionary.
        LzwDictionary dictionary = getInitializedDictionary();

        // Create output stream.
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        String sequence = "";

        int buffer;
        while ((buffer = inputStream.read()) != -1) {
            // If dictionary is full, clear it.
            if (dictionary.isFull()) {
                dictionary = getInitializedDictionary();
            }

            // Append buffer to sequence.
            String byteString = toHex(buffer);
            sequence += byteString;

            // If this sequence is not in the dictionary.
            if (!dictionary.hasData(sequence)) {
                // Add it.
                dictionary.addData(sequence);

                // Remove the last byte from the sequence.
                sequence = sequence.substring(0, sequence.length() - 2);

                // Write the code for the last sequence that was present to output.
                outputStream.write(shortToByteArray(dictionary.getCode(sequence)));

                // Start the sequence afresh with the new byte string.
                sequence = byteString;
            }
        }

        // Write any remaining data to output.
        if (sequence.length() != 0) {
            outputStream.write(shortToByteArray(dictionary.getCode(sequence)));
        }

        // Close stream and return compressed data.
        outputStream.close();
        return outputStream.toByteArray();
    }

    /**
     * Decompresses the given data using LZW encoding.
     * @param data          the data to decompress
     * @return              the decompressed data
     * @throws IOException
     */
    public byte[] decompress(byte[] data) throws IOException {
        // Initialize input stream on data.
        ByteArrayInputStream inputStream = new ByteArrayInputStream(data);

        // Initialise dictionary.
        LzwDictionary dictionary = getInitializedDictionary();

        // Create output stream.
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        String sequence = "";

        // Read file 16 bits at a time (fixed length codes).
        final byte[] buffer = new byte[2];
        while (inputStream.read(buffer) != -1) {
            // If dictionary is full, clear it.
            if (dictionary.isFull()) {
                dictionary = getInitializedDictionary();
            }

            // Decompress data, reconstructing dictionary.
            final short code = byteArrayToShort(buffer);
            if (code > dictionary.getSize()) {
                throw new IOException("Cannot reconstruct dictionary.");
            } else if (code == dictionary.getSize()) {
                dictionary.addData(sequence + firstByte(sequence));
            } else if (sequence.length() != 0) {
                dictionary.addData(sequence + firstByte(dictionary.getData(code)));
            }

            // Write the code for the last sequence that was present to output.
            outputStream.write(fromHexString(dictionary.getData(code)));

            // Start the sequence afresh with the new byte string.
            sequence = dictionary.getData(code);
        }

        // Close stream and return decompressed data.
        outputStream.close();
        return outputStream.toByteArray();
    }
}
