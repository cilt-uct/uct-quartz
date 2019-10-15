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
import org.sakaiproject.api.common.edu.person.SakaiPerson;
import org.sakaiproject.api.common.edu.person.SakaiPersonManager;
import org.sakaiproject.db.api.SqlService;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ClearorphanedProfiles implements Job {

	private static final String ADMIN = "admin";
	
	private SakaiPersonManager sakaiPersonManager;
	private SqlService sqlService;
	private SessionManager sessionManager;
	public void setSessionManager(SessionManager s) {
		this.sessionManager = s;
	}
	
	public void setSakaiPersonManager(SakaiPersonManager sakaiPersonManager) {
		this.sakaiPersonManager = sakaiPersonManager;
	}
	public void setSqlService(SqlService sqlService) {
		this.sqlService = sqlService;
	}

	
	public void execute(JobExecutionContext arg0) throws JobExecutionException {
	
		//set the user information into the current session
	    Session sakaiSession = sessionManager.getCurrentSession();
	    sakaiSession.setUserId(ADMIN);
	    sakaiSession.setUserEid(ADMIN);
		
		String sql = "select agent_uuid from SAKAI_PERSON_T where agent_uuid not in (SELEct user_id from SAKAI_USER_ID_MAP)";
	
		List<String> res = sqlService.dbRead(sql);
		log.info("got a result of: " + res.size());
		int count = 0;
		for (int i = 0; i < res.size(); i++) {
			String r = (String)res.get(i);
			count = i;
			log.info("found orphaned record: " + r);
			SakaiPerson up = sakaiPersonManager.getSakaiPerson(r, sakaiPersonManager.getUserMutableType());
			if (up != null)
				sakaiPersonManager.delete(up);
			
			SakaiPerson sp = sakaiPersonManager.getSakaiPerson(r, sakaiPersonManager.getSystemMutableType());
			if (sp != null)
				sakaiPersonManager.delete(sp);
			
		}
		
		log.info("Removed " + count + " orphaned profiles");
	}
	
}
