package edu.hm.hafner.grading.github;

import java.io.IOException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.kohsuke.github.GHCheckRun;
import org.kohsuke.github.GHCheckRun.AnnotationLevel;
import org.kohsuke.github.GHCheckRun.Conclusion;
import org.kohsuke.github.GHCheckRun.Status;
import org.kohsuke.github.GHCheckRunBuilder;
import org.kohsuke.github.GHCheckRunBuilder.Annotation;
import org.kohsuke.github.GHCheckRunBuilder.Output;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

import edu.hm.hafner.analysis.FileNameResolver;
import edu.hm.hafner.analysis.Issue;
import edu.hm.hafner.analysis.IssueBuilder;
import edu.hm.hafner.analysis.Report;

/**
 * Writes a comment in a pull request.
 *
 * @author Tobias Effner
 * @author Ullrich Hafner
 */
public class GitHubPullRequestWriter {
    /**
     * Writes the specified comment as GitHub checks result. Requires that the environment variables {@code HEAD_SHA},
     * {@code GITHUB_SHA}, {@code GITHUB_REPOSITORY}, and {@code TOKEN} are correctly set.
     *
     * @param header
     *         the header of the check
     * @param summary
     *         the summary of the check
     * @param comment
     *         the details of the check (supports Markdown)
     * @param testReports
     *         JUnit test reports
     * @param analysisReports
     *         the static analysis reports
     */
    @SuppressWarnings("deprecation")
    public void addComment(final String header, final String summary, final String comment,
            final List<Report> testReports, final List<Report> analysisReports) {
        String repository = System.getenv("GITHUB_REPOSITORY");
        if (repository == null) {
            System.out.println("No GITHUB_REPOSITORY defined - skipping");

            return;
        }
        String oAuthToken = System.getenv("TOKEN");
        if (oAuthToken == null) {
            System.out.println("No valid TOKEN found - skipping");
        }

        String sha = System.getenv("GITHUB_SHA");
        System.out.println(">>>> GITHUB_SHA: " + sha);

        String prSha = System.getenv("HEAD_SHA");
        System.out.println(">>>> HEAD_SHA: " + prSha);

        String actualSha = StringUtils.defaultIfBlank(prSha, sha);
        System.out.println(">>>> ACTUAL_SHA: " + actualSha);

        String workspace = System.getenv("GITHUB_WORKSPACE");
        System.out.println(">>>> GITHUB_WORKSPACE: " + workspace);

        String filesPrefix = StringUtils.defaultString(System.getenv("FILES_PREFIX"));
        System.out.println(">>>> FILES_PREFIX: " + filesPrefix);

        try {
            GitHub github = new GitHubBuilder().withAppInstallationToken(oAuthToken).build();
            GHCheckRunBuilder check = github.getRepository(repository)
                    .createCheckRun("Autograding", actualSha)
                    .withStatus(Status.COMPLETED)
                    .withStartedAt(Date.from(Instant.now()))
                    .withConclusion(Conclusion.SUCCESS);

            Pattern prefix = Pattern.compile("^.*" + StringUtils.substringAfterLast(repository, '/') + "/" + filesPrefix);
            System.out.println(">>>> Pattern: " + prefix.pattern());
            
            Output output = new Output(header, summary).withText(comment);
            analysisReports.stream()
                    .flatMap(Report::stream)
                    .map(issue -> createAnnotation(prefix, issue))
                    .forEach(output::add);

            check.add(output);
            GHCheckRun run = check.create();

            System.out.println(run);
        }
        catch (IOException exception) {
            System.out.println("Could not create check due to " + exception);
        }
    }

    private Annotation createAnnotation(final Pattern prefix, final Issue issue) {
        Annotation annotation = new Annotation(prefix.matcher(issue.getFileName()).replaceAll(""),
                issue.getLineStart(), issue.getLineEnd(),
                AnnotationLevel.WARNING, issue.getMessage()).withTitle(issue.getType());
        if (issue.getLineStart() == issue.getLineEnd()) {
            return annotation.withStartColumn(issue.getColumnStart()).withEndColumn(issue.getColumnEnd());
        }
        return annotation;
    }
}
