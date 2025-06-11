package com.audit_log_rotator.jenkins;

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
public class AuditLogRotatorConfig extends GlobalConfiguration {
    private static final Logger LOGGER = Logger.getLogger(AuditLogRotatorConfig.class.getName());
    private String LRRotationCron = "H 0 * * *";
    private boolean LRUploadToS3 = false;
    private String LRS3Bucket;
    private String LRS3BucketPath = "jenkins-audit-logs";
    private String LRS3Region = "ap-south-1";
    private boolean logRotationEnabled = true;
    private static final String JENKINS_HOME = Jenkins.get().getRootDir().getAbsolutePath();
    private String LRLogFilePath = JENKINS_HOME + "/logs/audit-1.log";
    public AuditLogRotatorConfig() {
        load();
    }

    public String getCurrentSystemTimezone() {
        return ZoneId.systemDefault().toString();
    }

    public static AuditLogRotatorConfig get() {
        return GlobalConfiguration.all().get(AuditLogRotatorConfig.class);
    }

    public boolean islogRotationEnabled() {
        return logRotationEnabled;
    }

    public String getLRLogFilePath() {
        return LRLogFilePath;
    }

    public void setlogRotationEnabled(boolean logRotationEnabled) {
        if(this.logRotationEnabled != logRotationEnabled)
            LOGGER.info("Setting audit log rotation enabled to: " + logRotationEnabled + " by user: " + getUserName());
        this.logRotationEnabled = logRotationEnabled;
    }

    private String getUserName() {
        Authentication auth = Jenkins.getAuthentication();
        String username = (auth != null) ? auth.getName() : "UNKNOWN";
        return username;
    }
    public void setLRLogFilePath(String LRLogFilePath) {
        if(!this.LRLogFilePath.equals(LRLogFilePath))
            LOGGER.info("Setting audit log rotator's log file path to: " + LRLogFilePath + " by user: " + getUserName());
        this.LRLogFilePath = LRLogFilePath;
    }

    public String getLRRotationCron() {
        return LRRotationCron;
    }

    public void setLRRotationCron(String LRRotationCron) {
        if(!this.LRRotationCron.equals(LRRotationCron))
            LOGGER.info("Setting audit log rotator's rotation cron to: " + LRRotationCron + " by user: " + getUserName());
        this.LRRotationCron = LRRotationCron;
    }

    public boolean isLRUploadToS3() {
        return LRUploadToS3;
    }

    public void setLRUploadToS3(boolean LRUploadToS3) {
        if(this.LRUploadToS3 != LRUploadToS3)
            LOGGER.info("Setting audit log rotator's upload to S3 to: " + LRUploadToS3 + " by user: " + getUserName());
        this.LRUploadToS3 = LRUploadToS3;
    }

    public String getLRS3Bucket() {
        return LRS3Bucket;
    }

    public void setLRS3Bucket(String LRS3Bucket) {
        if(!this.LRS3Bucket.equals(LRS3Bucket))
            LOGGER.info("Setting audit log rotator's S3 bucket to: " + LRS3Bucket + " by user: " + getUserName());
        this.LRS3Bucket = LRS3Bucket;
    }

    public String getLRS3BucketPath() {
        return LRS3BucketPath;
    }

    public void setLRS3BucketPath(String LRS3BucketPath) {
        if(!this.LRS3BucketPath.equals(LRS3BucketPath))
            LOGGER.info("Setting audit log rotator's S3 bucket path to: " + LRS3BucketPath + " by user: " + getUserName());
        this.LRS3BucketPath = LRS3BucketPath;
    }

    public String getLRS3Region() {
        return LRS3Region;
    }

    public void setLRS3Region(String LRS3Region) {
        if(!this.LRS3Region.equals(LRS3Region))
            LOGGER.info("Setting audit log rotator's S3 region to: " + LRS3Region + " by user: " + getUserName());
        this.LRS3Region = LRS3Region;
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        req.bindJSON(this, json);
        save();
        return true;
    }

        public FormValidation doCheckLRLogFilePath(@QueryParameter String value) {
        File f = new File(value);
        if (!f.getParentFile().canWrite()) {
            return FormValidation.warning("Jenkins might not be able to write to this directory.");
        }
        return FormValidation.ok();
    }
    public FormValidation doCheckLRRotationCron(@QueryParameter String value){
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
    public FormValidation doCheckLRS3Bucket(@QueryParameter String value) {
        if (value == null || value.trim().isEmpty()) {
            return FormValidation.warning("If Upload to S3 is true, S3 bucket name cannot be empty.");
        }
        return FormValidation.ok();
    }
    public FormValidation doCheckLRS3BucketPath(@QueryParameter String value) {
        if (value == null || value.trim().isEmpty()) {
            return FormValidation.warning("If Upload to S3 is true and S3 bucket path is empty, Logs will be uploaded to root of the S3 bucket.");
        }
        return FormValidation.ok();
    }
    public FormValidation doCheckLRS3Region(@QueryParameter String value) {
        if (value == null || value.trim().isEmpty()) {
            return FormValidation.warning("If Upload to S3 is true, S3 region cannot be empty.");
        }
        return FormValidation.ok();
    }

    public FormValidation doCheckLogRotationEnabled(@QueryParameter boolean value) {
        if (value) {
            return FormValidation.ok("Log Rotation Enabled.");
        } else {
            return FormValidation.warning("Log Rotation is disabled.");
        }
    }

    public FormValidation doCheckLRUploadToS3(@QueryParameter boolean value) {
        if (value) {
            return FormValidation.ok("Logs will be uploaded to S3 after rotation.");
        } else {
            return FormValidation.warning("Logs will not be uploaded to S3.");
        }
    }
}
