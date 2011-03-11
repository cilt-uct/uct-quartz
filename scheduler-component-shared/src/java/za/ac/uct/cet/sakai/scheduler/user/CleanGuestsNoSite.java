package za.ac.uct.cet.sakai.scheduler.user;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.sakaiproject.db.api.SqlService;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.user.api.UserEdit;
import org.sakaiproject.user.api.UserLockedException;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.user.api.UserPermissionException;

public class CleanGuestsNoSite implements Job{

	private static final Log LOG = LogFactory.getLog(CleanGuestsNoSite.class);
	private SqlService sqlService;
	private UserDirectoryService userDirectoryService;
	
	
	public void setSqlService(SqlService sqlService) {
		this.sqlService = sqlService;
	}


	public void setUserDirectoryService(UserDirectoryService userDirectoryService) {
		this.userDirectoryService = userDirectoryService;
	}


	private SessionManager sessionManager;
	public void setSessionManager(SessionManager s) {
		this.sessionManager = s;
	}
	
	
	public void execute(JobExecutionContext arg0) throws JobExecutionException {
		
		//set the user information into the current session
	    Session sakaiSession = sessionManager.getCurrentSession();
	    sakaiSession.setUserId("admin");
	    sakaiSession.setUserEid("admin");
	    
	    
	    
	    
		String sql = "select user_id from SAKAI_USER where type='guest' and user_id not in (select user_id from SAKAI_REALM_RL_GR) and modifiedon < date_sub(now(),INTERVAL 6 MONTH)";
		
		List<String> users = sqlService.dbRead(sql);
		
		LOG.info("got a list of " + users.size() + " users to remove");
		
		for (int i = 0; i < users.size(); i++) {
			String userId = users.get(i);
			try {
				UserEdit u = userDirectoryService.editUser(userId);
				userDirectoryService.removeUser(u);
				LOG.info("removed: " + userId);
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
		}
		
	}

}
