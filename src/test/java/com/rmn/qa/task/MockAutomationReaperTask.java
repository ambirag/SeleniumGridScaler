package com.rmn.qa.task;

import com.rmn.qa.RegistryRetriever;
import com.rmn.qa.aws.VmManager;

public class MockAutomationReaperTask extends AutomationReaperTask {
	
	public boolean checkNodeForHungSessions = true;

	public MockAutomationReaperTask(RegistryRetriever registryRetriever, VmManager ec2) {
		super(registryRetriever, ec2);
	}

	@Override
	protected boolean checkNodeForHungSessions(String instanceToFind, String ipAddress) {
		return checkNodeForHungSessions;
	}
}
