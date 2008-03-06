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
	
	public void execute(JobExecutionContext arg0) throws JobExecutionException {
		// TODO Auto-generated method stub

		//set the user information into the current session
	    Session sakaiSession = sessionManager.getCurrentSession();
	    sakaiSession.setUserId("admin");
	    sakaiSession.setUserEid("admin");
	    
	    List users = userDirectoryService.getUsers();
	    for (int i = 0; i < users.size(); i++) {
	    	User u = (User) users.get(i);
	    	
	    	if (u.getType() != null && (u.getType().equals("student") || u.getType().equals("staff") || u.getType().equals("thirdparty")))
	    	try {
				Site userSite = siteService.getSite(siteService.getUserSiteId(u.getId()));
				SitePage page = userSite.addPage();
				ToolConfiguration tool = page.addTool("sakai.iframe");
				tool.getPlacementConfig().setProperty(
						"source", "https://vula.uct.ac.za/web/learnonline/ekp/index.htm");
				tool.save();
				siteService.save(userSite);
				
			} catch (IdUnusedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (PermissionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

	    }
		
	}

}
