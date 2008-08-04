package org.sakaiproject.component.app.scheduler.jobs;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.mailarchive.api.MailArchiveChannel;
import org.sakaiproject.mailarchive.api.MailArchiveMessage;
import org.sakaiproject.mailarchive.api.MailArchiveService;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;

public class ClearRTTMail implements Job {
	private static final String MAIL_CHANNEL ="/mailarchive/channel/a2be4fcd-8e2e-40f8-0003-6c3d088393fe/main";
	
	private static final Log log = LogFactory.getLog(ClearRTTMail.class);
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
				log.debug("deleting message " + mes.getId());
				channel.removeMessage(mes.getId());
			}
			
			
		} catch (IdUnusedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (PermissionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}


}
