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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.StatefulJob;
import org.sakaiproject.coursemanagement.api.CourseManagementAdministration;
import org.sakaiproject.coursemanagement.api.CourseManagementService;
import org.sakaiproject.coursemanagement.api.EnrollmentSet;
import org.sakaiproject.coursemanagement.api.Section;
import org.sakaiproject.coursemanagement.api.exception.IdNotFoundException;
import org.sakaiproject.db.api.SqlReader;
import org.sakaiproject.db.api.SqlReaderFinishedException;
import org.sakaiproject.db.api.SqlService;
import org.sakaiproject.email.api.EmailService;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.user.api.UserAlreadyDefinedException;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.user.api.UserEdit;
import org.sakaiproject.user.api.UserLockedException;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.user.api.UserPermissionException;

import lombok.Data;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import za.uct.cilt.util.VulaUtil;


/**
 * Import course code updates from the incoming queue and
 * update the users memberships in CM
 *
 * @author dhorwitz
 *
 */
@Slf4j
public class ProcessPSUpdates implements StatefulJob {


	private static final String ADMIN = "admin";


	/**
	 * Academic sessions before this will be ignored
	 */
	private Integer earliestCourseYear = 2005;


	/**
	 * Services
	 */
	@Setter private SessionManager sessionManager;
	@Setter private CourseManagementService courseManagementService;
	@Setter private CourseManagementAdministration courseManagementAdministration;
	@Setter private SqlService sqlService;
	@Setter private EmailService emailService;
	@Setter private UserDirectoryService userDirectoryService;

	public void execute(JobExecutionContext arg0) throws JobExecutionException {

		log.info("Starting");

		// set the user information into the current session
		Session sakaiSession = sessionManager.getCurrentSession();
		sakaiSession.setUserId(ADMIN);
		sakaiSession.setUserEid(ADMIN);

		UserCourseRegistrations userCourseRegistrations = getNextUserCourseRegistrations();
		while (userCourseRegistrations != null) {

			log.info("Updating enrolment set for {}", userCourseRegistrations.getUserId());

			updateCourses(userCourseRegistrations);
			//TODO we should set a flag on the users properties
			removeUserDetails(userCourseRegistrations);

			//set the user flag
			updateUserSynchFlag(userCourseRegistrations.getUserId());
			userCourseRegistrations = getNextUserCourseRegistrations();

		}
		log.info("Completed");
	}

	private void updateUserSynchFlag(String userEId) {
		String userId;
		try {
			userId = userDirectoryService.getUserId(userEId);
			UserEdit user = userDirectoryService.editUser(userId);
			ResourceProperties rp = user.getProperties();
			rp.addProperty("PS_MEMEBERSHIPS_SYNCHED", VulaUtil.getISODate());
			userDirectoryService.commitEdit(user);
		} catch (UserNotDefinedException e) {
			log.warn(e.getMessage(), e);
		} catch (UserPermissionException e) {
			log.warn(e.getMessage(), e);
		} catch (UserLockedException e) {
			log.warn(e.getMessage(), e);
		} catch (UserAlreadyDefinedException e) {
			log.warn(e.getMessage(), e);
		}


	}


	/**
	 * remove the details from the database releated to this user object
	 * @param userCourseRegistrations
	 */
	private void removeUserDetails(UserCourseRegistrations userCourseRegistrations) {
		String sql = "delete from SPML_WSDL_IN where userid=?";
		sqlService.dbWrite(sql, new Object[]{userCourseRegistrations.getUserId()});

	}


