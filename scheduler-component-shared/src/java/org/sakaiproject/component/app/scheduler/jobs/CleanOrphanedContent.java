package org.sakaiproject.component.app.scheduler.jobs;

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
import org.sakaiproject.exception.InUseException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.exception.TypeException;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;

public class CleanOrphanedContent implements Job {

	private final String ADMIN = "admin";
	private static final Log log = LogFactory.getLog(CleanOrphanedContent.class);
	private SqlService sqlService;
	private SessionManager sessionManager;
	public void setSessionManager(SessionManager s) {
		this.sessionManager = s;
	}
	
	public void setSqlService(SqlService sqlService) {
		this.sqlService = sqlService;
	}
	
	private ContentHostingService contentHostingService;
	public void setContentHostingService(ContentHostingService contentHostingService) {
		this.contentHostingService = contentHostingService;
	}
	
	private Boolean doCleanUp;
	public void setDoCleanUp(Boolean doCleanUp) {
		this.doCleanUp = doCleanUp;
	}

	public void execute(JobExecutionContext context)
			throws JobExecutionException {
				
		//set the user information into the current session
	    Session sakaiSession = sessionManager.getCurrentSession();
	    sakaiSession.setUserId(ADMIN);
	    sakaiSession.setUserEid(ADMIN);
		
		String sql = "select collection_id as cuid from CONTENT_COLLECTION where in_collection='/user/' and (replace(mid(collection_id,7),'/','') not in (SELECT user_id from SAKAI_USER_ID_MAP));";
		List<String> res = sqlService.dbRead(sql);
		long userBytes = getBytesInCollection(res);
		
		
		sql = "select collection_id from CONTENT_COLLECTION where in_collection='/group/' and replace(mid(collection_id,7),'/','') not in (select site_id from SAKAI_SITE); ";
		res = sqlService.dbRead(sql);
		long siteBytes = getBytesInCollection(res);
		
		
		sql = "select collection_id from CONTENT_COLLECTION where in_collection='/attachment/' and replace(mid(collection_id,13),'/','') not in (select site_id from SAKAI_SITE); ";
		res = sqlService.dbRead(sql);
		long attachBytes = getBytesInCollection(res);
		
		log.info("Orpahned content in user collections: " + userBytes);
		log.info("Orpahned content in attachment collections: " + attachBytes);
		log.info("Orpahned content in site collections: " + siteBytes);
	}
	
	
	private long getBytesInCollection(List<String> collectionList) {
		long userBytes = 0;
		log.info("got a result of: " + collectionList.size());
		for (int i =0 ; i < collectionList.size(); i ++) {
			String r = (String)collectionList.get(i);
			log.debug("got resource: " + r);
			
			try {
				ContentCollection  collection = contentHostingService.getCollection(r);
				long thisOne = collection.getBodySizeK();
				log.info("Collection " + r + " has " + thisOne  + " in the collection");
				userBytes = userBytes + thisOne;
				
				//delete the resource
				if (doCleanUp) {
					contentHostingService.removeResource(r);
				}
			} catch (IdUnusedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (TypeException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (PermissionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InUseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} 
			
		} //For each collection
		return userBytes;
	}

}
