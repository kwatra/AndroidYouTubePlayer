package com.pierfrancescosoffritti.youtubeplayer;

import android.util.Log;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by kwatra on 4/28/17.
 */

public class ItemPool<T> {
    private static final String TAG = "NKSG:ItemPool";

    Set<T> mItems;

    private final Lock lock = new ReentrantLock();
    private final Condition releaseCountZero = lock.newCondition();

    public ItemPool(List<T> items) {
        mItems = new HashSet<>();
        for (T item : items) {
            add(item);
        }
    }

    private void add(T item) {
        Log.v(TAG, "add");
        lock.lock();

        mItems.add(item);
        lock.unlock();
    }

    public T get() {
        Log.v(TAG, "get");
        lock.lock();

        waitUntilItemAvailable_l();

        T item = null;
        if (mItems.size() > 0) {  // Didn't time out.
            item = mItems.iterator().next();
        }

        lock.unlock();

        return item;
    }

    public void release(T item) {
        Log.v(TAG, "release");
        lock.lock();

        mItems.add(item);

        lock.unlock();
    }

    private void waitUntilItemAvailable_l() {
        boolean stillWaiting = true;
        try {
            while (mItems.size() == 0) {
                Log.v(TAG, "wait for release");
                if (!stillWaiting) {
                    Log.e(TAG, "Timeout hit in waiting for items to be released.");
                    return;
                }
                stillWaiting = releaseCountZero.await(1000, TimeUnit.MILLISECONDS);
            }
            Log.v(TAG, "items size: " + mItems.size());
        } catch (Exception e) {
            Log.e(TAG, "Exception in await", e);
        }
    }
}
