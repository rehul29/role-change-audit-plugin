package com.rbac_audit.jenkins;

import hudson.scheduler.CronTabList;
import hudson.util.LogTaskListener;
import jenkins.model.Jenkins;


import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Calendar;
import java.util.Date;
import java.time.Duration;

// AWS SDK
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;

public class LogRotator {
    private static final Logger LOGGER = Logger.getLogger(LogRotator.class.getName());
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    
    public static void start() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                RoleAuditConfig config = RoleAuditConfig.get();
                String cronExpr = config.getRotationCron();
                //LOGGER.info("Cron: " + cronExpr);
                ZoneId jenkinsZoneId = ZoneId.systemDefault();
                CronTabList cronTabList = CronTabList.create(cronExpr);
                ZonedDateTime now = ZonedDateTime.now(jenkinsZoneId);
                Calendar cal = Calendar.getInstance();
                cal.setTime(Date.from(now.toInstant()));
                //LOGGER.info("Cal: "+ cal);
                if (cronTabList.check(cal)) {
                    //LOGGER.info("cal matched");
                    rotateLog(jenkinsZoneId);
                }
            } catch (Exception e) {
                LOGGER.warning("Log rotation failed: " + e.getMessage());
            }
        }, 0, 1, TimeUnit.MINUTES);
    }

    private static void rotateLog(ZoneId zoneId) throws IOException {
        RoleAuditConfig config = RoleAuditConfig.get();
        File logFile = new File(config.getLogFilePath());

        if (!logFile.exists()){
            LOGGER.info("Logfile not created. Skipping rotation.");
            return;
        }

        String timestamp = ZonedDateTime.now(zoneId).format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String originalName = logFile.getName();
        int dotIndex = originalName.lastIndexOf('.');
        String baseName = (dotIndex == -1) ? originalName : originalName.substring(0, dotIndex);
        String extension = (dotIndex == -1) ? "" : originalName.substring(dotIndex);
        File archive = new File(logFile.getParent(), baseName + "-" + timestamp + extension);

        Files.move(logFile.toPath(), archive.toPath(), StandardCopyOption.REPLACE_EXISTING);
        LOGGER.info("Log rotated: " + archive.getName());

        if (config.isUploadToS3()) {
            LOGGER.info("Uploading rotated log to S3: " + archive.getName() + " in bucket " + config.getS3Bucket() + "/" + config.getS3BucketPath());
            uploadToS3(archive, config);
        }
    }

    private static void uploadToS3(File file, RoleAuditConfig config) {
        try {
            // LOGGER.info("connecting to aws");
            AmazonS3 s3 = AmazonS3ClientBuilder.standard()
                .withRegion(config.getS3Region())
                .build();
            // LOGGER.info("Connected to aws");
            s3.putObject(config.getS3Bucket(), config.getS3BucketPath() + "/" + file.getName(), file);
            LOGGER.info("Uploaded rotated log " + file.getName() + " to S3 bucket " + config.getS3Bucket() + "/" + config.getS3BucketPath());
        } catch (Exception e) {
            LOGGER.warning("Failed to upload to S3: " + e.getMessage());
        }
    }
}
