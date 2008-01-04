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
	
	private static final int MAX_BUNCH = 1000;
	
	public void execute(JobExecutionContext arg0) throws JobExecutionException {
		// TODO Auto-generated method stub

		
//		 set the user information into the current session
	    Session sakaiSession = sessionManager.getCurrentSession();
	    sakaiSession.setUserId(ADMIN);
	    sakaiSession.setUserEid(ADMIN);
		List users = userDirectoryService.getUsers();
		for (int i= 0; i < users.size(); i++ ){
			User u = (User)users.get(i);
			String type = u.getType();
			if (type != null && (type.equals("student") || type.equals("staff") || type.equals("thirdparty")) && (u.getEmail() == null || u.getEmail().equals(""))) {
				//we need to set this users email
				LOG.info("Found: " + u.getId() + " (" + u.getEid()+") with ivalid email" + u.getEmail());
				try {
					SakaiPerson systemP = personManager.getSakaiPerson(u.getId(), personManager.getSystemMutableType());
					String mail = null;
					if (systemP != null) {
						if (systemP.getMail() == null || systemP.getMail().equals(""))
							mail = u.getEid() + "@uct.ac.za";
						else 
							mail = systemP.getMail();
					} else {
						LOG.warn("User " + u.getEid() +" has no system Profile");
						mail = u.getEid() + "@uct.ac.za";
					}
					
					UserEdit ue = userDirectoryService.editUser(u.getId());
					ue.setEmail(mail);
					userDirectoryService.commitEdit(ue);
					
					
					SakaiPerson sp = personManager.getSakaiPerson(u.getId(), personManager.getUserMutableType());
					if (sp != null) {
						sp.setMail(mail);
						sakaiSession.setUserId(u.getId());
					    sakaiSession.setUserEid(u.getEid());
						personManager.save(sp);
						sakaiSession.setUserId(ADMIN);
						sakaiSession.setUserEid(ADMIN);
					}
					
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
	
				
			
			} else {
				if (!isValidEmail(u.getEmail())) {
					LOG.warn(u.getEmail() + " is not a valid email");
					
				}
			}
		}
		
	}
	
	/**
	 * Is this a valid email the service will recognize
	 * @param email
	 * @return
	 */
	private boolean isValidEmail(String email) {
		
		// TODO: Use a generic Sakai utility class (when a suitable one exists)
		
		if (email == null || email.equals(""))
			return false;
		
		email = email.trim();
		//must contain @
		if (email.indexOf("@") == -1)
			return false;
		
		//an email can't contain spaces
		if (email.indexOf(" ") > 0)
			return false;
		
		//"^[_A-Za-z0-9-]+(\\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*$" 
		if (email.matches("^[_A-Za-z0-9-]+(\\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*$")) 
			return true;
	
		return false;
	}
	

}
