package org.sakaiproject.component.app.scheduler.jobs;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
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
import org.sakaiproject.coursemanagement.api.EnrollmentSet;
import org.sakaiproject.db.api.SqlService;
import org.sakaiproject.email.api.EmailService;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserAlreadyDefinedException;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.user.api.UserEdit;
import org.sakaiproject.user.api.UserLockedException;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.user.api.UserPermissionException;

public class CleanInactiveStudents implements Job {

	private static final String INACTIVE_STUDENT_TYPE = "inactiveStudent";
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
	
	private UserDirectoryService userDirectoryService;
	public void setUserDirectoryService(UserDirectoryService userDirectoryService) {
		this.userDirectoryService = userDirectoryService;
	}

	private EmailService emailService;
	public void setEmailService(EmailService e) {
		this.emailService = e;
	}
	
	private SessionManager sessionManager;
	public void setSessionManager(SessionManager s) {
		this.sessionManager = s;
	}
	
	private CourseManagementAdministration courseAdmin;
	
	public void setCourseAdmin(CourseManagementAdministration courseAdmin) {
		this.courseAdmin = courseAdmin;
	}


	public void execute(JobExecutionContext context)
			throws JobExecutionException {
	
	    Session sakaiSession = sessionManager.getCurrentSession();
	    sakaiSession.setUserId(ADMIN);
	    sakaiSession.setUserEid(ADMIN);
		
		List<String> removedEids = new ArrayList<String>();
		int studentCount = 0;
		String sql = "select user_id from CM_ENROLLMENT_T join CM_ENROLLMENT_SET_T on CM_ENROLLMENT_T.ENROLLMENT_SET=CM_ENROLLMENT_SET_T.ENROLLMENT_SET_ID where ENTERPRISE_ID like '%2008' group by user_id having count(ENROLLMENT_ID) = 2; ";
		List<String> eids = sqlService.dbRead(sql);
		for (int i = 0; i < eids.size(); i++) {
			String eid = eids.get(i);
			LOG.info("Checking: " + eid);
			//check the students current enrolements
			Set<EnrollmentSet> enrollments = courseManagementService.findCurrentlyEnrolledEnrollmentSets(eid);
			LOG.info("found: " + enrollments.size() + " enrollments for the student");
			if (enrollments.size() <= 2) {
				LOG.warn("This student has 2 or fewer enrollments!");
				try {
					User u = userDirectoryService.getUserByEid(eid);
					if (!INACTIVE_STUDENT_TYPE.equals(u.getType())) {
						UserEdit ue = userDirectoryService.editUser(u.getId());
						ue.setType(INACTIVE_STUDENT_TYPE);
						userDirectoryService.commitEdit(ue);
						//remove the student from current courses
						studentCount++;
						removedEids.add(eid);
						List<String> courseList = new ArrayList<String>();
						synchCourses(courseList, eid);
					}
					
				} catch (UserNotDefinedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (UserPermissionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (UserLockedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (UserAlreadyDefinedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
			}
		}
		StringBuilder sb = new StringBuilder();
		sb.append("we would inactivate these "+ studentCount +" students: \r\n");
		for (int i =0; i < removedEids.size(); i++) {
			sb.append(removedEids.get(i));
			sb.append("\r\n");
		}
		
		emailService.send("help@vula.uct.ac.za", "david.horwitz@uc.ac.za", "cleaned up users", sb.toString(), null, null, null);
	}

	
	//remove user from old courses
	private void synchCourses(List<String> uctCourse, String userEid){
		LOG.debug("Checking enrolments for " + userEid);
		SimpleDateFormat yearf = new SimpleDateFormat("yyyy");
		String thisYear = yearf.format(new Date());

		
		

		Set<EnrollmentSet> enroled = courseManagementService.findCurrentlyEnrolledEnrollmentSets(userEid);
		Iterator<EnrollmentSet> coursesIt = enroled.iterator();
		LOG.debug("got list of enrolement set with " + enroled.size());
		while(coursesIt.hasNext()) {
			EnrollmentSet eSet = (EnrollmentSet)coursesIt.next();
			String courseEid =  eSet.getEid();
			LOG.debug("got section: " + courseEid);
			boolean found = false;
			for (int i =0; i < uctCourse.size(); i++ ) {
				String thisEn = (String)uctCourse.get(i) + "," + thisYear;
				if (thisEn.equalsIgnoreCase(courseEid))
					found = true;
			}
			if (!found) {
				LOG.info("removing user from " + courseEid);
				courseAdmin.removeCourseOfferingMembership(userEid, courseEid);
				courseAdmin.removeSectionMembership(userEid, courseEid);
				courseAdmin.removeEnrollment(userEid, courseEid);


			}
		}

	}
	
}
