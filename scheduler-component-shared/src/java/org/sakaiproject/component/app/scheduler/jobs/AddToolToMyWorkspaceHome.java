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
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserDirectoryService;

public class AddToolToMyWorkspaceHome implements Job {

	private static final Log LOG = LogFactory.getLog(AddToolToMyWorkspaceHome.class);
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

	
	private String toolTitle;
	public void setToolTitle(String toolTitle) {
		this.toolTitle = toolTitle;
	}


	public void execute(JobExecutionContext arg0) throws JobExecutionException {
		// TODO Auto-generated method stub
		
		//set the user information into the current session
	    Session sakaiSession = sessionManager.getCurrentSession();
	    sakaiSession.setUserId("admin");
	    sakaiSession.setUserEid("admin");
	    
	    int first = 1;
		int increment = 1000;
		int last = increment;
		boolean doAnother = true;
		while (doAnother) {

			List<User> users = userDirectoryService.getUsers(first, last);
			for (int i = 0; i < users.size(); i++) {
				User u = (User) users.get(i);

				if (doUserType(u.getType()))

					try {
						Site userSite = siteService.getSite(siteService.getUserSiteId(u.getId()));
						if (!siteHomeContainsTool(userSite)) {
							
							SitePage page = getHomePage(userSite);
							if (page != null) {
								LOG.info("going to add tool to  page to: " + u.getEid() + " on page: " + page.getTitle());
								ToolConfiguration tool = page.addTool(toolId);
								tool.setLayoutHints("0,1");
								tool.setTitle(toolTitle);
								tool.moveUp();
								siteService.save(userSite);
							} else {
								LOG.warn("users worksite has no Home page!: " + u.getEid());

							}
						}

					} catch (IdUnusedException e) {
						LOG.debug("user has no workspace!: " + u.getId());
					} catch (PermissionException e) {
						e.printStackTrace();
					}

			}
			if (users.size() < increment) {
				doAnother = false;
			} else {
				first = last +1;
				last = last + increment;
			}
		}

		
	}


	private SitePage getHomePage(Site userSite) {
		List<SitePage> pages = userSite.getPages();
		for (int i = 0; i < pages.size();  i++) {
			SitePage page = (SitePage)pages.get(i);
			if (pageTitle.equals(page.getTitle())) {
				return page;
			}
		}
		
		return null;
	}


	private boolean siteHomeContainsTool(Site userSite) {
		List<SitePage> pages = userSite.getPages();
		for (int i = 0; i < pages.size();  i++) {
			SitePage page = (SitePage)pages.get(i);
			if (pageTitle.equals(page.getTitle())) {
				List<ToolConfiguration> tools = page.getTools();
				for (int q = 0; q < tools.size(); q++) {
					ToolConfiguration tool = tools.get(q);
					if (toolId.equals(tool.getToolId())) {
						return true;
					}
				}
				return false;
			}
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
