/**
 * **************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one * or more contributor license agreements. See the NOTICE file *
 * distributed with this work for additional information * regarding copyright ownership. The ASF licenses this file * to you
 * under the Apache License, Version 2.0 (the * "License"); you may not use this file except in compliance * with the License. You
 * may obtain a copy of the License at * * http://www.apache.org/licenses/LICENSE-2.0 * * Unless required by applicable law or
 * agreed to in writing, * software distributed under the License is distributed on an * "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY * KIND, either express or implied. See the License for the * specific language governing permissions and
 * limitations * under the License. * **************************************************************
 */
package ro.ieugen.mboxiterator;

import java.io.*;
import java.nio.CharBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class that provides an iterator over email messages inside an mbox file.
 *
 * @author Ioan Eugen Stan <stan.ieugen@gmail.com>
 */
public class MboxIterator implements Iterable<CharBuffer>, Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(MboxIterator.class);
    private static final int MAX_MSG_LENGTH = 1024 * 1024 * 10;     // 10Mb of Chars!
    private final FileInputStream fis;
    private final CharBuffer mboxCharBuffer;
    private final Matcher fromLineMathcer;
    private boolean hasMore;

    private MboxIterator(final File mbox,
            final String charset,
            final String regexpPattern,
            final int regexpFlags)
            throws FileNotFoundException, IOException {

        //TODO: do better exception handling - try to process ome of them maybe? 

        fis = new FileInputStream(mbox);
        final FileChannel fileChannel = fis.getChannel();
        final MappedByteBuffer byteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0,
                fileChannel.size());
        final CharsetDecoder DECODER = Charset.forName(charset).newDecoder();
        mboxCharBuffer = CharBuffer.allocate(MAX_MSG_LENGTH);
        CoderResult result = DECODER.decode(byteBuffer, mboxCharBuffer, false);
        
        final Pattern MESSAGE_START = Pattern.compile(regexpPattern, regexpFlags);
        fromLineMathcer = MESSAGE_START.matcher(mboxCharBuffer);
        hasMore = fromLineMathcer.find();
        if (!hasMore) {
            throw new RuntimeException("File does not contain From_ lines! "
                    + "Maybe not a vaild Mbox.");
        }
    }

    @Override
    public Iterator<CharBuffer> iterator() {
        return new MessageIterator();
    }

    @Override
    public void close() throws IOException {
        fis.close();
    }

    @Override
    protected void finalize() throws Throwable {
        fis.close();
        super.finalize();
    }

    private class MessageIterator implements Iterator<CharBuffer> {

        @Override
        public boolean hasNext() {
            return hasMore;
        }

        @Override
        public CharBuffer next() {
            //LOG.info("next() called at offset {}", fromLineMathcer.start());
            final CharBuffer message = mboxCharBuffer.slice();
            message.position(fromLineMathcer.start());
            //logBufferDetails(message);
            hasMore = fromLineMathcer.find();
            if (hasMore) {
                LOG.info("We limit the buffer to {} ?? {}",
                        fromLineMathcer.start(), fromLineMathcer.end());
                message.limit(fromLineMathcer.start());
            }
            return message;
        }

        private void logBufferDetails(final CharBuffer buffer) {
            LOG.info("Buffer details: "
                    + "\ncapacity:\t" + buffer.capacity()
                    + "\nlimit:\t" + buffer.limit()
                    + "\nremaining:\t" + buffer.remaining()
                    + "\nposition:\t" + buffer.position()
                    + "\nis direct:\t" + buffer.isDirect()
                    + "\nhas array:\t" + buffer.hasArray()
                    + "\nbuffer:\t" + buffer.isReadOnly()
                    + "\nclass:\t" + buffer.getClass()
                    + "\nlengt:\t" + buffer.length());
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }

    public static class Builder {

        private final File file;
        private String charset = "UTF-8";
        private String regexpPattern = "^From \\S+@\\S.*\\d{4}$";
        private int flags = Pattern.MULTILINE;

        public Builder(String filePath) {
            this(new File(filePath));
        }

        public Builder(File file) {
            this.file = file;
        }

        public Builder charset(String charset) {
            this.charset = charset;
            return this;
        }

        public Builder fromLine(String fromLine) {
            this.regexpPattern = fromLine;
            return this;
        }

        public Builder flags(int flags) {
            this.flags = flags;
            return this;
        }

        public MboxIterator build() throws FileNotFoundException, IOException {
            return new MboxIterator(file, charset, regexpPattern, flags);
        }
    }
}
