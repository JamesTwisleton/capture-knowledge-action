package com.cka.provider.capture;

import com.cka.provider.Provider;
import java.util.List;

/**
 * The folder {@link FolderWatcher} polls, and the only thing in T09 that knows where recordings
 * actually live.
 *
 * <p>A seam, not a testing device. PRD 7.2 describes the folder watcher as the implementation
 * "for folder-based sources" and names OneDrive alongside Drive, so what changes between them is
 * exactly this: how a folder is listed and a file read. Everything the watcher decides — which
 * files are one meeting, which meeting is ready, what it has already handed on — is the same
 * either way, and that is where the work is.
 *
 * <p>Names, not ids. Meet tells you which files belong together by naming them alike, so the name
 * is the only thing the watcher needs; an id would be an extra concept for it to carry around
 * and nothing to spend it on.
 */
public interface RecordingFolder extends Provider {

    /** Every file currently in the folder, by name. Throws if the folder cannot be reached. */
    List<String> list();

    /** The text of one file, or {@code null} if it has none. */
    String read(String name);
}
