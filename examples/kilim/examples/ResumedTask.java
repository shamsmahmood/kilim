package kilim.examples;

import kilim.Pausable;
import kilim.PauseReason;
import kilim.Task;

/**
 * @author Shams Imam (shams@rice.edu)
 */
public class ResumedTask extends Task {

    public static void main(String[] args) {
        System.out.println("FirstTask.main starts...");
        final Task t1 = new ResumedTask();
        t1.resumeExecution();
        System.out.println("FirstTask.main task started");
        while (true) {
            final MyPauseReason pauseReason = (MyPauseReason) t1.getPauseReason();
            if (pauseReason != null && pauseReason.isReasonValid()) {
                if (pauseReason.id == 0) {
                    break;
                }
                System.out.println("FirstTask.main found pause reason: " + pauseReason);
                pauseReason.setReasonValid(false);
                t1.resumeExecution();
            }
        }
        System.out.println("FirstTask.main ends.");
        System.exit(0);
    }

    @Override
    public void execute() throws Pausable, Exception {
        System.out.println("FirstTask.execute-1");
        for (int i = 6; i >= 0; i--) {
            Task.pause(new MyPauseReason(i));
            System.out.println("FirstTask.execute-2." + i);
        }
    }

    private static class MyPauseReason implements PauseReason {

        public final int id;
        private boolean reasonValid;

        MyPauseReason(int id) {
            this.id = id;
            this.reasonValid = true;
        }

        public void setReasonValid(boolean reasonValid) {
            this.reasonValid = reasonValid;
        }

        public boolean isReasonValid() {
            return reasonValid;
        }

        /**
         * @param t The given task
         * @return true if the given task's reason for pausing is still valid.
         */
        @Override
        public boolean isValid(Task t) {
            return reasonValid;
        }

        @Override
        public String toString() {
            return "MyPauseReason{" +
                    "reasonValid=" + reasonValid +
                    ", id=" + id +
                    '}';
        }
    }
}
