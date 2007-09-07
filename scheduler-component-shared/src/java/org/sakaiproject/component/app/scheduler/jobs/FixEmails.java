package org.sakaiproject.component.app.scheduler.jobs;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
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

public class FixEmails implements Job {

	private SessionManager sessionManager;
	public void setSessionManager(SessionManager s) {
		this.sessionManager = s;
	}
	
	private static final Log LOG = LogFactory.getLog(FixEmails.class);
	private static final String ADMIN = "admin";
	
	private UserDirectoryService userDirectoryService;
	public void setUserDirectoryService(UserDirectoryService s) {
		this.userDirectoryService = s;
	}
	
	private SakaiPersonManager personManager;
	public void setPersonManager(SakaiPersonManager spm) {
		personManager = spm;
	}
	
	public void execute(JobExecutionContext arg0) throws JobExecutionException {
		// TODO Auto-generated method stub

		
//		 set the user information into the current session
	    Session sakaiSession = sessionManager.getCurrentSession();
	    sakaiSession.setUserId(ADMIN);
	    sakaiSession.setUserEid(ADMIN);
		List users = userDirectoryService.getUsers(0,Integer.MAX_VALUE);
		for (int i= 0; i < users.size(); i++ ){
			User u = (User)users.get(i);
			String type = u.getType();
			if (type.equals("student") && (u.getEmail() == null || u.getEmail().equals(""))) {
				//we need to set this users email
				LOG.info("Found: " + u.getId() + " (" + u.getEid()+") with empty email");
				try {
					SakaiPerson systemP = personManager.getSakaiPerson(u.getId(), personManager.getSystemMutableType());
					String mail = null;
					if (systemP.getMail() == null || systemP.getMail().equals(""))
						mail = u.getEid() + "@uct.ac.za";
					else 
						mail = systemP.getMail();
					
					UserEdit ue = userDirectoryService.editUser(u.getId());
					ue.setEmail(mail);
					userDirectoryService.commitEdit(ue);
					
					SakaiPerson sp = personManager.getSakaiPerson(u.getId(), personManager.getUserMutableType());
					sp.setMail(mail);
					personManager.save(sp);
					
				} catch (UserNotDefinedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (UserPermissionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (UserLockedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (UserAlreadyDefinedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
	
				
			
			}
		}
		
	}

}
