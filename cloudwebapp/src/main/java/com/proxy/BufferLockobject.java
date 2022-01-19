package com.proxy;

import java.nio.ByteBuffer;

public class BufferLockobject {
    final Object lockObject = new Object();
    private ByteBuffer buffer;

    public void setBuffer(ByteBuffer buffer) {
        this.buffer = buffer;
    }

    public ByteBuffer getBuffer() {
        return buffer;
    }

    public Object getLockObject() {
        return lockObject;
    }
}
