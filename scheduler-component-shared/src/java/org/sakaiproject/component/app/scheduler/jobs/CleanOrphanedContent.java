package org.sakaiproject.component.app.scheduler.jobs;

import java.text.NumberFormat;
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
import org.sakaiproject.exception.ServerOverloadException;
import org.sakaiproject.exception.TypeException;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
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


	private SiteService siteService;	
	public void setSiteService(SiteService siteService) {
		this.siteService = siteService;
	}

	private Boolean doCleanUp;
	public void setDoCleanUp(Boolean doCleanUp) {
		this.doCleanUp = doCleanUp;
	}
	
	@SuppressWarnings("unchecked")
	public void execute(JobExecutionContext context)
	throws JobExecutionException {

		//set the user information into the current session
		Session sakaiSession = sessionManager.getCurrentSession();
		sakaiSession.setUserId(ADMIN);
		sakaiSession.setUserEid(ADMIN);

		/** 
		 * we may have orphaned sites
		 */
		String sql = "select site_id from SAKAI_SITE where site_id like '~%' and mid(site_id,2) not in (select user_id from SAKAI_USER)";
		List<String> res = sqlService.dbRead(sql);
		int orphanedSites = res.size();
		cleanUpOrphanedSites(res);

		sql = "select collection_id as cuid from CONTENT_COLLECTION where in_collection='/user/' and (replace(mid(collection_id,7),'/','') not in (SELECT user_id from SAKAI_USER_ID_MAP));";
		res = sqlService.dbRead(sql);
		long userBytes = getBytesInCollection(res);





		sql = "select collection_id from CONTENT_COLLECTION where in_collection='/group/' and replace(mid(collection_id,7),'/','') not in (select site_id from SAKAI_SITE) and length(collection_id)=length('/group/ffdd45d6-9e1d-4328-8082-646472c8b325/'); ";
		res = sqlService.dbRead(sql);
		long siteBytes = getBytesInCollection(res);

		/*
		sql = "select collection_id from CONTENT_COLLECTION where in_collection='/attachment/' and replace(mid(collection_id,13),'/','') not in (select site_id from SAKAI_SITE); ";
		res = sqlService.dbRead(sql);
		long attachBytes = getBytesInCollection(res);
		 */
		log.info("Orphaned content in user collections: " + formatSize(userBytes * 1024));
		//log.info("Orphaned content in attachment collections: " + attachBytes);
		log.info("Orphaned content in site collections: " + formatSize(siteBytes * 1024));
		log.info("Orphaned my workspace sites: " + orphanedSites);
	}


	private void cleanUpOrphanedSites(List<String> res) {
		for (int i =0; i < res.size(); i++) {
			String site_id = res.get(i);
			try {
				Site site = siteService.getSite(site_id);
				siteService.removeSite(site);
			} catch (IdUnusedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (PermissionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

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
				log.info("Collection " + r + " has " + thisOne  + "K in the collection");
				userBytes = userBytes + thisOne;

				//delete the resource
				if (doCleanUp) {
					if (r != null) {
						contentHostingService.removeCollection(r);
					}
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
			} catch (ServerOverloadException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (Exception e) {
				e.printStackTrace();
			}

		} //For each collection
		return userBytes;
	}


	/**
	 * Utility method to get a nice short filesize string.
	 * @param size_long The size to be displayed (bytes).
	 * @return A short human readable filesize.
	 */
	public static String formatSize(long size_long) {
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
