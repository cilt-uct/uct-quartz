package org.sakaiproject.component.app.scheduler.jobs;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.sakaiproject.coursemanagement.api.CourseManagementAdministration;
import org.sakaiproject.coursemanagement.api.CourseManagementService;
import org.sakaiproject.coursemanagement.api.CourseOffering;
import org.sakaiproject.coursemanagement.api.EnrollmentSet;
import org.sakaiproject.coursemanagement.api.Section;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;

import au.com.bytecode.opencsv.CSVReader;

public class UCTImportCourses implements Job {
	private static final Log log = LogFactory.getLog(UCTImportCourses.class);

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

	private String filePath;
	public void setFilePath(String fp) {
		filePath = fp;
	}

	private String term;
	public void setTerm(String t) {
		term = t;
	}

	public void execute(JobExecutionContext arg0) throws JobExecutionException {
		///data/sakai/import/2010_courses.csv

		importFile(filePath + "2010_courses.csv", "2010");
		importFile(filePath + "2011_courses.csv", "2011");
	}

	private void importFile(String file, String session) {
		// set the user information into the current session
		Session sakaiSession = sessionManager.getCurrentSession();
		sakaiSession.setUserId(ADMIN);
		sakaiSession.setUserEid(ADMIN);
		FileReader fr = null;
		CSVReader br = null;
		try {
			log.info("opening: " + file);
			fr = new FileReader(file);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}
		if (fr == null) {
			return;
		}

		try {
			br = new CSVReader(fr);
			String[] data;  
			while ( (data = br.readNext()) != null) { 
				/*
				 * this should be a record of the format:
				 * Course ID	Offer Nbr	Term	Session	Sect	Institution	Acad Group	Subject	Catalog	Career	Descr	Class Nbr	Component

				 */
				//date is in 11, 12
				Date startDate = parseDate(data[13]);
				Date endDate = parseDate(data[14]);

				this.createCourse(data[7] + data[8], session, data[10], data[7], startDate, endDate);
			} 


		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			if (fr != null) {
				try {
					fr.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

		}

	}


	private Date parseDate(String string) {
		//format is 7/25/2011
		DateFormat df = new SimpleDateFormat("M/d/y");
		Date ret = null;
		try {
			ret = df.parse(string);
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return ret;
	}

	private void createCourse(String courseCode, String term, String descr, String setId, Date startDate, Date endDate) {
		LOG.info("createCourse(" + courseCode + "," + term + "," + descr + "," + setId );

		//if this is a EWA or SUP course ignore
		if (courseCode.lastIndexOf("SUP") == 8 ||courseCode.lastIndexOf("EWA") == 8) {
			LOG.warn("we won't import " + courseCode + "as it is a SUP or EWA course");
		}

		String setCategory = "Department";
		String courseEid = courseCode +","+term;

		Calendar cal = Calendar.getInstance();
		if (startDate == null) {
			cal.set(Calendar.YEAR, new Integer(term).intValue());
			cal.set(Calendar.MONTH, Calendar.JANUARY);
			cal.set(Calendar.DAY_OF_MONTH, 1);
			startDate = cal.getTime();
		}


		cal.set(Calendar.MONTH, Calendar.DECEMBER);
		cal.set(Calendar.DAY_OF_MONTH, 31);
		Date yearEnd = cal.getTime();

		if (endDate == null) {
			endDate = cal.getTime();
		}

		//does the academic session exist
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
			courseAdmin.createCourseOffering(courseEid, courseCode + " - " + descr, courseEid + " - " + descr, "active", term, courseCode, startDate, endDate);

		} else {
			//update the name
			CourseOffering co = courseManagementService.getCourseOffering(courseEid);
			co.setTitle(courseEid + " - " + descr);
			co.setDescription(courseCode + " - " + descr);
			co.setStartDate(startDate);
			co.setEndDate(endDate);
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


	}

}
