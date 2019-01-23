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
package za.ac.uct.cet.sakai.scheduler.content;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.sakaiproject.content.api.ContentCollection;
import org.sakaiproject.content.api.ContentHostingService;
import org.sakaiproject.content.api.ContentResourceEdit;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.InUseException;
import org.sakaiproject.exception.OverQuotaException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.exception.ServerOverloadException;
import org.sakaiproject.exception.TypeException;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.site.api.SiteService.SortType;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;



@Slf4j
public class ResetContentTypes implements Job {

	@Setter private SiteService siteService;
	@Setter private SessionManager sessionManager;
	@Setter private ContentHostingService contentHostingService;

	
	Map<String, String> extensions = new HashMap<String, String>();
	
	public void setExtensions(Map<String, String> extensions) {
		this.extensions = extensions;
	}

	private List<String> forceTypes = new ArrayList<String>();
	
	public void setForceTypes(List<String> forceTypes) {
		this.forceTypes = forceTypes;
	}

	public void execute(JobExecutionContext arg0) throws JobExecutionException {
		//set the user information into the current session
		//for now 
		//extensions.put("doc", "application/msword");
		//extensions.put("odt", "application/openoffice");
		
		log.info("got a map of " + extensions.size() + " to change");
		Set<Entry<String, String>> es = extensions.entrySet();
		Iterator<Entry<String, String>> it = es.iterator();
		while (it.hasNext())  {
			Entry<String, String> ent = (Entry<String, String>) it.next();
			log.info(ent.getKey() + ": " + ent.getValue());
		}
		
		for (int i = 0; i < forceTypes.size(); i++ ) {
			log.info("force reindexing for " + forceTypes.get(i));
		}
		
	    Session sakaiSession = sessionManager.getCurrentSession();
	    sakaiSession.setUserId("admin");
	    sakaiSession.setUserEid("admin");
	    List<Site> sites = siteService.getSites(SiteService.SelectionType.ANY, null , null, null, SortType.NONE, null);
	    for (int i = 0 ; i< sites.size(); i++ ) {
	    	Site s = (Site)sites.get(i);
	    	String siteColl = contentHostingService.getSiteCollection(s.getId());
	    	ContentCollection collection;
			try {
				collection = contentHostingService.getCollection(siteColl);
		    	List<String> members = collection.getMembers();
		    	for (int q = 0; q < members.size(); q++) {
		    		String resId = (String)members.get(q);
		    		log.debug("got resource " + resId);
		    		if (reset(resId)) {
		    			ContentResourceEdit res = contentHostingService.editResource(resId);
		    			String oldType = res.getContentType();
		    			if ((!oldType.equals(getContentType(resId)) || forceTypes.contains(getExtension(resId))) && !"text/url".equals(oldType)) {
		    				log.info("content:"  + resId  + " had type: " + res.getContentType());
		    				res.setContentType(getContentType(resId));
		    				contentHostingService.commitResource(res, 0);
		    			} else {
		    				contentHostingService.cancelResource(res);
		    			}
		    		}
		    	}
			} catch (IdUnusedException e) {
				log.warn(e.getMessage(), e);
			} catch (TypeException e) {
				log.warn(e.getMessage(), e);
			} catch (PermissionException e) {
				log.warn(e.getMessage(), e);
			} catch (InUseException e) {
				log.warn(e.getMessage(), e);
			} catch (OverQuotaException e) {
				log.warn(e.getMessage(), e);
			} catch (ServerOverloadException e) {
				log.warn(e.getMessage(), e);
			}

	    }

	}

	private String getContentType(String resId) {
		return extensions.get(getExtension(resId));
	}

	
	private boolean reset(String resId) {
		String extension = getExtension(resId);
		if (extensions.containsKey(extension)) {
			return true;
		}
		log.debug("we don't change " + extension);
		return false;
	}

	private String getExtension(String resId) {
		String extension = null;
		if (resId.indexOf(".") > 0 ) {
			extension = resId.substring(resId.lastIndexOf(".") + 1, resId.length());
			log.debug("got extension: " + extension);
		}
			
		return extension;
	}

}
