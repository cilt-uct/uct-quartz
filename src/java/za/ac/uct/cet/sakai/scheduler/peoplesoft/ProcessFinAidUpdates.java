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
package za.ac.uct.cet.sakai.scheduler.peoplesoft;

import java.time.Year;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.StatefulJob;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.coursemanagement.api.CourseManagementAdministration;
import org.sakaiproject.coursemanagement.api.CourseManagementService;
import org.sakaiproject.coursemanagement.api.Enrollment;
import org.sakaiproject.coursemanagement.api.EnrollmentSet;
import org.sakaiproject.coursemanagement.api.Section;
import org.sakaiproject.coursemanagement.api.exception.IdNotFoundException;
import org.sakaiproject.db.api.SqlService;
import org.sakaiproject.email.api.EmailService;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;


/**
 * Import course code updates from the incoming queue and 
 * update the users memberships in CM
 * 
 * @author dhorwitz
 *
 */
@Slf4j
public class ProcessFinAidUpdates implements StatefulJob {

	private static final String ADMIN = "admin";


	/**
	 * Services
	 */
	@Setter private SessionManager sessionManager;
	@Setter private CourseManagementService courseManagementService;
	@Setter private CourseManagementAdministration courseManagementAdministration;
	@Setter private SqlService sqlService;
	@Setter private EmailService emailService;
	@Setter private ServerConfigurationService serverConfigurationService;
	
	
	private final String courseCode = "FINAID";
	private String term;



	public void execute(JobExecutionContext arg0) throws JobExecutionException {

		log.info("Updating FinAid users");
		term = serverConfigurationService.getString("uct_term", Year.now().toString());
		log.info("UCT Term: {}", term);

		// set the user information into the current session
		Session sakaiSession = sessionManager.getCurrentSession();
		sakaiSession.setUserId(ADMIN);
		sakaiSession.setUserEid(ADMIN);
		
		List<String> users = getQueuedUsers();

		if (users.size() == 0) {
			log.info("no queued FinAid users");
			return;
		}
		
		// Add all the users
		for (int i = 0; i < users.size(); i++) {
			String userEid = users.get(i);
			registerUser(userEid);
		}
		
		//we now need to drop students no longer on the list
		removeDroppedUsers(users);

		//remove the details from the tmp table
		removeUserDetails();
	}

	//Remove from CM students who have dropped the course
	private void removeDroppedUsers(List<String> users) {
		Set<Enrollment> enrolled = courseManagementService.getEnrollments(courseCode + "," + term);
		Iterator<Enrollment> it = enrolled.iterator();
		String courseEid = courseCode + "," + term;
		while (it.hasNext()) {
			Enrollment thisOne = it.next();
			String user = thisOne.getUserId();
			if (!users.contains(user)) {
				log.info("dropping user {} from {}",user, courseCode);
				courseManagementAdministration.removeCourseOfferingMembership(user, courseEid);
				courseManagementAdministration.removeSectionMembership(user, courseEid);
				courseManagementAdministration.removeEnrollment(user, courseEid);
			} else {
				log.debug("user {} is still registered", user);
			}
		}
	}


	private void registerUser(String userEid) {
		String setCategory = "special";
		addUserToCourse(userEid, courseCode, term, setCategory);
	}


	/**
	 * Get a list of users from storage
	 * this will be a list of all finAid students
	 * @return
	 */
	private List<String> getQueuedUsers() {
		String sql = "select userEid from FINAID_UPDATED_USERS";
		List<String> ret = sqlService.dbRead(sql);
		return ret;
	}

	
	private void removeUserDetails() {
		String sql = "delete from FINAID_UPDATED_USERS";
		sqlService.dbWrite(sql, new Object[]{});

	}
	
