package org.sakaiproject.component.app.scheduler.jobs;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SitePage;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.site.api.ToolConfiguration;
import org.sakaiproject.site.api.SiteService.SortType;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserDirectoryService;

public class AddPageToMyWorkspace implements Job {

	private static final Log LOG = LogFactory.getLog(AddPageToMyWorkspace.class);
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


	public void execute(JobExecutionContext arg0) throws JobExecutionException {
		// TODO Auto-generated method stub
		
		//set the user information into the current session
	    Session sakaiSession = sessionManager.getCurrentSession();
	    sakaiSession.setUserId("admin");
	    sakaiSession.setUserEid("admin");
	    
	    List users = userDirectoryService.getUsers();
	    for (int i = 0; i < users.size(); i++) {
	    	User u = (User) users.get(i);
	    	
	    	if (doUserType(u.getType()))
	    		LOG.info("going to add page to: " + u.getEid());
	    	try {
				Site userSite = siteService.getSite(siteService.getUserSiteId(u.getId()));
				if (!siteContainsPage(userSite)) {
					SitePage page = userSite.addPage();
					page.setTitle(pageTitle);
					siteService.save(userSite);
					ToolConfiguration tool = page.addTool(toolId);
					tool.setTitle(pageTitle);
					/*
				tool.getPlacementConfig().setProperty(
						"source", "https://vula.uct.ac.za/web/learnonline/ekp/index.htm");
					 */
					siteService.save(userSite);
				}
				
			} catch (IdUnusedException e) {
				// TODO Auto-generated catch block
				//e.printStackTrace();
				LOG.info("user has no workspace!: " + u.getId());
			} catch (PermissionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

	    }
		
	}


	private boolean siteContainsPage(Site userSite) {
		List pages = userSite.getPages();
		for (int i = 0; i < pages.size(); i++) {
			SitePage page = (SitePage)pages.get(i);
			if (pageTitle.equals(page.getTitle()))
				return true;
		}
		
		return false;
	}


	private boolean doUserType(String type) {
		//u.getType() != null && (u.getType().equals("student") || u.getType().equals("staff") || u.getType().equals("thirdparty"))
		/*
		 * for now do all users
		 */
		return true;
	}

}
