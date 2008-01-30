package org.sakaiproject.component.app.scheduler.jobs;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.sakaiproject.api.app.messageforums.Area;
import org.sakaiproject.api.app.messageforums.AreaManager;
import org.sakaiproject.api.app.messageforums.DiscussionForum;
import org.sakaiproject.api.app.messageforums.Message;
import org.sakaiproject.api.app.messageforums.MessageForumsForumManager;
import org.sakaiproject.api.app.messageforums.MessageForumsMessageManager;
import org.sakaiproject.api.app.messageforums.Topic;
import org.sakaiproject.api.common.type.Type;
import org.sakaiproject.api.common.type.TypeManager;
import org.sakaiproject.authz.api.AuthzGroup;
import org.sakaiproject.authz.api.AuthzGroupService;
import org.sakaiproject.authz.api.GroupNotDefinedException;
import org.sakaiproject.authz.api.Member;

public class MarkMessagesRead implements Job {

	private static final Log LOG = LogFactory.getLog(MarkMessagesRead.class);
	
	private AreaManager areaManager;
	private Date migrateDate = new Date();
	private Set siteUsers;
	private List sites = new ArrayList();
	private AuthzGroupService authzGroupService;
	private MessageForumsMessageManager messageForumsMessageManager;
	private MessageForumsForumManager messageForumsForumManager;
	
	public void execute(JobExecutionContext arg0) throws JobExecutionException {
		// TODO Auto-generated method stub
		
		//test with this one
		//f2d0210c-1a7a-4fe7-00c0-40e37f8891d4
		sites.add("f2d0210c-1a7a-4fe7-00c0-40e37f8891d4");
		sites.add("e5e7e315-35d5-40d3-8071-1ae7ccc2a247");
		//on my build
		//sites.add("8dbdcec6-18a1-4584-a199-d5b891d55347");
		
		
		Calendar cal = Calendar.getInstance();
		cal.set(Calendar.YEAR, 2008);
		cal.set(Calendar.MONTH, 1);
		cal.set(Calendar.DAY_OF_MONTH, 1);
		migrateDate = cal.getTime();
		
		for(int i =0; i < sites.size(); i++) {
			//first update the user list
			LOG.info("Doing site: " + (String)sites.get(i));
			try {
				AuthzGroup group = authzGroupService.getAuthzGroup("/site/" + (String)sites.get(i));
				siteUsers = group.getMembers();
				markAllRead((String)sites.get(i));
			} catch (GroupNotDefinedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			
			
			
		}
		
		
		
		
	}

	private void markAllRead(String contextId) {
		
		Area area = areaManager.getDiscussionArea(contextId);
		LOG.info("got area: " + area.getContextId());
		//lazy init error here
		List fora = area.getDiscussionForums();
		LOG.info("got a list of: " + fora.size() + " forums");
		//list fora = messageForumsForumManager.getForumByTypeAndContextWithTopicsAllAttachments(typeUuid)
		for (int i = 0; i < fora.size(); i ++) {
			DiscussionForum forum = (DiscussionForum)fora.get(i);
			List topics = forum.getTopics();
			LOG.info("Got a list of: " + topics.size() + " topics");
			for (int q=0; q < topics.size(); q ++) {
				Topic topic = (Topic)topics.get(q);
				List messages = topic.getMessages();
				LOG.info("got a list of: " + messages.size() + " messages");
				for (int r =0; r < messages.size(); r ++) {
					Message message = (Message)messages.get(r);
					if (message.getCreated().before(migrateDate)) {
						LOG.info("Message " + message.getTitle() + " before migrate date");
						//now mark for each user
						Iterator it = siteUsers.iterator();
						while (it.hasNext()) {
							Member member = (Member)it.next();
							messageForumsMessageManager.markMessageReadForUser(topic.getId(), message.getId(), true, member.getUserId());

						}
						
					}
				}
				
			}
			
		}
	}

	public void setAreaManager(AreaManager areaManager) {
		this.areaManager = areaManager;
	}

	public void setAuthzGroupService(AuthzGroupService authzGroupService) {
		this.authzGroupService = authzGroupService;
	}

	public void setMessageForumsMessageManager(
			MessageForumsMessageManager messageForumsMessageManager) {
		this.messageForumsMessageManager = messageForumsMessageManager;
	}

	public void setMigrateDate(Date migrateDate) {
		this.migrateDate = migrateDate;
	}

	public void setSites(List sites) {
		this.sites = sites;
	}
	
}
