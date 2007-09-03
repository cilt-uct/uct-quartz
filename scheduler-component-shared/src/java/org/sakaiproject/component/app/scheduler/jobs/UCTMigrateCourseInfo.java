package org.sakaiproject.component.app.scheduler.jobs;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import org.sakaiproject.db.api.SqlService;
import org.sakaiproject.db.api.SqlReader;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;

import java.util.Date;
import org.sakaiproject.coursemanagement.api.CourseManagementAdministration;
import org.sakaiproject.coursemanagement.api.CourseManagementService;
import org.sakaiproject.coursemanagement.api.CourseOffering;
import org.sakaiproject.coursemanagement.api.Section;
import org.sakaiproject.coursemanagement.api.exception.IdNotFoundException;

import java.util.List;
import java.util.Date;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;

public class UCTMigrateCourseInfo implements Job {
	
	
	private static final String ADMIN = "admin";
	private static final Log LOG = LogFactory.getLog(UCTMigrateCourseInfo.class);
	public void execute(JobExecutionContext arg0) throws JobExecutionException {
		
		getCourses();
		getCourseMemberships();
		courseSetMembers();
	}

	private Map users;
	public void setUsers(Map map) {
		users = map;
	}
	private SqlService sqlService;
	public void setSqlService(SqlService s) {
		sqlService = s;
	}
	
	private CourseManagementService courseManagementService;
	private CourseManagementAdministration courseAdmin;
	
	public void setCourseManagementService(CourseManagementService cs) {
		courseManagementService = cs;
	}
	
	private SessionManager sessionManager;
	public void setSessionManager(SessionManager s) {
		this.sessionManager = s;
	}
	
	public void setCourseManagementAdministration(CourseManagementAdministration cs) {
		courseAdmin = cs;
	}
	
