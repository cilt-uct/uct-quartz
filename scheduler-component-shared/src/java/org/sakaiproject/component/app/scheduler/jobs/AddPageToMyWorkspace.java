package org.sakaiproject.component.app.scheduler.jobs;

import java.util.List;
import java.util.Map;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SitePage;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.site.api.ToolConfiguration;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserDirectoryService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AddPageToMyWorkspace implements Job {


	private SiteService siteService;
	public void setSiteService(SiteService s) {
		this.siteService = s;
	}
	
	private SessionManager sessionManager;
	public void setSessionManager(SessionManager s) {
		this.sessionManager = s;
	}
	
	private UserDirectoryService userDirectoryService;
	public void setUserDirectoryService(UserDirectoryService s) {
		this.userDirectoryService = s;
	}
	
	private String pageTitle;
	public void setPageTitle(String pageTitle) {
		this.pageTitle = pageTitle;
	}

	private String toolId;
	public void setToolId(String toolId) {
		this.toolId = toolId;
	}
	
	private Map<String, String> pageProperties;
	public void setPageProperties(Map<String, String> pageProperties){
		this.pageProperties = pageProperties;
	}


	public void execute(JobExecutionContext arg0) throws JobExecutionException {
		// TODO Auto-generated method stub
		
		//set the user information into the current session
	    Session sakaiSession = sessionManager.getCurrentSession();
	    sakaiSession.setUserId("admin");
	    sakaiSession.setUserEid("admin");
	    
	    List<User> users = userDirectoryService.getUsers();
	    for (int i = 0; i < users.size(); i++) {
	    	User u = (User) users.get(i);
	    	
	    	if (doUserType(u))
	    		
	    	try {
				Site userSite = siteService.getSite(siteService.getUserSiteId(u.getId()));
				if (!siteContainsPage(userSite)) {
					log.info("going to add page to: " + u.getEid());
					SitePage page = userSite.addPage();
					page.setTitle(pageTitle);
					siteService.save(userSite);
					ToolConfiguration tool = page.addTool(toolId);
					tool.setTitle(pageTitle);					
					placePageProperties(tool);					
					siteService.save(userSite);
				}
				
			} catch (IdUnusedException e) {
				log.info("user has no workspace!: " + u.getId());
			} catch (PermissionException e) {
				log.warn("PermissionException", e);
			}

	    }
		
	}


	private boolean siteContainsPage(Site userSite) {
		List<SitePage> pages = userSite.getPages();
		for (int i = 0; i < pages.size(); i++) {
			SitePage page = (SitePage)pages.get(i);
			if (pageTitle.equals(page.getTitle()))
				return true;
		}
		
		return false;
	}


	private boolean doUserType(User u) {
		return u.getType() != null && (u.getType().equals("student") || u.getType().equals("staff"));
		/*
		 * for now do staff and students
		 */
		//return true;
	}
	
	private void placePageProperties(ToolConfiguration tool){
		if (pageProperties != null && pageProperties.size() > 0 ){
			for (Map.Entry<String, String> property : pageProperties.entrySet()){
				if(property.getKey() != null){
					tool.getPlacementConfig().setProperty(property.getKey(), property.getValue());
				}
			}
		}
	}

}
