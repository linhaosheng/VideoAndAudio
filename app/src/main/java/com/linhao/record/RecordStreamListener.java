package com.linhao.record;

/**
 * Created by haoshenglin on 2018/4/26.
 */

public interface RecordStreamListener {
    void recordOfByte(byte[] data, int begin, int end);
}
