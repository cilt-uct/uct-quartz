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
public class UCTImportPrograms implements Job {

	private static final String ADMIN = "admin";

	// These constants are shared with the SPML code
	// Special OFFER and PREREG terms (note: upper case)
	// OFFER means provisional or final offer (SPML student status Admitted)
	// PREREG means matriculated and term-activated in Peoplesoft with a program code but no courses yet
	private static final String TERM_OFFER = "OFFER";
	private static final String TERM_PREREG = "PREREG";

	private static final String CAT_DEGREE = "degree";
	private static final String CAT_COURSE = "course";

	@Setter private CourseManagementService courseManagementService;
	@Setter private CourseManagementAdministration courseManagementAdministration;
	@Setter private SessionManager sessionManager;

	private String filePath;
	public void setFilePath(String fp) {
		filePath = fp;
	}

	public void execute(JobExecutionContext arg0) throws JobExecutionException {
		// Program data spreadsheet typically in /data/sakai/otherdata/import/programinfo.csv
		importFile(filePath + "programinfo.csv");
	}

	private void importFile(String file) {
		// set the user information into the current session
		Session sakaiSession = sessionManager.getCurrentSession();
		sakaiSession.setUserId(ADMIN);
		sakaiSession.setUserEid(ADMIN);

		SimpleDateFormat yearf = new SimpleDateFormat("yyyy");
		String term = yearf.format(new Date());
		log.debug("term is {}", term);

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
			log.info("Updating program information from {}", file);
		} catch (FileNotFoundException e) {
			log.error("Cannot open program data file {} for reading", file);
			return;
		}
       
		// Read CSV line by line
		try {
			String[] nextLine;
			while ((nextLine = reader.readNext()) != null) {
				if (nextLine != null) {
					// CB003,"BBusSc in Actuarial Science"
					String program = nextLine[0];
					String descr =  nextLine[1];

					log.info("createProgram({}, {}) in term {}", program, descr, term);
					addProgram(program, term, descr);
					addProgram(program, TERM_OFFER, descr);
					addProgram(program, TERM_PREREG, descr);
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

	/**
	 * Create a program in the course management service
	 * @param programCode
	 * @param term - an academic year (e.g. 2021, OFFER or PREREG)
	 * @param setCategory - CM category. In use: Department, course, degree, faculty, Residence, NULL 
	 */
	private void addProgram(String programCode, String term, String description) {

		log.debug("addProgram({}, {})",  programCode, term);

		try {

			// Program codes always upper case
			programCode = programCode.toUpperCase().trim();
			
			// Using shorter names here for code consistency with SPML code
			CourseManagementAdministration courseAdmin = courseManagementAdministration;
			CourseManagementService cmService = courseManagementService;
			
			if (programCode == null || programCode.length() == 0) {
				return;
			}

			String setId = programCode.substring(0,2);
			String setCategory = CAT_DEGREE;

			// we already have a specific term
			String programEid = programCode + "," +term;

			// do we have an academic session?
			if (!cmService.isAcademicSessionDefined(term)) {

				if (TERM_OFFER.equals(term) || TERM_PREREG.equals(term)) {
					Calendar cal = Calendar.getInstance();
					cal.set(2021, 1, 1);
					Date start =  cal.getTime();
					cal.set(2099, Calendar.DECEMBER, 31);
					Date end = cal.getTime();
					courseAdmin.createAcademicSession(term, term, term, start, end);
				} else {
					Calendar cal = Calendar.getInstance();
					cal.set(new Integer(term).intValue(), 1, 1);
					Date start =  cal.getTime();
					cal.set(new Integer(term).intValue(), Calendar.DECEMBER, 30);
					Date end = cal.getTime();
					courseAdmin.createAcademicSession(term, term, term, start, end);
				}
			}
			
			// does the course set exist?
			if (!cmService.isCourseSetDefined(setId)) 
				courseAdmin.createCourseSet(setId, setId, setId, setCategory, null);

			// is there a canonical course?
			if (!cmService.isCanonicalCourseDefined(programCode)) {
				courseAdmin.createCanonicalCourse(programCode, programCode, programCode);
				courseAdmin.addCanonicalCourseToCourseSet(setId, programCode);
			}

			if (!cmService.isCourseOfferingDefined(programEid)) {
				
				// Create a new course offering for this course if it doesn't exist yet

				log.info("creating course offering for {} in year {}", programCode, term);
				
				// If this is being created by import, it is current now (except for offer terms)
				Date startDate = new Date();

				Calendar cal2 = Calendar.getInstance();

				if (TERM_OFFER.equals(term) || TERM_PREREG.equals(term)) {
					// Offer and Active program codes always predate the current year
					cal2.set(Calendar.DAY_OF_MONTH, 1);
					cal2.set(Calendar.MONTH, Calendar.JANUARY);
					cal2.set(Calendar.YEAR, 2001);
					startDate = cal2.getTime();
				} else {
					// Use the term date
					cal2.set(Calendar.DAY_OF_MONTH, 31);
					cal2.set(Calendar.MONTH, Calendar.DECEMBER);
					if (term != null) {
						cal2.set(Calendar.YEAR, Integer.valueOf(term));
					}
				}

				Date endDate = cal2.getTime();
				log.debug("got cal: {}/{}/{}", cal2.get(Calendar.YEAR), cal2.get(Calendar.MONTH), cal2.get(Calendar.DAY_OF_MONTH));
				
				courseAdmin.createCourseOffering(programEid, programEid, description, "active", term, programCode, startDate, endDate);
				courseAdmin.addCourseOfferingToCourseSet(setId, programEid);
			}

			// we know that all objects to this level must exist
			EnrollmentSet enrollmentSet = null;
			if (!cmService.isEnrollmentSetDefined(programEid)) {
				enrollmentSet =  courseAdmin.createEnrollmentSet(programEid, description, description, "category", "defaultEnrollmentCredits", programEid, null);
			} else {
				enrollmentSet = cmService.getEnrollmentSet(programEid);
			}

			if (!cmService.isSectionDefined(programEid)) {
				courseAdmin.createSection(programEid, programEid, description, CAT_COURSE, null, programEid, enrollmentSet.getEid());
			} else {
				Section section = cmService.getSection(programEid);
				// Check the section has a properly defined Enrollment set
				if (section.getEnrollmentSet() == null) {
					EnrollmentSet enrolmentSet = cmService.getEnrollmentSet(programEid);
					section.setEnrollmentSet(enrolmentSet);
					section.setCategory(CAT_COURSE);
					courseAdmin.updateSection(section);
				}
			}

		}
		catch(Exception e) {
			log.warn("Exception adding user to course:", e);
		}
	}

}
