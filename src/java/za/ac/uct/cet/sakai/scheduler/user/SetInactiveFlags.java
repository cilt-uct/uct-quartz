package za.ac.uct.cet.sakai.scheduler.user;

import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.sakaiproject.db.api.SqlService;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.user.api.UserAlreadyDefinedException;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.user.api.UserEdit;
import org.sakaiproject.user.api.UserLockedException;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.user.api.UserPermissionException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SetInactiveFlags implements Job{

	
	private static final String PROPERTY_DEACTIVATED = "SPML_DEACTIVATED";
	
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
	    
	    
	    
	    
		String sql = "select user_id from SAKAI_USER where type like 'inactive%' and user_id not in (select user_id from SAKAI_USER_PROPERTY where name = 'SPML_DEACTIVATED')";
		
		List<String> users = sqlService.dbRead(sql);
		
		log.info("got a list of " + users.size() + " users to remove");
		
		for (int i = 0; i < users.size(); i++) {
			String userId = users.get(i);
			try {
				UserEdit u = userDirectoryService.editUser(userId);
				//set the inactive date
				ResourceProperties rp = u.getProperties();
				DateTime dt = new DateTime(u.getModifiedDate());
				DateTimeFormatter fmt = ISODateTimeFormat.dateTime();
				
				//do we have an inactive flag?
				String deactivated = rp.getProperty(PROPERTY_DEACTIVATED);
				if (deactivated == null) {
					rp.addProperty(PROPERTY_DEACTIVATED, fmt.print(dt));
				}

				userDirectoryService.commitEdit(u);
				log.info("updated: " + userId);
			} catch (UserNotDefinedException e) {
				log.warn(e.getMessage(), e);
			} catch (UserPermissionException e) {
				log.warn(e.getMessage(), e);
			} catch (UserLockedException e) {
				log.warn(e.getMessage(), e);
			} catch (UserAlreadyDefinedException e) {
				log.warn(e.getMessage(), e);
			}
		}
		
	}

}
