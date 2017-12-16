package com.rmn.qa.task;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceState;
import com.amazonaws.services.ec2.model.Reservation;
import com.rmn.qa.AutomationContext;
import com.rmn.qa.AutomationDynamicNode;
import com.rmn.qa.AutomationRunContext;
import com.rmn.qa.AutomationUtils;
import com.rmn.qa.BaseTest;
import com.rmn.qa.MockVmManager;

import junit.framework.Assert;

public class AutomationReaperTaskTest extends BaseTest {
	
	@Before
	public void reset() {
		AutomationContext.refreshContext();
	}
	
	@After
	public void clear() {
		AutomationUtils.terminateBySec = false;
	}

    @Test
    public void testShutdown() {
        MockVmManager ec2 = new MockVmManager();
        Reservation reservation = new Reservation();
        String instanceId = "foobar";
        Instance instance = new Instance()
        		.withState(new InstanceState().withCode(45))
        		.withInstanceId(instanceId)
        		.withLaunchTime(AutomationUtils.modifyDate(new Date(),-5,Calendar.HOUR));
        reservation.setInstances(Arrays.asList(instance));
        ec2.setReservations(Arrays.asList(reservation));
        AutomationReaperTask task = new AutomationReaperTask(null,ec2);
        task.run();
        Assert.assertTrue("Node should be terminated as it was empty", ec2.isTerminated());
    }
    
    @Test
    public void testShutdownTerminateBySec() {
        MockVmManager ec2 = new MockVmManager();
        Reservation reservation = new Reservation();
        Instance instance = new Instance();
        instance.setState(new InstanceState().withCode(10));
        String instanceId = "foo";
        instance.setInstanceId(instanceId);
        instance.setLaunchTime(AutomationUtils.modifyDate(new Date(),-5,Calendar.HOUR));
        reservation.setInstances(Arrays.asList(instance));
        ec2.setReservations(Arrays.asList(reservation));
        AutomationUtils.terminateBySec = true;
        AutomationReaperTask task = new MockAutomationReaperTask(null,ec2);
        task.run();
        Assert.assertTrue("Node should be tracked by nodeEmptyTime", task.nodeEmptyTime.containsKey(instanceId));
        task.nodeEmptyTime.put(instanceId, System.currentTimeMillis() - AutomationRunContext.EXPIRED_LIFE_LENGTH_IN_MIL_SECONDS - AutomationRunContext.TERMINATE_LIFE_LENGTH_IN_MIL_SECONDS);
        task.run();
        Assert.assertTrue("Node should be terminated as it was empty", ec2.isTerminated());
    }

    @Test
    // Tests that a node that is not old enough is not terminated
    public void testNoShutdownTooRecent() {
        MockVmManager ec2 = new MockVmManager();
        Reservation reservation = new Reservation();
        Instance instance = new Instance();
        String instanceId = "foo";
        instance.setInstanceId(instanceId);
        instance.setLaunchTime(AutomationUtils.modifyDate(new Date(),-15,Calendar.MINUTE));
        reservation.setInstances(Arrays.asList(instance));
        ec2.setReservations(Arrays.asList(reservation));
        AutomationReaperTask task = new AutomationReaperTask(null,ec2);
        task.run();
        Assert.assertFalse("Node should NOT be terminated as it was not old", ec2.isTerminated());
    }
    
    @Test
    // Tests that a node that is not empty for a specific period is not terminated
    public void testNoShutdownTooRecentTerminateBySec() {
        MockVmManager ec2 = new MockVmManager();
        Reservation reservation = new Reservation();
        Instance instance = new Instance();
        instance.setState(new InstanceState().withCode(10));
        String instanceId = "foo";
        instance.setInstanceId(instanceId);
        reservation.setInstances(Arrays.asList(instance));
        ec2.setReservations(Arrays.asList(reservation));
        AutomationUtils.terminateBySec = true;
        AutomationReaperTask task = new MockAutomationReaperTask(null,ec2);
        task.run();
        Assert.assertTrue("Node should be tracked by nodeEmptyTime", task.nodeEmptyTime.containsKey(instanceId));
        task.nodeEmptyTime.put(instanceId, System.currentTimeMillis() + 5 * 60 * 1000 - AutomationRunContext.EXPIRED_LIFE_LENGTH_IN_MIL_SECONDS - AutomationRunContext.TERMINATE_LIFE_LENGTH_IN_MIL_SECONDS);
        task.run();
        Assert.assertFalse("Node should NOT be terminated as it was not old", ec2.isTerminated());
    }

    @Test
    // Tests that a node that is being tracked internally is not shut down
    public void testNoShutdownNodeTracked() {
        MockVmManager ec2 = new MockVmManager();
        Reservation reservation = new Reservation();
        Instance instance = new Instance();
        String instanceId = "foo";
        AutomationContext.getContext().addNode(new AutomationDynamicNode("faky",instanceId,null,null,new Date(),1));
        instance.setInstanceId(instanceId);
        instance.setLaunchTime(AutomationUtils.modifyDate(new Date(),-5,Calendar.HOUR));
        reservation.setInstances(Arrays.asList(instance));
        ec2.setReservations(Arrays.asList(reservation));
        AutomationReaperTask task = new AutomationReaperTask(null,ec2);
        task.run();
        Assert.assertFalse("Node should NOT be terminated as it was tracked internally", ec2.isTerminated());
        
        AutomationUtils.terminateBySec = true;
        task.run();
        Assert.assertFalse("Node should NOT be terminated as it was tracked internally", ec2.isTerminated());
    }

    @Test
    // Tests that the hardcoded name of the task is correct
    public void testTaskName() {
        AutomationReaperTask task = new AutomationReaperTask(null,null);
        Assert.assertEquals("Name should be the same",AutomationReaperTask.NAME, task.getDescription()  );
    }
}
