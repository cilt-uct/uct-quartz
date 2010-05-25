package za.ac.uct.cet.sakai.scheduler.sakai3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.sakaiproject.entitybroker.util.http.HttpRESTUtils;
import org.sakaiproject.entitybroker.util.http.HttpResponse;
import org.sakaiproject.entitybroker.util.http.HttpRESTUtils.HttpIOException;
import org.sakaiproject.entitybroker.util.http.HttpRESTUtils.HttpRequestException;
import org.sakaiproject.entitybroker.util.http.HttpRESTUtils.Method;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserDirectoryService;



public class Sakai3UserSych implements Job {

	private static final Log LOG = LogFactory.getLog(Sakai3UserSych.class);
	
	private static final String ADMIN = "admin";
	
	private UserDirectoryService userDirectoryService;
	public void setUserDirectoryService(UserDirectoryService s) {
		this.userDirectoryService = s;
	}
	
	private SessionManager sessionManager;
	public void setSessionManager(SessionManager s) {
		this.sessionManager = s;
	}
	
	private String restUrl = "https//mocha.cet.uct.ac.za/system/userManager/user.create.html";
	public void setRestUrl(String restUrl) {
		this.restUrl = restUrl;
	}

	public void execute(JobExecutionContext context)
			throws JobExecutionException {
		//set the user information into the current session
	    Session sakaiSession = sessionManager.getCurrentSession();
	    sakaiSession.setUserId(ADMIN);
	    sakaiSession.setUserEid(ADMIN);
	
	    int first = 1;
		int increment = 1000;
		int last = increment;
		boolean doAnother = true;
		while (doAnother) {
			
		
			List<User> users = userDirectoryService.getUsers(first, last);
			for (int i= 0; i < users.size(); i++ ){
				//is this user f the right type?
				User u = (User)users.get(i);
				if (doThisUser(u)) {
					LOG.info("going to add " + u.getEid());
					
					//curl -F:name=username -Fpwd=password -FpwdConfirm=password -Fproperty1=value1 http://localhost:8080/system/userManager/user.create.html
					Map<String,String> params = new HashMap<String,String>();
					params.put("name", u.getEid());
					params.put("pwd", "123");
					params.put("pwdConfirm", "123");
					
					HttpResponse resp = null;
					
					try {
						resp = HttpRESTUtils.fireRequest(restUrl, Method.POST, params);
					} catch (HttpRequestException e) {
						LOG.warn(" create user threw Exception: " + e);
						if (LOG.isDebugEnabled()) {
							e.printStackTrace();
						}
						
					} catch (HttpIOException hio) {
						LOG.warn("Create user threw Exception: " + hio);
						if (LOG.isDebugEnabled()) {
							hio.printStackTrace();
						}
					}
					
				}
			}
			if (users.size() < increment) {
				doAnother = false;
			} else {
				first = last +1;
				last = last + increment;
			}
		}
		
	}
	
	private boolean doThisUser(User u) {
		String type = u.getType();
		if (type == null)
			return false;
	
		if (u.getEmail() == null || "".equals(u.getEmail().trim()))
			return false;
	
		if ("student".equals(type) || "staff".equals(type) || "thirdparty".equals(type))
			return true;
		
		return false;
	}
}
