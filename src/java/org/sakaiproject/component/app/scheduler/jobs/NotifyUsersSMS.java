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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.sakaiproject.api.common.edu.person.SakaiPerson;
import org.sakaiproject.api.common.edu.person.SakaiPersonManager;
import org.sakaiproject.emailtemplateservice.api.EmailTemplateService;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserDirectoryService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NotifyUsersSMS implements Job {


	private SessionManager sessionManager;
	public void setSessionManager(SessionManager s) {
		this.sessionManager = s;
	}

	private UserDirectoryService userDirectoryService;
	public void setUserDirectoryService(UserDirectoryService s) {
		this.userDirectoryService = s;
	}
	private SakaiPersonManager sakaiPersonManager;
	public void setSakaiPersonManager(SakaiPersonManager sakaiPersonManager) {
		this.sakaiPersonManager = sakaiPersonManager;
	}

	private EmailTemplateService emailTemplateService;
	public void setEmailTemplateService(EmailTemplateService emailTemplateService) {
		this.emailTemplateService = emailTemplateService;
	}

	private static final String ADMIN = "admin";

	public void execute(JobExecutionContext arg0) throws JobExecutionException {
		//set the user information into the current session
	    Session sakaiSession = sessionManager.getCurrentSession();
	    sakaiSession.setUserId(ADMIN);
	    sakaiSession.setUserEid(ADMIN);

	    int first = 1;
		int increment = 1000;
		int last = increment;
		boolean doAnother = true;
		while (doAnother) {

			List<User> users = userDirectoryService.getUsers(first, last);
			for (int i= 0; i < users.size(); i++ ){
				//is this user f the right type?
				User u = (User)users.get(i);
				if (doThisUser(u)) {
					//get the users profile
					SakaiPerson sp = sakaiPersonManager.getSakaiPerson(u.getId(), sakaiPersonManager.getUserMutableType());
					//the profile may be null (i.e. user has no profile
					if (sp == null) {
						continue;
					}

					//if the user has a mobile number generate and send an email
					if (sp.getMobile() != null && sp.getMobile().length() > 2) {
						Map<String, Object> replacementValues = new HashMap<>();
						//we need display name, mobileno, recieve preference, hidePreference
						if (u.getFirstName() != null && !"".equals(u.getFirstName())) {
							replacementValues.put("greeting", u.getFirstName());
						} else {
							replacementValues.put("greeting", u.getDisplayId());
						}

						replacementValues.put("mobile", sp.getMobile());

						ResourceProperties rp = u.getProperties();
						String val = rp.getProperty("smsnotifications");

						if ("true".equals(val) || val == null)
							val = "Yes";

						if ("false".equals(val))
							val = "No";

						replacementValues.put("prefReceive", val);

						String hideS = "No";
						if (sp.getHidePrivateInfo() != null && sp.getHidePrivateInfo().booleanValue()) {
							hideS = "Yes";
						}
						replacementValues.put("prefHide", hideS);

						List<String> to = new ArrayList<String>();
						to.add(u.getReference());
						String key = "smsnotify";
						if ("student".equals(u.getType()))
							key = "smsnotify.student";

						replacementValues.put("eid", u.getEid());

						log.info("Sending SMS notification email to " + u.getEid());

						emailTemplateService.sendRenderedMessages(key, to , replacementValues, "help@vula.uct.ac.za", "Vula Team, UCT");

						// pause to prevent generating an email flood
						try {
							Thread.sleep(2000);
						} catch (InterruptedException e) {
							//not much to do here - we want to keep running ...
						}
					}

				}

			}
			if (users.size() < increment) {
				doAnother = false;
			} else {
				first = last + 1;
				last = last + increment;
			}
		}  //End WHILE
	}

	private boolean doThisUser(User u) {
		String type = u.getType();
		if (type == null)
			return false;

		if (u.getEmail() == null || "".equals(u.getEmail().trim()))
			return false;

		if ("student".equals(type) || "staff".equals(type) || "guest".equals(type)
				|| "pace".equals(type) || "thirdparty".equals(type))
			return true;

		return false;
	}

}
