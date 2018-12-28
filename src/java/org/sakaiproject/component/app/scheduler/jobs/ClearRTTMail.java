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

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.mailarchive.api.MailArchiveChannel;
import org.sakaiproject.mailarchive.api.MailArchiveMessage;
import org.sakaiproject.mailarchive.api.MailArchiveService;
import org.sakaiproject.message.api.MessageHeader;
import org.sakaiproject.time.api.Time;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ClearRTTMail implements Job {
	private static final String MAIL_CHANNEL = "/mailarchive/channel/a2be4fcd-8e2e-40f8-0003-6c3d088393fe/main";
	

	private static final String ADMIN = "admin";
	private MailArchiveService mailArchiveService;
	public void setMailArchiveService(MailArchiveService mailArchiveService) {
		this.mailArchiveService = mailArchiveService;
	}
	
	private SessionManager sessionManager;
	public void setSessionManager(SessionManager s) {
		this.sessionManager = s;
	}
	public void execute(JobExecutionContext arg0) throws JobExecutionException {
		//set the user information into the current session
	    Session sakaiSession = sessionManager.getCurrentSession();
	    sakaiSession.setUserId(ADMIN);
	    sakaiSession.setUserEid(ADMIN);
		
	    try {
			MailArchiveChannel channel = mailArchiveService.getMailArchiveChannel(MAIL_CHANNEL);
			log.info("got message channel with " + channel.getCount() + " messages");
			List messages = channel.getMessages(null, true);
			for (int i = 0; i < messages.size(); i++) {
				MailArchiveMessage mes = (MailArchiveMessage) messages.get(i);
				MessageHeader messageHeader = mes.getHeader();
				Time mTime = messageHeader.getDate();
				Date d = new Date(mTime.getTime());

				//What was the time an hour agon
				Calendar cal = Calendar.getInstance();
				cal.roll(Calendar.HOUR, false);

				if (d.before(cal.getTime())) {
					log.debug("deleting message " + mes.getId());
					channel.removeMessage(mes.getId());
				}
			}
			
			
		} catch (IdUnusedException e) {
			log.warn(e.getMessage(), e);
		} catch (PermissionException e) {
			log.warn(e.getMessage(), e);
		}

	}


}
