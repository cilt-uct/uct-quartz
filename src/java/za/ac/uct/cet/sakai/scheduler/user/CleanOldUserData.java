package za.ac.uct.cet.sakai.scheduler.user;

import java.text.NumberFormat;
import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.sakaiproject.content.api.ContentCollection;
import org.sakaiproject.content.api.ContentHostingService;
import org.sakaiproject.db.api.SqlService;
import org.sakaiproject.email.api.EmailService;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.InUseException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.exception.ServerOverloadException;
import org.sakaiproject.exception.TypeException;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
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
public class CleanOldUserData implements Job{

	private static final String WORKSPACE_CONTENT_REMOVED = "workspace_content_removed";
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
	
	private EmailService emailService;
	public void setEmailService(EmailService emailService) {
		this.emailService = emailService;
	}


	public void execute(JobExecutionContext arg0) throws JobExecutionException {
		
		//set the user information into the current session
	    Session sakaiSession = sessionManager.getCurrentSession();
	    sakaiSession.setUserId("admin");
	    sakaiSession.setUserEid("admin");
	    
	    
	    
	    //TODO make this a calendar year
	    DateTime forQuery = new DateTime().minusYears(1);
	    DateTimeFormatter fmt = ISODateTimeFormat.dateTime();
	    String strDate = fmt.print(forQuery);
	    String sql = "select user_id from SAKAI_USER_PROPERTY where name = 'SPML_DEACTIVATED' and timestamp(value) < '" + strDate + "' and user_id not in (select user_id from SAKAI_USER_PROPERTY where name='workspace_content_removed')";
		log.info("sql: " + sql);
		List<String> users = sqlService.dbRead(sql);
		
		log.info("got a list of " + users.size() + " user who's data to clean up");
		int noCollection = 0;
		int hasCollection = 0;
		long totalSize = 0;
		
		for (int i = 0; i < users.size(); i++) {
			String userId = users.get(i);
			
			String siteId = siteService.getUserSiteId(userId);
			String collectionId = contentHostingService.getSiteCollection(siteId);
			UserEdit user = null;
			try {
				//TODO double check the account is inactive etc
				user = userDirectoryService.editUser(userId);
				String type = user.getType();
				if (!type.toLowerCase().startsWith("inactive")) {
					log.warn("user: " + user.getEid() + " has type of: " + type);
					userDirectoryService.cancelEdit(user);
					continue;
				}
				
				ContentCollection collection = contentHostingService.getCollection(collectionId);
				hasCollection++;
				long bodySize = collection.getBodySizeK();
				log.info("user: " + userId + " has a collection of " + bodySize +"kb " + "(" + i + "/" + users.size() + ")");
				totalSize = totalSize + bodySize;
				//remove the collection
				try {
					
					contentHostingService.removeCollection(collectionId);

				} catch (IdUnusedException e) {

				} catch (InUseException e) {
					log.warn(e.getMessage(), e);
				} catch (ServerOverloadException e) {
					log.warn(e.getMessage(), e);
				}

				//Remove the site
				try {
					Site site = siteService.getSite(siteId);
					siteService.removeSite(site);
				} catch (IdUnusedException e) {

				}
				
				//set a flag on the user account
				setUserFlags(user);
				

				
			} catch (IdUnusedException e) {
				log.info("user: " + userId + " has no resource collection");
				setUserFlags(user);
				noCollection++;
			} catch (TypeException e) {
				log.warn(e.getMessage(), e);
			} catch (PermissionException e) {
				log.warn(e.getMessage(), e);
			} catch (UserNotDefinedException e) {
				log.warn(e.getMessage(), e);
			} catch (UserPermissionException e) {
				log.warn(e.getMessage(), e);
			} catch (UserLockedException e) {
				log.warn(e.getMessage(), e);
			}
			finally {
				if (user != null && user.isActiveEdit()) {
					try {
						userDirectoryService.commitEdit(user);
					} catch (UserAlreadyDefinedException e) {
						log.warn(e.getMessage(), e);
					}
				}
			}
			
		}
		log.info("checked " + users.size() + " accounts of which " + noCollection + " had no content collection, " + hasCollection + " had a collection");
		log.info("total resource size: " + totalSize + "kb");
		//email that
		String body = "checked " + users.size() + " accounts of which " + noCollection + " had no content collection, " + hasCollection + " had a collection\n";
		body +=  "total resource size: " + formatSize(totalSize * 1024);
		emailService.send("help@vula.uct.ac.za", "help-team@vula.uct.ac.za", "Old user data cleaned", body, null, null, null);
		
	}


	private void setUserFlags(UserEdit user) {
		ResourceProperties rp = user.getProperties();
		DateTime dt = new DateTime();
		DateTimeFormatter fmt = ISODateTimeFormat.dateTime();
		rp.addProperty(WORKSPACE_CONTENT_REMOVED, fmt.print(dt));
		
	}
	
	/**
	 * Utility method to get a nice short filesize string.
	 * @param size_long The size to be displayed (bytes).
	 * @return A short human readable filesize.
	 */
	private static String formatSize(long size_long) {
		// This method needs to be moved somewhere more sensible.
		String size = "";
		NumberFormat formatter = NumberFormat.getInstance();
		formatter.setMaximumFractionDigits(1);
		if(size_long > 700000000L)
		{

			size = formatter.format(1.0 * size_long / (1024L * 1024L * 1024L)) + "G";

		}
		else if(size_long > 700000L)
		{
			
			size = formatter.format(1.0 * size_long / (1024L * 1024L)) + "Mb";

		}
		else if(size_long > 700L)
		{		
			size = formatter.format(1.0 * size_long / 1024L) + "kb";
		}
		else 
		{
			size = formatter.format(size_long) +"b";
		}
		return size;
	}


}
