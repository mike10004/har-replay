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

import java.util.HashMap;

/**
 * Represents a bidirectional mapping dictionary used for LZW compression.
 *
 * @version 1.0 25 May 2016
 * @author Saul Johnson, Alex Mullen, Lee Oliver
 */
@SuppressWarnings("Convert2Diamond")
public class LzwDictionary {

    /** Maps hexadecimal strings to 16-bit data codes. */
    private final HashMap<String, Short> dataToCodes;

    /** Maps 16-bit data codes to hexadecimal strings. */
    private final HashMap<Short, String> codesToData;

    /**
     * Initialises a new instance of an LZW compression dictionary.
     */
    public LzwDictionary() {
        dataToCodes = new HashMap<String, Short>();
        codesToData = new HashMap<Short, String>();
    }

    /**
     * Adds a hexadecimal string to the end of this dictionary.
     * @param data  the string to add
     */
    public void addData(String data) {
        if (isFull()) {
            throw new RuntimeException("Compression dictionary is full.");
        }
        dataToCodes.put(data, (short) dataToCodes.size());
        codesToData.put((short) codesToData.size(), data);
    }

    /**
     * Gets whether or not the dictionary has an entry with data matching the specified hexadecimal string.
     * @param data  the string to check for
     * @return      true if dictionary has matching data, otherwise false
     */
    public boolean hasData(String data) {
        return dataToCodes.containsKey(data);
    }

    /**
     * Gets the 16-bit encoded integer associated with the specified hexadecimal string.
     * @param data  the string to check for
     * @return      the associated 16-bit encoded integer
     */
    public Short getCode(String data) {
        return dataToCodes.get(data);
    }

    /**
     * Gets the hexadecimal string associated with the specified 16-bit encoded integer.
     * @param value the 16-bit encoded integer to check for
     * @return      the associated hexadecimal string
     */
    public String getData(short value) {
        return codesToData.get(value);
    }

    /**
     * Gets the size of the dictionary.
     * @return  the size of the dictionary
     */
    public int getSize() {
        return dataToCodes.size();
    }

    /**
     * Gets whether or not the dictionary is full.
     * @return  true if the dictionary is full, otherwise false
     */
    public boolean isFull() {
        return dataToCodes.size() == Short.MAX_VALUE;
    }
}