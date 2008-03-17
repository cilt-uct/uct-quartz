package org.sakaiproject.component.app.scheduler.jobs;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.StatefulJob;

public class UCTTestJob implements StatefulJob {
	private static final Log LOG = LogFactory.getLog(UCTTestJob.class);
	
	public void execute(JobExecutionContext arg0) throws JobExecutionException {
		// TODO Auto-generated method stub
		LOG.info("This job is stateful?: " +arg0.getJobDetail().isStateful());
		LOG.info("UCTTestJob fired");
		try {
			Thread.sleep(120*1000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		LOG.info("UCTTestJob finnished");
	}

}