	private void getCourses() {
		
	    // set the user information into the current session
	    Session sakaiSession = sessionManager.getCurrentSession();
	    sakaiSession.setUserId(ADMIN);
	    sakaiSession.setUserEid(ADMIN);
	    //we need to create 
	    try {
	    	if(!courseManagementService.isAcademicSessionDefined("2006"))
	    		courseAdmin.createAcademicSession("2006", "2006", "2006 academic year", new Date(106,1,1),new Date(106,12,31));
	    	if(!courseManagementService.isAcademicSessionDefined("2007"))
	    		courseAdmin.createAcademicSession("2007", "2007", "2007 academic year", new Date(107,1,1),new Date(107,12,31));
	    	if(!courseManagementService.isAcademicSessionDefined("2008"))
	    		courseAdmin.createAcademicSession("2008", "2008", "2008 academic year", new Date(108,1,1),new Date(108,12,31));
	    	if(!courseManagementService.isAcademicSessionDefined("2009"))
	    		courseAdmin.createAcademicSession("2009", "2009", "2009 academic year", new Date(109,1,1),new Date(109,12,31));
	    	if(!courseManagementService.isAcademicSessionDefined("2010"))
	    		courseAdmin.createAcademicSession("2010", "2010", "2010 academic year", new Date(110,1,1),new Date(110,12,31));
	    }
	    catch (Exception e) {
	    	e.printStackTrace();
	    }
	    
		String statement = "Select SOURCEDID_ID,TIMEFRAME_ADMINPERIOD,DESCRIPTION_SHORT from UCT_GROUP";
	    LOG.info(statement);
		List dbResults = sqlService.dbRead(statement, null, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					Course c = new Course();
					c.setId(result.getString(1));
					c.setTermId(result.getString(2));
					c.setTitle(result.getString(3));
					return c;
				}
				catch (SQLException e)
				{
					LOG.warn(this + ".getInstrotorCourse:" + e);
					return null;
				}
			}
		} );
			
		//itterate through the list
		LOG.info("got a list of " + dbResults.size());
		for (int i =0; i < dbResults.size();i++) {
			Course thisCourse = (Course)dbResults.get(i);
			LOG.info("got a course: " + thisCourse.getId());
			//does the parent set exist?
			//program code: HB055
			//course code: FAM1000F
			String setId = null;
			String setCategory = null;
			if (thisCourse.getId().length() == 5) {
				setId = thisCourse.getId().substring(0,2);
				setCategory = "degree";
			} else {
				setId = thisCourse.getId().substring(0,3);
				setCategory = "Department";
			}
			LOG.info("got course set: " + setId);
			if(!courseManagementService.isCourseSetDefined(setId)) {
				LOG.info("creating set " + setId);
				courseAdmin.createCourseSet(setId,
                        setId,
                        setId,
                        setCategory,
                        null);
			}
			if (thisCourse.getId().length()>9)
				thisCourse.setId(thisCourse.getId().substring(0, 8));
			LOG.info("got course with ID of " + thisCourse.getId());
			//first the canonical course
			if (!courseManagementService.isCanonicalCourseDefined(thisCourse.getId())) {
				LOG.info("course does not exist creating");
				courseAdmin.createCanonicalCourse(thisCourse.getId(), thisCourse.getTitle(), thisCourse.getTitle());
				courseAdmin.addCanonicalCourseToCourseSet(setId,thisCourse.getId());
			}
			
			String courseEid = thisCourse.getId()+","+thisCourse.getTermId();
			
			if (!courseManagementService.isCourseOfferingDefined(thisCourse.getId()+","+thisCourse.getTermId())) {
				LOG.info("courseOferring does not exist creating");
				courseAdmin.createCourseOffering(courseEid, thisCourse.getTitle(), thisCourse.getTitle(), "active", thisCourse.getTermId(), thisCourse.getId(), new Date(), new Date());
				courseAdmin.addCourseOfferingToCourseSet(setId, courseEid);
			}
				
		}
	}
	
	private void getCourseMemberships() {
		
		String statement ="Select * from UCT_MEMBERSHIP";
		List dbResults = sqlService.dbRead(statement, null, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					UCTMembership c = new UCTMembership();
					c.setCourseOfferingEID(result.getString(1));
					c.setUserEid(result.getString(2));
					c.setUserRole(result.getString(3));
					return c;
				}
				catch (SQLException e)
				{
					LOG.warn(this + ".getInstrotorCourse:" + e);
					return null;
				}
			}
		} );
		
		
		//now iterate through the list
		LOG.info("Got a list of: " + dbResults.size() + " course memberships");
		for (int i=0;i < dbResults.size();i++ ) {
			UCTMembership member = (UCTMembership) dbResults.get(i);
			String courseCode = member.getCourseOfferingEID();
			try {
				SimpleDateFormat yearf = new SimpleDateFormat("yyyy");
				String thisYear = yearf.format(new Date());
				
				//does the 
				String courseEid = courseCode;
				LOG.info("We have " + member.getUserEid() + " in " + member.getCourseOfferingEID());
				try {
					CourseOffering co = courseManagementService.getCourseOffering(courseEid);
				} 
				catch (IdNotFoundException id) {
					//create the CO
					//lets create the 2007 academic year :-)
					
					LOG.info("creating course offering for " + courseCode + " in year " + thisYear);
					getCanonicalCourse(courseCode);
					courseAdmin.createCourseOffering(courseEid, "sometitle", "someDescription", "active", "2007", courseCode, new Date(), new Date());
					courseAdmin.createEnrollmentSet(courseEid, "title", "description", "category", "defaultEnrollmentCredits", courseEid, null);
					courseAdmin.createSection(courseEid, courseEid, "someDescription","course",null,courseEid,courseEid);
				}
				
				//no longer used
				//courseAdmin.addOrUpdateCourseOfferingMembership(member.getUserEid(), "Student", courseEid, "enroled");
				
				//now add the user to a section of the same name
				try {
					Section co = courseManagementService.getSection(courseEid);
				} 
				catch (IdNotFoundException id) {
					//create the CO
					//lets create the 2007 academic year :-)
					//create enrolmentset
					courseAdmin.createEnrollmentSet(courseEid, "title", "description", "category", "defaultEnrollmentCredits", courseEid, null);
					
					
					LOG.info("creating Section for " + courseCode + " in year " + thisYear);
					getCanonicalCourse(courseCode);
					courseAdmin.createSection(courseEid, courseEid, "someDescription","course",null,courseEid,courseEid);
				}
				courseAdmin.addOrUpdateSectionMembership(member.getUserEid(), "Student", courseEid, "enroled");
				courseAdmin.addOrUpdateEnrollment(member.getUserEid(), courseEid, "enrolled", "NA", "0"); 
			}
			catch(Exception e) {
				e.printStackTrace();
				
			}
				
			
		}
	}
	
	private void courseSetMembers() {
		Iterator i = users.keySet().iterator();
		while ( i.hasNext()) {
			String key = (String) i.next();
			LOG.info("Got: " + key + "for " + users.get(key));
			courseAdmin.addOrUpdateCourseSetMembership(key, "DeptAdmin", (String)users.get(key), "enroled");
		}

	}

	private void getCanonicalCourse(String courseCode) {
		try {
			courseManagementService.getCanonicalCourse(courseCode);
		}
		catch (IdNotFoundException id) {
			LOG.info("creating canonicalcourse " + courseCode);
			courseAdmin.createCanonicalCourse(courseCode, "something", "something else");
		}
	}
	
	private class Course {
		
		private String id;
		public void setId(String s) {
			id = s;
		}
		
		public String getId() {
			return id;
		}
		
		private String termId;
		public void setTermId(String tid) {
			termId = tid;
		}
		
		public String getTermId() {
			return termId;
		}
		private String title;
		public void setTitle(String t){
			title = t;
		}
		public String getTitle(){
			return title;
		}
	}
	
	private class UCTMembership {
		private String courseOfferingEID;
		private String userEid;
		private String userRole;
		
		public void setCourseOfferingEID(String co) {
			courseOfferingEID=co;
		}
		public String getCourseOfferingEID() {
			return courseOfferingEID;
		}
		
		public void setUserEid(String UE) {
			userEid = UE;
		}
		public String getUserEid() {
			return this.userEid;
		}
		
		public void setUserRole(String r) {
			userRole= r;
		}
		
		public String getUserRole() {
			return this.userRole;
		}
		
	}
}
