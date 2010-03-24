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
import org.sakaiproject.user.api.UserDirectoryService;

public class CleanFacOnlyStudents implements Job {

	private static final String INACTIVE_STUDENT_TYPE = "inactiveStudent";
	private static final Log LOG = LogFactory.getLog(CleanFacOnlyStudents.class);
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

		int first = 1;
		int increment = 1000;
		int last = increment;
		boolean doAnother = true;
		while (doAnother) {

			List<User> users = userDirectoryService.getUsers(first, last);
			for (int i = 0; i < users.size(); i++) {
				User user = users.get(i);
				if ("Student".equals(user.getType())) {

					LOG.info("Checking: " + user.getEid());
					//check the students current enrollments
					String eid = user.getEid();
					Set<EnrollmentSet> enrollments = courseManagementService.findCurrentlyEnrolledEnrollmentSets(eid);
					LOG.info("found: " + enrollments.size() + " enrollments for the student");
					if (!enrollmentsOk(enrollments)) {
						LOG.warn("This student has no course registrations or fewer enrollments!");

						//remove the student from current courses
						studentCount++;
						removedEids.add(eid);
						List<String> courseList = new ArrayList<String>();
						synchCourses(courseList, eid);


					}
				}
			}
			if (users.size() < increment) {
				doAnother = false;
			} else {
				first = last +1;
				last = last + increment;
			}
		}
		StringBuilder sb = new StringBuilder();
		sb.append("we have removed these "+ studentCount +" students from fac course groups: \r\n");
		for (int i =0; i < removedEids.size(); i++) {
			sb.append(removedEids.get(i));
			sb.append("\r\n");
		}

		emailService.send("help@vula.uct.ac.za", "david.horwitz@uc.ac.za", "cleaned up users", sb.toString(), null, null, null);
	}


	private boolean enrollmentsOk(Set<EnrollmentSet> enrollments) {
		Iterator<EnrollmentSet> it = enrollments.iterator();
		while (it.hasNext()) {
			EnrollmentSet enrollment = it.next();
			int check = "PHI3009F,2010".length();
			if (enrollment.getEid().length() == check) {
				LOG.info("This student is a member of" + enrollment.getEid());
				return true;
			}
		}
		return false;
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
