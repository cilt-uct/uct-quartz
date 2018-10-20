package org.sakaiproject.component.app.scheduler.jobs;

import java.util.List;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.sakaiproject.coursemanagement.api.CourseManagementAdministration;
import org.sakaiproject.coursemanagement.api.CourseManagementService;
import org.sakaiproject.coursemanagement.api.CourseSet;
import org.sakaiproject.db.api.SqlService;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UpdateOrgNames implements Job {


	private static final String ADMIN = "admin";
	
	private SqlService sqlService;
	public void setSqlService(SqlService ss) {
		this.sqlService = ss;
	}
	
	private CourseManagementService cmService;
	public void setCourseManagementService(CourseManagementService cms) {
		this.cmService = cms;
	}
	
	private CourseManagementAdministration cmAdminService;
	public void setCourseManagementAdministration(CourseManagementAdministration cma) {
		this.cmAdminService = cma;
	}
	
	
	private SessionManager sessionManager;
	public void setSessionManager(SessionManager s) {
		this.sessionManager = s;
	}
	
	
	public void execute(JobExecutionContext arg0) throws JobExecutionException {
		// TODO Auto-generated method stub
		
		Session sakaiSession = sessionManager.getCurrentSession();
	    sakaiSession.setUserId(ADMIN);
	    sakaiSession.setUserEid(ADMIN);
		
		List<CourseSet> sets = cmService.findCourseSets("Department");
		log.debug("got a list of: " + sets.size() + " course sets");
		for (int i = 0; i < sets.size(); i++ ) {
			CourseSet thisSet = (CourseSet)sets.get(i);
			
			String descr = this.getOrgNameByEid(thisSet.getEid());
			if (descr != null && descr.length() > 0 ) {
				thisSet.setTitle(thisSet.getEid() + " - " + descr);
				//if the name contains "residence" change the type"
				if (descr.indexOf("Residence")> -1)
					thisSet.setCategory("Residence");

				cmAdminService.updateCourseSet(thisSet);
			}
			
		}
 
	}

	
	
	private String getOrgNameByEid(String modOrgUnit) {
		
		String statement = "Select Description from UCT_ORG where ORG = ?";
		Object[] fields = new Object[]{modOrgUnit};
		List<String> result = sqlService.dbRead(statement, fields, null);
		if (result.size() > 0) {
			log.info("got org unit of " + (String)result.get(0));
			return (String)result.get(0);
		} else {
			log.info("Unknown org code of " + modOrgUnit + " received" );
		}
				
		return null;
	}
}
