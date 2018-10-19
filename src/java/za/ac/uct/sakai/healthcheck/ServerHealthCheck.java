package za.ac.uct.sakai.healthcheck;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.db.api.SqlService;
import org.sakaiproject.email.api.EmailService;
import org.sakaiproject.time.api.UserTimeService;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author dhorwitz
 *
 */
@Slf4j
public class ServerHealthCheck  {
  
  @Setter private SqlService sqlService;
  @Setter private Integer seconds;
  @Setter private EmailService emailService;
  @Setter private ServerConfigurationService serverConfigurationService;
  @Setter private UserTimeService userTimeService;


  private CheckRunner checkRunner;

  /**
  * init.
  */
  public void init() {
    log.info("init()");
    if (seconds == null) {
      seconds = 30;
    }
    checkRunner = new CheckRunner(seconds.intValue());
  }

  public void destroy() {
    checkRunner.setThreadStop(true);
  }

  private class CheckRunner implements Runnable {

    private Thread thread;
    
    private boolean stopThread = false;
    
    /**
     * threshold minutes.
     */
    private int threshold =  30;
    
    Instant nextCheck;
    
    public CheckRunner(int threshold) {
      thread = new Thread(this);
      thread.start();
      this.threshold = threshold;
    }
    
    public void run() {
      int checkPeriod = -(5 * 60 * 1000);
      nextCheck = Instant.now();
      while (!stopThread) {
        try {
          if (Instant.now().isAfter(nextCheck)) {
            checkServerHealth();
            checkNTP();
            nextCheck = nextCheck.minusMillis(checkPeriod);
            log.info("next check at " + dateIso(nextCheck));
          }
          Thread.sleep(5000);
        } catch (InterruptedException e) {
          log.warn(e.getLocalizedMessage(), e);
        } catch (NumberFormatException e) {
          log.error("Format exception ", e);
        }
      }
    }
    
    public void setThreadStop(boolean val) {
      stopThread = val;
    }
    
    private String dateIso(Instant i) {
      DateTimeFormatter formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
      ZoneId t = userTimeService.getLocalTimeZone().toZoneId();
      ZonedDateTime date = ZonedDateTime.ofInstant(i, t);
      String strDate = date.format(formatter);
      return strDate;
    }
    
    @SuppressWarnings("unchecked")
    private void checkServerHealth() {
      String strDate = dateIso(Instant.now());
      Object[] fields = new Object[]{strDate};

      // This can return a decimal value in mysql 5.7
      String sql = "select UNIX_TIMESTAMP(?) - UNIX_TIMESTAMP(now())S";

      List<String> ret = sqlService.dbRead(sql, fields, null);
      int seconds = threshold;
      if (ret.size() > 0) {
        double d = Double.parseDouble(ret.get(0));
        int intVal = (int) d;
        log.info("Offset from database timestamp is: " + intVal + " seconds (" + d + ")");
        if (intVal > seconds || intVal < (seconds * -1)) {
          log.error("Drift is " + intVal + " exceeding threshold of " + threshold + " seconds");
          String nodeId = serverConfigurationService.getServerId();
          String body = "Server: " + nodeId + " exceeded time drift of " + seconds + " seconds from db with a value of: " + intVal + " seconds";
          emailService.send("help@vula.uct.ac.za", "alerts@vula.uct.ac.za", "Server-DB clock alert", 
              body, null, null, null);
        } else {
          log.debug("in range : " + intVal + " threshold: " + seconds);
        }
      } else {
        log.warn("query returned no result");
      }
    }
    
    
    private void checkNTP() {
      log.debug("checkNTP()");
      NTPUDPClient client = new NTPUDPClient();
      try {
        String ntpHost = serverConfigurationService.getString("ntp", "za.pool.ntp.org");
        InetAddress address = InetAddress.getByName(ntpHost);
        TimeInfo timeInfo = client.getTime(address);
        timeInfo.computeDetails();
        Instant rDate = Instant.ofEpochMilli(timeInfo.getReturnTime());
        String strDate = dateIso(rDate);
        log.info("Offset to " + ntpHost + " is: " + timeInfo.getOffset() + "ms ntp host time is: " + strDate);
        double offset = timeInfo.getOffset().longValue()/1000D;
        if (offset > seconds || offset < (seconds * -1)) {
          log.error("Drift from " + ntpHost + " is: "  + offset + " seconds exceeding threshold of " + threshold + " seconds");
          String nodeId = serverConfigurationService.getServerId();
          String body = "Server: " + nodeId + " exceeded time drift of " + seconds + " seconds with a value of: " + offset + " seconds from: " + ntpHost;
          emailService.send("help@vula.uct.ac.za", "alerts@vula.uct.ac.za", "Server clock alert", 
              body, null, null, null);
        }
      } catch (UnknownHostException e) {
        log.warn(e.getLocalizedMessage());
      } catch (IOException e) {
        log.warn(e.getLocalizedMessage(), e);
      } finally {
        client.close();
      }
    }
  }
}
