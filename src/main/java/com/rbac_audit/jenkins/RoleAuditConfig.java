package com.rbac_audit.jenkins;

import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;
import hudson.Extension;
import hudson.util.FormValidation;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.time.ZoneId;
import java.util.logging.Logger;
import hudson.scheduler.CronTabList;

@Extension
public class RoleAuditConfig extends GlobalConfiguration {
    private static final Logger LOGGER = Logger.getLogger(RoleAuditConfig.class.getName());
    private String rotationCron = "H 0 * * *";
    private boolean uploadToS3 = false;
    private String s3Bucket;
    private String s3BucketPath = "jenkins-logs";
    private String s3Region = "ap-south-1";
    private boolean loggingEnabled = true;
    private static final String JENKINS_HOME = Jenkins.get().getRootDir().getAbsolutePath();
    private String logFilePath = JENKINS_HOME + "/logs/role-changes.log";
    public RoleAuditConfig() {
        load();
    }

    public String getCurrentSystemTimezone() {
        return ZoneId.systemDefault().toString();
    }

    public static RoleAuditConfig get() {
        return GlobalConfiguration.all().get(RoleAuditConfig.class);
    }

    public boolean isLoggingEnabled() {
        return loggingEnabled;
    }

    public String getLogFilePath() {
        return logFilePath;
    }

    public void setLoggingEnabled(boolean loggingEnabled) {
        if(this.loggingEnabled != loggingEnabled)
            LOGGER.info("Setting role based strategy audit logging to: " + loggingEnabled + " by user: " + getUserName());
        this.loggingEnabled = loggingEnabled;
    }

    private String getUserName() {
        Authentication auth = Jenkins.getAuthentication();
        String username = (auth != null) ? auth.getName() : "UNKNOWN";
        return username;
    }
    public void setLogFilePath(String logFilePath) {
        if(!this.logFilePath.equals(logFilePath))
            LOGGER.info("Setting role based strategy audit logs file path to: " + logFilePath + " by user: " + getUserName());
        this.logFilePath = logFilePath;
    }

    public String getRotationCron() {
        return rotationCron;
    }

    public void setRotationCron(String rotationCron) {
        if(!this.rotationCron.equals(rotationCron))
            LOGGER.info("Setting role based strategy audit logs rotation cron to: " + rotationCron + " by user: " + getUserName());
        this.rotationCron = rotationCron;
    }

    public boolean isUploadToS3() {
        return uploadToS3;
    }

    public void setUploadToS3(boolean uploadToS3) {
        if(this.uploadToS3 != uploadToS3)
            LOGGER.info("Setting role based strategy audit logs upload to S3 to: " + uploadToS3 + " by user: " + getUserName());
        this.uploadToS3 = uploadToS3;
    }

    public String getS3Bucket() {
        return s3Bucket;
    }

    public void setS3Bucket(String s3Bucket) {
        if(!this.s3Bucket.equals(s3Bucket))
            LOGGER.info("Setting role based strategy audit logs S3 bucket to: " + s3Bucket + " by user: " + getUserName());
        this.s3Bucket = s3Bucket;
    }

    public String getS3BucketPath() {
        return s3BucketPath;
    }

    public void setS3BucketPath(String s3BucketPath) {
        if(!this.s3BucketPath.equals(s3BucketPath))
            LOGGER.info("Setting role based strategy audit logs S3 bucket path to: " + s3BucketPath + " by user: " + getUserName());
        this.s3BucketPath = s3BucketPath;
    }

    public String getS3Region() {
        return s3Region;
    }

    public void setS3Region(String s3Region) {
        if(!this.s3Region.equals(s3Region))
            LOGGER.info("Setting role based strategy audit logs S3 region to: " + s3Region + " by user: " + getUserName());
        this.s3Region = s3Region;
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        req.bindJSON(this, json);
        save();
        return true;
    }

    public FormValidation doCheckLogFilePath(@QueryParameter String value) {
        File f = new File(value);
        if (!f.getParentFile().canWrite()) {
            return FormValidation.warning("Jenkins might not be able to write to this directory.");
        }
        return FormValidation.ok();
    }
    public FormValidation doCheckRotationCron(@QueryParameter String value){
        if (value == null || value.trim().isEmpty()) {
            return FormValidation.error("Rotation cron expression cannot be empty.");
        }
        try {
            // Validate the cron expression
            CronTabList.create(value);
        } catch (Exception e) {
            return FormValidation.error("Invalid cron expression: " + e.getMessage());
        }
        return FormValidation.ok("Cron expression for log rotation. Current Timezone: " + getCurrentSystemTimezone());
    }
    public FormValidation doCheckS3Bucket(@QueryParameter String value) {
        if (value == null || value.trim().isEmpty()) {
            return FormValidation.warning("If Upload to S3 is true, S3 bucket name cannot be empty.");
        }
        return FormValidation.ok();
    }
    public FormValidation doCheckS3BucketPath(@QueryParameter String value) {
        if (value == null || value.trim().isEmpty()) {
            return FormValidation.warning("If Upload to S3 is true and S3 bucket path is empty, Logs will be uploaded to root of the S3 bucket.");
        }
        return FormValidation.ok();
    }
    public FormValidation doCheckS3Region(@QueryParameter String value) {
        if (value == null || value.trim().isEmpty()) {
            return FormValidation.warning("If Upload to S3 is true, S3 region cannot be empty.");
        }
        return FormValidation.ok();
    }

    public FormValidation doCheckLoggingEnabled(@QueryParameter boolean value) {
        if (value) {
            return FormValidation.ok("Role change logging is enabled.");
        } else {
            return FormValidation.warning("Role change logging is disabled. No logs will be recorded.");
        }
    }

    public FormValidation doCheckUploadToS3(@QueryParameter boolean value) {
        if (value) {
            return FormValidation.ok("Logs will be uploaded to S3 after rotation.");
        } else {
            return FormValidation.warning("Logs will not be uploaded to S3.");
        }
    }
}
