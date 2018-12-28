/**********************************************************************************
 *
 * Copyright (c) 2013 The Sakai Foundation
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
import org.sakaiproject.api.common.edu.person.SakaiPerson;
import org.sakaiproject.api.common.edu.person.SakaiPersonManager;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserAlreadyDefinedException;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.user.api.UserEdit;
import org.sakaiproject.user.api.UserLockedException;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.user.api.UserPermissionException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FixEmails implements Job {

	private SessionManager sessionManager;
	public void setSessionManager(SessionManager s) {
		this.sessionManager = s;
	}
	
	private static final String ADMIN = "admin";
	
	private UserDirectoryService userDirectoryService;
	public void setUserDirectoryService(UserDirectoryService s) {
		this.userDirectoryService = s;
	}
	
	private SakaiPersonManager personManager;
	public void setPersonManager(SakaiPersonManager spm) {
		personManager = spm;
	}
	
	private static final int MAX_BUNCH = 1000;
	
	public void execute(JobExecutionContext arg0) throws JobExecutionException {
		// TODO Auto-generated method stub

		
//		 set the user information into the current session
	    Session sakaiSession = sessionManager.getCurrentSession();
	    sakaiSession.setUserId(ADMIN);
	    sakaiSession.setUserEid(ADMIN);
		List users = userDirectoryService.getUsers();
		for (int i= 0; i < users.size(); i++ ){
			User u = (User)users.get(i);
			String type = u.getType();
			if (type != null && (type.equals("student") || type.equals("staff") || type.equals("thirdparty")) && (u.getEmail() == null || u.getEmail().equals(""))) {
				//we need to set this users email
				log.info("Found: " + u.getId() + " (" + u.getEid() + ") with ivalid email" + u.getEmail());
				try {
					SakaiPerson systemP = personManager.getSakaiPerson(u.getId(), personManager.getSystemMutableType());
					String mail = null;
					if (systemP != null) {
						if (systemP.getMail() == null || systemP.getMail().equals(""))
							mail = u.getEid() + "@uct.ac.za";
						else 
							mail = systemP.getMail();
					} else {
						log.warn("User " + u.getEid() + " has no system Profile");
						mail = u.getEid() + "@uct.ac.za";
					}
					
					UserEdit ue = userDirectoryService.editUser(u.getId());
					ue.setEmail(mail);
					userDirectoryService.commitEdit(ue);
					
					
					SakaiPerson sp = personManager.getSakaiPerson(u.getId(), personManager.getUserMutableType());
					if (sp != null) {
						sp.setMail(mail);
						sakaiSession.setUserId(u.getId());
					    sakaiSession.setUserEid(u.getEid());
						personManager.save(sp);
						sakaiSession.setUserId(ADMIN);
						sakaiSession.setUserEid(ADMIN);
					}
					
				} catch (UserNotDefinedException e) {
					log.warn(e.getMessage(), e);
				} catch (UserPermissionException e) {
					log.warn(e.getMessage(), e);
				} catch (UserLockedException e) {
					log.warn(e.getMessage(), e);
				} catch (UserAlreadyDefinedException e) {
					log.warn(e.getMessage(), e);
				}
	
				
			
			} else {
				if (!isValidEmail(u.getEmail())) {
					log.warn(u.getEmail() + " is not a valid email");
					
				} else {
					SakaiPerson sp = personManager.getSakaiPerson(u.getId(), personManager.getUserMutableType());
					String mail = u.getEmail();
					if (sp != null && sp.getMail() == null) {
						sp.setMail(mail);
						sakaiSession.setUserId(u.getId());
					    sakaiSession.setUserEid(u.getEid());
						personManager.save(sp);
						sakaiSession.setUserId(ADMIN);
						sakaiSession.setUserEid(ADMIN);
					}
				}
			}
		}
		
	}
	
	/**
	 * Is this a valid email the service will recognize
	 * @param email
	 * @return
	 */
	private boolean isValidEmail(String email) {
		
		// TODO: Use a generic Sakai utility class (when a suitable one exists)
		
		if (email == null || email.equals(""))
			return false;
		
		email = email.trim();
		//must contain @
		if (email.indexOf("@") == -1)
			return false;
		
		//an email can't contain spaces
		if (email.indexOf(" ") > 0)
			return false;
		
		//"^[_A-Za-z0-9-]+(\\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*$" 
		if (email.matches("^[_A-Za-z0-9-]+(\\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*$")) 
			return true;
	
		return false;
	}
	

}
