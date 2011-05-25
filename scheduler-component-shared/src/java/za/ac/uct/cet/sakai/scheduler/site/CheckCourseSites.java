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
import org.sakaiproject.content.api.ContentCollection;
import org.sakaiproject.content.api.ContentHostingService;
import org.sakaiproject.email.api.EmailService;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.exception.TypeException;
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

		private EmailService emailService;
	public void setEmailService(EmailService emailService) {
		this.emailService = emailService;
	}

	private UserDirectoryService userDirectoryService;
	public void setUserDirectoryService(UserDirectoryService s) {
		this.userDirectoryService = s;
	}

	
	private ContentHostingService contentHostingService; 
	public void setContentHostingService(ContentHostingService contentHostingService) {
		this.contentHostingService = contentHostingService;
	}

	public void execute(JobExecutionContext arg0) throws JobExecutionException {
		//set the user information into the current session
		Session sakaiSession = sessionManager.getCurrentSession();
		sakaiSession.setUserId("admin");
		sakaiSession.setUserEid("admin");

		List<Site> sites = siteService.getSites(SiteService.SelectionType.NON_USER, "course", null, null, SortType.NONE, null);
		
		List<Site> nonActiveSites = new ArrayList<Site>();
		Long totalBodyK = 0L;
		for (int i =0 ; i< sites.size(); i++ ) {
			//iterate through the sites
			Site s1 = sites.get(i);
			//get the membership
			Set<Member> members = s1.getMembers();
			Iterator<Member> it = members.iterator();
			boolean hasActiveStudent = false;
			while (it.hasNext()) {
				Member member = it.next();
				String role = member.getRole().getId();
				LOG.debug("got member: " + member.getUserDisplayId() + " with role: " + role);
				if ("Student".equals(role)) {
					LOG.debug("got a student user");
					try {
						User user = userDirectoryService.getUser(member.getUserId());
						if ("student".equals(user.getType())) {
							LOG.debug("got an active student");
							hasActiveStudent = true;
							break;
						}
					} catch (UserNotDefinedException e) {
						//nothing to do we expect some of these
					}
					
				}
			}
			
			if (!hasActiveStudent) {
				LOG.info("site: " + s1.getTitle() + " has no active students");
				
				//get the size of the collection
				String siteCollection  = contentHostingService.getSiteCollection(s1.getId());
				try {
					ContentCollection collection = contentHostingService.getCollection(siteCollection);
					Long bodyK = collection.getBodySizeK();
					totalBodyK = totalBodyK + bodyK;
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
				
				nonActiveSites.add(s1);
			}
			
		}
		
		//compose the email
		StringBuilder sb = new StringBuilder();
		sb.append("found " + nonActiveSites.size() + " sites that could be archived\n");
		sb.append("containing " + totalBodyK + "K of content in resources\n\n");
		for (int i = 0; i < nonActiveSites.size(); i++) {
			Site s = nonActiveSites.get(i);
			sb.append(s.getTitle() + " (" + s.getId() + ")\n");
		}
		
		emailService.send("help@vula.uct.ac.za", "help-team@vula.uct.ac.za", "Course sites with no active students", sb.toString(), null, null, null);
		
	}





}
