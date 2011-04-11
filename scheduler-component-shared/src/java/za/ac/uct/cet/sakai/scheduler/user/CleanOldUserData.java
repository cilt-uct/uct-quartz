package za.ac.uct.cet.sakai.scheduler.user;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.sakaiproject.content.api.ContentCollection;
import org.sakaiproject.content.api.ContentHostingService;
import org.sakaiproject.db.api.SqlService;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.exception.TypeException;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.user.api.UserDirectoryService;

public class CleanOldUserData implements Job{

	private static final Log LOG = LogFactory.getLog(CleanOldUserData.class);
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
	
	private ContentHostingService contentHostingService;	
	public void setContentHostingService(ContentHostingService contentHostingService) {
		this.contentHostingService = contentHostingService;
	}

	private SiteService siteService;
	public void setSiteService(SiteService siteService) {
		this.siteService = siteService;
	}


	public void execute(JobExecutionContext arg0) throws JobExecutionException {
		
		//set the user information into the current session
	    Session sakaiSession = sessionManager.getCurrentSession();
	    sakaiSession.setUserId("admin");
	    sakaiSession.setUserEid("admin");
	    
	    
	    
	    
		String sql = "select user_id from SAKAI_USER_PROPERTY where name = 'SPML_DEACTIVATED' and timestamp(value) < '2010-01-01 00:00:00';";
		
		List<String> users = sqlService.dbRead(sql);
		
		LOG.info("got a list of " + users.size() + " user whos data to clean up");
		int noCollection = 0;
		int hasCollection = 0;
		long totalSize = 0;
		
		for (int i = 0; i < users.size(); i++) {
			String userId = users.get(i);
			
			String siteId = siteService.getUserSiteId(userId);
			String collectionId = contentHostingService.getSiteCollection(siteId);
			try {
				ContentCollection collection = contentHostingService.getCollection(collectionId);
				hasCollection++;
				long bodySize = collection.getBodySizeK();
				LOG.info("user: " + userId + "has a collection of " + bodySize +"kb");
				totalSize = totalSize + bodySize;
				
			} catch (IdUnusedException e) {
				LOG.info("user: " + userId + " has no resource collection");
				noCollection++;
				e.printStackTrace();
			} catch (TypeException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (PermissionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			
		}
		LOG.info("checked " + users.size() + " accounts of which " + noCollection + " had no content collection, " + hasCollection + " had a collection");
		LOG.info("total resource size: " + totalSize + "kb");
		
	}

}