	/**
	 * Update a users course membership in CM
	 * @param userCourseRegistrations
	 */
	private void updateCourses(UserCourseRegistrations userCourseRegistrations) {
		log.debug("updateCourses(UserCourseRegistrations userCourseRegistrations)");
		//Remove any ;courses that we're not interested in

		List<String> incomingList = userCourseRegistrations.getCourseRegistrations();
		List<String> incomingListFixed = new ArrayList<String>();
		log.debug("initial incoming list: {}", incomingList.size());
		//if the student has no course reg there will be 1 course with the ied "null)
		if (!hasNoCourses(incomingList)) {
			for (int i = 0; i < incomingList.size(); i++) {
				String courseCode = incomingList.get(i);
				String fixedCourseCode = fixCourseCode(courseCode);
				if (!isAfterVula(fixedCourseCode)) {
					log.debug("removing {} from incoming list", courseCode);
					continue;
				}

				incomingListFixed.add(fixedCourseCode);
			}
		}

		Set<Section> sections = courseManagementService.findEnrolledSections(userCourseRegistrations.getUserId());
		List<Section> filteredList = filterCMSectionList(sections);


		List<String> drops = new ArrayList<String>();
		List<String> adds = new ArrayList<String>();
 		List<String> enrolledSectionEids = new ArrayList<String>();

 		//Build a list of the eids from CM
 		for (int i = 0; i < filteredList.size(); i++) {
 			Section section = filteredList.get(i);
 			enrolledSectionEids.add(section.getEid());

 		}

 		log.debug("have a list of {} eids from CM", enrolledSectionEids.size());
 		//build a list of drops - in the CM list but not in incoming
 		for (int i = 0; i < enrolledSectionEids.size(); i++) {
 			String eid = enrolledSectionEids.get(i);
 			if (!incomingListFixed.contains(eid)) {
 				log.debug(i + ". looks like user dropped " + eid);
 				drops.add(eid);
 			} else {
 				log.debug("{}. looks like user is still registered for {}", i, eid);
 			}
 		}
 		log.debug("we have an incoming list of " + incomingList.size());

		for (int i = 0; i < incomingListFixed.size(); i++) {
			String courseCode =  incomingListFixed.get(i);
			if (! enrolledSectionEids.contains(courseCode)) {
				log.debug("looks like use added {}", courseCode);
				adds.add(courseCode);
			} else {
				log.debug("{}. looks like user is already registered for {}", i, courseCode);
			}
		 }


		proccessAdds(adds, userCourseRegistrations.getUserId());
		proccessDrops(drops, userCourseRegistrations.getUserId());
	}

	private boolean hasNoCourses(List<String> incomingList) {
		if (incomingList.size() == 1 && "null".equals(incomingList.get(0))) {
			return true;
		}


		return false;
	}


	/**
	 * Pre 2005 course codes where i digit shorter. PS returns
	 * them padded with a space
	 * @param courseCode
	 * @return
	 */
	private String fixCourseCode(String courseCode) {
		if (courseCode == null) {
			return null;
		}

		return courseCode.replace(" ", "");

	}


	/**
	 * Process the list of course drops
	 * @param drops
	 * @param userId
	 */
	private void proccessDrops(List<String> drops, String userId) {

		for (int i = 0; i < drops.size(); i++) {
			String courseEid = drops.get(i);
			log.info("removing {} from {}", userId, courseEid);
			courseManagementAdministration.removeCourseOfferingMembership(userId, courseEid);
			courseManagementAdministration.removeSectionMembership(userId, courseEid);
			courseManagementAdministration.removeEnrollment(userId, courseEid);
		}
	}


	private void proccessAdds(List<String> adds, String userId) {
		for (int i = 0; i < adds.size(); i++) {
			String eid = adds.get(i);
			String term = eid.substring(eid.lastIndexOf(",") + 1);
			addUserToCourse(userId, eid, term, "Department");
		}

	}


	/**
	 * Filter the section list for types we don't do (residences, program codes, faculty groups)
	 * @param sections
	 * @return
	 */
	private List<Section> filterCMSectionList(Set<Section> sections) {
		List<Section> ret = new ArrayList<Section>();
		Iterator<Section> siter = sections.iterator();
		while (siter.hasNext()) {
			Section section = siter.next();
			if (doSection(section)) {
				ret.add(section);
			}
		}
		return ret;
	}

