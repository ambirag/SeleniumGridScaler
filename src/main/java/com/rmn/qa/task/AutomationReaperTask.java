package com.rmn.qa.task;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.google.common.annotations.VisibleForTesting;
import com.rmn.qa.AutomationContext;
import com.rmn.qa.AutomationRunContext;
import com.rmn.qa.AutomationUtils;
import com.rmn.qa.RegistryRetriever;
import com.rmn.qa.aws.VmManager;

public class AutomationReaperTask extends AbstractAutomationCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(AutomationReaperTask.class);
    @VisibleForTesting static final String NAME = "VM Reaper Task";

    private VmManager ec2;
    
    protected Map<String, Long> nodeEmptyTime = new HashMap<>();

    /**
     * Constructs a registry task with the specified context retrieval mechanism
     * @param registryRetriever Represents the retrieval mechanism you wish to use
     */
    public AutomationReaperTask(RegistryRetriever registryRetriever,VmManager ec2) {
        super(registryRetriever);
        this.ec2 = ec2;
    }
    @Override
    public void doWork() {
        log.info("Running " + AutomationReaperTask.NAME);
        DescribeInstancesRequest describeInstancesRequest = new DescribeInstancesRequest();
        Filter filter = new Filter("tag:LaunchSource");
        filter.withValues("SeleniumGridScaler"+AutomationUtils.getHubInstanceId());
        describeInstancesRequest.withFilters(filter);
        List<Reservation> reservations = ec2.describeInstances(describeInstancesRequest);
        for(Reservation reservation : reservations) {
            for(Instance instance : reservation.getInstances()) {
		String instanceId = instance.getInstanceId();
		if(AutomationUtils.terminateBySec) {
			// if we found a node we're not tracking, and there is no hung sessions on it for 15 minutes, we should terminate it
			if(!AutomationContext.getContext().nodeExists(instanceId) && instance.getState().getCode() != 48 // 48 == terminated
					&& checkNodeForHungSessions(instanceId, instance.getPrivateIpAddress())) {
				// 
				if(nodeEmptyTime.get(instance.getInstanceId()) != null) {
					long emptyTime = System.currentTimeMillis() - nodeEmptyTime.get(instanceId);
					if(emptyTime >= AutomationRunContext.EXPIRED_LIFE_LENGTH_IN_MIL_SECONDS + AutomationRunContext.TERMINATE_LIFE_LENGTH_IN_MIL_SECONDS) {
						log.info("TerminateBySec: Terminating orphaned node: " + instanceId);
				    ec2.terminateInstance(instanceId);
				    nodeEmptyTime.remove(instance.getInstanceId());
					}
				} else {
					nodeEmptyTime.put(instanceId, System.currentTimeMillis());
				}
			} else {
					nodeEmptyTime.remove(instance.getInstanceId());
			}
		} else {
			// Look for orphaned nodes
                    Date threshold = AutomationUtils.modifyDate(new Date(),-90, Calendar.MINUTE);
                    
                    // If we found a node old enough AND we're not internally tracking it, this means this is an orphaned node and we should terminate it
                    if(threshold.after(instance.getLaunchTime()) && !AutomationContext.getContext().nodeExists(instanceId) && instance.getState().getCode() != 48) { // 48 == terminated
                        log.info("Terminating orphaned node: " + instanceId);
                        ec2.terminateInstance(instanceId);
                    }
		}
                
            }
        }
    }
    
    /**
     * Checks the instance in question to see if any browser slots are 'hung' that would otherwise block terminating the instance
     * @param instanceToFind
     * @return
     */
    protected boolean checkNodeForHungSessions(String instanceToFind, String ipAddress) {
        //AutomationDynamicNode node = AutomationContext.getContext().getNode(instanceToFind);
        // If the IP is null, there isn't anything we can do with the node and we have to treat it as not empty
        if (ipAddress == null) {
            return false;
        } else {
            String url = String.format("http://%s:5555/wd/hub/sessions", ipAddress);
            log.info("Orphaned nodes URL: " + url);
            try {
                log.info("Attempting to retrieve in progress sessions before termination for node: " + instanceToFind);
                URL obj = new URL(url);
                HttpURLConnection con = (HttpURLConnection) obj.openConnection();
                con.setConnectTimeout(10000);
                con.setReadTimeout(10000);

                BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                String inputLine;
                StringBuffer responseBuffer = new StringBuffer();

                while ((inputLine = in.readLine()) != null) {
                    responseBuffer.append(inputLine);
                }
                in.close();
                String response = responseBuffer.toString();
                if  (response != null && !response.contains("capabilities")) {
                    log.info("Node had hung sessions but will be terminated anyways.");
                    return true;
                }
            } catch(SocketTimeoutException ste) {
                log.warn("Timeout attempting to retrieve in progress sessions for node: " + instanceToFind, ste);
            } catch (Exception e) {
                log.warn("Error retrieving sessions from node", e);
                // We don't need an explicit return here as we can just reuse the one below
            }
            return false;
        }
    }

    @Override
    public String getDescription() {
        return AutomationReaperTask.NAME;
    }
    
}
