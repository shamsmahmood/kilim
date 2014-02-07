/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Timer;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A base class for tasks. A task is a lightweight thread (it contains its
 * own stack in the form of a fiber). A concrete subclass of Task must
 * provide a pausable execute method.
 */
public abstract class Task {
    public volatile Thread currentThread = null;

    static PauseReason yieldReason = new YieldReason();
    /**
     * Task id, automatically generated
     */
    public final int id;
    static final AtomicInteger idSource = new AtomicInteger();

    /**
     * The stack manager in charge of rewinding and unwinding
     * the stack when Task.pause() is called.
     */
    protected Fiber fiber;

    /**
     * The reason for pausing (duh) and performs the role of a await
     * condition in CCS. This object is responsible for resuming
     * the task.
     *
     * @see kilim.PauseReason
     */
    protected PauseReason pauseReason;

    /**
     * running = true when it is put on the schdulers run Q (by Task.resume()).
     * The Task.runExecute() method is called at some point; 'running' remains
     * true until the end of runExecute (where it is reset), at which point a
     * fresh decision is made whether the task needs to continue running.
     */
    protected boolean running = false;
    protected boolean done = false;

    /**
     *
     */
    int numActivePins;

    public Object exitResult = "OK";

    // TODO: move into a separate timer service or into the schduler.
    public final static Timer timer = new Timer(true);

    public Task() {
        // TODO remove id generation to minimize overhead
        id = idSource.incrementAndGet();
        fiber = new Fiber(this);
    }

    public int id() {
        return id;
    }

    /**
     * The generated code calls Fiber.upEx, which in turn calls
     * this to find out out where the current method is w.r.t
     * the closest _runExecute method.
     *
     * @return the number of stack frames above _runExecute(), not including
     * this method
     */
    public int getStackDepth() {
        final String DELIMIT_CALLER = "resumeExecution";

        StackTraceElement[] stes;
        stes = new Exception().getStackTrace();
        int len = stes.length;
        for (int i = 0; i < len; i++) {
            StackTraceElement ste = stes[i];
            if (ste.getMethodName().equals(DELIMIT_CALLER)) {
                // discounting WorkerThread.run, Task._runExecute, and Scheduler.getStackDepth
                return i - 1;
            }
        }
        throw new AssertionError("Expected task to be run by WorkerThread");
    }

    /**
     * This is a placeholder that doesn't do anything useful.
     * Weave replaces the call in the bytecode from
     * invokestateic Task.getCurrentTask
     * to
     * load fiber
     * getfield task
     */
    public static Task getCurrentTask() throws Pausable {
        return null;
    }

    /**
     * Analogous to System.exit, except an Object can
     * be used as the exit value
     */

    public static void exit(Object aExitValue) throws Pausable {
    }

    public static void exit(Object aExitValue, Fiber f) {
        assert f.pc == 0 : "f.pc != 0";
        f.task.setPauseReason(new TaskDoneReason(aExitValue));
        f.togglePause();
    }

    /**
     * Exit the task with a throwable indicating an error condition. The value
     * is conveyed through the exit mailslot (see informOnExit).
     * All exceptions trapped by the task scheduler also set the error result.
     */
    public static void errorExit(Throwable ex) throws Pausable {
    }

    public static void errorExit(Throwable ex, Fiber f) {
        assert f.pc == 0 : "fc.pc != 0";
        f.task.setPauseReason(new TaskDoneReason(ex));
        f.togglePause();
    }

    public static void errNotWoven() {
        System.err.println("############################################################");
        System.err.println("Task has either not been woven or the classpath is incorrect");
        System.err.println("############################################################");
        Thread.dumpStack();
        System.exit(0);
    }

    public static void errNotWoven(Task t) {
        System.err.println("############################################################");
        System.err.println("Task " + t.getClass() + " has either not been woven or the classpath is incorrect");
        System.err.println("############################################################");
        Thread.dumpStack();
        System.exit(0);
    }

    static class ArgState extends kilim.State {
        Object mthd;
        Object obj;
        Object[] fargs;
    }

    /**
     * Invoke a pausable method via reflection. Equivalent to Method.invoke().
     *
     * @param mthd:   The method to be invoked. (Implementation note: the corresponding woven method is invoked instead).
     * @param target: The object on which the method is invoked. Can be null if the method is static.
     * @param args:   Arguments to the method
     * @return
     * @throws Pausable
     * @throws IllegalAccessException
     * @throws IllegalArgumentException
     * @throws InvocationTargetException
     */
    public static Object invoke(Method mthd, Object target, Object... args)
            throws Pausable, IllegalAccessException, IllegalArgumentException,
            InvocationTargetException {
        Fiber f = getCurrentTask().fiber;
        Object[] fargs;
        if (f.pc == 0) {
            mthd = getWovenMethod(mthd);
            // Normal invocation.
            if (args == null) {
                fargs = new Object[1];
            } else {
                fargs = new Object[args.length + 1]; // for fiber
                System.arraycopy(args, 0, fargs, 0, args.length);
            }
            fargs[fargs.length - 1] = f;
        } else {
            // Resuming from a previous yield
            ArgState as = (ArgState) f.getState();
            mthd = (Method) as.mthd;
            target = as.obj;
            fargs = as.fargs;
        }
        f.down();
        Object ret = mthd.invoke(target, fargs);
        switch (f.up()) {
            case Fiber.NOT_PAUSING__NO_STATE:
            case Fiber.NOT_PAUSING__HAS_STATE:
                return ret;
            case Fiber.PAUSING__NO_STATE:
                ArgState as = new ArgState();
                as.obj = target;
                as.fargs = fargs;
                as.pc = 1;
                as.mthd = mthd;
                f.setState(as);
                return null;
            case Fiber.PAUSING__HAS_STATE:
                return null;
        }
        throw new IllegalAccessException("Internal Error");
    }

