/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.test;

import junit.framework.Test;
import junit.framework.TestSuite;

public class AllWoven extends TestSuite {
    public static Test suite() {
        final TestSuite ret = new AllWoven();

        ret.addTestSuite(TestBasicBlock.class);
        ret.addTestSuite(TestClassInfo.class);
        ret.addTestSuite(TestDynamicWeaver.class);
        ret.addTestSuite(TestExprs.class);
        ret.addTestSuite(TestFlow.class);
        ret.addTestSuite(TestFrame.class);
        ret.addTestSuite(TestInvalidPausables.class);
        ret.addTestSuite(TestJSR.class);
        ret.addTestSuite(TestRing.class);
        ret.addTestSuite(TestTypeDesc.class);
        ret.addTestSuite(TestUsage.class);
        ret.addTestSuite(TestValue.class);

        return ret;
    }
}
