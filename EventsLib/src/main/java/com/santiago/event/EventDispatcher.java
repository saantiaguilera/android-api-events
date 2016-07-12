package com.santiago.event;

import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;

import com.santiago.event.anotation.EventAsync;
import com.santiago.event.anotation.EventMethod;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @note In case you are planning on using multiple EventBus, there will be only
 * one handler and pool executor for all of them.
 *
 * Created by saantiaguilera on 20/04/16.
 */
final class EventDispatcher {

    /*-------------------------------Cache---------------------------------*/

    private static EventCache cache = null;

    /**
     * Getter for the cache manager
     *
     * @return cache were all the annotated methods are stored using ""lru""
     */
    private @NonNull EventCache getCache() {
        if (cache == null) {
            synchronized (EventDispatcher.class) {
                if (cache == null) {
                    cache = new EventCache();
                }
            }
        }

        return cache;
    }

    /*-------------------------------Concurrency----------------------------*/

    private static ThreadPoolExecutor poolExecutor = null;
    private static Handler uiHandler = null;

    /**
     * Getter for the thread pool executor
     *
     * @return thread pool executor
     */
    private @NonNull ThreadPoolExecutor getPoolExecutor() {
        if (poolExecutor == null) {
            synchronized (EventDispatcher.class) {
                if (poolExecutor == null) {
                    poolExecutor = new ThreadPoolExecutor(
                            Runtime.getRuntime().availableProcessors() * 2,
                            Runtime.getRuntime().availableProcessors() * 2,
                            20,
                            TimeUnit.SECONDS,
                            new LinkedBlockingQueue<Runnable>()
                    );
                }
            }
        }

        return poolExecutor;
    }

    /**
     * Getter for the handler of the main UI
     *
     * @return handler that posts messages on the main UI
     */
    private @NonNull Handler getUiHandler() {
        if (uiHandler == null) {
            synchronized (EventDispatcher.class) {
                if (uiHandler == null) {
                    //Because we dont have guarantees its first time wont be in another thread use Looper.getMain...
                    uiHandler = new Handler(Looper.getMainLooper());
                }
            }
        }

        return uiHandler;
    }

    /**
     * Its wrong to take as assumption that broadcasting will always happen from
     * the main UI thread. Since we could be dispatching a method from another
     * thread, and if that methods broadcasts another event, we wont be anymore
     * in the main UI.
     *
     * As solution, we must always post on the main UI.
     *
     * @note Take into account that this will post the runnable to the end of the queue
     * of the main thread, that means that it wont be executed right away (or maybe yes,
     * but dont make that assumption)
     *
     * @param runnable to post to the main looper (attached to a msg)
     */
    private void postSync(@NonNull Runnable runnable) {
        getUiHandler().post(runnable);
    }

    /**
     * Private method that queues a Runnable in the thread pool
     * Since the threadpool doesnt use a blocking queue and has fixed size,
     * it will always execute this code in a worker thread (They wont be rejected).
     *
     * @note Since we are using the available processors as max and core pool sizes,
     * at start we will be spawning workers until we reach the max. Once that happens, it will
     * execute new runnables only when one of those workers is terminated / idle for more than
     * the timeout time
     *
     * @see { https://docs.oracle.com/javase/7/docs/api/java/util/concurrent/ThreadPoolExecutor.html }
     * @param runnable to post to the pool thread queue
     */
    private void postAsync(@NonNull Runnable runnable) {
        getPoolExecutor().execute(runnable);
    }

    /*-------------------------------Dispatching methods--------------------------------------*/

    /**
     * This can be read as "Dispatch Event: Event To: Object"
     *
     * Dispatchs to an object (listener) the event through all the hierarchy of the Event class (since you wont be dispatching an event
     * of class Event, probably a child of it)
     *
     * This means that it will first try to dispatch it to this instance(a child), then to his parent, and like that
     * until it reaches to Event.class (non inclusive)
     *
     * <strong> Make sure your proguard runs keepattributes '*Annotation*' , in case Proguard removes all of them, and since
     * this works in RunTime we wont catch a single method because proguard removed all of the anotations. With that line we are
     * telling the proguard to dont remove them. Check in your sdk folder inside the proguard for the files were you have your settings
     * (Check where you read your proguard settings from the build.gradle. There you will see if its doing it or not. In my case it has been done
     * by default</strong>
     *
     * <strong> NOTE: "this" class wont be necesarilly our particular class from which we called dispatchEventTo,
     * it can be its super or someone among that hierarchy, since we will be dispatching this event among all of them</strong>
     *
     * @param to object that will respond to the event
     */
    public void dispatchEvent(@NonNull final Event event, @NonNull final Object to) {
        //If the class isnt in the cache, add it
        if (!getCache().isCached(to.getClass())) {
            //Iterate through all the super classes also!
            for (Class toClass = to.getClass(); toClass != Object.class; toClass = toClass.getSuperclass()) {
                //Loop over all the methods
                for (Method method : toClass.getDeclaredMethods()) {
                    //And finally if it has the Event annotation, cache that method
                    EventMethod annotation = method.getAnnotation(EventMethod.class);
                    if (annotation != null) {
                        getCache().put(to.getClass(), annotation.value(), method);
                    }
                }
            }
        }

        //Iterate through all the super classes of the event !
        for (Class eventClass = event.getClass(); eventClass != Event.class; eventClass = eventClass.getSuperclass()) {
            //Iterate through all the methods of our object that will respond to the event (they have to be cached yes or yes)
            List<Method> methods = getCache().get(to.getClass(), eventClass);
            if (methods != null) {
                for (final Method method : methods) {
                    Runnable runnable = new Runnable() {
                        @Override
                        public void run() {
                            invokeMethod(method, to, event);
                        }
                    };

                    if (method.getAnnotation(EventAsync.class) != null)
                        postAsync(runnable);
                    else postSync(runnable);
                }
            }
        }
    }

    /**
     * Invokes the method m on the receiver.
     * Will only invoke methods of form:
     *    methodName();
     *    methodName(MyEvent event);
     *
     * @param method
     * @param receiver
     * @throws IllegalArgumentException if method with the anotation uses different paramenters from the ones we try to invoke
     * @throws IllegalAccessException if some AsyncException occurs or you are implementing a security manager, then we cant turn the method accesible
     * @throws InvocationTargetException if an exception was throw inside the method we invoked (this is an error of that method, but we catch it here)
     * @throws NullPointerException if receiver is null (Wont happen unless you implement your own dispatcher)
     */
    private void invokeMethod(@NonNull Method method, @NonNull Object receiver, @NonNull Event event) {
        try {
            //Set it accessible in case it has private access (which is most certain cases)
            method.setAccessible(true);

            //If it doesnt have parameters try to invoke it without them, else invoke them with ourselves as a param
            if (method.getParameterTypes().length == 0)
                method.invoke(receiver);
            else method.invoke(receiver, event);
        } catch ( IllegalArgumentException e) {
            //The user has the method defined with wrong arguments, throw an Ex telling him
            throw new IllegalArgumentException("You have wrong arguments in your method: " + method.getName() + ". Only methods that can be invoked are either with no args or with a param of type " + event.getClass().getSimpleName());
        } catch ( Exception e ) {
            //IllegalAccess and InvocationTarget are Api19, so we catch Exception for not raising our ApiLvl
            throw new RuntimeException(e);
        }
    }

}
