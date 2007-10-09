package org.sakaiproject.component.app.scheduler.jobs;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketAddress;
import java.net.URL;
import java.net.URLDecoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

import javax.net.ssl.HttpsURLConnection;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.sakaiproject.assignment.api.Assignment;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.contentreview.exception.SubmissionException;
import org.sakaiproject.contentreview.model.ContentReviewItem;
import org.sakaiproject.db.api.SqlReader;
import org.sakaiproject.db.api.SqlService;
import org.sakaiproject.entity.api.Entity;
import org.sakaiproject.entity.api.EntityManager;
import org.sakaiproject.entity.api.EntityProducer;
import org.sakaiproject.entity.api.Reference;
import org.w3c.dom.CharacterData;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class UpdateTiiAssignments implements Job {

	
	private static final Log log = LogFactory.getLog(UpdateTiiAssignments.class);
	
	private static final String SERVICE_NAME="Turnitin";

	private String aid = null;

	private String said = null;

	private String secretKey = null;

	private String apiURL = "https://www.turnitin.com/api.asp?";

	private String proxyHost = null;

	private String proxyPort = null;

	private String defaultAssignmentName = null;


	private String defaultInstructorEmail = null;

	private String defaultInstructorFName = null;

	private String defaultInstructorLName = null;

	private String defaultInstructorPassword = null;

	private Long maxRetry = null;

	private int TII_MAX_FILE_SIZE = 10995116;

	// Proxy if set
	private Proxy proxy = null; 
	
	//note that the assignment id actually has to be unique globally so use this as a prefix
	// eg. assignid = defaultAssignId + siteId
	private String defaultAssignId = null;

	private String defaultClassPassword = null;

	//private static final String defaultInstructorId = defaultInstructorFName + " " + defaultInstructorLName;
	private String defaultInstructorId = null;

	
	//Setters 
	
	private EntityManager entityManager;

	public void setEntityManager(EntityManager en){
		this.entityManager = en;
	}
	

	private ServerConfigurationService serverConfigurationService; 

	public void setServerConfigurationService (ServerConfigurationService serverConfigurationService) {
		this.serverConfigurationService = serverConfigurationService;
	}
	
	private SqlService sqlService;
	public void setSqlService(SqlService sql) {
		sqlService = sql;
	}
	
	
	public void execute(JobExecutionContext arg0) throws JobExecutionException {
		// TODO Auto-generated method stub
		log.info("init()");

		proxyHost = serverConfigurationService.getString("turnitin.proxyHost"); 

		proxyPort = serverConfigurationService.getString("turnitin.proxyPort");

		if (!"".equals(proxyHost) && !"".equals(proxyPort)) {
			try {
				SocketAddress addr = new InetSocketAddress(proxyHost, new Integer(proxyPort).intValue());
				proxy = new Proxy(Proxy.Type.HTTP, addr);
				log.debug("Using proxy: " + proxyHost + " " + proxyPort);
			} catch (NumberFormatException e) {
				log.debug("Invalid proxy port specified: " + proxyPort);
			}
		}

		aid = serverConfigurationService.getString("turnitin.aid");

		said = serverConfigurationService.getString("turnitin.said");

		secretKey = serverConfigurationService.getString("turnitin.secretKey");

		apiURL = serverConfigurationService.getString("turnitin.apiURL","https://www.turnitin.com/api.asp?");

		defaultAssignmentName = serverConfigurationService.getString("turnitin.defaultAssignmentName");

		defaultInstructorEmail = serverConfigurationService.getString("turnitin.defaultInstructorEmail");

		defaultInstructorFName = serverConfigurationService.getString("turnitin.defaultInstructorFName");;

		defaultInstructorLName = serverConfigurationService.getString("turnitin.defaultInstructorLName");;

		defaultInstructorPassword = serverConfigurationService.getString("turnitin.defaultInstructorPassword");;

		//note that the assignment id actually has to be unique globally so use this as a prefix
		// assignid = defaultAssignId + siteId
		defaultAssignId = serverConfigurationService.getString("turnitin.defaultAssignId");;

		defaultClassPassword = serverConfigurationService.getString("turnitin.defaultClassPassword","changeit");;

		//private static final String defaultInstructorId = defaultInstructorFName + " " + defaultInstructorLName;
		defaultInstructorId = serverConfigurationService.getString("turnitin.defaultInstructorId","admin");

		maxRetry = new Long(serverConfigurationService.getInt("turnitin.maxRetry",100));

		TII_MAX_FILE_SIZE = serverConfigurationService.getInt("turnitin.maxFileSize",10995116);
		doAssignments();
	}

	private void doAssignments() {
		String statement = "Select siteid,taskid from CONTENTREVIEW_ITEM group by siteid,taskid";
		Object[] fields = new Object[0];
		List objects = sqlService.dbRead(statement, fields, new SqlReader(){
			public Object readSqlResultRecord(ResultSet result)
			{
				try {
					ContentReviewItem c = new ContentReviewItem();
					c.setSiteId(result.getString(1));
					c.setTaskId(result.getString(2));
					return c;
				} catch (SQLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					return null;
				}

			}
		});
		
		for (int i = 0; i < objects.size(); i ++) {
			ContentReviewItem cri = (ContentReviewItem) objects.get(i);
			try {
				updateAssignment(cri.getSiteId(),cri.getTaskId());
			} catch (SubmissionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
	}
	
	private void updateAssignment(String siteId, String taskId) throws SubmissionException {
		log.info("updateAssignment(" + siteId +" , " + taskId + ")");
		//get the assignment reference
		String taskTitle = getAssignmentTitle(taskId);
		log.debug("Creating assignment for site: " + siteId + ", task: " + taskId +" tasktitle: " + taskTitle);

		String diagnostic = "0"; //0 = off; 1 = on

		SimpleDateFormat dform = ((SimpleDateFormat) DateFormat.getDateInstance());
		dform.applyPattern("yyyyMMdd");
		Calendar cal = Calendar.getInstance();
		//set this to yesterday so we avoid timezine probelms etc
		cal.add(Calendar.DAY_OF_MONTH, -1);
		String dtstart = dform.format(cal.getTime());


		//set the due dates for the assignments to be in 5 month's time
		//turnitin automatically sets each class end date to 6 months after it is created
		//the assignment end date must be on or before the class end date

		//TODO use the 'secret' function to change this to longer
		cal.add(Calendar.MONTH, 5);
		String dtdue = dform.format(cal.getTime());

		String encrypt = "0";					//encryption flag
		String fcmd = "3";						//new assignment
		String fid = "4";						//function id
		String uem = defaultInstructorEmail;
		String ufn = defaultInstructorFName;
		String uln = defaultInstructorLName;
		String utp = "2"; 					//user type 2 = instructor
		String upw = defaultInstructorPassword;
		String s_view_report = "1";

		String cid = siteId;
		String uid = defaultInstructorId;
		String assignid = taskId;
		String assign = taskTitle;
		String ctl = siteId;

		String gmtime = getGMTime();
		String assignEnc = assign;
		try {
			if (assign.contains("&")) {
				//log.debug("replacing & in assingment title");
				assign = assign.replace('&', 'n');

			}
			assignEnc = assign;
			log.debug("Assign title is " + assignEnc);

		}
		catch (Exception e) {
			e.printStackTrace();
		}

		String md5_str  = aid + assignEnc + assignid + cid + ctl + diagnostic + dtdue + dtstart + encrypt +
		fcmd + fid + gmtime + said + uem + ufn + uid + uln + upw + utp + secretKey;

		String md5;
		try{
			md5 = this.getMD5(md5_str);
		} catch (Throwable t) {
			log.warn("MD5 error creating assignment on turnitin");
			throw new SubmissionException("Could not generate MD5 hash for \"Create Assignment\" Turnitin API call");
		}

		HttpsURLConnection connection;

		try {
			URL hostURL = new URL(apiURL);
			if (proxy == null) {
				connection = (HttpsURLConnection) hostURL.openConnection();
			} else {
				connection = (HttpsURLConnection) hostURL.openConnection(proxy);
			}

			connection.setRequestMethod("GET");
			connection.setDoOutput(true);
			connection.setDoInput(true);

			log.debug("HTTPS connection made to Turnitin");

			OutputStream outStream = connection.getOutputStream();

			outStream.write("aid=".getBytes("UTF-8"));
			outStream.write(aid.getBytes("UTF-8"));

			outStream.write("&assign=".getBytes("UTF-8"));
			outStream.write(assignEnc.getBytes("UTF-8"));

			outStream.write("&assignid=".getBytes("UTF-8"));
			outStream.write(assignid.getBytes("UTF-8"));

			outStream.write("&cid=".getBytes("UTF-8"));
			outStream.write(cid.getBytes("UTF-8"));

			outStream.write("&uid=".getBytes("UTF-8"));
			outStream.write(uid.getBytes("UTF-8"));

			outStream.write("&ctl=".getBytes("UTF-8"));
			outStream.write(ctl.getBytes("UTF-8"));	

			outStream.write("&diagnostic=".getBytes("UTF-8"));
			outStream.write(diagnostic.getBytes("UTF-8"));

			outStream.write("&dtdue=".getBytes("UTF-8"));
			outStream.write(dtdue.getBytes("UTF-8"));

			outStream.write("&dtstart=".getBytes("UTF-8"));
			outStream.write(dtstart.getBytes("UTF-8"));

			outStream.write("&encrypt=".getBytes("UTF-8"));
			outStream.write(encrypt.getBytes("UTF-8"));

			outStream.write("&fcmd=".getBytes("UTF-8"));
			outStream.write(fcmd.getBytes("UTF-8"));

			outStream.write("&fid=".getBytes("UTF-8"));
			outStream.write(fid.getBytes("UTF-8"));

			outStream.write("&gmtime=".getBytes("UTF-8"));
			outStream.write(gmtime.getBytes("UTF-8"));

			outStream.write("&s_view_report=".getBytes("UTF-8"));
			outStream.write(s_view_report.getBytes("UTF-8"));
			
			outStream.write("&said=".getBytes("UTF-8"));
			outStream.write(said.getBytes("UTF-8"));

			outStream.write("&uem=".getBytes("UTF-8"));
			outStream.write(uem.getBytes("UTF-8"));

			outStream.write("&ufn=".getBytes("UTF-8"));
			outStream.write(ufn.getBytes("UTF-8"));

			outStream.write("&uln=".getBytes("UTF-8"));
			outStream.write(uln.getBytes("UTF-8"));

			outStream.write("&upw=".getBytes("UTF-8"));
			outStream.write(upw.getBytes("UTF-8"));

			outStream.write("&utp=".getBytes("UTF-8"));
			outStream.write(utp.getBytes("UTF-8"));

			outStream.write("&md5=".getBytes("UTF-8"));
			outStream.write(md5.getBytes("UTF-8"));

			outStream.close();
		}
		catch (Throwable t) {
			throw new SubmissionException("Assignment creation call to Turnitin API failed", t);
		}

		BufferedReader in;
		try {
			in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		} catch (Throwable t) {
			throw new SubmissionException ("Cannot get Turnitin response. Assuming call was unsuccessful", t);
		}

		Document document = null;
		try {	
			DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder  parser = documentBuilderFactory.newDocumentBuilder();
			document = parser.parse(new org.xml.sax.InputSource(in));
		}
		catch (ParserConfigurationException pce){
			log.error("parser configuration error: " + pce.getMessage());
		} catch (Throwable t) {
			throw new SubmissionException ("Cannot parse Turnitin response. Assuming call was unsuccessful", t);
		}		

		Element root = document.getDocumentElement();
		int rcode = new Integer(((CharacterData) (root.getElementsByTagName("rcode").item(0).getFirstChild())).getData().trim()).intValue();
		if ((rcode > 0 && rcode < 100) || rcode == 419) {
			log.debug("Create Assignment successful");						
		} else {
			log.debug("Assignment creation failed with message: " + ((CharacterData) (root.getElementsByTagName("rmessage").item(0).getFirstChild())).getData().trim() + ". Code: " + rcode);
			throw new SubmissionException("Create Assignment not successful. Message: " + ((CharacterData) (root.getElementsByTagName("rmessage").item(0).getFirstChild())).getData().trim() + ". Code: " + rcode);
		}
	}
	
	private String getAssignmentTitle(String taskId){
		try {
			Reference ref = entityManager.newReference(taskId);
			log.debug("got ref " + ref + " of type: " + ref.getType());
			EntityProducer ep = ref.getEntityProducer();
			Entity ent = ep.getEntity(ref);
			log.debug("got entity " + ent);
			if (ent instanceof Assignment) {
				Assignment as = (Assignment)ent;
				log.debug("Got assignemment with title " + as.getTitle());
				return URLDecoder.decode(as.getTitle(),"UTF-8");
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		return taskId;

	}
	
	
	private String getGMTime() {
		// calculate function2 data
		SimpleDateFormat dform = ((SimpleDateFormat) DateFormat
				.getDateInstance());
		dform.applyPattern("yyyyMMddHH");
		dform.setTimeZone(TimeZone.getTimeZone("GMT"));
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));

		String gmtime = dform.format(cal.getTime());
		gmtime += Integer.toString(((int) Math.floor((double) cal
				.get(Calendar.MINUTE) / 10)));

		return gmtime;
	}
	
	
	private String getMD5(String md5_string) throws NoSuchAlgorithmException {

		MessageDigest md = MessageDigest.getInstance("MD5");

		md.update(md5_string.getBytes());

		// convert the binary md5 hash into hex
		String md5 = "";
		byte[] b_arr = md.digest();

		for (int i = 0; i < b_arr.length; i++) {
			// convert the high nibble
			byte b = b_arr[i];
			b >>>= 4;
			b &= 0x0f; // this clears the top half of the byte
			md5 += Integer.toHexString(b);

			// convert the low nibble
			b = b_arr[i];
			b &= 0x0F;
			md5 += Integer.toHexString(b);
		}

		return md5;
	}
}
