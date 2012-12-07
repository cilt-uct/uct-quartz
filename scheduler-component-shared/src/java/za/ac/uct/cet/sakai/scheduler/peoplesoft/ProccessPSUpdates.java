package za.ac.uct.cet.sakai.scheduler.peoplesoft;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.joda.time.DateTime;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.sakaiproject.coursemanagement.api.CourseManagementAdministration;
import org.sakaiproject.coursemanagement.api.CourseManagementService;
import org.sakaiproject.coursemanagement.api.EnrollmentSet;
import org.sakaiproject.coursemanagement.api.Section;
import org.sakaiproject.coursemanagement.api.exception.IdNotFoundException;
import org.sakaiproject.db.api.SqlReader;
import org.sakaiproject.db.api.SqlReaderFinishedException;
import org.sakaiproject.db.api.SqlService;
import org.sakaiproject.email.api.EmailService;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;


/**
 * Import course code updates from the incomming queue and 
 * update the users memberships in CM
 * 
 * @author dhorwitz
 *
 */
public class ProccessPSUpdates implements Job {


	private static final Log log = LogFactory.getLog(ProccessPSUpdates.class);

	private static final String ADMIN = "admin";
	
	
	/**
	 * Academic sessions before this will be ignored
	 */
	private Integer earliestCourseYear = 2005;
	
	
	/**
	 * Services
	 */
	private SessionManager sessionManager;
	private CourseManagementService courseManagementService;
	private CourseManagementAdministration courseAdmin;
	private SqlService sqlService;
	private EmailService emailService;
	
	


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
		// set the user information into the current session
		Session sakaiSession = sessionManager.getCurrentSession();
		sakaiSession.setUserId(ADMIN);
		sakaiSession.setUserEid(ADMIN);
		UserCourseRegistrations userCourseRegistrations = getNextUserCourseRegistrations();
		if (userCourseRegistrations == null) {
			log.debug("no registrations found!");
			return;
		}
		updateCourses(userCourseRegistrations);
		//TODO we should set a flag on the users properties
		removeUserDetails(userCourseRegistrations);
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
		log.debug("initial incoming list: " + incomingList.size());
		for (int i = 0; i < incomingList.size(); i++) {
			String courseCode = incomingList.get(i);
			if (!isAfterVula(courseCode)) {
				log.debug("removing " + courseCode + " from incoming list");
				incomingList.remove(courseCode);
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
 		
 		log.debug("have a list of " + enrolledSectionEids.size() + " eids from CM");
 		//build a list of drops - in the CM list but not in incoming
 		for (int i = 0; i < enrolledSectionEids.size(); i++) {
 			String eid = enrolledSectionEids.get(i);
 			if (!incomingList.contains(eid)) {
 				log.debug(i + ". looks like user dropped " + eid);
 				drops.add(eid);
 			} else {
 				log.debug(i +". looks like user is still registered for " + eid);
 			}
 		}
 		log.debug("we have an incoming list of " + incomingList.size());
		
		for (int i = 0; i < incomingList.size(); i++) {
			String courseCode =  incomingList.get(i);
			if (! enrolledSectionEids.contains(courseCode)) {
				log.debug("looks like use added " + courseCode);
				adds.add(courseCode);
			} else {
				log.debug(i + ". looks like user is already registered for " + courseCode);
			}
		 }
 		
		
		proccessAdds(adds, userCourseRegistrations.getUserId());
		proccessDrops(drops, userCourseRegistrations.getUserId());
	}
	
	/**
	 * Process the list of course drops
	 * @param drops
	 * @param userId
	 */
	private void proccessDrops(List<String> drops, String userId) {
		
		for (int i = 0; i < drops.size(); i++) {
			String courseEid = drops.get(i);
			log.info("removing user from " + courseEid);
			courseAdmin.removeCourseOfferingMembership(userId, courseEid);
			courseAdmin.removeSectionMembership(userId, courseEid);
			courseAdmin.removeEnrollment(userId, courseEid);
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
		
		//if the length is not right junk is
		if (eid.length() != "PSY3007S,2010".length()) {
			log.warn("we don't work with " + eid);
			return false;
		}
		
		if (eid.indexOf("_STUD") > 0) {
			log.warn(eid + " looks like a faculty group");
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
		String[] fields = courseCode.split(",");
		String yearS =fields[1]; 
		Integer year = Integer.valueOf(yearS);
		if (year >= earliestCourseYear) {
			return true;
		}
		log.warn("Ignoring course " + courseCode + " as it predates Vula");
		return false;
	}


	@SuppressWarnings("unchecked")
	private UserCourseRegistrations getNextUserCourseRegistrations() {
		
		//get the user we limit them to older than 5m to avoid race conditions
		String sql = "select userid from SPML_WSDL_IN where queued < date_sub(now(), interval 5 minute) limit 1";
		List<String> userIds = sqlService.dbRead(sql);
		if (userIds == null || userIds.size() == 0 ) {
			return null;
		}
		String userid = userIds.get(0);
		
		String sql2 = "Select * from SPML_WSDL_IN where userid = ?";
		
		List<UserCourseRegistrations> ucrList = (List<UserCourseRegistrations>)sqlService.dbRead(sql2, new Object[]{userid}, new SqlReader() {
			
			public Object readSqlResultRecord(ResultSet result)
					throws SqlReaderFinishedException {
				UserCourseRegistrations ret = new UserCourseRegistrations(); 
				List<String> courses = new ArrayList<String>();
				DateTime updated = null;
				String user = null;
				try {
					result.beforeFirst();
					while (result.next()) {
						String c = result.getString(2);
						courses.add(c);
						updated = new DateTime(result.getDate(3));
						user = result.getString(1);
					}
				} catch (SQLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				ret.setCourseRegistrations(courses);
				ret.setLastUpdated(updated);
				ret.setUserId(user);
				
				return ret;
			}
		});
		UserCourseRegistrations ucr = ucrList.get(0);
		return ucr;
	}


	private void addUserToCourse(String userId, String courseCode, String term, String setCategory) {
		log.debug("addUserToCourse(" + userId +", " + courseCode + "," + term + "," + setCategory + ")");
		

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
				if (term !=null) {
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
			
			if(! courseManagementService.isSectionDefined(courseEid)) {
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


			log.info("adding this student to " + courseEid);
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
		catch(Exception e) {
			e.printStackTrace();

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
	
	/**
	 * A simple POJO to hold the course information
	 * @author dhorwitz
	 *
	 */
	private class UserCourseRegistrations {
		private String userId;
		private List<String> courseRegistrations;
		private DateTime lastUpdated;
		public String getUserId() {
			return userId;
		}
		public void setUserId(String userId) {
			this.userId = userId;
		}
		public List<String> getCourseRegistrations() {
			return courseRegistrations;
		}
		public void setCourseRegistrations(List<String> courseRegistrations) {
			this.courseRegistrations = courseRegistrations;
		}
		public DateTime getLastUpdated() {
			return lastUpdated;
		}
		public void setLastUpdated(DateTime lastUpdated) {
			this.lastUpdated = lastUpdated;
		}
	}

}
