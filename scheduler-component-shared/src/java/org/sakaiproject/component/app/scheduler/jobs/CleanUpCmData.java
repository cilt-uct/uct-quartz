package org.sakaiproject.component.app.scheduler.jobs;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.ObjectNotFoundException;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.sakaiproject.coursemanagement.api.AcademicSession;
import org.sakaiproject.coursemanagement.api.CanonicalCourse;
import org.sakaiproject.coursemanagement.api.CourseManagementAdministration;
import org.sakaiproject.coursemanagement.api.CourseManagementService;
import org.sakaiproject.coursemanagement.api.CourseSet;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;

public class CleanUpCmData implements Job {
	
	
	private static final String ADMIN = "admin";
	private static final Log log = LogFactory.getLog(CleanUpCmData.class);
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
	
	
	private String term;
	public void setTerm(String t) {
		term = t;
	}
	
	public void execute(JobExecutionContext arg0) throws JobExecutionException {
		// TODO Auto-generated method stub
	    log.info("about to clean up cm date in term: " + 2008);
		Session sakaiSession = sessionManager.getCurrentSession();
	    sakaiSession.setUserId(ADMIN);
	    sakaiSession.setUserEid(ADMIN);
	    
	    AcademicSession thisTerm = courseManagementService.getAcademicSession(term);
	    
	    //first get course sets
	    List depts = courseManagementService.findCourseSets("Department");
	    
	    for (int i =0 ; i < depts.size(); i++) {
	    	CourseSet dept = (CourseSet) depts.get(i);
	    	log.info("got set: " + dept.getEid());
	    	Set canonCourses = courseManagementService.getCanonicalCourses(dept.getEid());
	    	Iterator it = canonCourses.iterator();
	    	while (it.hasNext()) {
	    		CanonicalCourse course = (CanonicalCourse) it.next();
	    		String eid = course.getEid();
	    		if (eid.lastIndexOf("SUP") == 8 ||eid.lastIndexOf("EWA") == 8) {
	    			log.warn("Found course to delete: " + eid);
	    			String fullEid = eid + "," + term;
	    			//we only need to remove at this level
	    			//we need to remove the course offering from its course set
	    			Set courseSets = course.getCourseSetEids();
	    			Iterator ita = courseSets.iterator();
	    			while (ita.hasNext()) {
	    				String set = (String)ita.next();
	    				courseAdmin.removeCanonicalCourseFromCourseSet(set, eid);
	    			}
	    			
	    			//course offering
	    			if (courseManagementService.isCourseOfferingDefined(fullEid)) 
	    				courseAdmin.removeCourseOffering(fullEid);
	    				
	    			//canon course
	    			courseAdmin.removeCanonicalCourse(eid);
	    		}
	    	}
	    }
	    
	    
	}

}