	/**
	 * 
	 * @param userId
	 * @param courseCode
	 * @param term
	 * @param setCategory
	 */
	private void addUserToCourse(String userId, String courseCode, String term, String setCategory) {

		log.debug("addUserToCourse({}, {}, {}, {})", userId, courseCode, term, setCategory);
		
		try {

			courseCode = courseCode.toUpperCase().trim();
			
			if (courseCode == null || courseCode.length() == 0) {
				return;
			}
			
			//Get the role based on the type of object this is
			String role = "Student";
			String setId = courseCode.substring(0,3);
			setCategory = "Department";
			
			String courseEid = courseCode + "," + term;
			
			//do we have a academic session?
			if (!courseManagementService.isAcademicSessionDefined(term)) {
				Calendar cal = Calendar.getInstance();
				cal.set(new Integer(term).intValue(), 1, 1);
				Date start =  cal.getTime();
				cal.set(new Integer(term).intValue(), Calendar.DECEMBER, 30);
				Date end = cal.getTime();
				courseManagementAdministration.createAcademicSession(term, term, term, start, end);
			}

			//does the course set exist?
			if (!courseManagementService.isCourseSetDefined(setId)) 
				courseManagementAdministration.createCourseSet(setId, setId, setId, setCategory, null);

			//is there a cannonical course?
			if (!courseManagementService.isCanonicalCourseDefined(courseCode)) {
				courseManagementAdministration.createCanonicalCourse(courseCode, courseCode, courseCode);
				courseManagementAdministration.addCanonicalCourseToCourseSet(setId, courseCode);
			}

			if (!courseManagementService.isCourseOfferingDefined(courseEid)) {
				log.info("creating course offering for " + courseCode + " in year " + term);
				emailService.send("help-team@vula.uct.ac.za", "help-team@vula.uct.ac.za", "[CM]: new course created on vula: " + courseEid, "[CM]: new course created on vula: " + courseEid, null, null, null);
				//if this is being created by SPML its current now
				Date startDate = new Date();

				//use the term date
				Calendar cal2 = Calendar.getInstance();
				cal2.set(Calendar.DAY_OF_MONTH, 31);
				cal2.set(Calendar.MONTH, Calendar.OCTOBER);
				if (term != null) {
					cal2.set(Calendar.YEAR, Integer.valueOf(term));
				}
				//if this is a residence the end date is later.
				if (setCategory.equalsIgnoreCase("residence")) {
					cal2.set(Calendar.DAY_OF_MONTH, 19);
					cal2.set(Calendar.MONTH, Calendar.NOVEMBER);
					
				}
				
				Date endDate = cal2.getTime();
				log.debug("got cal:" + cal2.get(Calendar.YEAR) + "/" + cal2.get(Calendar.MONTH) + "/" + cal2.get(Calendar.DAY_OF_MONTH));
				courseManagementAdministration.createCourseOffering(courseEid, courseEid, "someDescription", "active", term, courseCode, startDate, endDate);
				courseManagementAdministration.addCourseOfferingToCourseSet(setId, courseEid);
			}

			//we know that all objects to this level must exist
			EnrollmentSet enrollmentSet = null;
			if (! courseManagementService.isEnrollmentSetDefined(courseEid)) {
				enrollmentSet =  courseManagementAdministration.createEnrollmentSet(courseEid, "title", "description", "category", "defaultEnrollmentCredits", courseEid, null);
			} else {
				enrollmentSet = courseManagementService.getEnrollmentSet(courseEid);
			}
			
			if (!courseManagementService.isSectionDefined(courseEid)) {
				courseManagementAdministration.createSection(courseEid, courseEid, "description", "course", null, courseEid, enrollmentSet.getEid());
			} else {
				Section section = courseManagementService.getSection(courseEid);
				//Check the section has a properly defined Enrolment set
				if (section.getEnrollmentSet() == null) {
					EnrollmentSet enrolmentSet = courseManagementService.getEnrollmentSet(courseEid);
					section.setEnrollmentSet(enrolmentSet);
					section.setCategory("course");
					courseManagementAdministration.updateSection(section);
				}
			}


			log.info("Adding student {} to {}", userId, courseEid);
			courseManagementAdministration.addOrUpdateSectionMembership(userId, role, courseEid, "enrolled");
			courseManagementAdministration.addOrUpdateEnrollment(userId, courseEid, "enrolled", "NA", "0");
			//now add the user to a section of the same name
			//TODO this looks like duplicate LOGic
			
			try {
				courseManagementService.getSection(courseEid);
			} 
			catch (IdNotFoundException id) {
				//create the CO
				//lets create the 2007 academic year :-)
				//create enrolmentset
				courseManagementAdministration.createEnrollmentSet(courseEid, courseEid, "description", "category", "defaultEnrollmentCredits", courseEid, null);
				log.info("creating Section for {} in year {}", courseCode, term);
				getCanonicalCourse(courseEid);
				courseManagementAdministration.createSection(courseEid, courseEid, "someDescription","course",null,courseEid,courseEid);
			}
			courseManagementAdministration.addOrUpdateSectionMembership(userId, role, courseEid, "enrolled");
		}
		catch (Exception e) {
			log.warn(e.getMessage(), e);

		}

	}

	private void getCanonicalCourse(String courseEid) {
		String courseCode = courseEid.substring(0, "PSY2006F".length());
		log.debug("get Cannonical course: {}", courseCode);
		try {
			courseManagementService.getCanonicalCourse(courseCode);
		}
		catch (IdNotFoundException id) {
			log.debug("creating canonicalcourse {}", courseCode);
			courseManagementAdministration.createCanonicalCourse(courseCode, "something", "something else");
		}
	}
	
}
