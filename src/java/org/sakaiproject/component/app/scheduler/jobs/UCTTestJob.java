package org.sakaiproject.component.app.scheduler.jobs;

import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.StatefulJob;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UCTTestJob implements StatefulJob {

	
	public void execute(JobExecutionContext arg0) throws JobExecutionException {
		// TODO Auto-generated method stub
		log.info("This job is stateful?: " +arg0.getJobDetail().toString());    //.isStateful());
		log.info("UCTTestJob fired");
		try {
			Thread.sleep(120*1000);
		} catch (InterruptedException e) {
			log.warn(e.getMessage(), e);
		}
		log.info("UCTTestJob finnished");
	}

}
