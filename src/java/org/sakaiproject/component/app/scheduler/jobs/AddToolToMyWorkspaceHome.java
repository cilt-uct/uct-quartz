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

import java.util.List;

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
public class AddToolToMyWorkspaceHome implements Job {

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
								log.info("going to add tool to  page to: " + u.getEid() + " on page: " + page.getTitle());
								ToolConfiguration tool = page.addTool(toolId);
								tool.setLayoutHints("0,1");
								tool.setTitle(toolTitle);
								tool.moveUp();
								siteService.save(userSite);
							} else {
								log.warn("users worksite has no Home page!: " + u.getEid());

							}
						}

					} catch (IdUnusedException e) {
						log.debug("user has no workspace!: " + u.getId());
					} catch (PermissionException e) {
						log.warn("PermissionException", e);
					}

			}
			if (users.size() < increment) {
				doAnother = false;
			} else {
				first = last + 1;
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
