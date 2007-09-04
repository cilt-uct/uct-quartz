package org.sakaiproject.component.app.scheduler.jobs;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.sakaiproject.content.api.ContentCollection;
import org.sakaiproject.content.api.ContentCollectionEdit;
import org.sakaiproject.content.api.ContentHostingService;
import org.sakaiproject.entity.api.EntityPropertyNotDefinedException;
import org.sakaiproject.entity.api.EntityPropertyTypeException;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.InUseException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.exception.TypeException;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.site.api.SiteService.SortType;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;

public class IncreaseQuotas implements Job {

	private static final Log LOG = LogFactory.getLog(IncreaseQuotas.class);
	
	private SiteService siteService;
	public void setSiteService(SiteService s) {
		this.siteService = s;
	}
	
	private SessionManager sessionManager;
	public void setSessionManager(SessionManager s) {
		this.sessionManager = s;
	}
	
	private long minQuota = 1024;
	public void setMinQuota(long q) {
		minQuota = q;
	}
	
	private ContentHostingService contentHostingService;
	public void setContentHostingService(ContentHostingService chs) {
		contentHostingService = chs;
	}
	
	
	public void execute(JobExecutionContext arg0) throws JobExecutionException {
		// TODO Auto-generated method stub

		//set the user information into the current session
	    Session sakaiSession = sessionManager.getCurrentSession();
	    sakaiSession.setUserId("admin");
	    sakaiSession.setUserEid("admin");
	    List sites = siteService.getSites(SiteService.SelectionType.NON_USER, "course", null, null, SortType.NONE, null);
		Long totalCollectionSize = new Long(0);
	    for (int i =0 ; i< sites.size(); i++ ) {
			Site s = (Site)sites.get(i);
			LOG.debug("got site " + s.getTitle());
			if (s.getType()!= null && s.getType().equals("course")) {
				try {
				ContentCollectionEdit collection = contentHostingService.editCollection(s.getId());
				Long collectionSize = collection.getBodySizeK();
				totalCollectionSize = new Long(totalCollectionSize.longValue() + collectionSize.longValue() );
				ResourceProperties properties = collection.getProperties();
				long quota = (long) properties.getLongProperty(ResourceProperties.PROP_COLLECTION_BODY_QUOTA);
				LOG.debug("got quota of " + quota);
				if (quota > minQuota) {
					properties.removeProperty(ResourceProperties.PROP_COLLECTION_BODY_QUOTA);
					properties.addProperty(ResourceProperties.PROP_COLLECTION_BODY_QUOTA, Long.toString(minQuota));
					contentHostingService.commitCollection(collection);
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
				} catch (EntityPropertyNotDefinedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (EntityPropertyTypeException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (InUseException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		
				
			}
		}
		
	}

}
