package za.ac.uct.cet.sakai.scheduler.peoplesoft;

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
import org.sakaiproject.coursemanagement.api.Enrollment;
import org.sakaiproject.coursemanagement.api.EnrollmentSet;
import org.sakaiproject.coursemanagement.api.Section;
import org.sakaiproject.coursemanagement.api.exception.IdNotFoundException;
import org.sakaiproject.db.api.SqlService;
import org.sakaiproject.email.api.EmailService;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;

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
	private SessionManager sessionManager;
	private CourseManagementService courseManagementService;
	private CourseManagementAdministration courseAdmin;
	private SqlService sqlService;
	private EmailService emailService;
	
	
	private final String courseCode = "FINAID";
	private final String term = "2019";

	public void setEmailService(EmailService emailService) {
		this.emailService = emailService;
	}


	public void setSqlService(SqlService sqlService) {
		this.sqlService = sqlService;
	}


	public void setCourseManagementService(CourseManagementService cs) {
		courseManagementService = cs;
	}


	public void setSessionManager(SessionManager s) {
		this.sessionManager = s;
	}

	public void setCourseManagementAdministration(CourseManagementAdministration cs) {
		courseAdmin = cs;
	}

	public void execute(JobExecutionContext arg0) throws JobExecutionException {

		log.info("Updating FinAid users");

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
				log.info("dropping user " + user + " from " + courseCode);
				courseAdmin.removeCourseOfferingMembership(user, courseEid);
				courseAdmin.removeSectionMembership(user, courseEid);
				courseAdmin.removeEnrollment(user, courseEid);
			} else {
				log.debug("user " + user + " is still registered");
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

		log.debug("addUserToCourse(" + userId + ", " + courseCode + "," + term + "," + setCategory + ")");
		
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
				courseAdmin.createAcademicSession(term, term, term, start, end);
			}

			//does the course set exist?
			if (!courseManagementService.isCourseSetDefined(setId)) 
				courseAdmin.createCourseSet(setId, setId, setId, setCategory, null);

			//is there a cannonical course?
			if (!courseManagementService.isCanonicalCourseDefined(courseCode)) {
				courseAdmin.createCanonicalCourse(courseCode, courseCode, courseCode);
				courseAdmin.addCanonicalCourseToCourseSet(setId, courseCode);
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
				courseAdmin.createCourseOffering(courseEid, courseEid, "someDescription", "active", term, courseCode, startDate, endDate);
				courseAdmin.addCourseOfferingToCourseSet(setId, courseEid);
			}

			//we know that all objects to this level must exist
			EnrollmentSet enrollmentSet = null;
			if (! courseManagementService.isEnrollmentSetDefined(courseEid)) {
				enrollmentSet =  courseAdmin.createEnrollmentSet(courseEid, "title", "description", "category", "defaultEnrollmentCredits", courseEid, null);
			} else {
				enrollmentSet = courseManagementService.getEnrollmentSet(courseEid);
			}
			
			if (!courseManagementService.isSectionDefined(courseEid)) {
				courseAdmin.createSection(courseEid, courseEid, "description", "course", null, courseEid, enrollmentSet.getEid());
			} else {
				Section section = courseManagementService.getSection(courseEid);
				//Check the section has a properly defined Enrolment set
				if (section.getEnrollmentSet() == null) {
					EnrollmentSet enrolmentSet = courseManagementService.getEnrollmentSet(courseEid);
					section.setEnrollmentSet(enrolmentSet);
					section.setCategory("course");
					courseAdmin.updateSection(section);
				}
			}


			log.info("Adding student " + userId + " to " + courseEid);
			courseAdmin.addOrUpdateSectionMembership(userId, role, courseEid, "enrolled");
			courseAdmin.addOrUpdateEnrollment(userId, courseEid, "enrolled", "NA", "0");
			//now add the user to a section of the same name
			//TODO this looks like duplicate LOGic
			
			try {
				courseManagementService.getSection(courseEid);
			} 
			catch (IdNotFoundException id) {
				//create the CO
				//lets create the 2007 academic year :-)
				//create enrolmentset
				courseAdmin.createEnrollmentSet(courseEid, courseEid, "description", "category", "defaultEnrollmentCredits", courseEid, null);
				log.info("creating Section for " + courseCode + " in year " + term);
				getCanonicalCourse(courseEid);
				courseAdmin.createSection(courseEid, courseEid, "someDescription","course",null,courseEid,courseEid);
			}
			courseAdmin.addOrUpdateSectionMembership(userId, role, courseEid, "enrolled");
		}
		catch (Exception e) {
			log.warn(e.getMessage(), e);

		}

	}

	private void getCanonicalCourse(String courseEid) {
		String courseCode = courseEid.substring(0, "PSY2006F".length());
		log.debug("get Cannonical course:" + courseCode);
		try {
			courseManagementService.getCanonicalCourse(courseCode);
		}
		catch (IdNotFoundException id) {
			log.debug("creating canonicalcourse " + courseCode);
			courseAdmin.createCanonicalCourse(courseCode, "something", "something else");
		}
	}
	
}
