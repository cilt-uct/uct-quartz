package za.ac.uct.cet.sakai.scheduler.sms;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.sakaiproject.db.api.SqlService;
import org.sakaiproject.sms.logic.SmsTaskLogic;
import org.sakaiproject.sms.model.SmsTask;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserDirectoryService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SmsBirthdayMessage implements Job {

	private static final String ADMIN = "admin";
	
	private SqlService sqlService;
	private SessionManager sessionManager;
	public void setSessionManager(SessionManager s) {
		this.sessionManager = s;
	}
	
	
	public void setSqlService(SqlService sqlService) {
		this.sqlService = sqlService;
	}
	
	private UserDirectoryService userDirectoryService;	
	public void setUserDirectoryService(UserDirectoryService userDirectoryService) {
		this.userDirectoryService = userDirectoryService;
	}

	private SmsTaskLogic smsTaskLogic;	
	public void setSmsTaskLogic(SmsTaskLogic smsTaskLogic) {
		this.smsTaskLogic = smsTaskLogic;
	}


	public void execute(JobExecutionContext context)
			throws JobExecutionException {
		
		
		//set the user information into the current session
	    Session sakaiSession = sessionManager.getCurrentSession();
	    sakaiSession.setUserId(ADMIN);
	    sakaiSession.setUserEid(ADMIN);
	    
	    
	    String sql = "select distinct agent_UUid from SAKAI_PERSON_T where day(dateofbirth)=day(now()) and month(dateofbirth) = month(now()) order by dateofbirth;";
	    List<String> res = sqlService.dbRead(sql);
	    log.info("got a result of: " + res.size());
		List<String> filtered = filterUserList(res);
	    //TODO we need to filter the list to remove inactive users
	    
	    Set<String> userSet = new HashSet<String>(filtered);
	    SmsTask task = new SmsTask();
	    task.setSakaiUserIdsList(userSet);
	    task.setMessageBody("Happy Birthday from the Vula Team at UCT!");
	    task.setDateCreated(new Date());
	    Calendar cal = new GregorianCalendar();
	    cal.set(Calendar.HOUR_OF_DAY, 9);
	    cal.set(Calendar.MINUTE, 0);
	    Date dateToSend = cal.getTime();
	    task.setDateToSend(dateToSend);
	    task.setSmsAccountId(Long.valueOf(1));
	    task.setSakaiSiteId("d02f250e-be2d-4b72-009a-161d66ed6df9");
	    task.setAttemptCount(0);
	    
	    smsTaskLogic.persistSmsTask(task);
	    
	}

	private List<String> filterUserList(List<String> res) {
		List<User> users = userDirectoryService.getUsers(res);
		List<String> ret = new ArrayList<String>();
		for (int i = 0; i < users.size(); i++) {
			User user = users.get(i);
			if ("student".equals(user.getType()) || "staff".equals(user.getType())) {
				ret.add(user.getId());
			}
		}
		return ret;
	}

}
