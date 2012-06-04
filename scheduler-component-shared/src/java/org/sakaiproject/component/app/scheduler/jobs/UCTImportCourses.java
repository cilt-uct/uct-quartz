package org.sakaiproject.component.app.scheduler.jobs;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.Row;
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

	public void execute(JobExecutionContext arg0) throws JobExecutionException {
		///data/sakai/import/2010_courses.csv

		//importFile(filePath + "2010_courses.xls", "2010");
		//importFile(filePath + "2011_courses.xls", "2011");
		//importFile(filePath + "2011S_courses.xls", "2011");
		importFile(filePath + "2012_courses.xls", "2012");
	}

	private void importFile(String file, String session) {
		// set the user information into the current session
		Session sakaiSession = sessionManager.getCurrentSession();
		sakaiSession.setUserId(ADMIN);
		sakaiSession.setUserEid(ADMIN);
		InputStream fr = null;
		//CSVReader br = null;
		POIFSFileSystem fileSystem = null;
		try {
			log.info("opening: " + file);
			fr = new FileInputStream(file);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}
		if (fr == null) {
			return;
		}

		try {
			
			fileSystem = new POIFSFileSystem(fr);
			
			HSSFWorkbook workBook = new HSSFWorkbook (fileSystem);
			HSSFSheet sheet = workBook.getSheetAt(0);
			Iterator<Row> rows = sheet.rowIterator ();
			//we need to skip the first 3 rows
			rows.next();
			rows.next();
			rows.next();
			
			while (rows.hasNext()) { 
				Row row = rows.next();
				
				/*
				 * this should be a record of the format:
				 * Course ID	Offer Nbr	Term	Session	Sect	Institution	Acad Group	Subject	Catalog	Career	Descr	Class Nbr	Component

				 */
				//date is in 11, 12
				Date startDate = row.getCell(13).getDateCellValue();
				Date endDate = row.getCell(14).getDateCellValue();
				String sectionType = row.getCell(4).getStringCellValue();
				if (doSectionType(sectionType)) {
					this.createCourse(row.getCell(7).getStringCellValue() + row.getCell(8).getStringCellValue(), session, row.getCell(10).getStringCellValue(), row.getCell(7).getStringCellValue(), startDate, endDate);
				} else {
					LOG.info(row.getCell(7).getStringCellValue() + row.getCell(8).getStringCellValue() + " is of type " + row.getCell(12).getStringCellValue() + " with id " + sectionType +  " so ignoring");
				}
			} 


		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		finally {
			
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

	private boolean doSectionType(String sectionType) {
		if (sectionType == null) {
			return false;
		}
		
		//TODO set this by string injection
		List<String> sections = new ArrayList<String>();
		sections.add("LG01");
		sections.add("LECT");
		sections.add("RG01");
		sections.add("1");
		sections.add("EG");
		sections.add("EG001");
		sections.add("EWA");
		sections.add("FG01");
		//Clinical courses in HS
		sections.add("CG01");
		//prac groups - used in music and drama
		sections.add("PG01");
		
		return sections.contains(sectionType);
	}


	private void createCourse(String courseCode, String term, String descr, String setId, Date startDate, Date endDate) {
		LOG.info("createCourse(" + courseCode + "," + term + "," + descr + "," + setId );

		//if this is a EWA or SUP course ignore if before 2012
		int yearNumeric = Integer.valueOf(term).intValue();
		
		if (yearNumeric < 2012 && (courseCode.lastIndexOf("SUP") == 8 ||courseCode.lastIndexOf("EWA") == 8)) {
			LOG.warn("we won't import " + courseCode + " as it is a SUP or EWA course in year " + term);
			return;
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
			courseAdmin.updateSection(section);
		}


	}

}
