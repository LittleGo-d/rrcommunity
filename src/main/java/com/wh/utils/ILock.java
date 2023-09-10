package com.wh.utils;

public interface ILock {

    /**
     * 获取锁
     */
    boolean tryLock(long timeoutSec);

    void deleteKey();
}
