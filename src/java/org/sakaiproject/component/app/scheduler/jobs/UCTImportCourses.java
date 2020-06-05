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

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

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

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UCTImportCourses implements Job {

	private static final String ADMIN = "admin";

	@Setter private CourseManagementService courseManagementService;
	@Setter private CourseManagementAdministration courseManagementAdministration;
	@Setter private SessionManager sessionManager;

	private String filePath;
	public void setFilePath(String fp) {
		filePath = fp;
	}

	public void execute(JobExecutionContext arg0) throws JobExecutionException {
		// Course data spreadsheet typically in /data/sakai/otherdata/import/201x_courses.csv
		importFile(filePath + "courseinfo.csv");
	}

	private void importFile(String file) {
		// set the user information into the current session
		Session sakaiSession = sessionManager.getCurrentSession();
		sakaiSession.setUserId(ADMIN);
		sakaiSession.setUserEid(ADMIN);

		CSVReader reader = null;

		try {
			CSVParser parser = new CSVParserBuilder()
					.withSeparator(',')
					.withQuoteChar('"')
					.withStrictQuotes(false)
					.build();
			reader = new CSVReaderBuilder(new FileReader(file))
					.withCSVParser(parser)
					.build();
					//new CSVReader(new FileReader(file), ',' , '"' , 0);
			log.info("Updating course information from " + file);
		} catch (FileNotFoundException e) {
			log.error("Cannot open file " + file + " for reading");	
			return;
		}
       
		DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");

		// Read CSV line by line
		try {
			String[] nextLine;
			while ((nextLine = reader.readNext()) != null) {
				if (nextLine != null) {
					try {
						// ACC3023S,2016,"Management Accounting II",ACC,2016-07-18,2016-11-11
						String course = nextLine[0];
						String year = nextLine[1];
						String descr =  nextLine[2];
						String subject =  nextLine[3];
						Date startDate = formatter.parse(nextLine[4]);
						Date endDate = formatter.parse(nextLine[5]);
						log.info("createCourse(" + course + "," + year + "," + descr + "," + subject + "," + 
							nextLine[4] + "," + nextLine[5] + ")");
						this.createCourse(course, year, descr, subject, startDate, endDate);
					} catch (java.text.ParseException e) {
						log.error("Skipping CSV record for " + nextLine[0] + " - cannot parse date formats");
					}
				}
			}
		} catch (IOException e) {
			log.error("Error reading CSV file {}", file);	
			return;
		} catch (CsvValidationException e) {
			log.error("Error reading CSV file {}", file);	
			return;
		}

		log.info("Finished");
	}

	private Set<String> acadTerms = new HashSet<String>();
	private Set<String> courseSets = new HashSet<String>();
	
	private void createCourse(String courseCode, String term, String descr, String setId, Date startDate, Date endDate) {

		String setCategory = "Department";
		String courseEid = courseCode + "," + term;

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

		// does the academic session exist
		if (!acadTerms.contains(term)) {
			if (!courseManagementService.isAcademicSessionDefined(term)) {
				courseManagementAdministration.createAcademicSession(term, term,term + " academic year", new Date(), yearEnd);
				acadTerms.add(term);
			} else {
				acadTerms.add(term);
			}
		}

		// does the course set exist?
		if (!courseSets.contains(setId)) {
			if (!courseManagementService.isCourseSetDefined(setId)) { 
				courseManagementAdministration.createCourseSet(setId, setId, setId, setCategory, null);
				courseSets.add(setId);
			} else {
				courseSets.add(setId);
			}
		}

		if (!courseManagementService.isCanonicalCourseDefined(courseCode)) {
			courseManagementAdministration.createCanonicalCourse(courseCode, courseCode, descr);
			courseManagementAdministration.addCanonicalCourseToCourseSet(setId, courseCode);
		}

		if (!courseManagementService.isCourseOfferingDefined(courseEid)) {
			// create new course
			log.info("creating course offering for " + courseCode + " in year " + term);
			courseManagementAdministration.createCourseOffering(courseEid, courseCode + " - " + descr, courseEid + " - " + descr, "active", term, courseCode, startDate, endDate);

			courseManagementAdministration.addCourseOfferingToCourseSet(setId, courseEid);		 

			EnrollmentSet enrolmentSet = null; 
			if (!courseManagementService.isEnrollmentSetDefined(courseEid)) {
				enrolmentSet = courseManagementAdministration.createEnrollmentSet(courseEid, descr, descr, "category", "defaultEnrollmentCredits", courseEid, null);
			} else {
				enrolmentSet = courseManagementService.getEnrollmentSet(courseEid);
			}

			if (!courseManagementService.isSectionDefined(courseEid)) {
				courseManagementAdministration.createSection(courseEid, courseEid, descr, "course", null, courseEid, enrolmentSet.getEid());
			} else {
				Section section = courseManagementService.getSection(courseEid);
				section.setEnrollmentSet(enrolmentSet);
				section.setCategory("course");
				courseManagementAdministration.updateSection(section);
			}
		} else {
			// update the course details
			CourseOffering co = courseManagementService.getCourseOffering(courseEid);
			co.setTitle(courseEid + " - " + descr);
			co.setDescription(courseCode + " - " + descr);
			co.setStartDate(startDate);
			co.setEndDate(endDate);
			courseManagementAdministration.updateCourseOffering(co);
		}
	}
}
