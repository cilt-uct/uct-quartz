package org.sakaiproject.component.app.scheduler.jobs;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.sakaiproject.authz.api.Member;
import org.sakaiproject.emailtemplateservice.service.EmailTemplateService;
import org.sakaiproject.entity.api.EntityPropertyNotDefinedException;
import org.sakaiproject.entity.api.EntityPropertyTypeException;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.site.api.SiteService.SortType;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.user.api.UserNotDefinedException;


public class JoinableSiteJob implements Job {

	private static final Log LOG = LogFactory.getLog(JoinableSiteJob.class);
	private static final String PROP_LAST_CHECK = "JoinableLastCheck";
	private static final String PROP_ARCHIVE = "archive";
	
	
	private SiteService siteService;
	public void setSiteService(SiteService s) {
		this.siteService = s;
	}
	
	private SessionManager sessionManager;
	public void setSessionManager(SessionManager s) {
		this.sessionManager = s;
	}
	
	private EmailTemplateService emailTemplateService;
	public void setEmailTemplateService(EmailTemplateService emailTemplateService) {
		this.emailTemplateService = emailTemplateService;
	}

	private UserDirectoryService userDirectoryService;
	public void setUserDirectoryService(UserDirectoryService s) {
		this.userDirectoryService = s;
	}
	
	
	public void execute(JobExecutionContext arg0) throws JobExecutionException {
		//set the user information into the current session
	    Session sakaiSession = sessionManager.getCurrentSession();
	    sakaiSession.setUserId("admin");
	    sakaiSession.setUserEid("admin");
	    
	    List<Site> sites = siteService.getSites(SiteService.SelectionType.NON_USER, "project", null, null, SortType.NONE, null);
	    for (int i =0 ; i< sites.size(); i++ ) {
	    	//iterate through the sites
	    	Site s = sites.get(i);
	    	LOG.info("checking:" + s.getId());
	    	if (s.isJoinable()) {
	    		LOG.info("site is joinable!");
	    		ResourceProperties rp = s.getProperties();
	    		Long time = Long.valueOf(0);
	    		
	    		try {
					boolean b = rp.getBooleanProperty(PROP_ARCHIVE);
					if (b)
						continue;
					
				} catch (EntityPropertyNotDefinedException e1) {
					//not much to do this is expected
				} catch (EntityPropertyTypeException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
	    		
	    		try {
	    			time = rp.getLongProperty(PROP_LAST_CHECK);
	    		} catch (EntityPropertyNotDefinedException e) {
	    			//add the property
	    			time = Long.valueOf(new Date().getTime());
	    			rp.addProperty(PROP_LAST_CHECK, time.toString());

	    		} catch (EntityPropertyTypeException e) {
	    			// TODO Auto-generated catch block
	    			e.printStackTrace();
	    		}
	    		Date checkDate = new Date(time);
	    		Calendar cal = new GregorianCalendar();
	    		cal.add(Calendar.MONTH, -1);
	    		Date oneMonthPast = cal.getTime();
	    		//if before we send the mail
	    		if (checkDate.before(oneMonthPast)) {
	    			//we send the stuff
	    			time = Long.valueOf(new Date().getTime());
	    			rp.addProperty(PROP_LAST_CHECK, time.toString());

	    			sendOwnerNotification(rp, s);
	    			if (!siteHasActiveMembers(s)) {
	    				s.setJoinable(false);
	    				rp.addProperty(PROP_ARCHIVE, "true");
	    			}
	    		}

	    		if ("Site owner".equalsIgnoreCase(s.getJoinerRole())) {
	    			LOG.warn("site has join role of site owner");
	    		}
	    		

	    		try {
	    			siteService.save(s);
	    		} catch (IdUnusedException e) {
	    			// TODO Auto-generated catch block
	    			e.printStackTrace();
	    		} catch (PermissionException e) {
	    			// TODO Auto-generated catch block
	    			e.printStackTrace();
	    		}
	    	}
	    }

	}

	private boolean siteHasActiveMembers(Site site) {
		Set<Member> members = site.getMembers();
		Iterator<Member> it = members.iterator();
		boolean active = false;
		while (it.hasNext()) {
			Member m = it.next();
			try {
				User u = userDirectoryService.getUser(m.getUserId());
				String type = u.getType();
				type = type.toLowerCase();
				if (type != null && !(type.contains("inactive"))) {
					active = true;
				}
			} catch (UserNotDefinedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return active;
		
	}

	private void sendOwnerNotification(ResourceProperties rp, Site site) {
		
		String contactEmail = rp.getProperty("contact-email");
		User u = null;
		if (contactEmail == null) {
			//get the creator
			u = site.getCreatedBy();
		} else {
			Collection<User> users = userDirectoryService.findUsersByEmail(contactEmail);
			if (users.size() > 0) {
				u = users.iterator().next();
			} else {
				//the email may not be resolvable to a user
				u = site.getCreatedBy();
			}
		}
		Map<String, String> replacementValues = new HashMap<String, String>();
		replacementValues.put("siteTitle", site.getTitle());
		replacementValues.put("siteJoinRole", site.getJoinerRole());
		replacementValues.put("siteOwner", u.getDisplayName());
		
		List<String> userRefs = new ArrayList<String>();
		userRefs.add(u.getReference());
		
		emailTemplateService.sendRenderedMessages("joinableSiteReminder", userRefs, replacementValues, "help@vula.uct.ac.za", "Vula help");
		
		
		
		
	}

}