	/**
	 * We are only interested in course sections no other types.
	 * @param section
	 * @return
	 */
	private boolean doSection(Section section) {
		String eid = section.getEid();
		if (eid.endsWith("OFFER") || eid.endsWith("PREREG")) {
			return false;
		}

		Integer term = getTermIntFromCourseEid(eid);
		//if the length is not right junk is
		if (term.intValue() > 2005) {
			if (eid.length() == "PSY3007SSUP,2010".length()) {
				return true;
			} else	if (eid.length() != "PSY3007S,2010".length()) {
				log.debug("we don't work with {}", eid);
				return false;
			}
		} else {
			if (eid.length() == "PSY307SSUP,2010".length()) {
				return true;
			} else	if (eid.length() != "PSY307S,2010".length()) {
				log.debug("we don't work with {}", eid);
				return false;
			}
		}

		if (eid.indexOf("_STUD") > 0) {
			log.debug("{} looks like a faculty group", eid);
			return false;
		}


		return true;
	}


	/**
	 * Is  this courseCode after what we are interested in on Vula?
	 *
	 * @param courseCode
	 * @return true if the course code is for a date after the launch of vula
	 */
	private boolean isAfterVula(String courseCode) {
		if (courseCode.endsWith("OFFER") || courseCode.endsWith("PREREG")) {
			return false;
		}

		Integer year = getTermIntFromCourseEid(courseCode);
		if (year >= earliestCourseYear) {
			return true;
		}
		log.debug("Ignoring course {} as it predates Vula", courseCode);
		return false;
	}


	private Integer getTermIntFromCourseEid(String courseCode) {
		String[] fields = courseCode.split(",");
		String yearS = fields[1];
		Integer year = Integer.valueOf(yearS);
		return year;
	}


	@SuppressWarnings({ "unchecked" })
	private UserCourseRegistrations getNextUserCourseRegistrations() {

		// get the next user to process
		String sql = "select userid from SPML_WSDL_IN order by queued limit 1";

		List<String> userIds = sqlService.dbRead(sql);
		if (userIds == null || userIds.size() == 0 ) {
			return null;
		}
		final String userid = userIds.get(0).toLowerCase();

		String sql2 = "SELECT courseEid, MAX(queued) from SPML_WSDL_IN WHERE userid = ? GROUP BY courseEid";

		List<UserCourseRegistrations> ucrList = (List<UserCourseRegistrations>)sqlService.dbRead(sql2, new Object[]{userid}, new SqlReader() {

			public Object readSqlResultRecord(ResultSet result)
					throws SqlReaderFinishedException {
				UserCourseRegistrations ret = new UserCourseRegistrations();
				List<String> courses = new ArrayList<String>();
				java.sql.Date updated = null;
				try {
					result.beforeFirst();
					while (result.next()) {
						String c = result.getString(1);
						courses.add(c);
						updated = result.getDate(2);
					}
				} catch (SQLException e) {
					log.warn(e.getMessage(), e);
				}
				ret.setCourseRegistrations(courses);
				ret.setLastUpdated(Instant.ofEpochMilli(updated.getTime()));
				ret.setUserId(userid);

				return ret;
			}
		});
		UserCourseRegistrations ucr = ucrList.get(0);
		return ucr;
	}


	private void addUserToCourse(String userId, String courseCode, String term, String setCategory) {
		log.debug("addUserToCourse( {}, {}, {}, {})", userId, courseCode, term,setCategory);


		try {


			courseCode = courseCode.toUpperCase().trim();


			if (courseCode == null || courseCode.length() == 0) {
				return;
			}

			//Get the role based on the type of object this is
			String role = "Student";
			String setId = courseCode.substring(0,3);
			setCategory = "Department";

			String courseEid = courseCode;

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
				log.info("creating course offering for {} in year {}" , courseCode, term);
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

			log.info("adding {} to {}", userId, courseEid);

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
				log.info("creating Section for {} in year {}",courseCode, term);
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

	/**
	 * A simple POJO to hold the course information
	 * @author dhorwitz
	 *
	 */
	@Data
	private class UserCourseRegistrations {
		private String userId;
		private List<String> courseRegistrations;
		private Instant lastUpdated;
	}

}
