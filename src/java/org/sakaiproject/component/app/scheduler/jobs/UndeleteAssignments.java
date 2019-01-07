/**********************************************************************************
 *
 * Copyright (c) 2019 University of Cape Town
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.opensource.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **********************************************************************************/
package org.sakaiproject.component.app.scheduler.jobs;

import java.util.Map;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.sakaiproject.assignment.api.AssignmentService;
import org.sakaiproject.assignment.api.model.Assignment;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UndeleteAssignments implements Job {


	private AssignmentService assignmentService;
	private SessionManager sessionManager;
	private String context= "e9501306-1c1c-4694-b2bf-b9056c8f1bff";
	
        /**
         * Undelete Assignment
         * Method is very similar to: 
         * webservices/cxf/src/java/org/sakaiproject/webservices/Assignments.java
         *   public String undeleteAssignments
         * @param arg0
         * @throws JobExecutionException 
         */
	public void execute(JobExecutionContext arg0) throws JobExecutionException {
		
	    // set the user information into the current session
	    Session sakaiSession = sessionManager.getCurrentSession();
	    sakaiSession.setUserId("admin");
	    sakaiSession.setUserEid("admin");
	    
            try {
                
                for (Assignment ass : assignmentService.getAssignmentsForContext(context)) {
                    Map<String, String> rp = ass.getProperties();

                    try {
                        String deleted = rp.get(ResourceProperties.PROP_ASSIGNMENT_DELETED);

                        log.info("Assignment " + ass.getTitle()+ " deleted status: " + deleted);
                        if (deleted != null) {
                            log.info("undeleting" + ass.getTitle() + " for site " + context);
                            rp.remove(ResourceProperties.PROP_ASSIGNMENT_DELETED);

                            assignmentService.updateAssignment(ass);
                        }
                    } catch (PermissionException e) {
                        log.warn("Could not undelete assignment: {}, {}", ass.getId(), e.getMessage());
                    }
                }
                
            } catch (Exception e) {
                log.error("UndeleteAssignments(): " + e.getClass().getName() + " : " + e.getMessage()); 
            }	
	}

	public void setAssignmentService(AssignmentService assignmentService) {
		this.assignmentService = assignmentService;
	}

	public void setSessionManager(SessionManager sessionManager) {
		this.sessionManager = sessionManager;
	}

}
