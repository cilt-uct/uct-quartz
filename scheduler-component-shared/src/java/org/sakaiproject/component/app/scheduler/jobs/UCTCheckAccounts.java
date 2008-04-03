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
import org.sakaiproject.user.api.UserDirectoryService;

import com.novell.ldap.LDAPConnection;
import com.novell.ldap.LDAPEntry;
import com.novell.ldap.LDAPException;
import com.novell.ldap.LDAPJSSESecureSocketFactory;
import com.novell.ldap.LDAPSearchConstraints;
import com.novell.ldap.LDAPSearchResults;
import com.novell.ldap.LDAPSocketFactory;



public class UCTCheckAccounts implements Job {

	
	private UserDirectoryService userDirectoryService;
	public void setUserDirectoryService(UserDirectoryService s) {
		this.userDirectoryService = s;
	}
	
	private SessionManager sessionManager;
	public void setSessionManager(SessionManager s) {
		this.sessionManager = s;
	}
	private static final Log LOG = LogFactory.getLog(UCTCheckAccounts.class);
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
				if (!userExists(u.getEid())) {
					LOG.warn("user: " + u.getEid() + "does not exist in auth tree" );
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
	
	
	private boolean userExists(String id) {
		
		LDAPConnection conn = new LDAPConnection();
		String sFilter = "cn=" + id;

		String thisDn = "";
		String[] attrList = new String[] { "dn" };
		try{
			conn.connect( ldapHost, ldapPort );
			//this will fail if user does not exist	
			LDAPEntry userEntry = getEntryFromDirectory(sFilter,attrList,conn);			
			conn.disconnect();
		}
		catch(Exception e)
		{
			return false;	
		}		
		return true;
	}
	
	// search the directory to get an entry
	private LDAPEntry getEntryFromDirectory(String searchFilter, String[] attribs, LDAPConnection conn)
	throws LDAPException
	{
		LDAPEntry nextEntry = null;
		LDAPSearchConstraints cons = new LDAPSearchConstraints();
		cons.setDereference(LDAPSearchConstraints.DEREF_NEVER);		
		cons.setTimeLimit(operationTimeout);

		LDAPSearchResults searchResults =
			conn.search(basePath,
					LDAPConnection.SCOPE_SUB,
					searchFilter,
					attribs,
					false,
					cons);

		if(searchResults.hasMore()){
			nextEntry = searchResults.next();            
		}

		return nextEntry;
	}


}
