/**
 * Copyright (c) KMG. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.sbk.FileChannel;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.javaprop.JavaPropsFactory;
import io.sbk.api.DataType;
import io.sbk.api.Storage;
import io.sbk.api.Parameters;
import io.sbk.api.Writer;
import io.sbk.api.Reader;
import io.sbk.api.impl.NioByteBuffer;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Class for File System Benchmarking using File Channel.
 */
public class FileChannel implements Storage<ByteBuffer> {
    private final static String CONFIGFILE = "filechannel.properties";
    private FileChannelConfig config;
    private DataType<ByteBuffer> dType;

    @Override
    public void addArgs(final Parameters params) throws IllegalArgumentException {
        final ObjectMapper mapper = new ObjectMapper(new JavaPropsFactory())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try {
            config = mapper.readValue(
                    Objects.requireNonNull(FileChannel.class.getClassLoader().getResourceAsStream(CONFIGFILE)),
                    FileChannelConfig.class);
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new IllegalArgumentException(ex);
        }
        params.addOption("file", true, "File name, default file name : "+config.fileName);
    }

    @Override
    public void parseArgs(final Parameters params) throws IllegalArgumentException {
        config.fileName =  params.getOptionValue("file", config.fileName);
        if (params.getWritersCount() > 1) {
            throw new IllegalArgumentException("Writers should be only 1 for File writing");
        }
        if (params.getReadersCount() > 0 && params.getWritersCount() > 0) {
            throw new IllegalArgumentException("Specify either Writer or readers ; both are not allowed");
        }
    }

    @Override
    public void openStorage(final Parameters params) throws  IOException {
        if (config.reCreate && params.getWritersCount() > 0) {
            File file = new File(config.fileName);
            file.delete();
        }
    }

    @Override
    public void closeStorage(final Parameters params) throws IOException {

    }

    @Override
    public Writer<ByteBuffer> createWriter(final int id, final Parameters params) {
        try {
            return new FileChannelWriter(id, params, config);
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
    }

    @Override
    public Reader<ByteBuffer> createReader(final int id, final Parameters params) {
        try {
            return new FileChannelReader(id, params, dType, config);
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
    }

    @Override
    public DataType<ByteBuffer> getDataType() {
        dType = new NioByteBuffer();
        return dType;
    }
}



