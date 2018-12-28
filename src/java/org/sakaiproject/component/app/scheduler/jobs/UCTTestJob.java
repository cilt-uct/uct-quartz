/**********************************************************************************
 *
 * Copyright (c) 2013 The Sakai Foundation
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

import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.StatefulJob;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UCTTestJob implements StatefulJob {

	
	public void execute(JobExecutionContext arg0) throws JobExecutionException {
		// TODO Auto-generated method stub
		log.info("This job is stateful?: " + arg0.getJobDetail().toString());    //.isStateful());
		log.info("UCTTestJob fired");
		try {
			Thread.sleep(120 * 1000);
		} catch (InterruptedException e) {
			log.warn(e.getMessage(), e);
		}
		log.info("UCTTestJob finnished");
	}

}
