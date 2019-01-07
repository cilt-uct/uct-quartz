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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

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

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CleanFacOnlyStudents implements Job {

	private static final String INACTIVE_STUDENT_TYPE = "inactiveStudent";
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
				if ("student".equals(user.getType())) {

					log.info("Checking: " + user.getEid());
					//check the students current enrollments
					String eid = user.getEid();
					Set<EnrollmentSet> enrollments = courseManagementService.findCurrentlyEnrolledEnrollmentSets(eid);
					log.info("found: " + enrollments.size() + " enrollments for the student");
					if (enrollments.size() > 0 && !enrollmentsOk(enrollments)) {
						log.warn("This student has no course registrations or fewer enrollments!");

						//remove the student from current courses
						studentCount++;
						removedEids.add(eid);
						List<String> courseList = new ArrayList<String>();
						synchCourses(courseList, eid);


					}
				} else if ("offer".equals(user.getType())) {
					//check that the student only in the fac offer group
					String eid = user.getEid();
					Set<EnrollmentSet> enrollments = courseManagementService.findCurrentlyEnrolledEnrollmentSets(eid);
					if (enrollments.size() > 1) {
						List<String> courseList = new ArrayList<String>();
						//we have more than just the enrollemnent set could include a faculty
						Iterator<EnrollmentSet> it = enrollments.iterator();
						while (it.hasNext()) {
							EnrollmentSet set = it.next();
							if (set.getEid().contains("OFFER_STUDENT,2010")) {
								courseList.add(set.getEid());
							} else if (set.getEid().length() == "FUL,2010".length()) {
								courseList.add(set.getEid());
							}
						}
						synchCourses(courseList, eid);
					}
					
				}
			} 
			if (users.size() < increment) {
				doAnother = false;
			} else {
				first = last + 1;
				last = last + increment;
			}
		}
		StringBuilder sb = new StringBuilder();
		sb.append("we have removed these "+ studentCount + " students from fac course groups: \r\n");
		for (int i = 0; i < removedEids.size(); i++) {
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
			String course = enrollment.getEid();
			if (course.length() == check && course.indexOf("_STUD,") == -1) {
				log.info("This student is a member of" + enrollment.getEid());
				return true;
			}
		}
		return false;
	}


	//remove user from old courses
	private void synchCourses(List<String> uctCourse, String userEid){
		log.debug("Checking enrolments for " + userEid);
		SimpleDateFormat yearf = new SimpleDateFormat("yyyy");
		String thisYear = yearf.format(new Date());




		Set<EnrollmentSet> enroled = courseManagementService.findCurrentlyEnrolledEnrollmentSets(userEid);
		Iterator<EnrollmentSet> coursesIt = enroled.iterator();
		log.debug("got list of enrolement set with " + enroled.size());
		while (coursesIt.hasNext()) {
			EnrollmentSet eSet = (EnrollmentSet)coursesIt.next();
			String courseEid =  eSet.getEid();
			log.debug("got section: " + courseEid);
			boolean found = false;
			for (int i = 0; i < uctCourse.size(); i++ ) {
				String thisEn = (String)uctCourse.get(i) + "," + thisYear;
				if (thisEn.equalsIgnoreCase(courseEid))
					found = true;
			}
			if (!found) {
				log.info("removing user from " + courseEid);
				courseAdmin.removeCourseOfferingMembership(userEid, courseEid);
				courseAdmin.removeSectionMembership(userEid, courseEid);
				courseAdmin.removeEnrollment(userEid, courseEid);


			}
		}

	}

}
