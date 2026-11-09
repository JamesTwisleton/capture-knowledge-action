package com.cka.provider.capture;

import com.cka.provider.ProviderHealth;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * One Google Drive folder, as {@link FolderWatcher} sees it (T09, second pass). Everything that
 * knows Drive exists is here; everything that decides anything is in the watcher.
 *
 * <p>Read-only, and one folder. The OAuth scope is {@code drive.readonly} and every query is
 * fenced to the configured folder id, so the worst a bug here can do is read something it should
 * not have — CKA never writes to Drive, and the recordings are the one thing it cannot replace.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "cka.provider", name = "capture", havingValue = "google-drive")
public class GoogleDriveRecordingFolder implements RecordingFolder {

    /**
     * What Drive calls a Google Doc. Meet writes transcripts and Gemini notes as Docs rather than
     * text files, and a Doc has no bytes to download — it has to be exported to a format, which
     * is why {@link #read} cannot be a plain media download.
     */
    private static final String GOOGLE_DOC = "application/vnd.google-apps.document";

    /**
     * Asked for by name, because the default is a much smaller set and a missing field comes back
     * as {@code null} rather than as an error — an omission here would surface as a
     * {@code NullPointerException} in the watcher, a layer away from its cause.
     */
    private static final String FIELDS = "nextPageToken, files(id, name, mimeType, webViewLink)";

    private final Drive drive;
    private final String folderId;

    GoogleDriveRecordingFolder(Drive drive, @Value("${cka.capture.google-drive.folder-id}") String folderId) {
        // Checked here as the credentials are, so a missing one is a sentence at startup rather
        // than a query for "'' in parents" failing on every poll.
        if (folderId == null || folderId.isBlank()) {
            throw new IllegalStateException("No Google Drive folder: set GOOGLE_DRIVE_FOLDER_ID in app/.env "
                    + "(cka.capture.google-drive.folder-id), or set CKA_PROVIDER_CAPTURE=none to start the "
                    + "stack without capture.");
        }
        this.drive = drive;
        this.folderId = folderId;
    }

    /**
     * Every page, not the first. Drive returns 100 files by default and a team's recordings
     * folder passes that within a few months — at which point the watcher would stop seeing new
     * meetings, silently, because the ones it can see are all already handed on.
     *
     * <p>{@code trashed = false} because a deleted recording is still returned otherwise, and
     * re-capturing something somebody deliberately threw away is the one outcome nobody wants.
     */
    @Override
    public List<FolderFile> list() {
        var files = new ArrayList<FolderFile>();
        String page = null;
        try {
            do {
                var response = drive.files()
                        .list()
                        .setQ("'" + folderId + "' in parents and trashed = false")
                        .setFields(FIELDS)
                        .setPageSize(100)
                        .setPageToken(page)
                        .execute();
                for (var file : response.getFiles()) {
                    files.add(new FolderFile(file.getId(), file.getName(), linkFor(file), file.getMimeType()));
                }
                page = response.getNextPageToken();
            } while (page != null);
        } catch (IOException e) {
            // Unchecked, because ContentListener's contract has no checked exception and the
            // watcher is the one place that decides what a failed look means — it logs and polls
            // again. Wrapping here keeps that decision in one place rather than two.
            throw new IllegalStateException("Could not list the Drive folder " + folderId, e);
        }
        return List.copyOf(files);
    }

    /**
     * A Doc is exported as plain text; anything else is read as it is. A recording has no text
     * worth returning and is never asked for — the watcher reads a transcript and notes, and
     * takes only the link from a recording.
     */
    @Override
    public String read(FolderFile file) {
        try {
            var bytes = GOOGLE_DOC.equals(file.mimeType())
                    ? drive.files()
                            .export(file.id(), "text/plain")
                            .executeMedia()
                            .getContent()
                    : drive.files().get(file.id()).executeMedia().getContent();
            try (bytes) {
                return new String(bytes.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            // Reported as absent rather than thrown: the watcher treats a transcript it cannot
            // read as one that has not arrived, and tries again next poll. A Doc that is still
            // being written is exactly that case.
            log.warn("Could not read \"{}\" from Drive: {}", file.name(), e.getMessage());
            return null;
        }
    }

    /**
     * One cheap call against the configured folder. Asking Drive for the folder itself rather
     * than listing its contents, because the question is whether the credential works and the
     * folder is reachable — not how much is in it.
     */
    @Override
    public ProviderHealth checkHealth() {
        try {
            var folder = drive.files().get(folderId).setFields("id, name").execute();
            return ProviderHealth.up("watching the Drive folder \"" + folder.getName() + "\"");
        } catch (Exception e) {
            return ProviderHealth.down("cannot reach the Drive folder " + folderId + ": " + e.getMessage());
        }
    }

    /**
     * {@code webViewLink} is what opens the file in a browser, which is what a person clicking
     * through from a vault note wants. Drive omits it for some files, so a note always has
     * something to link to even then.
     */
    private String linkFor(File file) {
        return file.getWebViewLink() != null
                ? file.getWebViewLink()
                : "https://drive.google.com/file/d/" + file.getId() + "/view";
    }
}
