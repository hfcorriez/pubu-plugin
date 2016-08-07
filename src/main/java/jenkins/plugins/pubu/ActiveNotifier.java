package jenkins.plugins.pubu;

import hudson.Util;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Hudson;
import hudson.model.Run;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.AffectedFile;
import hudson.scm.ChangeLogSet.Entry;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.triggers.SCMTrigger;
import org.apache.commons.lang.StringUtils;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import org.json.JSONObject;
import org.json.JSONArray;

@SuppressWarnings("rawtypes")
public class ActiveNotifier implements FineGrainedNotifier {

    private static final Logger logger = Logger.getLogger(SlackListener.class.getName());

    SlackNotifier notifier;

    public ActiveNotifier(SlackNotifier notifier) {
        super();
        this.notifier = notifier;
    }

    private SlackService getLeanChat(AbstractBuild r) {
        AbstractProject<?, ?> project = r.getProject();
        String teamDomain = Util.fixEmpty(project.getProperty(SlackNotifier.SlackJobProperty.class).getTeamDomain());
        return notifier.newSlackService(teamDomain);
    }

    public void deleted(AbstractBuild r) {
    }

    public void started(AbstractBuild build) {
        CauseAction causeAction = build.getAction(CauseAction.class);

        if (causeAction != null) {
            Cause scmCause = causeAction.findCause(SCMTrigger.SCMTriggerCause.class);
            if (scmCause == null) {
                JSONObject payload = new JSONObject();
                payload.put("project", build.getProject().getFullDisplayName());
                payload.put("display", build.getDisplayName());
                payload.put("link", build.getUrl());
                payload.put("event", "Cause");
                payload.put("reason", causeAction.getShortDescription());
                notifyStart(build, payload);
            }
        }

        JSONObject changes = getChanges(build);
        if (changes != null) {
            notifyStart(build, changes);
        } else {
            notifyStart(build, getBuildStatusPayload(build, false, "start"));
        }
    }

    private void notifyStart(AbstractBuild build, JSONObject payload) {
        AbstractProject<?, ?> project = build.getProject();
        AbstractBuild<?, ?> previousBuild = project.getLastBuild().getPreviousCompletedBuild();
        if (previousBuild == null) {
            getLeanChat(build).publish(payload);
        } else {
            payload.put("status", getBuildStatus(previousBuild));
            getLeanChat(build).publish(payload);
        }
    }

    public void finalized(AbstractBuild r) {
    }

    public void completed(AbstractBuild build) {
        AbstractProject<?, ?> project = build.getProject();
        SlackNotifier.SlackJobProperty jobProperty = project.getProperty(SlackNotifier.SlackJobProperty.class);
        if (jobProperty == null) {
            logger.warning("Project " + project.getName() + " has no Pubu configuration.");
            return;
        }
        Result result = build.getResult();
        AbstractBuild<?, ?> previousBuild = project.getLastBuild();
        do {
            previousBuild = previousBuild.getPreviousCompletedBuild();
        } while (previousBuild != null && previousBuild.getResult() == Result.ABORTED);
        Result previousResult = (previousBuild != null) ? previousBuild.getResult() : Result.SUCCESS;
        if ((result == Result.ABORTED && jobProperty.getNotifyAborted())
                || (result == Result.FAILURE
                && (previousResult != Result.FAILURE || jobProperty.getNotifyRepeatedFailure())
                && jobProperty.getNotifyFailure())
                || (result == Result.NOT_BUILT && jobProperty.getNotifyNotBuilt())
                || (result == Result.SUCCESS
                && (previousResult == Result.FAILURE || previousResult == Result.UNSTABLE)
                && jobProperty.getNotifyBackToNormal())
                || (result == Result.SUCCESS && jobProperty.getNotifySuccess())
                || (result == Result.UNSTABLE && jobProperty.getNotifyUnstable())) {
            getLeanChat(build).publish(getBuildStatusPayload(build, jobProperty.includeTestSummary(), "completed"));
            if (jobProperty.getShowCommitList()) {
                getLeanChat(build).publish(getCommitList(build));
            }
        }
    }

