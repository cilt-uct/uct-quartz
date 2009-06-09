package org.sakaiproject.component.app.scheduler.jobs;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.sakaiproject.coursemanagement.api.CourseManagementService;
import org.sakaiproject.coursemanagement.api.EnrollmentSet;
import org.sakaiproject.db.api.SqlService;
import org.sakaiproject.email.api.EmailService;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;

public class CleanInactiveStudents implements Job {

	private static final Log LOG = LogFactory.getLog(CleanInactiveStudents.class);
	private static final String ADMIN = "admin";
	
	private SqlService sqlService;
	public void setSqlService(SqlService ss) {
		this.sqlService = ss;
	}
	
	private CourseManagementService courseManagementService;
	public void setCourseManagementService(CourseManagementService cs) {
		courseManagementService = cs;
	}
	
	
	private EmailService emailService;
	public void setEmailService(EmailService e) {
		this.emailService = e;
	}
	
	private SessionManager sessionManager;
	public void setSessionManager(SessionManager s) {
		this.sessionManager = s;
	}
	@Override
	public void execute(JobExecutionContext context)
			throws JobExecutionException {
	
	    Session sakaiSession = sessionManager.getCurrentSession();
	    sakaiSession.setUserId(ADMIN);
	    sakaiSession.setUserEid(ADMIN);
		
		List<String> removedEids = new ArrayList<String>();
		String sql = "select user_id from CM_ENROLLMENT_T join CM_ENROLLMENT_SET_T on CM_ENROLLMENT_T.ENROLLMENT_SET=CM_ENROLLMENT_SET_T.ENROLLMENT_SET_ID where ENTERPRISE_ID like '%2008' group by user_id having count(ENROLLMENT_ID) = 2; ";
		List<String> eids = sqlService.dbRead(sql);
		for (int i = 0; i < eids.size(); i++) {
			String eid = eids.get(i);
			LOG.info("Checking: " + eid);
			//check the students current enrolements
			Set<EnrollmentSet> enrollments = courseManagementService.findCurrentlyEnrolledEnrollmentSets(eid);
			LOG.info("found: " + enrollments.size() + " enrollments for the student");
			if (enrollments.size() >= 2) {
				LOG.warn("This student has 2 or fewer enrollments!");
				removedEids.add(eid);
			}
		}
		StringBuilder sb = new StringBuilder();
		sb.append("we would inactivate these students: \r\n");
		for (int i =0; i < removedEids.size(); i++) {
			sb.append(removedEids.get(i));
			sb.append("\r\n");
		}
		
		emailService.send("help@vula.uct.ac.za", "david.horwitz@uc.ac.za", "cleaned up users", sb.toString(), null, null, null);
	}

}
