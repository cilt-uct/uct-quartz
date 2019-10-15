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
package org.sakaiproject.component.app.scheduler.jobs;

import java.util.List;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.sakaiproject.content.api.ContentCollection;
import org.sakaiproject.content.api.ContentCollectionEdit;
import org.sakaiproject.content.api.ContentHostingService;
import org.sakaiproject.email.api.EmailService;
import org.sakaiproject.entity.api.EntityPropertyNotDefinedException;
import org.sakaiproject.entity.api.EntityPropertyTypeException;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.InUseException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.exception.TypeException;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.site.api.SiteService.SortType;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IncreaseQuotas implements Job {


	private SiteService siteService;
	public void setSiteService(SiteService s) {
		this.siteService = s;
	}
	
	private SessionManager sessionManager;
	public void setSessionManager(SessionManager s) {
		this.sessionManager = s;
	}
	
	private long minQuota = 1024 * 1024;
	public void setMinQuota(long q) {
		minQuota = q;
	}
	
	private ContentHostingService contentHostingService;
	public void setContentHostingService(ContentHostingService chs) {
		contentHostingService = chs;
	}
	private EmailService emailService; 
	public void setEmailService(EmailService emailService) {
		this.emailService = emailService;
	}
	
	public void execute(JobExecutionContext arg0) throws JobExecutionException {
		

		//set the user information into the current session
	    Session sakaiSession = sessionManager.getCurrentSession();
	    sakaiSession.setUserId("admin");
	    sakaiSession.setUserEid("admin");
	    List<Site> sites = siteService.getSites(SiteService.SelectionType.NON_USER, "course", null, null, SortType.NONE, null);
		StringBuffer sb = new StringBuffer();
	    for (int i = 0; i< sites.size(); i++ ) {
			Site s = (Site)sites.get(i);
			if (s.getType()!= null && s.getType().equals("course")) {
				ResourceProperties sp = s.getProperties();
				String term = sp.getProperty("term");
				if (term != null ) {
					term = term.trim();
					log.debug("site is in term: " + term);
					if (term.equals("2008")) {
						log.debug("got site " + s.getTitle());
						try {
							
							String siteColl = contentHostingService.getSiteCollection(s.getId());
							ContentCollection collection = contentHostingService.getCollection(siteColl);
							Long collectionSize = collection.getBodySizeK();
							//	totalCollectionSize = new Long(totalCollectionSize.longValue() + collectionSize.longValue() );
							//Long collectionSize = new Long(0);
							ResourceProperties properties = collection.getProperties();
							long quota = (long) properties.getLongProperty(ResourceProperties.PROP_COLLECTION_BODY_QUOTA);
							log.debug("got quota of " + quota);
							if (quota != 0 && quota < minQuota) {
								log.info("setting new quota for site: " + s.getId());
								ContentCollectionEdit collectionEdit = contentHostingService.editCollection(collection.getId());
								properties.removeProperty(ResourceProperties.PROP_COLLECTION_BODY_QUOTA);
								properties.addProperty(ResourceProperties.PROP_COLLECTION_BODY_QUOTA, Long.toString(minQuota));
								contentHostingService.commitCollection(collectionEdit);
							} else if (quota != 0 && (collectionSize.longValue() >= (quota - 1024))) {
								sb.append(s.getId() + " " + s.getTitle() + " (" + collectionSize.toString() + "/" + quota + ")\n");
								log.debug("Site is close to quota");
							}
							 				
						} catch (IdUnusedException e) {
							log.info("IdUnused: " + s.getId());
							if (log.isDebugEnabled())
								log.warn(e.getMessage(), e);
						} catch (TypeException e) {
							log.warn(e.getMessage(), e);
						} catch (PermissionException e) {
							log.warn(e.getMessage(), e);
						} catch (EntityPropertyNotDefinedException e) {
							log.info("Quota property is not set: " + s.getId());
							try {
							ContentCollectionEdit collectionEdit = contentHostingService.editCollection(contentHostingService.getSiteCollection(s.getId()));
							ResourceProperties properties = collectionEdit.getProperties();
							properties.removeProperty(ResourceProperties.PROP_COLLECTION_BODY_QUOTA);
							properties.addProperty(ResourceProperties.PROP_COLLECTION_BODY_QUOTA, Long.toString(minQuota));
							log.debug("setting new quota for site");
							contentHostingService.commitCollection(collectionEdit);
							}
							catch (Exception ec) {
								log.error("Exception in catch block!" , e);
							}
						} catch (EntityPropertyTypeException e) {
							log.warn(e.getMessage(), e);
						} catch (InUseException e) {
							log.warn(e.getMessage(), e);
						} catch (Exception ex) {
							log.warn(ex.getMessage(), ex);
						}

						
					}
				}
				
			}
		}
		//we have some sites in the Stringbuffer
		if (sb.length() > 0) {
			emailService.send("help@vula.uct.ac.za", "help@vula.uct.ac.za", "Sites Close to Quota", sb.toString(),null,null, null);							
			
			
		}
	}

	

}
