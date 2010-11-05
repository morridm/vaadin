package com.vaadin.terminal.gwt.server;

import com.vaadin.terminal.StreamVariable;
import com.vaadin.terminal.StreamVariable.StreamingEvent;

/**
 * Abstract base class for StreamingEvent implementations.
 */
@SuppressWarnings("serial")
abstract class AbstractStreamingEvent implements StreamingEvent {
    private final String type;
    private final String filename;
    private final StreamVariable streamVariable;
    private final long contentLength;
    private final long bytesReceived;

    public final String getFileName() {
        return filename;
    }

    public final String getMimeType() {
        return type;
    }

    protected AbstractStreamingEvent(StreamVariable streamVariable, String filename,
            String type, long length, long bytesReceived) {
        this.streamVariable = streamVariable;
        this.filename = filename;
        this.type = type;
        contentLength = length;
        this.bytesReceived = bytesReceived;
    }

    public final StreamVariable getReceiver() {
        return streamVariable;
    }

    public final long getContentLength() {
        return contentLength;
    }

    public final long getBytesReceived() {
        return bytesReceived;
    }

}