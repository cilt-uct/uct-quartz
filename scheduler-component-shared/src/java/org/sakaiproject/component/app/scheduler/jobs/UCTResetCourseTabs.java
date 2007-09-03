package org.sakaiproject.component.app.scheduler.jobs;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.PreferencesService;
import org.sakaiproject.user.api.Preferences;
import org.sakaiproject.user.api.PreferencesEdit;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.entity.api.ResourcePropertiesEdit;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.site.api.SiteService.SelectionType;
import org.sakaiproject.site.api.SiteService.SortType;
import org.sakaiproject.site.api.Site;


import java.util.List;
import java.util.Collection;
import java.util.Iterator;


public class UCTResetCourseTabs implements Job {

	private static final Log LOG = LogFactory.getLog(UCTResetCourseTabs.class);
	private static final String ADMIN = "admin";
	private UserDirectoryService userDirectoryService;
	public void setUserDirectoryService(UserDirectoryService s) {
		this.userDirectoryService = s;
	}
	
	private PreferencesService preferencesService;
	public void setPreferencesService(PreferencesService p){
		preferencesService = p;
		
	}
	private SessionManager sessionManager;
	public void setSessionManager(SessionManager s) {
		this.sessionManager = s;
	}
	private SiteService siteService;
	public void setSiteService(SiteService s) {
		this.siteService = s;
	}
	
	public void execute(JobExecutionContext arg0) throws JobExecutionException {
		// TODO Auto-generated method stub
		
	    // set the user information into the current session
	    Session sakaiSession = sessionManager.getCurrentSession();
	    sakaiSession.setUserId(ADMIN);
	    sakaiSession.setUserEid(ADMIN);
		List users = userDirectoryService.getUsers();
		for (int i= 0; i < users.size(); i++ ){
			User u = (User)users.get(i);
			LOG.info("GOT User " + u.getId());
			
			//get the 	list of sites
			
			
			PreferencesEdit p = null;
			try {
				p = preferencesService.edit(u.getId());
			}
			catch (IdUnusedException e) {
				//e.printStackTrace();
				LOG.warn("user has no preferences set!");
				try {
					p = preferencesService.add(u.getId());
				}
				catch (Exception ex) {
					ex.printStackTrace();
				}
			}
			catch (Exception ex) {
				ex.printStackTrace();
			}
			if (p!= null) {
//				the key we need is sakai:portal:sitenav
				ResourcePropertiesEdit rp = p.getPropertiesEdit("sakai:portal:sitenav");
//				name is exclude
				/* no tneeded
				List ex = null;
				if (rp != null ){
					//we need this list so we don't loose sites later
					ex = rp.getPropertyList("exclude");
					if (ex != null ) {
					for (int q =0;q < ex.size();q++){
						String value = (String) ex.get(q);
						LOG.info("got a vaulue of " + value);
					}
					}
				} else {
					LOG.warn("resourceProperites is null");
				}
				*/
			    sakaiSession.setUserId(u.getId());
			    sakaiSession.setUserEid(u.getEid());
			    
				List allSites = siteService.getSites(SelectionType.ACCESS, null, null,
						  null, SortType.TITLE_ASC, null);
				List moreSites = siteService.getSites(SelectionType.UPDATE, null, null,
							null, SortType.TITLE_ASC, null);
				allSites.addAll(moreSites);
				 for (int q = 0; q < allSites.size();q++) {
					 Site s = (Site)allSites.get(q);
					 LOG.info("got site " + s.getTitle());
					 if (s.getType()!= null && s.getType().equals("course")) {
						 ResourceProperties sp = s.getProperties();
						 String term = sp.getProperty("term");
						 if (term != null ) {
							 term = term.trim();
							 LOG.info("site is in term: " + term);
							 if (term.equals("2006")) {
								 LOG.warn("addding this site to the excludes list");
								 rp.addPropertyToList("exclude", s.getId()); 
								 
							 }
						 }
					 }
				 }
				
				
				
				
				
				preferencesService.commit(p);
			    sakaiSession.setUserId(ADMIN);
			    sakaiSession.setUserEid(ADMIN);
			}
		}
		

	}

}
