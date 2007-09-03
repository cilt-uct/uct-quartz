package org.sakaiproject.component.app.scheduler.jobs;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;



import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.sakaiproject.authz.cover.AuthzGroupService;
import org.sakaiproject.db.cover.SqlService;
import org.sakaiproject.event.cover.EventTrackingService;
import org.sakaiproject.event.cover.UsageSessionService;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SitePage;
import org.sakaiproject.site.api.ToolConfiguration;
import org.sakaiproject.site.cover.SiteService;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.cover.SessionManager;
import org.sakaiproject.tool.cover.ToolManager;

public class SplitMCToolsJob implements Job {

	  private static final Log LOG = LogFactory.getLog(SplitMCToolsJob.class);
	
	public void execute(JobExecutionContext arg0) throws JobExecutionException {
		
		Connection conn = null;
		PreparedStatement statement = null;
		ResultSet result = null;
		String sql = null;
		
		
		    Session sakaiSession = SessionManager.getCurrentSession();
			sakaiSession.setUserId("admin");
			sakaiSession.setUserEid("admin");

			// establish the user's session
			UsageSessionService.startSession("admin", "127.0.0.1", "RosterSync");
			
			// update the user's externally provided realm definitions
			AuthzGroupService.refreshUser("admin");

			// post the login event
			EventTrackingService.post(EventTrackingService.newEvent(UsageSessionService.EVENT_LOGIN, null, true));
		
		List<String> messagesSites = new ArrayList<String>();
		
		try {
			 conn = SqlService.borrowConnection();
			 
			 // get list of sites that have private messages enabled
		    sql = "select CONTEXT_ID from MFR_AREA_T where TYPE_UUID = '42d85d7a-ab49-4879-00ab-e494e8f42446' and ENABLED = 1";
		    
		    statement = conn.prepareStatement(sql);
			
			result = statement.executeQuery();
			
			while(result.next()) {
				
				String siteId = result.getString("CONTEXT_ID");
				
				messagesSites.add(siteId);
				
				
			}
			
			result.close();
			statement.close();
			
		    
			//Get list of sites that have the Message Center tool
			sql = "select SAKAI_SITE_PAGE.site_id SITE_ID from SAKAI_SITE_PAGE,SAKAI_SITE_TOOL " +
			"where SAKAI_SITE_PAGE.page_id = SAKAI_SITE_TOOL.page_id " +
			"and SAKAI_SITE_TOOL.registration = 'sakai.messagecenter' ORDER BY SITE_ID DESC";
	
			
			statement = conn.prepareStatement(sql);
			
			result = statement.executeQuery();
			
			while(result.next()) {
				
				String siteId = result.getString("SITE_ID");
				
				
				try {
					Site site = SiteService.getSite(siteId);
				
				
					//Remove sakai.messagecenter tool from site
					
					Iterator pages = site.getPages().iterator();
					
					System.out.println("Site: "+site.getTitle());
					
					SitePage pageToRemove = null;
					
					while(pages.hasNext()) {
						SitePage page = (SitePage)pages.next();
						Iterator tools = page.getTools().iterator();
						while(tools.hasNext()) {
							ToolConfiguration tool = (ToolConfiguration) tools.next();
							if("sakai.messagecenter".equals(tool.getToolId())) {
							  pageToRemove = page;
							}
						}
					}
					
					
				
					if(pageToRemove != null) {
						
						
						LOG.info(siteId+": Removing page '"+pageToRemove.getTitle()+"'");
						site.removePage(pageToRemove);
					
					
						
						
						// Add sakai.messages tool to site
						SitePage page = null;
						ToolConfiguration tool = null;
						
						
						if(hasPrivateMessagesEnabled(siteId, messagesSites)) {
						
							LOG.info(siteId+": Adding a Messages page");
							
							page = site.addPage();
							page.setTitle("Messages");
							page.setLayout(SitePage.LAYOUT_SINGLE_COL);
			
							tool = page.addTool();
							tool.setTool("sakai.messages", ToolManager.getTool("sakai.messages"));
							tool.setTitle("Messages");
							tool.setLayoutHints("0,0");
						
						}
						
						//	Add sakai.forums tool to site
						LOG.info(siteId+": Adding a Forums page");
						page = site.addPage();
						page.setTitle("Forums");
						page.setLayout(SitePage.LAYOUT_SINGLE_COL);
		
						tool = page.addTool();
						tool.setTool("sakai.forums", ToolManager.getTool("sakai.forums"));
						tool.setTitle("Forums");
						tool.setLayoutHints("0,0");
					
					
					
						SiteService.save(site);
					
					}
					
				} catch (IdUnusedException e) {
					LOG.error("unable to access site ["+siteId+"]: "+e);
				} catch (PermissionException e) {
					LOG.error("unable to access site ["+siteId+"]: "+e);
				}
			
				
				
				
				
			}
		
		} catch (SQLException e) {
			LOG.error("Job Aborted!  SQLException: "+e);
		
		} finally {
			try {
				if(result != null) result.close();		
				if(statement != null) statement.close();
				if(conn != null) SqlService.returnConnection(conn);
			} catch (SQLException e) {
				LOG.error("SQLException in finally block: "+e);
			}
			
		}
		
		// post the login event
		EventTrackingService.post(EventTrackingService.newEvent(UsageSessionService.EVENT_LOGOUT, null, true));


	}
	
	
	private boolean hasPrivateMessagesEnabled(String siteId, List<String> messagesSites) {
		
		if(messagesSites.contains(siteId)) return true;
		
		return false;
		
	}

}
