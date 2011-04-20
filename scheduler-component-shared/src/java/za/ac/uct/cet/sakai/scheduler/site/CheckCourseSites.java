package za.ac.uct.cet.sakai.scheduler.site;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.sakaiproject.authz.api.Member;
import org.sakaiproject.emailtemplateservice.service.EmailTemplateService;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.site.api.SiteService.SortType;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.user.api.UserNotDefinedException;


public class CheckCourseSites implements Job {

	private static final Log LOG = LogFactory.getLog(CheckCourseSites.class);
	
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

		List<Site> sites = siteService.getSites(SiteService.SelectionType.NON_USER, "course", null, null, SortType.NONE, null);
		
		List<Site> nonActiveSites = new ArrayList<Site>();
		for (int i =0 ; i< sites.size(); i++ ) {
			//iterate through the sites
			Site s1 = sites.get(i);
			//get the membership
			Set<Member> members = s1.getMembers();
			Iterator<Member> it = members.iterator();
			boolean hasActiveStudent = false;
			while (it.hasNext()) {
				Member member = it.next();
				LOG.info("got member: " + member.getUserDisplayId() + " with role: " + member.getRole());
				if ("Student".equals(member.getRole())) {
					LOG.info("got a student user");
					try {
						User user = userDirectoryService.getUser(member.getUserId());
						if ("student".equals(user.getType())) {
							LOG.info("got an active student");
							hasActiveStudent = true;
							break;
						}
					} catch (UserNotDefinedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
				}
			}
			
			if (!hasActiveStudent) {
				LOG.info("site: " + s1.getTitle() + " has no active students");
				nonActiveSites.add(s1);
			}
			
		}

	}





}
