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

import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.sakaiproject.authz.api.AuthzGroup;
import org.sakaiproject.authz.api.AuthzGroupService;
import org.sakaiproject.authz.api.Member;
import org.sakaiproject.email.api.EmailService;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.user.api.UserNotDefinedException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UCTCourseSiteOwners implements Job {
	
	private static final String ADMIN = "admin";
	
	private AuthzGroupService authzGroupService;
	public void setAuthzGroupService(AuthzGroupService a) {
		this.authzGroupService = a;
	}
	public AuthzGroupService getAuthzGroupService(){
		
		return this.authzGroupService;
	}
	
	private EmailService emailService;
	public void setEmailService(EmailService e) {
		this.emailService = e;
	}
	
	private SiteService siteService;
	public void setSiteService(SiteService s) {
		this.siteService = s;
	}
	
	private UserDirectoryService userDirectoryService;
	public void setUserDirectoryService(UserDirectoryService s) {
		this.userDirectoryService = s;
	}

	private SessionManager sessionManager;
	public void setSessionManager(SessionManager s) {
		this.sessionManager = s;
	}
	
	public void execute(JobExecutionContext arg0) throws JobExecutionException {
		// TODO Auto-generated method stub
		addCourseOwners();
	}

	
	
private void addCourseOwners() {
	
    // set the user information into the current session
    Session sakaiSession = sessionManager.getCurrentSession();
    sakaiSession.setUserId(ADMIN);
    sakaiSession.setUserEid(ADMIN);
    
		AuthzGroup courseOwners = null;
		AuthzGroup groupCourseOwners = null;
		AuthzGroup groupProjectOwners = null;
		try {
		
			
			//get the authz for the site we need to add to 
			
			courseOwners = authzGroupService.getAuthzGroup("/site/922248af-9892-4539-0001-172b35ae4981");

			/*
			 * Course Site  Owners /site/922248af-9892-4539-0001-172b35ae4981/group/8f8418e9-783c-4fa1-0029-07dfc83811a2
			 * Project Site Owners /site/922248af-9892-4539-0001-172b35ae4981/group/f8605f3f-b585-4335-00ac-39adf25c97b2
			 *	
			 */
			groupCourseOwners = authzGroupService.getAuthzGroup("/site/922248af-9892-4539-0001-172b35ae4981/group/8f8418e9-783c-4fa1-0029-07dfc83811a2");
			groupProjectOwners = authzGroupService.getAuthzGroup("/site/922248af-9892-4539-0001-172b35ae4981/group/f8605f3f-b585-4335-00ac-39adf25c97b2");
		}
		catch (Exception e) {
			log.warn(e.getMessage(), e);
		}
		
		
		
		String addedUsers = "";
		//first get a list all course sites
		
		List<Site> siteList = siteService.getSites( org.sakaiproject.site.api.SiteService.SelectionType.ANY, null, null, null,SiteService.SortType.TITLE_ASC,null);
		                                   //(org.sakaiproject.site.api.SiteService.SelectionType.UPDATE, null, null, null, SortType.TITLE_ASC, null));
		log.debug("got a list with " + siteList.size() + " sites");
		for (int i = 0; i < siteList.size();i++) {
			Site thisSite = (Site)siteList.get(i);
			log.debug(thisSite.getTitle() + "(" + thisSite.getId() + ")");
			//ignore if type is null
			if (wantSite(thisSite)) {
				Set<Member> members = thisSite.getMembers();
				Iterator<Member> it = members.iterator();
				while (it.hasNext()){
					Member thisM = (Member)it.next();
					String role = thisM.getRole().getId();
					//LOG.debug("got member " + thisM.getUserEid());
					//LOG.debug("with roleid " + thisM.getRole().getId());
					//LOG.debug("with roleid " + role);
					if (role.equals("Site owner") || role.equals("Lecturer") || role.equals("Support staff")) {
						log.debug("got member " + thisM.getUserEid());
						User user = null;
						try {
							user = userDirectoryService.getUser(thisM.getUserId());
							log.debug("got user");
						}
						catch (UserNotDefinedException e) {
						 //no such user
							log.debug(e.getMessage(), e);
						}
						if (user != null) {
							String type = user.getType();
							if (type == null) {
							  type = "student";
							 }
							
							if (!type.equals("guest") && !type.equals("student") && !isInactiveType(type)) {
							
								//are they a member of the realm?
								String arole = authzGroupService.getUserRole(thisM.getUserId(),courseOwners.getId());
								if (arole == null) {
									//try add this user to the group
									log.debug("about to add " + thisM.getUserEid());
									Member m = courseOwners.getMember(thisM.getUserId());
									if (m == null ) {
										courseOwners.addMember(thisM.getUserId(),"Participant",true,false);
										addedUsers = addedUsers + thisM.getUserEid() + " (" + user.getFirstName() + " " + user.getLastName() + ") \n";
									} else {
										log.debug("user is already a member with active: " + m.isActive());
									}
								} else {
										log.debug("user is already a member");
								}
								if (thisSite.getType().equals("project") && role.equals("Site owner")) {
									String grole = authzGroupService.getUserRole(thisM.getUserId(),groupProjectOwners.getId());
									if (grole == null) {
										//try add this user to the group
										log.debug("about to add " + thisM.getUserEid());
										groupProjectOwners.addMember(thisM.getUserId(),"Participant",true,false);
										addedUsers = addedUsers + thisM.getUserEid() + " (" + user.getFirstName() + " " + user.getLastName() + ") \n";
									} else {
											log.debug("user is already a member");
									}
									
								}
								if (thisSite.getType().equals("course") && role.equals("Site owner")) {
									String grole = authzGroupService.getUserRole(thisM.getUserId(),groupCourseOwners.getId());
									if (grole == null) {
										//try add this user to the group
										log.debug("about to add " + thisM.getUserEid());
										groupCourseOwners.addMember(thisM.getUserId(),"Participant",true,false);
										addedUsers = addedUsers + thisM.getUserEid() + " (" + user.getFirstName() + " " + user.getLastName() + ") \n";
									} else {
											log.debug("user is already a member");
									}
								}
								
							}
							
						}
					} else if (!role.equals("Student")) {
						log.debug("got member " + thisM.getUserEid() + " with role " + role);
					}
				}
			} //end if type!=null
		

			
		}
		
		//beffore we save we need to clean up the groups of inactive users
		courseOwners = cleanUpGroup(courseOwners);
		
		try {
			authzGroupService.save(courseOwners);
			authzGroupService.save(groupCourseOwners);
			authzGroupService.save(groupProjectOwners);
			log.debug("added " + addedUsers);
			if (addedUsers.length() > 0) {
				
				emailService.send("help-team@vula.uct.ac.za","help-team@vula.uct.ac.za","Users added to CourseSiteOwners",addedUsers,null,null,null);
			}
		}
		catch (Exception e)
		{
			log.warn(e.getMessage(), e);
		}
		
		
		
		
}
private AuthzGroup cleanUpGroup(AuthzGroup group) {
	Set<Member> members = group.getMembers();
	Iterator<Member> it = members.iterator();
	while (it.hasNext()) {
		Member member = it.next();
		try {
			User u = userDirectoryService.getUser(member.getUserId());
			if (isInactiveType(u.getType())) {
				group.removeMember(member.getUserId());
			}
		} catch (UserNotDefinedException e) {
			//user doesn't exist remove the record
			group.removeMember(member.getUserId());
		}
	}
	
	return group;
}
private boolean isInactiveType(String type) {
	if (type == null)
		return false;
	
	if (type.startsWith("inactive") || type.startsWith("Inactive"))
		return true;
	
	return false;
}

private boolean wantSite(Site thisSite) {

	if (thisSite.getType() == null) 
		return false;

	if ("course".equals(thisSite.getType())) {
	
		ResourceProperties sp = thisSite.getProperties();
               	String term = sp.getProperty("term");

		if (term == null) return false;

                term = term.trim();
		if ("2015".equals(term) || "2016".equals(term) || "2017".equals(term)) {
			return true;
		} else {
			return false;
		}
	}

	if ("project".equals(thisSite.getType())) {
		return true;
	}

	return false;
}
	
	
}
