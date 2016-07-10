package com.santiago.event;

import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches the observables methods from a particular dispatcher so we dont have to iterate over all their methods for every dispatch
 *
 * Another good idea would have been to get ALL anotated methods (easy with Reflections lib)
 * and create a single map of <class, <anotation, List<method>>>, then you just have to check in the
 * map if your class is there, and if the anotation contains methods.
 * I think this way it would create a lot of overhead (because maybe the user will just go to a
 * specific screen) and you have in memory all of them. So we just go cache the ones they suscribe
 * and in the worst case we will have all of them (like the other way would have done from start)
 *
 * We will be also doing a "LRU" by tracking down time of the last hit on a class
 *
 * Created by saantiaguilera on 08/07/16.
 */
final class EventCache {

    //Loop time for cleaning our map of lru items
    private static final int LRU_DELAYED_TIME_FOR_PROCESS = 300_000; // Default: 5 minutes
    //Handler that will post messages to a handler thread for the lru clean up
    private final @NonNull Handler mHandler;

    //Map for the cache and one for the hits across time
    private final @NonNull Map<Class, Map<Class, List<Method>>> cache;
    private final @NonNull Map<Class, Long> hits;

    public EventCache() {
        cache = new ConcurrentHashMap<>();
        hits = new ConcurrentHashMap<>();

        //Create a thread with really low priority since this is only useful for memory performance
        HandlerThread mThread = new HandlerThread(EventCache.class.getName() + ".HandlerThread", Thread.MIN_PRIORITY);
        mThread.start();

        mHandler = new Handler(mThread.getLooper());
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                //Iterate through all our cache, and all the ones that werent hit even once during the last LRU_DELAYED_TIME_FOR_PROCESS millis, remove them
                Iterator<Map.Entry<Class, Map<Class, List<Method>>>> iter = cache.entrySet().iterator();
                while (iter.hasNext()) {
                    Map.Entry<Class, Map<Class, List<Method>>> entry = iter.next();
                    if (hits.get(entry.getKey()) == null)
                        iter.remove();
                }

                hits.clear();

                //Dont forget to reque this runnable!
                mHandler.postDelayed(this, LRU_DELAYED_TIME_FOR_PROCESS);
            }
        }, LRU_DELAYED_TIME_FOR_PROCESS);

    }

    /**
     * Know if a specific class is in the cache
     *
     * @param clazz class to know about
     * @return true if its cached, false otherwise
     */
    public boolean isCached(@NonNull Class clazz) {
        return cache.get(clazz) != null;
    }

    /**
     * Gets from the cache the mapped annotations and events of a class
     * @param clazz class
     * @param annotatedClass annotation
     * @return Map of annotations and methods
     */
    public @Nullable List<Method> get(@NonNull Class clazz, @NonNull Class annotatedClass) {
        if (cache.get(clazz) == null || cache.get(clazz).get(annotatedClass) == null)
            return null;

        if (hits.get(clazz) == null)
            hits.put(clazz, System.currentTimeMillis());

        return cache.get(clazz).get(annotatedClass);
    }

    /**
     * Puts in the cache a list of methods
     *
     * @param clazz class
     * @param annotatedClass annotation
     * @param methods methods
     */
    public void put(@NonNull Class clazz, @NonNull Class annotatedClass, @NonNull List<Method> methods) {
        if (cache.get(clazz) == null) {
            cache.put(clazz, new ConcurrentHashMap<Class, List<Method>>());
        }

        if (cache.get(clazz).get(annotatedClass) == null) {
            cache.get(clazz).put(annotatedClass, methods);
        } else {
            cache.get(clazz).get(annotatedClass).addAll(methods);
        }
    }

    /**
     * A bit more of boilerplate but it doesnt has to do unnecessary calls or jumps
     *
     * @param clazz class
     * @param annotatedClass annotation
     * @param method method
     */
    public void put(@NonNull Class clazz, @NonNull Class annotatedClass, @NonNull Method method) {
        if (cache.get(clazz) == null) {
            cache.put(clazz, new ConcurrentHashMap<Class, List<Method>>());
        }

        if (cache.get(clazz).get(annotatedClass) == null) {
            cache.get(clazz).put(annotatedClass, new ArrayList<Method>());
            cache.get(clazz).get(annotatedClass).add(method);
        } else {
            cache.get(clazz).get(annotatedClass).add(method);
        }
    }

}
