package org.sakaiproject.component.app.scheduler.jobs;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.sakaiproject.authz.api.AuthzGroup;
import org.sakaiproject.authz.api.AuthzGroupService;
import org.sakaiproject.authz.api.GroupProvider;
import org.sakaiproject.authz.api.Member;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.site.api.SiteService.SortType;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UCTSaveRealmsUpdate implements Job {

	private SessionManager sessionManager;
	public void setSessionManager(SessionManager s) {
		this.sessionManager = s;
	}
	private SiteService siteService;
	public void setSiteService(SiteService s) {
		this.siteService = s;
	}
	private AuthzGroupService authzGroupService;
	public void setAuthzGroupService(AuthzGroupService a) {
		this.authzGroupService = a;
	}
	
	private GroupProvider groupProvider;
	public void setGroupProvider(GroupProvider gp){
		groupProvider =gp;
	}
	public void execute(JobExecutionContext arg0) throws JobExecutionException {
		// TODO Auto-generated method stub

	    // set the user information into the current session
	    Session sakaiSession = sessionManager.getCurrentSession();
	    sakaiSession.setUserId("admin");
	    sakaiSession.setUserEid("admin");
	    
	List sites = siteService.getSites(SiteService.SelectionType.NON_USER, "course", null, null, SortType.NONE, null);
	for (int i =0 ; i< sites.size(); i++ ) {
		Site s = (Site)sites.get(i);
		log.info("got site " + s.getTitle());
		if (s.getType()!= null && s.getType().equals("course")) {
			ResourceProperties sp = s.getProperties();
			String term = sp.getProperty("term");
			if (term != null ) {
				term = term.trim();
				log.info("site is in term: " + term);
				if (term.equals("2007") || term.equals("2006")) {
					try {
					AuthzGroup group = authzGroupService.getAuthzGroup("/site/" + s.getId());
					if (group.getProviderGroupId() != null && group.getProviderGroupId().length()>0 ) {
						String pId = group.getProviderGroupId();
						String[] pIds = groupProvider.unpackId(pId);
						Set members = group.getMembers();
						Iterator it = members.iterator();
						while (it.hasNext()) {
							Member m = (Member) it.next();
							//ignore for provided users
							if (!m.isProvided()) {
							for  (String thisId: pIds) {
								String role = groupProvider.getRole(thisId, m.getUserEid());
								if (role!= null && role.length()>0) {
									log.info("Found external role of " + role + " internal role is: " + m.getRole().getId());
									if (role.equals(m.getRole().getId())) {
									log.info("Seting user: " + m.getUserEid() + " to provided");
									group.removeMember(m.getUserId());

									}
								}
							}
							}
						}
					}
					
					
					authzGroupService.save(group);
					}
					catch(Exception e) {
						log.warn(e.getMessage(), e);
					}
				}
			}
		}
				
	}
		
	}

}
