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
 * <p>Files, not names. The watcher groups by name — Meet names a meeting's files alike — but it
 * also needs somewhere a person can open the recording, which becomes the link on the vault
 * note, and a handle to read a file back by. A name alone cannot carry either.
 */
public interface RecordingFolder extends Provider {

    /** Every file currently in the folder. Throws if the folder cannot be reached. */
    List<FolderFile> list();

    /** The text of one file, or {@code null} if it has none — a recording, for instance. */
    String read(FolderFile file);
}