    JSONObject getChanges(AbstractBuild build) {
        if (!build.hasChangeSetComputed()) {
            logger.info("No change set computed...");
            return null;
        }
        ChangeLogSet changeSet = build.getChangeSet();
        List<Entry> entries = new LinkedList<Entry>();
        Set<AffectedFile> files = new HashSet<AffectedFile>();
        for (Object o : changeSet.getItems()) {
            Entry entry = (Entry) o;
            logger.info("Entry " + o);
            entries.add(entry);
            files.addAll(entry.getAffectedFiles());
        }
        if (entries.isEmpty()) {
            logger.info("Empty change...");
            return null;
        }
        JSONObject payload = new JSONObject();
        Set<String> authors = new HashSet<String>();
        for (Entry entry : entries) {
            authors.add(entry.getAuthor().getDisplayName());
        }

        payload.put("authors", StringUtils.join(authors, ", "));
        payload.put("changes", files.size());
        payload.put("project", build.getProject().getFullDisplayName());
        payload.put("display", build.getDisplayName());
        payload.put("link", build.getUrl());
        payload.put("event", "start");
        return payload;
    }

    JSONObject getCommitList(AbstractBuild build) {
        ChangeLogSet changeSet = build.getChangeSet();
        JSONObject payload = getChanges(build);

        List<Entry> entries = new LinkedList<Entry>();
        for (Object o : changeSet.getItems()) {
            Entry entry = (Entry) o;
            logger.info("Entry " + o);
            entries.add(entry);
        }
        if (entries.isEmpty()) {
            logger.info("Empty change...");
            Cause.UpstreamCause c = (Cause.UpstreamCause) build.getCause(Cause.UpstreamCause.class);
            if (c == null) {
                return payload;
            }
            String upProjectName = c.getUpstreamProject();
            int buildNumber = c.getUpstreamBuild();
            AbstractProject project = Hudson.getInstance().getItemByFullName(upProjectName, AbstractProject.class);
            AbstractBuild upBuild = (AbstractBuild) project.getBuildByNumber(buildNumber);
            return getCommitList(upBuild);
        }

        JSONArray commits = new JSONArray();

        for (Entry entry : entries) {
            JSONObject commit = new JSONObject();
            commit.put("entry", entry.getMsg());
            commit.put("commit", commit.toString());
            commit.put("author", entry.getAuthor().getDisplayName());
            commits.put(commit);
        }
        payload.put("commits", commits);
        return payload;
    }

    static String getBuildStatus(AbstractBuild r) {
        Result result = r.getResult();
        if (result == Result.SUCCESS) {
            return "Success";
        } else if (result == Result.FAILURE) {
            return "Failure";
        } else if (result == Result.ABORTED) {
            return "Aborted";
        } else if (result == Result.NOT_BUILT) {
            return "NoBuild";
        } else if (result == Result.UNSTABLE) {
            return "Unstable";
        }
        return "Unknown";
    }

    JSONObject getBuildStatusPayload(AbstractBuild build, boolean includeTestSummary, String event) {
        JSONObject payload = new JSONObject();
        payload.put("status", getBuildStatus(build));
        payload.put("duration", build.getDurationString());
        payload.put("project", build.getProject().getFullDisplayName());
        payload.put("display", build.getDisplayName());
        payload.put("link", build.getUrl());
        payload.put("event", event);

        if (!includeTestSummary) {
            return payload;
        }

        AbstractTestResultAction<?> action = build.getAction(AbstractTestResultAction.class);
        if (action != null) {
            int total = action.getTotalCount();
            int failed = action.getFailCount();
            int skipped = action.getSkipCount();
            payload.put("Passed", total - failed - skipped);
            payload.put("Failed", failed);
            payload.put("Skipped", skipped);

        }
        return payload;
    }
}
