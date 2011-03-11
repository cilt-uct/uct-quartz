package za.ac.uct.cet.sakai.scheduler.user;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.sakaiproject.api.common.edu.person.SakaiPerson;
import org.sakaiproject.api.common.edu.person.SakaiPersonManager;
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

public class FixInactiveEmails implements Job{

	private static final Log LOG = LogFactory.getLog(FixInactiveEmails.class);
	
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
	
	private SakaiPersonManager sakaiPersonManager;	
	public void setSakaiPersonManager(SakaiPersonManager sakaiPersonManager) {
		this.sakaiPersonManager = sakaiPersonManager;
	}


	public void execute(JobExecutionContext arg0) throws JobExecutionException {
		
		//set the user information into the current session
	    Session sakaiSession = sessionManager.getCurrentSession();
	    sakaiSession.setUserId("admin");
	    sakaiSession.setUserEid("admin");
	    
	    
	    
	    
		String sql = "select SAKAI_USER.user_id from SAKAI_USER join SAKAI_USER_ID_MAP on SAKAI_USER.user_id=SAKAI_USER_ID_MAP.user_id where email <> concat(eid,'@uct.ac.za') and type like 'inactive%'";
		
		List<String> users = sqlService.dbRead(sql);
		
		LOG.info("got a list of " + users.size() + " users to remove");
		
		for (int i = 0; i < users.size(); i++) {
			String userId = users.get(i);
			try {
				UserEdit u = userDirectoryService.editUser(userId);
				String mail = u.getEid() + "@uct.ac.za";
				u.setEmail(mail);
				//set the inactive date if none
				ResourceProperties rp = u.getProperties();
				DateTime dt = new DateTime();
				DateTimeFormatter fmt = ISODateTimeFormat.dateTime();
				
				//do we have an inactive flag?
				String deactivated = rp.getProperty(PROPERTY_DEACTIVATED);
				if (deactivated == null) {
					rp.addProperty(PROPERTY_DEACTIVATED, fmt.print(dt));
				}

				userDirectoryService.commitEdit(u);
				
				SakaiPerson sp = sakaiPersonManager.getSakaiPerson(u.getId(), sakaiPersonManager.getSystemMutableType());
				if (sp != null) {
					sp.setMail(mail);
					sakaiPersonManager.save(sp);
				}
				
				
				sp = sakaiPersonManager.getSakaiPerson(u.getId(), sakaiPersonManager.getUserMutableType());
				if (sp != null) {
					sp.setMail(mail);
					sakaiPersonManager.save(sp);
				}
				
				
				LOG.info("updated: " + userId);
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
