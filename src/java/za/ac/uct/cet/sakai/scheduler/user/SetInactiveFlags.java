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
package za.ac.uct.cet.sakai.scheduler.user;

import java.util.List;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.sakaiproject.db.api.SqlService;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.user.api.UserAlreadyDefinedException;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.user.api.UserEdit;
import org.sakaiproject.user.api.UserLockedException;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.user.api.UserPermissionException;

import lombok.extern.slf4j.Slf4j;
import za.uct.cilt.util.VulaUtil;

@Slf4j
public class SetInactiveFlags implements Job{

	
	private static final String PROPERTY_DEACTIVATED = "SPML_DEACTIVATED";
	
	private SqlService sqlService;
	private UserDirectoryService userDirectoryService;
	
	
	public void setSqlService(SqlService sqlService) {
		this.sqlService = sqlService;
	}


	public void setUserDirectoryService(UserDirectoryService userDirectoryService) {
		this.userDirectoryService = userDirectoryService;
	}


	private SessionManager sessionManager;
	public void setSessionManager(SessionManager s) {
		this.sessionManager = s;
	}
	


	public void execute(JobExecutionContext arg0) throws JobExecutionException {
		
		//set the user information into the current session
	    Session sakaiSession = sessionManager.getCurrentSession();
	    sakaiSession.setUserId("admin");
	    sakaiSession.setUserEid("admin");
	    
	    
	    
	    
		String sql = "select user_id from SAKAI_USER where type like 'inactive%' and user_id not in (select user_id from SAKAI_USER_PROPERTY where name = 'SPML_DEACTIVATED')";
		
		List<String> users = sqlService.dbRead(sql);
		
		log.info("got a list of " + users.size() + " users to remove");
		
		for (int i = 0; i < users.size(); i++) {
			String userId = users.get(i);
			try {
				UserEdit u = userDirectoryService.editUser(userId);
				//set the inactive date
				ResourceProperties rp = u.getProperties();
				
				//do we have an inactive flag?
				String deactivated = rp.getProperty(PROPERTY_DEACTIVATED);
				if (deactivated == null) {
					rp.addProperty(PROPERTY_DEACTIVATED, VulaUtil.getISODate());
				}

				userDirectoryService.commitEdit(u);
				log.info("updated: " + userId);
			} catch (UserNotDefinedException e) {
				log.warn(e.getMessage(), e);
			} catch (UserPermissionException e) {
				log.warn(e.getMessage(), e);
			} catch (UserLockedException e) {
				log.warn(e.getMessage(), e);
			} catch (UserAlreadyDefinedException e) {
				log.warn(e.getMessage(), e);
			}
		}
		
	}

}
