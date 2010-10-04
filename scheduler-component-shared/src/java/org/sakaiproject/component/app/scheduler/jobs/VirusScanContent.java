package org.sakaiproject.component.app.scheduler.jobs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.sakaiproject.antivirus.api.VirusFoundException;
import org.sakaiproject.antivirus.api.VirusScanner;
import org.sakaiproject.content.api.ContentCollection;
import org.sakaiproject.content.api.ContentHostingService;
import org.sakaiproject.email.api.EmailService;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.exception.TypeException;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.site.api.SiteService.SortType;
import org.sakaiproject.thread_local.api.ThreadLocalManager;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;




public class VirusScanContent implements Job {

	private SiteService siteService;
	public void setSiteService(SiteService s) {
		this.siteService = s;
	}
	
	private SessionManager sessionManager;
	public void setSessionManager(SessionManager s) {
		this.sessionManager = s;
	}
	


	private ContentHostingService contentHostingService;
	public void setContentHostingService(ContentHostingService chs) {
		contentHostingService = chs;
	}
	


	
	 private VirusScanner virusScanner;	
	public void setVirusScanner(VirusScanner virusScanner) {
		this.virusScanner = virusScanner;
	}
	
	private ThreadLocalManager threadLocalManager;
	public void setThreadLocalManager(ThreadLocalManager threadLocalManager) {
		this.threadLocalManager = threadLocalManager;
	}

	private EmailService emailService;	
	public void setEmailService(EmailService emailService) {
		this.emailService = emailService;
	}

	private static final Log log = LogFactory.getLog(VirusScanContent.class);
	
	public void execute(JobExecutionContext arg0) throws JobExecutionException {
		//set the user information into the current session
		//for now 
		//extensions.put("doc", "application/msword");
		//extensions.put("odt", "application/openoffice");
		

		StringBuilder sb = new StringBuilder();
	    Session sakaiSession = sessionManager.getCurrentSession();
	    sakaiSession.setUserId("admin");
	    sakaiSession.setUserEid("admin");
	    List<Site> sites = siteService.getSites(SiteService.SelectionType.ANY, null , null, null, SortType.NONE, null);
	    for (int i =0 ; i< sites.size(); i++ ) {
	    	
			//SAK-17117 before we do this clear threadLocal
			//get the security advisor stack otherwise later calls will fail
			Object obj = threadLocalManager.get("SakaiSecurity.advisor.stack");
			threadLocalManager.clear();
			threadLocalManager.set("SakaiSecurity.advisor.stack", obj);
	    	
	    	Site s = (Site)sites.get(i);
	    	String siteColl = contentHostingService.getSiteCollection(s.getId());
	    	ContentCollection collection;
			try {
				collection = contentHostingService.getCollection(siteColl);
		    	List<String> members = collection.getMembers();
		    	for (int q =0; q < members.size(); q++) {
		    		String resId = (String)members.get(q);
		    		log.debug("got resource " + resId);
		    		try {
		    			if (!contentHostingService.isCollection(resId)) {
		    				virusScanner.scanContent(resId);
		    			}
		    		}
		    		catch (VirusFoundException e) {
						//we have a virus!
		    			sb.append(resId +": " + e.getMessage() + "\n");
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
			} 

	    }
	    
	    log.info("virii found : " + sb.toString());
	    emailService.send("help@vula.uct.ac.za", "help-team@vula.uct.ac.za", "virusScan results", sb.toString(), null, null, null);
	    
	    

	}

	
	
}
