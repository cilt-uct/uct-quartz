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
package za.ac.uct.cet.sakai.scheduler.src;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.StatefulJob;
import org.sakaiproject.chat2.model.ChatChannel;
import org.sakaiproject.chat2.model.ChatManager;
import org.sakaiproject.chat2.model.ChatMessage;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MaintainSRCSite implements StatefulJob {

	private final String ADMIN = "admin";
	private SessionManager sessionManager;
	public void setSessionManager(SessionManager s) {
		this.sessionManager = s;
	}
	
	private ChatManager chatManager;
	public void setChatManager(ChatManager chatManager) {
		this.chatManager = chatManager;
	}
	
	//The site context
	String context = "96dbcba6-01c2-4059-007a-40e484873bb9";
	public void execute(JobExecutionContext arg0) throws JobExecutionException {
		//set the user information into the current session
				Session sakaiSession = sessionManager.getCurrentSession();
				sakaiSession.setUserId(ADMIN);
				sakaiSession.setUserEid(ADMIN);
				
				//the cut of date
				LocalDateTime cutOff = LocalDateTime.of(2013, 1, 1, 0, 1);
				
				//clear all chat messages before the current year
				List<ChatChannel> channels = chatManager.getContextChannels(context, false);
				for (int i = 0; i < channels.size(); i++) {
					ChatChannel channel = channels.get(i);
					log.info("got channel: " + channel.getTitle());
					Set<ChatMessage> messages = channel.getMessages();
					Iterator<ChatMessage> it = messages.iterator();
					while (it.hasNext()) {
						ChatMessage message = it.next();
						LocalDateTime posted = LocalDateTime.ofInstant(message.getMessageDate().toInstant(), ZoneId.systemDefault());
						if (posted.isBefore(cutOff)) {
							try {
								log.info("deleting message posted on: "  + posted.toString());
								chatManager.deleteMessage(message);
							} catch (PermissionException e) {
								log.warn(e.getMessage(), e);
							}
						}
					}
					
				}
	}

}
