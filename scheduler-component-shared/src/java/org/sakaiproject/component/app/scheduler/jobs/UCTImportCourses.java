package org.sakaiproject.component.app.scheduler.jobs;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.sakaiproject.content.api.ContentHostingService;
import org.sakaiproject.coursemanagement.api.CourseManagementAdministration;
import org.sakaiproject.coursemanagement.api.CourseManagementService;
import org.sakaiproject.coursemanagement.api.CourseOffering;
import org.sakaiproject.coursemanagement.api.EnrollmentSet;
import org.sakaiproject.coursemanagement.api.Section;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;

public class UCTImportCourses implements Job {

	private static final String ADMIN = "admin";
	private static final Log LOG = LogFactory.getLog(UCTImportCourses.class);
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
	
	private ContentHostingService contentHostingService;
	public void setContentHostingService(ContentHostingService chs) {
		contentHostingService = chs;
	}
	
	private String filePath;
	public void setFilePath(String fp) {
		filePath = fp;
	}
	
	private String term;
	public void setTerm(String t) {
		term = t;
	}
	
	public void execute(JobExecutionContext arg0) throws JobExecutionException {
		// TODO Auto-generated method stub
	    // set the user information into the current session
	    Session sakaiSession = sessionManager.getCurrentSession();
	    sakaiSession.setUserId(ADMIN);
	    sakaiSession.setUserEid(ADMIN);
	    try {
	    	FileReader fr = new FileReader(filePath);
	    	BufferedReader br = new BufferedReader(fr);
	    	String record = null;  
	    	while ( (record=br.readLine()) != null) { 
	    		/*
	    		 * this should be a record of the format:
	    		 * Course ID	Offer Nbr	Term	Session	Sect	Institution	Acad Group	Subject	Catalog	Career	Descr	Class Nbr	Component

	    		 */
	    		record = record.trim();
	    		if (record.equals(""))
	    				continue;
	    		String[] data = record.split(",");
	    		this.createCourse(data[7] + data[8], term, data[10], data[7]);
	    	} 
	    } catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	    finally {
	    	
	    }

	}
	
	
	private void createCourse(String courseCode, String term, String descr, String setId) {
		LOG.info("createCourse(" + courseCode + "," + term + "," + descr + "," + setId );
		try {
		String setCategory = "Department";
		String courseEid = courseCode +","+term;
		
		SimpleDateFormat yearf = new SimpleDateFormat("yyyy");
		String thisYear = yearf.format(new Date());
		
		Calendar cal = Calendar.getInstance();
		cal.set(Calendar.YEAR, new Integer(term).intValue());
		cal.set(Calendar.MONTH, Calendar.JANUARY);
		cal.set(Calendar.DAY_OF_MONTH, 1);
		
		Date yearStart = cal.getTime();
		
		cal.set(Calendar.MONTH, Calendar.DECEMBER);
		cal.set(Calendar.DAY_OF_MONTH, 31);
		
		Date yearEnd = cal.getTime();
		
		SimpleDateFormat dateForm = new SimpleDateFormat("yyyy-mm-dd");
		
		//does the accademic session exist
		if (!courseManagementService.isAcademicSessionDefined(term))
			courseAdmin.createAcademicSession(term, term,term + " academic year", new Date(), yearEnd);
		
		//does the course set exist?
		if (!courseManagementService.isCourseSetDefined(setId)) 
			courseAdmin.createCourseSet(setId, setId, setId, setCategory, null);
		
		if (!courseManagementService.isCanonicalCourseDefined(courseCode)) {
			courseAdmin.createCanonicalCourse(courseCode, courseCode, descr);
			courseAdmin.addCanonicalCourseToCourseSet(setId, courseCode);
		}
		
		if (!courseManagementService.isCourseOfferingDefined(courseEid)) {
		 	LOG.info("creating course offering for " + courseCode + " in year " + term);
		 	Date startDate = dateForm.parse(term + "-01-01");
		 	Date endDate = dateForm.parse(term + "-12-31");
			courseAdmin.createCourseOffering(courseEid, courseEid + " - " + descr, courseEid + " - " + descr, "active", term, courseCode, new Date(), yearEnd);
			
		} else {
			//update the name
			CourseOffering co = courseManagementService.getCourseOffering(courseEid);
			co.setTitle(courseEid + " - " + descr);
			co.setDescription(courseEid + " - " + descr);
			courseAdmin.updateCourseOffering(co);
			
		}
		
		
		courseAdmin.addCourseOfferingToCourseSet(setId, courseEid);		 
		 
		EnrollmentSet enrolmentSet = null; 
		if (! courseManagementService.isEnrollmentSetDefined(courseEid))
			enrolmentSet = courseAdmin.createEnrollmentSet(courseEid, descr, descr, "category", "defaultEnrollmentCredits", courseEid, null);
		else
			enrolmentSet = courseManagementService.getEnrollmentSet(courseEid);
		
		if(! courseManagementService.isSectionDefined(courseEid)) {
			courseAdmin.createSection(courseEid, courseEid, descr, "course", null, courseEid, enrolmentSet.getEid());
		} else {
			Section section = courseManagementService.getSection(courseEid);
			section.setEnrollmentSet(enrolmentSet);
			section.setCategory("course");
		}
		
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		finally {
			
		}
		
	}

}
