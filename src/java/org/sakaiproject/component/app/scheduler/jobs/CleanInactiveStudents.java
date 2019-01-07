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
import org.sakaiproject.user.api.UserAlreadyDefinedException;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.user.api.UserEdit;
import org.sakaiproject.user.api.UserLockedException;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.user.api.UserPermissionException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CleanInactiveStudents implements Job {

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
		String sql = "select user_id from CM_ENROLLMENT_T join CM_ENROLLMENT_SET_T on CM_ENROLLMENT_T.ENROLLMENT_SET=CM_ENROLLMENT_SET_T.ENROLLMENT_SET_ID where ENTERPRISE_ID like '%2008' group by user_id having count(ENROLLMENT_ID) = 2; ";
		List<String> eids = sqlService.dbRead(sql);
		for (int i = 0; i < eids.size(); i++) {
			String eid = eids.get(i);
			log.info("Checking: " + eid);
			//check the students current enrolements
			Set<EnrollmentSet> enrollments = courseManagementService.findCurrentlyEnrolledEnrollmentSets(eid);
			log.info("found: " + enrollments.size() + " enrollments for the student");
			if (enrollments.size() <= 2) {
				log.warn("This student has 2 or fewer enrollments!");
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
					log.warn("UserNotDefinedException", e);
				} catch (UserPermissionException e) {
					log.warn("UserPermissionException", e);
				} catch (UserLockedException e) {
					log.warn("UserLockedException", e);
				} catch (UserAlreadyDefinedException e) {
					log.warn("UserAlreadyDefinedException", e);
				}
				
			}
		}
		StringBuilder sb = new StringBuilder();
		sb.append("we would inactivate these "+ studentCount + " students: \r\n");
		for (int i = 0; i < removedEids.size(); i++) {
			sb.append(removedEids.get(i));
			sb.append("\r\n");
		}
		
		emailService.send("help@vula.uct.ac.za", "david.horwitz@uc.ac.za", "cleaned up users", sb.toString(), null, null, null);
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
