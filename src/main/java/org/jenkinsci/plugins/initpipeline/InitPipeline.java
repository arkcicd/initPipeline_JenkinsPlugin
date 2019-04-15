package org.jenkinsci.plugins.initpipeline;

/**
 * initPipeline developed to extend git-plugin and handle the notifyCommit?url=URL commithook
 * when the repo string passed is NOT known in Jenkins jobs and doesn't match any existing job
 * This plugin catches the same /git/notifyCommit?url=URL commithook as git-plugin by
 * extending the Listener in hudson.plugins.git.GitStatus.Listener, running parallel coding and
 * then catching and handling the case of unknown repos and creating the job in Jenkins
 * This done by calling out to an external executable to
 * configure and instantiate a pipeline build and then kick it off for a first run
 */

import hudson.Extension;
import hudson.Util;
import hudson.model.*;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.impl.IgnoreNotifyCommit;
import hudson.scm.SCM;
import hudson.security.ACL;
import hudson.triggers.SCMTrigger;
import jdk.nashorn.internal.runtime.options.LoggingOption;
import jenkins.model.Jenkins;
import jenkins.triggers.SCMTriggerItem;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.kohsuke.stapler.*;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.apache.commons.lang.StringUtils.isNotEmpty;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;

// jgit
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.revwalk.*;

// jsch
import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/**
 * This page was originally gitplugin/gitstatus.java in the git-plugin code
 * initPipeline extends the original git plugin listener and handles the
 * !urlFound and !scmFound cases
 */

@Extension
@SuppressWarnings("unused") // Jenkins extension

// Extend the git-plugin Listener and catch the /git/notifyCommit?url=URL in parallel
public class InitPipeline extends hudson.plugins.git.GitStatus.Listener {

    public String getDisplayName() {
        return "Git";
    }

    public String getSearchUrl() {
        return getUrlName();
    }

    public String getIconFileName() {
        // TODO
        return null;
    }

    public String getUrlName() {
        return "git";
    }

    private String lastURL = "";        // Required query parameter

    private static String urlPassed = "";
        // clearly capture and use url string sent to class outside of
        // doNotifyCommit method

    private String debugEnabled = "false";
        // enables process-tracking messages to jenkins.log

    private String lastBranches = null; // Optional query parameter
    private String lastSHA1 = null;     // Optional query parameter
    private List<ParameterValue> lastBuildParameters = null;
    private static List<ParameterValue> lastStaticBuildParameters = null;

    // remove *** when no longer used
    private String haveEnvVars = null;

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();

        s.append("URL: ");
        s.append(lastURL);

        if (lastSHA1 != null) {
            s.append(" SHA1: ");
            s.append(lastSHA1);
        }

        if (lastBranches != null) {
            s.append(" Branches: ");
            s.append(lastBranches);
        }

        if (lastBuildParameters != null && !lastBuildParameters.isEmpty()) {
            s.append(" Parameters: ");
            for (ParameterValue buildParameter : lastBuildParameters) {
                s.append(buildParameter.getName());
                s.append("='");
                s.append(buildParameter.getValue());
                s.append("',");
            }
            s.delete(s.length() - 1, s.length());
        }

