package org.sakaiproject.component.app.scheduler.jobs;

import java.util.Iterator;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.sakaiproject.assignment.api.Assignment;
import org.sakaiproject.assignment.api.AssignmentService;
import org.sakaiproject.entity.api.EntityPropertyNotDefinedException;
import org.sakaiproject.entity.api.EntityPropertyTypeException;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;

public class UndeleteAssignments implements Job {

	private static final Log LOG = LogFactory.getLog(UndeleteAssignments.class);
	private AssignmentService assignmentService;
	private SessionManager sessionManager;
	private String context="e9501306-1c1c-4694-b2bf-b9056c8f1bff";
	
	public void execute(JobExecutionContext arg0) throws JobExecutionException {
		
	    // set the user information into the current session
	    Session sakaiSession = sessionManager.getCurrentSession();
	    sakaiSession.setUserId("admin");
	    sakaiSession.setUserEid("admin");
	    
		// TODO Auto-generated method stub
		Iterator assingments = assignmentService.getAssignmentsForContext(context);
		while (assingments.hasNext()) {
			Assignment ass =  (Assignment)assingments.next();
			ResourceProperties rp = ass.getProperties();
			//"CHEF:assignment_deleted"
			
			try {
				if (rp.getBooleanProperty("CHEF:assignment_deleted")) {
					LOG.info("undeleting" + ass.getTitle());
					rp.removeProperty("CHEF:assignment_deleted");
					rp.addProperty("CHEF:assignment_deleted", "fasle");
					
				}
			} catch (EntityPropertyNotDefinedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (EntityPropertyTypeException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		}
		
	}

	public void setAssignmentService(AssignmentService assignmentService) {
		this.assignmentService = assignmentService;
	}

	public void setSessionManager(SessionManager sessionManager) {
		this.sessionManager = sessionManager;
	}

}
