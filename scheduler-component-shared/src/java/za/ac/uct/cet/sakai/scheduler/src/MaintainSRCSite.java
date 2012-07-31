package za.ac.uct.cet.sakai.scheduler.src;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.joda.time.DateTime;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.StatefulJob;
import org.sakaiproject.chat2.model.ChatChannel;
import org.sakaiproject.chat2.model.ChatManager;
import org.sakaiproject.chat2.model.ChatMessage;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;

public class MaintainSRCSite implements StatefulJob {

	private final String ADMIN = "admin";
	private static final Log log = LogFactory.getLog(MaintainSRCSite.class);
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
				DateTime cutOff = new DateTime(2012, 1, 1, 0, 1);
				
				//clear all chat messages before the current year
				List<ChatChannel> channels = chatManager.getContextChannels(context, false);
				for (int i = 0; i < channels.size(); i++) {
					ChatChannel channel = channels.get(i);
					log.info("got channel: " + channel.getTitle());
					Set<ChatMessage> messages = channel.getMessages();
					Iterator<ChatMessage> it = messages.iterator();
					while (it.hasNext()) {
						ChatMessage message = it.next();
						DateTime posted = new DateTime(message.getMessageDate());
						if (posted.isBefore(cutOff)) {
							try {
								log.info("deleting message posted on: "  + posted.toString());
								chatManager.deleteMessage(message);
							} catch (PermissionException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
					}
					
				}
	}

}
