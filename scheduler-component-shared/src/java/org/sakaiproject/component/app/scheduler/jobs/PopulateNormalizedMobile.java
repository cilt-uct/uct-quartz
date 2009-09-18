package org.sakaiproject.component.app.scheduler.jobs;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.sakaiproject.api.common.edu.person.SakaiPerson;
import org.sakaiproject.api.common.edu.person.SakaiPersonManager;
import org.sakaiproject.sms.logic.external.NumberRoutingHelper;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserDirectoryService;

public class PopulateNormalizedMobile implements Job {

	private static final Log LOG = LogFactory.getLog(PopulateNormalizedMobile.class);

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
	
	private NumberRoutingHelper numberRoutingHelper;
	public void setNumberRoutingHelper(NumberRoutingHelper numberRoutingHelper) {
		this.numberRoutingHelper = numberRoutingHelper;
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
					if (sp != null) {
						//if the user has a mobile number generate and send an email
						if (sp.getMobile() != null && sp.getMobile().length() > 2) {
							sp.setNormalizedMobile(numberRoutingHelper.normalizeNumber(sp.getMobile()));
							sakaiPersonManager.save(sp);
						}
					}
					
					sp = sakaiPersonManager.getSakaiPerson(u.getId(), sakaiPersonManager.getSystemMutableType());
					//the profile may be null (i.e. user has no profile
					if (sp != null) {
						//if the user has a mobile number generate and send an email
						if (sp.getMobile() != null && sp.getMobile().length() > 2) {
							sp.setNormalizedMobile(numberRoutingHelper.normalizeNumber(sp.getMobile()));
							sakaiPersonManager.save(sp);
						}

					}
				}
				
			}
			if (users.size() < increment) {
				doAnother = false;
			} else {
				first = last +1;
				last = last + increment;
			}
		}//End WHILE

	}

	private boolean doThisUser(User u) {
		String type = u.getType();
		if (type == null)
			return false;
		
		if ("student".equals(type) || "staff".equals(type) || "guest".equals(type)
				|| "pace".equals(type) || "thirdparty".equals(type))
			return true;
		
		return false;
	}

}
