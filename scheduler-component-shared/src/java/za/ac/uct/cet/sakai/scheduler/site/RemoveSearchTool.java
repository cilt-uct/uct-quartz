package za.ac.uct.cet.sakai.scheduler.site;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.site.api.ToolConfiguration;
import org.sakaiproject.site.api.SiteService.SortType;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;

public class RemoveSearchTool implements Job{

	private static final Log LOG = LogFactory.getLog(RemoveSearchTool.class);

	private SiteService siteService;
	public void setSiteService(SiteService s) {
		this.siteService = s;
	}

	private SessionManager sessionManager;
	public void setSessionManager(SessionManager s) {
		this.sessionManager = s;
	}

	public void setMinTermId(int minTermId) {
		this.minTermId = minTermId;
	}

	/*
	 * The minimum term for keeping the searhc tool
	 */
	private int minTermId = 2012;

	public void execute(JobExecutionContext arg0) throws JobExecutionException {

		//set the user information into the current session
		Session sakaiSession = sessionManager.getCurrentSession();
		sakaiSession.setUserId("admin");
		sakaiSession.setUserEid("admin");



		List<Site> sites = siteService.getSites(SiteService.SelectionType.NON_USER, "project", null, null, SortType.NONE, null);
		for (int i =0 ; i< sites.size(); i++ ) {
			Site s1 = sites.get(i);
			checkRemoveProjectSites(s1);

		}

		sites = siteService.getSites(SiteService.SelectionType.NON_USER, "collaboration", null, null, SortType.NONE, null);
		for (int i =0 ; i< sites.size(); i++ ) {
			Site s1 = sites.get(i);
			checkRemoveProjectSites(s1);

		}

		//if this is before 2011 remove the search tool
		//site will be lazily loaded
		sites = siteService.getSites(SiteService.SelectionType.NON_USER, "course", null, null, SortType.NONE, null);
		for (int i =0 ; i< sites.size(); i++ ) {
			Site s1 = sites.get(i);
			checkRemoveCourseSites(s1);

		}

	}

	private void checkRemoveProjectSites(Site s1) {
		Site site;
		try {
			site = siteService.getSite(s1.getId());
			if (!site.isPublished()) {
				removeSearchTool(site); 

			}

		} catch (IdUnusedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (PermissionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}	

	private void checkRemoveCourseSites(Site s1) {
		Site site;
		try {
			site = siteService.getSite(s1.getId());
			ResourceProperties rp = site.getProperties();
			String term = rp.getProperty("term");
			LOG.info("found term " + term + " for " + site.getTitle());

			if (term == null || Integer.valueOf(term).intValue() < minTermId) {
				removeSearchTool(site); 

			}

		} catch (IdUnusedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (PermissionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void removeSearchTool(Site site)
			throws IdUnusedException, PermissionException {
		LOG.info("checking for search tool");
		//find the search tool
		Collection<ToolConfiguration> tc = site.getTools("sakai.search");
		LOG.info("got " + tc.size() + " matching configs");
		Iterator<ToolConfiguration> itar = tc.iterator();
		while (itar.hasNext()) {
			ToolConfiguration toolConfig = itar.next();
			LOG.info("removing search page from " + site.getTitle());
			site.removePage(toolConfig.getContainingPage());
			siteService.save(site);
		}
	}

}
