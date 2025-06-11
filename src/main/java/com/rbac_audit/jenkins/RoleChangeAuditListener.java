package com.rbac_audit.jenkins;

import hudson.Extension;
import hudson.init.Initializer;
import hudson.init.InitMilestone;
import hudson.XmlFile;
import hudson.model.Saveable;
import hudson.model.listeners.SaveableListener;
import hudson.security.ACL;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.logging.Logger;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import com.audit_log_rotator.jenkins.AuditLogRotator;

@Extension
public class RoleChangeAuditListener extends SaveableListener {
    private static final Logger LOGGER = Logger.getLogger(RoleChangeAuditListener.class.getName());
    private static final String JENKINS_HOME = Jenkins.get().getRootDir().getAbsolutePath();
    private static final File CACHE_FILE = new File(JENKINS_HOME+"/logs/jenkins-roles-prev.xml");
    private static final File LOG_FILE = new File(RoleAuditConfig.get().getLogFilePath());
    
    @Override
    public void onChange(Saveable saveable, XmlFile file) {
        File configFile = file.getFile();
        if (!RoleAuditConfig.get().isLoggingEnabled()) return;
        
        if (!configFile.getAbsolutePath().contains(JENKINS_HOME+"/config.xml")) {
            return;
        }

        try {
            // If this is the first time, store the baseline
            if (!CACHE_FILE.exists()) {
                Files.copy(configFile.toPath(), CACHE_FILE.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return;
            }

            Map<String, Map<String, RoleInfo>> oldRoles = parseRoles(CACHE_FILE);
            Map<String, Map<String, RoleInfo>> newRoles = parseRoles(configFile);

            Authentication auth = Jenkins.getAuthentication();
            String username = (auth != null) ? auth.getName() : "UNKNOWN";

            List<String> logs = compareRoles(oldRoles, newRoles, username);

            writeLogs(logs);

            // Update cache
            Files.copy(configFile.toPath(), CACHE_FILE.toPath(), StandardCopyOption.REPLACE_EXISTING);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static class RoleInfo {
        Set<String> permissions = new HashSet<>();
        Set<String> sids = new HashSet<>();
        String pattern = "";
    }

    private Map<String, Map<String, RoleInfo>> parseRoles(File xmlFile) throws Exception {
        Map<String, Map<String, RoleInfo>> roleMap = new HashMap<>();

        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document doc = builder.parse(xmlFile);
        NodeList roleMaps = doc.getElementsByTagName("roleMap");

        for (int i = 0; i < roleMaps.getLength(); i++) {
            Element rm = (Element) roleMaps.item(i);
            String type = rm.getAttribute("type");
            Map<String, RoleInfo> roles = new HashMap<>();

            NodeList roleList = rm.getElementsByTagName("role");
            for (int j = 0; j < roleList.getLength(); j++) {
                Element roleElem = (Element) roleList.item(j);
                String roleName = roleElem.getAttribute("name");
                String pattern = roleElem.getAttribute("pattern");
                RoleInfo roleInfo = new RoleInfo();

                
                NodeList permList = roleElem.getElementsByTagName("permission");
                for (int p = 0; p < permList.getLength(); p++) {
                    roleInfo.permissions.add(permList.item(p).getTextContent());
                }

                NodeList sidList = roleElem.getElementsByTagName("sid");
                for (int s = 0; s < sidList.getLength(); s++) {
                    roleInfo.sids.add(sidList.item(s).getTextContent());
                }
                roleInfo.pattern = pattern; 
                roles.put(roleName, roleInfo);
            }
            roleMap.put(type, roles);
        }

        return roleMap;
    }

    private List<String> compareRoles(Map<String, Map<String, RoleInfo>> oldMap, Map<String, Map<String, RoleInfo>> newMap, String username) {
        List<String> logs = new ArrayList<>();
        ZoneId jenkinsZoneId = ZoneId.systemDefault();  // Gets the JVM/Jenkins timezone
        ZonedDateTime now = ZonedDateTime.now(jenkinsZoneId);
        String timestamp = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"));

        for (String roleType : Arrays.asList("globalRoles", "projectRoles")) {
            Map<String, RoleInfo> oldRoles = oldMap.getOrDefault(roleType, new HashMap<>());
            Map<String, RoleInfo> newRoles = newMap.getOrDefault(roleType, new HashMap<>());
            String label = roleType.equals("globalRoles") ? "global" : "project";

            for (String roleName : newRoles.keySet()) {
                if (!oldRoles.containsKey(roleName)) {
                    logs.add("[" + timestamp + "] New " + label + " role created: '" + roleName + "' by '" + username + "'");
                    logs.add("[" + timestamp + "] " + label + " role details: '" + roleName + "' | pattern: '" + newRoles.get(roleName).pattern + "' | permissions: " + newRoles.get(roleName).permissions);
                }
            }
            for (String roleName : oldRoles.keySet()) {
                if (!newRoles.containsKey(roleName)) {
                    logs.add("[" + timestamp + "] " + label + " role deleted: '" + roleName + "' by '" + username + "'");
                }
            }

            for (String roleName : newRoles.keySet()) {
                if (!oldRoles.containsKey(roleName)) continue;

                RoleInfo oldRole = oldRoles.get(roleName);
                RoleInfo newRole = newRoles.get(roleName);

                Set<String> addedPerms = new HashSet<>(newRole.permissions);
                addedPerms.removeAll(oldRole.permissions);
                for (String perm : addedPerms) {
                    logs.add("[" + timestamp + "] Permission " + perm + " added to " + label + " role " + roleName + "' by '" + username + "'");
                }

                Set<String> removedPerms = new HashSet<>(oldRole.permissions);
                removedPerms.removeAll(newRole.permissions);
                for (String perm : removedPerms) {
                    logs.add("[" + timestamp + "] Permission '" + perm + "' removed from " + label + " role '" + roleName + "' by '" + username + "'");
                }

                Set<String> addedSIDs = new HashSet<>(newRole.sids);
                addedSIDs.removeAll(oldRole.sids);
                for (String sid : addedSIDs) {
                    logs.add("[" + timestamp + "] SID '" + sid + "' added to " + label + " role '" + roleName + "' by '" + username + "'");
                }

                Set<String> removedSIDs = new HashSet<>(oldRole.sids);
                removedSIDs.removeAll(newRole.sids);
                for (String sid : removedSIDs) {
                    logs.add("[" + timestamp + "] SID '" + sid + "' removed from " + label + " role '" + roleName + "' by '" + username + "'");
                }

                if (!oldRole.pattern.equals(newRole.pattern)) {
                    logs.add("[" + timestamp + "] Pattern changed for " + label + " role '" + roleName + "' from '" + oldRole.pattern + "' to '" + newRole.pattern + "' by '" + username + "'");
                }
            }
        }

        return logs;
    }

    private void writeLogs(List<String> logs) {
        LOGGER.info("writing logs to: " + LOG_FILE.getAbsolutePath());
        if (logs.isEmpty()) return;
        try (FileWriter writer = new FileWriter(LOG_FILE, true)) {
            for (String line : logs) {
                writer.write(line + "\n");
                LOGGER.info(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static class PluginStartup {
        @Initializer(after = InitMilestone.JOB_LOADED)
        public static void init() {
            LOGGER.info("Starting log rotation threads...");
            LogRotator.start(); 
            AuditLogRotator.start();
        }
    }
}

