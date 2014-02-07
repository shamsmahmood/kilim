/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim;

public class TaskDoneReason implements PauseReason {
    private final Object exitObj;

    TaskDoneReason(Object o) {
        exitObj = o;
    }

    public Object exitReason() {
        return exitObj;
    }

    public boolean isValid(Task t) {
        // When a task is done, it is reason to continue pausing
        return true;
    }

    public String toString() {
        return "Done. Exit msg = " + exitObj;
    }
}