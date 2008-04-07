package org.sakaiproject.component.app.scheduler.jobs;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserDirectoryProvider;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.user.api.UserEdit;
import org.sakaiproject.user.api.UserLockedException;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.user.api.UserPermissionException;

import com.novell.ldap.LDAPConnection;
import com.novell.ldap.LDAPJSSESecureSocketFactory;
import com.novell.ldap.LDAPSocketFactory;



public class UCTCheckAccounts implements Job {

	
	private UserDirectoryService userDirectoryService;
	public void setUserDirectoryService(UserDirectoryService s) {
		this.userDirectoryService = s;
	}

	private UserDirectoryProvider userDirectoryProvider;

	public void setUserDirectoryProvider(UserDirectoryProvider userDirectoryProvider) {
		this.userDirectoryProvider = userDirectoryProvider;
	}

	
	
	private SessionManager sessionManager;
	public void setSessionManager(SessionManager s) {
		this.sessionManager = s;
	}
	private static final Log LOG = LogFactory.getLog(UCTCheckAccounts.class);
	
	private static final String NOT_FOUND_TYPE = "ldapNotFound";
	private static final String ADMIN = "admin";
	
	private String ldapHost = ""; //address of ldap server
	private int ldapPort = 389; //port to connect to ldap server on
	private int operationTimeout = 5000; //default timeout for operations (in ms)
	
	public void setLdapHost(String ldapHost) {
		this.ldapHost = ldapHost;
	}


	public void setLdapPort(int ldapPort) {
		this.ldapPort = ldapPort;
	}
	
	private String basePath;
	/**
	 * @param basePath The basePath to set.
	 */
	public void setBasePath(String basePath) {
		this.basePath = basePath;
	}


	public void execute(JobExecutionContext arg0) throws JobExecutionException {
		// TODO Auto-generated method stub
	
//		 set the user information into the current session
	    Session sakaiSession = sessionManager.getCurrentSession();
	    sakaiSession.setUserId(ADMIN);
	    sakaiSession.setUserEid(ADMIN);
	    
	    //set up the secure connection
	    LDAPSocketFactory ssf = new LDAPJSSESecureSocketFactory();
		LDAPConnection.setSocketFactory(ssf);
		
	    List users = userDirectoryService.getUsers();
		for (int i= 0; i < users.size(); i++ ){
			User u = (User)users.get(i);
			if (doThisUser(u)) {
				//if (!userDirectoryProvider.userExists(u.getEid())) {
				if (1 == 1) {
					LOG.warn("user: " + u.getEid() + "does not exist in auth tree" );
					try {
						UserEdit ue = userDirectoryService.editUser(u.getId());
						ue.setType(NOT_FOUND_TYPE);
						userDirectoryService.commitEdit(ue));
						
					} catch (UserNotDefinedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (UserPermissionException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (UserLockedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
				} else {
					LOG.info("user: " + u.getEid() + "is in ldap");
				}
					
				
			}
			
			
			
		}
	}
	
	
	private boolean doThisUser(User u) {
		if ("staff".equalsIgnoreCase(u.getType()) || "thirdparty".equalsIgnoreCase(u.getType()) ) {
			return true;
		}
		
		return false;
		
	}
	
	
	


}
