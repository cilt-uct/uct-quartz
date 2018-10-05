package org.sakaiproject.component.app.scheduler.jobs;

import java.util.ArrayList;
import java.util.List;

import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.StatefulJob;
import org.sakaiproject.authz.api.AuthzGroup;
import org.sakaiproject.authz.api.AuthzGroupService;
import org.sakaiproject.coursemanagement.api.AcademicSession;
import org.sakaiproject.coursemanagement.api.CourseManagementService;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.site.api.SiteService.SortType;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UCTSaveRealms implements StatefulJob {
	
	private static boolean FIX_USERS = false;
	private SessionManager sessionManager;
	public void setSessionManager(SessionManager s) {
		this.sessionManager = s;
	}
	private SiteService siteService;
	public void setSiteService(SiteService s) {
		this.siteService = s;
	}
	private AuthzGroupService authzGroupService;
	public void setAuthzGroupService(AuthzGroupService a) {
		this.authzGroupService = a;
	}
	
	private CourseManagementService courseManagementService;
	public void setCourseManagementService(
			CourseManagementService courseManagementService) {
		this.courseManagementService = courseManagementService;
	}
	
	
	private List<String> getTerms() {
		List<AcademicSession>  as = courseManagementService.getCurrentAcademicSessions();
		List<String> ret = new ArrayList<String>();
		for (int i =0; i < as.size(); i++) {
			AcademicSession a = as.get(i);
			log.debug("got accademic session: " + a.getEid());
			ret.add(a.getEid());
		}
		return ret;
	}
	
	public void execute(JobExecutionContext arg0) throws JobExecutionException {
		// TODO Auto-generated method stub

	    // set the user information into the current session
	    Session sakaiSession = sessionManager.getCurrentSession();
	    sakaiSession.setUserId("admin");
	    sakaiSession.setUserEid("admin");
	    
	List<Site> sites = siteService.getSites(SiteService.SelectionType.NON_USER, "course", null, null, SortType.NONE, null);
	List<String> currentTerms = getTerms();
	for (int i =0 ; i< sites.size(); i++ ) {
		Site s = (Site)sites.get(i);
		log.debug("got site " + s.getTitle());
		if (s.getType()!= null && s.getType().equals("course")) {
			ResourceProperties sp = s.getProperties();
			String term = sp.getProperty("term");
			if (term != null ) {
				term = term.trim();
				log.debug("site is in term: " + term);
				if (currentTerms.contains(term)) {
					log.debug("saving realm: " + s.getTitle());
					try {
						AuthzGroup group = authzGroupService.getAuthzGroup("/site/" + s.getId());
						//we only need to save if there is a provider set
						if (group.getProviderGroupId() != null && group.getProviderGroupId().length() > 0) {
							authzGroupService.save(group);
						}
					}
					catch(Exception e) {
						log.warn(e.getMessage(), e);
					}
				}
			}
		}
				
	}
		
	}

}
