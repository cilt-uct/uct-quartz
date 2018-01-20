package org.sakaiproject.component.app.scheduler.jobs;

import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.lang3.StringUtils;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.sakaiproject.assignment.api.model.Assignment;
import org.sakaiproject.assignment.api.AssignmentService;
import org.sakaiproject.entity.api.EntityPropertyNotDefinedException;
import org.sakaiproject.entity.api.EntityPropertyTypeException;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.InUseException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UndeleteAssignments implements Job {

        private static final Logger LOG = LoggerFactory.getLogger(UndeleteAssignments.class);
	private AssignmentService assignmentService;
	private SessionManager sessionManager;
	private String context="e9501306-1c1c-4694-b2bf-b9056c8f1bff";
	
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

                        LOG.info("Assignment " + ass.getTitle()+ " deleted status: " + deleted);
                        if (deleted != null) {
                            LOG.info("undeleting" + ass.getTitle() + " for site " + context);
                            rp.remove(ResourceProperties.PROP_ASSIGNMENT_DELETED);

                            assignmentService.updateAssignment(ass);
                        }
                    } catch (PermissionException e) {
                        LOG.warn("Could not undelete assignment: {}, {}", ass.getId(), e.getMessage());
                    }
                }
                
            } catch (Exception e) {
                LOG.error("UndeleteAssignments(): " + e.getClass().getName() + " : " + e.getMessage()); 
            }	
	}

	public void setAssignmentService(AssignmentService assignmentService) {
		this.assignmentService = assignmentService;
	}

	public void setSessionManager(SessionManager sessionManager) {
		this.sessionManager = sessionManager;
	}

}