        if (lastStaticBuildParameters != null && !lastStaticBuildParameters.isEmpty()) {
            s.append(" More parameters: ");
            for (ParameterValue buildParameter : lastStaticBuildParameters) {
                s.append(buildParameter.getName());
                s.append("='");
                s.append(buildParameter.getValue());
                s.append("',");
            }
            s.delete(s.length() - 1, s.length());
        }
        return s.toString();
    }

    public HttpResponse doNotifyCommit(HttpServletRequest request, @QueryParameter(required=true) String url,
                                       @QueryParameter(required=false) String branches,
                                       @QueryParameter(required=false) String sha1) throws ServletException, IOException {
        lastURL = url;
        urlPassed = url; // isolate url string passed
        lastBranches = branches;
        lastSHA1 = sha1;
        lastBuildParameters = null;
        lastStaticBuildParameters = null;
        URIish uri;
        List<ParameterValue> buildParameters = new ArrayList<ParameterValue>();

        try {
            uri = new URIish(url);
        } catch (URISyntaxException e) {
            return HttpResponses.error(SC_BAD_REQUEST, new Exception("Illegal URL: " + url, e));
        }
        String capture_me = "";
        final Map<String, String[]> parameterMap = request.getParameterMap();
        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
            if (!(entry.getKey().equals("url")))
                capture_me = entry.getKey();
            if (!(entry.getKey().equals("url")) && !(entry.getKey().equals("branches")) && !(entry.getKey().equals("sha1")))
                if (entry.getValue()[0] != null)
                    buildParameters.add(new StringParameterValue(entry.getKey(), entry.getValue()[0]));
        }
        lastBuildParameters = buildParameters;
        branches = Util.fixEmptyAndTrim(branches);

        String[] branchesArray;
        if (branches == null) {
            branchesArray = new String[0];
        } else {
            branchesArray = branches.split(",");
        }

        final List<hudson.plugins.git.GitStatus.ResponseContributor> contributors = new ArrayList<hudson.plugins.git.GitStatus.ResponseContributor>();
        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins == null) {
            return HttpResponses.error(SC_BAD_REQUEST, new Exception("Jenkins.getInstance() null for : " + url));
        }
        for (hudson.plugins.git.GitStatus.Listener listener : jenkins.getExtensionList(hudson.plugins.git.GitStatus.Listener.class)) {
            contributors.addAll(listener.onNotifyCommit(uri, sha1, buildParameters, branchesArray));
        }

        return new HttpResponse() {
            public void generateResponse(StaplerRequest req, StaplerResponse rsp, Object node)
                    throws IOException, ServletException {
                rsp.setStatus(SC_OK);
                rsp.setContentType("text/plain");
                for (hudson.plugins.git.GitStatus.ResponseContributor c : contributors) {
                    c.addHeaders(req, rsp);
                }
                PrintWriter w = rsp.getWriter();
                for (hudson.plugins.git.GitStatus.ResponseContributor c : contributors) {
                    c.writeBody(req, rsp, w);
                }
            }
        };
    }

    public static boolean looselyMatches(URIish lhs, URIish rhs) {
        return StringUtils.equals(lhs.getHost(),rhs.getHost())
                && StringUtils.equals(normalizePath(lhs.getPath()), normalizePath(rhs.getPath()));
    }

    private static String normalizePath(String path) {
        if (path.startsWith("/"))   path=path.substring(1);
        if (path.endsWith("/"))     path=path.substring(0,path.length()-1);
        if (path.endsWith(".git"))  path=path.substring(0,path.length()-4);
        return path;
    }

    @Override

    public List<hudson.plugins.git.GitStatus.ResponseContributor> onNotifyCommit(URIish uri, String sha1, List<ParameterValue> buildParameters, String... branches) {

        /** 
         * Untouched code...
        */
         
        // log
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Received notification for uri = " + uri + " ; sha1 = " + sha1 + " ; branches = " + Arrays.toString(branches));
        }

        lastStaticBuildParameters = null;
        List<ParameterValue> allBuildParameters = new ArrayList<ParameterValue>(buildParameters);
        List<hudson.plugins.git.GitStatus.ResponseContributor> result = new ArrayList<hudson.plugins.git.GitStatus.ResponseContributor>();
        // run in high privilege to see all the projects anonymous users don't see.
        // this is safe because when we actually schedule a build, it's a build that can
        // happen at some random time anyway.
        SecurityContext old = ACL.impersonate(ACL.SYSTEM);
        try {

            boolean scmFound = false,
                    urlFound = false;
            Jenkins jenkins = Jenkins.getInstance();
            if (jenkins == null) {
                LOGGER.severe("Jenkins.getInstance() is null in CicdDiscover.onNotifyCommit");
                return result;
            }
            for (final Item project : Jenkins.getInstance().getAllItems()) {
                SCMTriggerItem scmTriggerItem = SCMTriggerItem.SCMTriggerItems.asSCMTriggerItem(project);
                if (scmTriggerItem == null) {
                    continue;
                }
                SCMS:
                for (SCM scm : scmTriggerItem.getSCMs()) {
                    if (!(scm instanceof GitSCM)) {
                        continue;
                    }
                    GitSCM git = (GitSCM) scm;
                    // here's the first "exists" case
                    scmFound = true;

                    for (RemoteConfig repository : git.getRepositories()) {
                        boolean repositoryMatches = false,
                                branchMatches = false;
                        URIish matchedURL = null;
                        for (URIish remoteURL : repository.getURIs()) {
                            if (looselyMatches(uri, remoteURL)) {
                                repositoryMatches = true;
                                matchedURL = remoteURL;
                                break;
                            }
                        }

                        if (!repositoryMatches || git.getExtensions().get(IgnoreNotifyCommit.class) != null) {
                            continue;
                        }

                        SCMTrigger trigger = scmTriggerItem.getSCMTrigger();
                        if (trigger == null || trigger.isIgnorePostCommitHooks()) {
                            LOGGER.info("no trigger, or post-commit hooks disabled, on " + project.getFullDisplayName());
                            continue;
                        }

                        boolean branchFound = false,
                                parametrizedBranchSpec = false;
                        if (branches.length == 0) {
                            branchFound = true;
                        } else {
                            OUT:
                            for (BranchSpec branchSpec : git.getBranches()) {
                                if (branchSpec.getName().contains("$")) {
                                    // If the branchspec is parametrized, always run the polling
                                    if (LOGGER.isLoggable(Level.FINE)) {
                                        LOGGER.fine("Branch Spec is parametrized for " + project.getFullDisplayName() + ". ");
                                    }
                                    branchFound = true;
                                    parametrizedBranchSpec = true;
                                } else {
                                    for (String branch : branches) {
                                        if (branchSpec.matches(repository.getName() + "/" + branch)) {
                                            if (LOGGER.isLoggable(Level.FINE)) {
                                                LOGGER.fine("Branch Spec " + branchSpec + " matches modified branch "
                                                        + branch + " for " + project.getFullDisplayName() + ". ");
                                            }
                                            branchFound = true;
                                            break OUT;
                                        }
                                    }
                                }
                            }
                        }
                        if (!branchFound) continue;
                        urlFound = true;
                        if (!(project instanceof AbstractProject && ((AbstractProject) project).isDisabled())) {
                            //JENKINS-30178 Add default parameters defined in the job
                            if (project instanceof Job) {
                                Set<String> buildParametersNames = new HashSet<String>();
                                for (ParameterValue parameterValue : allBuildParameters) {
                                    buildParametersNames.add(parameterValue.getName());
                                }

                                List<ParameterValue> jobParametersValues = getDefaultParametersValues((Job) project);
                                for (ParameterValue defaultParameterValue : jobParametersValues) {
                                    if (!buildParametersNames.contains(defaultParameterValue.getName())) {
                                        allBuildParameters.add(defaultParameterValue);
                                    }
                                }
                            }
                            if (!parametrizedBranchSpec && isNotEmpty(sha1)) {
                                    /* If SHA1 and not a parameterized branch spec, then schedule build.
                                     * NOTE: This is SCHEDULING THE BUILD, not triggering polling of the repo.
                                     * If no SHA1 or the branch spec is parameterized, it will only poll.
                                     */
                                LOGGER.info("Scheduling " + project.getFullDisplayName() + " to build commit " + sha1);

                                /**
                                 * Dropping the job scheduling section
                                 * Cases where the job exists should log but not act for InitPipeline...
                                 */

                                /*
                                scmTriggerItem.scheduleBuild2(scmTriggerItem.getQuietPeriod(),
                                        new CauseAction(new CommitHookCause(sha1)),
                                        new RevisionParameterAction(sha1, matchedURL), new ParametersAction(allBuildParameters));
                                result.add(new ScheduledResponseContributor(project));
                                */

                            } else {

                                    /* Poll the repository for changes
                                     * NOTE: This is not scheduling the build, just polling for changes
                                     * If the polling detects changes, it will schedule the build
                                     */
                                LOGGER.info(" CICD Discover:: NOT Triggering the polling of " + project.getFullDisplayName());


                                 /**
                                 * Dropping the job scheduling section
                                 * Cases where the job exists should log but not act for InitPipeline...
                                 */

                                /*
                                trigger.run();
                                result.add(new PollingScheduledResponseContributor(project));
                                */

                                break SCMS; // no need to trigger the same project twice, so do not consider other GitSCMs in it
                            }
                        }
                        break;
                    }

                }
            }

            /**
             * END Gitstatus.java mostly unmodified
             * NEW CODE starts...
             */

            if (!scmFound) {
                result.add(new MessageResponseContributor("initPipeline:: repository: " +  uri.toString() + " - START"));
                urlPassed = uri.toString();
                // call prelims
                LOGGER.log(Level.INFO, "initPipeline:: onNotifyCommit !scmFound: - START init Pipeline...");

                /**
                 * ...am I in debug mode or no?
                 */
                checkDebug();  // if /var/lib/jenkins/plugins/.cicd-discover.debug exists, enable debug
                // sets global haveEnvVars if completely successful from getEnvVars()
                if (debugEnabled == "true") {
                    result.add(new MessageResponseContributor("initPipeline:: " +  uri.toString() + ": debug verbose logging enabled to Jenkins controller"));
                } else {
                    result.add(new MessageResponseContributor("initPipeline:: " +  uri.toString() + ": terse logging, no debug"));
                }

                /**
                 * drop out of java to /var/lib/jenkins/scripts/initPipeline executable...
                 */
                instantiatePipeline();

            } else if (!urlFound) {
                result.add(new MessageResponseContributor("initPipeline:: repository: " +  uri.toString() + " - START"));
                urlPassed = uri.toString();
                // call prelims
                LOGGER.log(Level.INFO, "initPipeline:: onNotifyCommit !urlFound:  - START Cicd Discover...");

                /**
                 *  ...am I in debug mode or no?
                 */
                checkDebug();  // if /var/lib/jenkins/plugins/.cicd-discover.debug exists, enable debug
                // sets global haveEnvVars if completely successful
                if (debugEnabled == "true") {
                    result.add(new MessageResponseContributor("initPipeline:: " +  uri.toString() + ": debug verbose logging enabled to Jenkins controller"));
                } else {
                    result.add(new MessageResponseContributor("initPipeline:: " +  uri.toString() + ": terse logging, no debug"));
                }

                /**
                 * drop out of java to /var/lib/jenkins/scripts/initPipeline executable...
                 */
                instantiatePipeline();

            }
            LOGGER.log(Level.INFO, "initPipeline:: onNotifyCommit COMPLETE init pipeline execution");
            result.add(new MessageResponseContributor("initPipeline:: repository: " +  uri.toString() + " - COMPLETED"));
            lastStaticBuildParameters = allBuildParameters;
            return result;
        } finally {
            SecurityContextHolder.setContext(old);
        }
    }

    /**
     * START of Gitstatus.java legacy methods    
     * This code  from lines 452 through to 590 (or so...)
     * is directly from Gitstatus.java, unaltered
     */

    public static class CommitHookCause extends Cause {

        public final String sha1;

        public CommitHookCause(String sha1) {
            this.sha1 = sha1;
        }

        @Override
        public String getShortDescription() {
            return "commit notification " + sha1;
        }
    }

    public static class MessageResponseContributor extends hudson.plugins.git.GitStatus.ResponseContributor {
        /**
         * The message.
         */
        private final String msg;

        /**
         * Constructor.
         *
         * @param msg the message.
         */
        public MessageResponseContributor(String msg) {
            this.msg = msg;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void writeBody(PrintWriter w) {
            w.println(msg);
        }
    }

    private static class ScheduledResponseContributor extends hudson.plugins.git.GitStatus.ResponseContributor {
        /**
         * The project
         */
        private final Item project;

        /**
         * Constructor.
         *
         * @param project the project.
         */
        public ScheduledResponseContributor(Item project) {
            this.project = project;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void addHeaders(StaplerRequest req, StaplerResponse rsp) {
            rsp.addHeader("Triggered", project.getAbsoluteUrl());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void writeBody(PrintWriter w) {
            w.println("Scheduled " + project.getFullDisplayName());
        }
    }

    private static class PollingScheduledResponseContributor extends hudson.plugins.git.GitStatus.ResponseContributor {
        /**
         * The project
         */
        private final Item project;

        /**
         * Constructor.
         *
         * @param project the project.
         */
        public PollingScheduledResponseContributor(Item project) {
            this.project = project;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void addHeaders(StaplerRequest req, StaplerResponse rsp) {
            rsp.addHeader("Triggered", project.getAbsoluteUrl());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void writeBody(PrintWriter w) {
            w.println("Scheduled polling of " + project.getFullDisplayName());
        }
    }

    private static final Logger LOGGER = Logger.getLogger(InitPipeline.class.getName());

    private ArrayList<ParameterValue> getDefaultParametersValues(Job<?,?> job) {
        ArrayList<ParameterValue> defValues;
        ParametersDefinitionProperty paramDefProp = job.getProperty(ParametersDefinitionProperty.class);

        if (paramDefProp != null) {
            List <ParameterDefinition> parameterDefinition = paramDefProp.getParameterDefinitions();
            defValues = new ArrayList<ParameterValue>(parameterDefinition.size());

        } else {
            defValues = new ArrayList<ParameterValue>();
            return defValues;
        }

            /* Scan for all parameter with an associated default values */
        for (ParameterDefinition paramDefinition : paramDefProp.getParameterDefinitions()) {
            ParameterValue defaultValue  = paramDefinition.getDefaultParameterValue();

            if (defaultValue != null) {
                defValues.add(defaultValue);
            }
        }

        return defValues;
    }

    /**
     * END GitStaatus.java original code
     */

    /**
     * NEW CODE
     * InstantiatePipeline()
     */

    private void instantiatePipeline() {
        
        // Using jenkinsHome from /etc/sysconfig/jenkins, explicitly exporting JENKINS_HOME
        String jenkinsHome = System.getenv("JENKINS_HOME");
        // arg is urlPassed, call is to initpipeline which does the heavy lifting
        ProcessBuilder newpipe = new ProcessBuilder(jenkinsHome + "/scripts/" + "initpipeline", urlPassed);
        Process process;
        InputStream is;
        try {
            process = newpipe.start();
            is  = process.getInputStream();
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader isbr = new BufferedReader(isr);
            String idline;
            // from this we should retrieve output
            try {
                while ((idline = isbr.readLine()) != null) {
                    if (debugEnabled == "true") {
                        LOGGER.log(Level.INFO, "initPipeline:: instantiatePipeline: idline: " + idline);
                    }
                }
            } catch (IOException e) {
                    e.printStackTrace();
            }
            try {
                process.waitFor();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (debugEnabled == "true") {
            LOGGER.log(Level.INFO, "InitPipeline:: instanciatePipeline:  completed initpipeline call");
        }

    }

    /**
     * checks for /var/lib/jenkins/plugins/.cicd-discover.debug file present
     * if present, enables debug flag, which sends added logging to jenkins.log
     */
    private void checkDebug() {
        // set up file name
        String jen_home = System.getenv("JENKINS_HOME");
        String debugFile = jen_home + "/plugins/.cicd-discover.debug";
        // verify the file
        File f = new File(debugFile);
        if (f.exists()) {
            LOGGER.log(Level.INFO, "initPipeline:: checkDebug: found file at " + debugFile + " ...enabling debug messages");
            debugEnabled = "true";
        } else {
            debugEnabled = "false";
        }
    }

}
