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

import java.util.ArrayList;
import java.util.List;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.entity.api.ResourcePropertiesEdit;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.user.api.PreferencesEdit;
import org.sakaiproject.user.api.PreferencesService;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserDirectoryService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UCTResetCourseTabs implements Job {

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
			log.info("GOT User " + u.getId());
			
			//get the 	list of sites
			
			
			PreferencesEdit p = null;
			try {
				p = preferencesService.edit(u.getId());
			}
			catch (IdUnusedException e) {
				//e.printStackTrace();
				log.warn("user has no preferences set!");
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
				sakaiSession.setUserId(u.getId());
				sakaiSession.setUserEid(u.getEid());
				ResourcePropertiesEdit rp = p.getPropertiesEdit("sakai:portal:sitenav");
//				name is exclude
				List order = null;
				List top = new ArrayList();
				List bottom =  new ArrayList();
				if (rp != null ){
					//we need this list so we don't loose sites later
					order = rp.getPropertyList("order");
					if (order != null ) {
						for (int q = 0;q < order.size();q++){
							String value = (String) order.get(q);
							log.info("got a vaulue of " + value);
							Site s = null;
							try {
								s = siteService.getSite(value);
							} catch (IdUnusedException e) {
								log.warn(e.getMessage(), e);
							}
							if (s != null && s.getType()!= null && s.getType().equals("course")) {
								//anything not 2008 moves down
								ResourceProperties sp = s.getProperties();
								String term = sp.getProperty("term");
								term = term.trim();
								if (term != null && !term.equals("2008") ) {
									log.info("site is in term: " + term);
									log.info("adding term to bottom list");
									bottom.add(value);
								} else {
									top.add(value);
								}

							} else {
								top.add(value);
							}
						}
						rp.removeProperty("order");
						
						for (int q = 0; q < top.size(); q++) {
							rp.addPropertyToList("order", (String)top.get(q));
						}
						for (int q = 0; q < bottom.size(); q++) {
							rp.addPropertyToList("order", (String)bottom.get(q));
						}
						
				} else {
					log.warn("resourceProperites is null");
				}
				
			  
			    

				 }
				
				
				
				
				
				preferencesService.commit(p);
			    sakaiSession.setUserId(ADMIN);
			    sakaiSession.setUserEid(ADMIN);
			}
		}
		

	}

}
