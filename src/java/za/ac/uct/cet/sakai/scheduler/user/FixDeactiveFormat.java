/**********************************************************************************
 *
 * Copyright (c) 2019 University of Cape Town
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.opensource.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **********************************************************************************/
package za.ac.uct.cet.sakai.scheduler.user;

import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;
import org.joda.time.format.ISODateTimeFormat;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.sakaiproject.db.api.SqlService;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserAlreadyDefinedException;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.user.api.UserEdit;
import org.sakaiproject.user.api.UserLockedException;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.user.api.UserPermissionException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FixDeactiveFormat implements Job {

	private SqlService sqlService;
	private UserDirectoryService userDirectoryService;
	
	
	public void setSqlService(SqlService sqlService) {
		this.sqlService = sqlService;
	}


	public void setUserDirectoryService(UserDirectoryService userDirectoryService) {
		this.userDirectoryService = userDirectoryService;
	}
	
	private SessionManager sessionManager;
	public void setSessionManager(SessionManager s) {
		this.sessionManager = s;
	}

	
	public void execute(JobExecutionContext arg0) throws JobExecutionException {
		// TODO Auto-generated method stub
		String sql = "select user_id from SAKAI_USER_PROPERTY where name='SPML_DEACTIVATED'";
		List<String> users = sqlService.dbRead(sql);
		
		//set the user information into the current session
	    Session sakaiSession = sessionManager.getCurrentSession();
	    sakaiSession.setUserId("admin");
	    sakaiSession.setUserEid("admin");
	    
	    DateTimeFormatter fmt = ISODateTimeFormat.dateTime();
	    //Tue Feb 01 15:11:42 SAST 2011 
	    DateTimeFormatter oldFormat = new DateTimeFormatterBuilder()
		.appendDayOfWeekShortText()
		.appendLiteral(' ')
		.appendMonthOfYearShortText()
		.appendLiteral(' ')
		.appendDayOfMonth(2)
		.appendLiteral(' ')
		.appendHourOfDay(2)
		.appendLiteral(':')
		.appendMinuteOfHour(2)
		.appendLiteral(':')
		.appendSecondOfMinute(2)
		.appendLiteral(' ')
		.appendTimeZoneOffset(null, true, 1, 1)
		.appendLiteral(' ')
		.appendYear(4, 4)
		.toFormatter()
		;
		for (int i = 0; i < users.size(); i++) {
			String userId = users.get(i);
			try {
				User user = userDirectoryService.getUser(userId);
				ResourceProperties rp = user.getProperties();
				String deactive = rp.getProperty("SPML_DEACTIVATED");
				log.info("deactive string is  " + deactive);
				try {
					DateTime dt = fmt.parseDateTime(deactive);
				}
				catch (IllegalArgumentException e) {
					log.info("that's not an ISO date!");
					//todo we need to parse
					String value = deactive.replace("SAST", "+02");
					DateTime newOne = oldFormat.parseDateTime(value);
					try {
						UserEdit edit = userDirectoryService.editUser(userId);
						ResourceProperties rp1 = user.getProperties();
						rp1.addProperty("SPML_DEACTIVATED", fmt.print(newOne));
						userDirectoryService.commitEdit(edit);
						
					} catch (UserPermissionException e1) {
						log.warn(e.getMessage(), e);
					} catch (UserLockedException e1) {
						log.warn(e.getMessage(), e);
					} catch (UserAlreadyDefinedException e3) {
						log.warn(e.getMessage(), e);
					}
					
					
				}
				
			} catch (UserNotDefinedException e) {
				log.warn(e.getMessage(), e);
			}
			
		}
		
	}

}