    // Given a method corresp. to "f(int)", return the equivalent woven method for "f(int, kilim.Fiber)" 
    private static Method getWovenMethod(Method m) {
        Class<?>[] ptypes = m.getParameterTypes();
        if (!(ptypes.length > 0 && ptypes[ptypes.length - 1].getName().equals("kilim.Fiber"))) {
            // The last param is not "Fiber", so m is not woven.
            // Get the woven method corresponding to m(..., Fiber)
            boolean found = false;
            LOOP:
            for (Method wm : m.getDeclaringClass().getDeclaredMethods()) {
                if (wm != m && wm.getName().equals(m.getName())) {
                    // names match. Check if the wm has the exact parameter types as m, plus a fiber.
                    Class<?>[] wptypes = wm.getParameterTypes();
                    if (wptypes.length != ptypes.length + 1 ||
                            !(wptypes[wptypes.length - 1].getName().equals("kilim.Fiber"))) {
                        continue LOOP;
                    }
                    for (int i = 0; i < ptypes.length; i++) {
                        if (ptypes[i] != wptypes[i]) {
                            continue LOOP;
                        }
                    }
                    m = wm;
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new IllegalArgumentException("Found no pausable method corresponding to supplied method: " + m);
            }
        }
        return m;
    }

    /**
     * Yield cooperatively to the next task waiting to use the thread.
     */
    public static void yield() throws Pausable {
        errNotWoven();
    }

    public static void yield(Fiber f) {
        if (f.pc == 0) {
            f.task.setPauseReason(yieldReason);
        } else {
            f.task.setPauseReason(null);
        }
        f.togglePause();
        f.task.checkKill();
    }

    /**
     * Ask the current task to pause with a reason object, that is
     * responsible for resuming the task when the reason (for pausing)
     * is not valid any more.
     *
     * @param pauseReason the reason
     */
    public static void pause(PauseReason pauseReason) throws Pausable {
        errNotWoven();
    }

    public static void pause(PauseReason pauseReason, Fiber f) {
        if (f.pc == 0) {
            f.task.setPauseReason(pauseReason);
        } else {
            f.task.setPauseReason(null);
        }
        f.togglePause();
        f.task.checkKill();
    }

    /*
     * This is the fiber counterpart to the execute() method
     * that allows us to detec when a subclass has not been woven.
     * 
     * If the subclass has not been woven, it won't have an
     * execute method of the following form, and this method 
     * will be called instead. 
     */
    public void execute() throws Pausable, Exception {
        errNotWoven(this);
    }

    public void execute(Fiber f) throws Exception {
        errNotWoven(this);
    }

    public String toString() {
        return "" + id + "(running=" + running + ",pr=" + pauseReason + ")";
    }

    public String dump() {
        synchronized (this) {
            return "" + id +
                    "(running=" + running +
                    ", pr=" + pauseReason +
                    ")";
        }
    }

    public void pinToThread() {
        numActivePins++;
    }

    public void unpinFromThread() {
        numActivePins--;
    }

    final protected void setPauseReason(PauseReason pr) {
        pauseReason = pr;
    }

    public final PauseReason getPauseReason() {
        return pauseReason;
    }

    public boolean isDone() {
        return done;
    }

    /**
     * synchronously execute the current task
     *
     * @return true if Task completed execution without being paused
     * @throws NotPausable
     */
    public boolean resumeExecution() throws NotPausable {
        final Fiber f = fiber;
        boolean isDone = false;
        try {
            // start execute. fiber is wound to the beginning.
            execute(f.begin());

            // execute() done. Check fiber if it is pausing and reset it.
            isDone = f.end() || (pauseReason instanceof TaskDoneReason);
            assert (pauseReason == null && isDone) || (pauseReason != null && !isDone) : "pauseReason:" + pauseReason + ",isDone =" + isDone;
        } catch (final Throwable th) {
            th.printStackTrace();
            // Definitely done
            setPauseReason(new TaskDoneReason(th));
            isDone = true;
        }

        if (isDone) {
            done = true;
            // inform on exit
            assert numActivePins == 0 : ("Task ended but has " + numActivePins + " active locks");
        } else {
            assert numActivePins == 0 : ("Task suspended but has " + numActivePins + " active locks");
        }

        return isDone;
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this;
    }

    @Override
    public int hashCode() {
        return id;
    }

    public void checkKill() {
    }

}

