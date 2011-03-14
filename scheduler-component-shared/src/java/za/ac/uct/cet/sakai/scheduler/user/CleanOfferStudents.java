package za.ac.uct.cet.sakai.scheduler.user;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.sakaiproject.coursemanagement.api.CourseManagementAdministration;
import org.sakaiproject.coursemanagement.api.CourseManagementService;
import org.sakaiproject.coursemanagement.api.Section;
import org.sakaiproject.db.api.SqlService;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.user.api.UserAlreadyDefinedException;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.user.api.UserEdit;
import org.sakaiproject.user.api.UserLockedException;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.user.api.UserPermissionException;

public class CleanOfferStudents implements Job{

	private static final Log LOG = LogFactory.getLog(CleanOfferStudents.class);

	private static final String PROPERTY_DEACTIVATED = "SPML_DEACTIVATED";

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

	private CourseManagementService courseManagementService;
	public void setCourseManagementService(
			CourseManagementService courseManagementService) {
		this.courseManagementService = courseManagementService;
	}


	private CourseManagementAdministration courseManagementAdministration;
	public void setCourseManagementAdministration(
			CourseManagementAdministration courseManagementAdministration) {
		this.courseManagementAdministration = courseManagementAdministration;
	}


	public void execute(JobExecutionContext arg0) throws JobExecutionException {

		//set the user information into the current session
		Session sakaiSession = sessionManager.getCurrentSession();
		sakaiSession.setUserId("admin");
		sakaiSession.setUserEid("admin");




		String sql = "select USER_ID from SAKAI_USER where type ='offer';";

		List<String> users = sqlService.dbRead(sql);

		LOG.info("got a list of " + users.size() + " users to remove");

		for (int i = 0; i < users.size(); i++) {
			String userId = users.get(i);
			try {
				UserEdit u = userDirectoryService.editUser(userId);
				String mail = u.getEid() + "@uct.ac.za";
				//TODO remove user from cm groups
				removeUserFromCMGroups(u.getEid());
				
				//is this user a member of a course at any point?
				if (!hasMemberships(u.getEid())) {
					u.setType("inactive");
					//set the inactive date if none
					ResourceProperties rp = u.getProperties();
					DateTime dt = new DateTime();
					DateTimeFormatter fmt = ISODateTimeFormat.dateTime();

					//do we have an inactive flag?
					String deactivated = rp.getProperty(PROPERTY_DEACTIVATED);
					if (deactivated == null) {
						rp.addProperty(PROPERTY_DEACTIVATED, fmt.print(dt));
					}

					userDirectoryService.commitEdit(u);
				} else {
					userDirectoryService.cancelEdit(u);
				}

				

				LOG.info("updated: " + userId);
			} catch (UserNotDefinedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (UserPermissionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (UserLockedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (UserAlreadyDefinedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}


	private void removeUserFromCMGroups(String userEid) {
		Set<Section> enrollmentSet = courseManagementService.findEnrolledSections(userEid);
		Iterator<Section> it = enrollmentSet.iterator();
		while (it.hasNext()) {
			Section section = it.next();
			String courseEid = section.getEid();
			LOG.info("removing user from " + courseEid);
			courseManagementAdministration.removeCourseOfferingMembership(userEid, courseEid);
			courseManagementAdministration.removeSectionMembership(userEid, courseEid);
			courseManagementAdministration.removeEnrollment(userEid, courseEid);
		}

		
	}


	private boolean hasMemberships(String eid) {
		Set<Section> enrollmentSet = courseManagementService.findEnrolledSections(eid);
		Iterator<Section> it = enrollmentSet.iterator();
		while (it.hasNext()) {
			Section section = it.next();
			String sectionEid = section.getEid();
			if (sectionEid.length() == "AAE5000H,2006".length()) {
				LOG.info("section " + eid + " looks like a course");
				return true;
			} else if (isCurrentOfferGroup(sectionEid)) {
				LOG.info("this is a 2011 student");
				return true;
			} else {
				LOG.info(sectionEid + " doesn't match our filters");
			}
			
		}
		
		return false;
	}


	private boolean isCurrentOfferGroup(String sectionEid) {
		if ("SCI_OFFER_STUDENT,2011".equals(sectionEid) || "COM_OFFER_STUDENT,2011".equals(sectionEid) ||
				"HUM_OFFER_STUDENT,2011".equals(sectionEid) || "LAW_OFFER_STUDENT,2011".equals(sectionEid) || 
				"EBE_OFFER_STUDENT,2011".equals(sectionEid)) {
			LOG.info(sectionEid + " is a current offer group");
			return true;
		}
		
			return false;
	}

}
