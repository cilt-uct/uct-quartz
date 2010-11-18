package za.ac.uct.cet.sakai.scheduler.courses;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.sakaiproject.coursemanagement.api.CourseManagementAdministration;
import org.sakaiproject.coursemanagement.api.CourseManagementService;
import org.sakaiproject.coursemanagement.api.Membership;
import org.sakaiproject.coursemanagement.api.exception.IdNotFoundException;
import org.sakaiproject.db.api.SqlService;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;

public class SynchResearchCourses implements Job {
	private static final String ADMIN = "admin";
	private static final Log LOG = LogFactory.getLog(SynchResearchCourses.class);
	
	private CourseManagementService courseManagementService;
	private CourseManagementAdministration courseAdmin;

	public void setCourseManagementService(CourseManagementService cs) {
		courseManagementService = cs;
	}

	private SessionManager sessionManager;
	public void setSessionManager(SessionManager s) {
		this.sessionManager = s;
	}

	private SqlService sqlService;	
	public void setSqlService(SqlService sqlService) {
		this.sqlService = sqlService;
	}
	public void setCourseManagementAdministration(CourseManagementAdministration cs) {
		courseAdmin = cs;
	}
	public void execute(JobExecutionContext arg0) throws JobExecutionException {
		// set the user information into the current session
		Session sakaiSession = sessionManager.getCurrentSession();
		sakaiSession.setUserId(ADMIN);
		sakaiSession.setUserEid(ADMIN);
		
		//we need to get all active courses in a course set
		String sql = "select CM_MEMBER_CONTAINER_T.enterprise_id, CM_MEMBER_CONTAINER_T.end_date from CM_MEMBER_CONTAINER_T join CM_ACADEMIC_SESSION_T on CM_MEMBER_CONTAINER_T.academic_session=CM_ACADEMIC_SESSION_T.ACADEMIC_SESSION_ID where CM_ACADEMIC_SESSION_T.ENTERPRISE_ID = '2010' and CM_MEMBER_CONTAINER_T.end_Date > '2010-12-30'";
		
		List<String> vals = sqlService.dbRead(sql);
		for (int i =0; i < vals.size(); i++) {
			String courseEid = vals.get(i);
			LOG.info("going to synch " + courseEid);
			Set<Membership> members = courseManagementService.getSectionMemberships(courseEid);
			Iterator<Membership> mit = members.iterator();
			
			//each of these needs to be added to the exivelent 2011 course
			String nextCourseEid = courseEid.replace(",2010", ",2011");
			LOG.info("going to add " + members.size() + " members to " + nextCourseEid + " from " + courseEid);
			while (mit.hasNext()) {
				Membership membership = mit.next();
				try {
				courseAdmin.addOrUpdateSectionMembership(membership.getUserId(), membership.getRole(), nextCourseEid, membership.getRole());
				}
				catch (IdNotFoundException e) {
					LOG.warn("could not find course: " + nextCourseEid);
					//TODO should we clear the course?
				}
			}
		}
		
	}

}
