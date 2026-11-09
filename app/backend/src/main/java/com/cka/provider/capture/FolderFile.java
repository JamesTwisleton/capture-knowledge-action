package com.cka.provider.capture;

/**
 * One file in the watched folder.
 *
 * @param id   the folder's own handle for it, used to read it back. Opaque: a Drive file id, a
 *             path, whatever the implementation needs
 * @param name what it is called, which is how {@link FolderWatcher} tells which files belong to
 *             one meeting — Meet names them alike
 * @param uri  where a person can open it, which ends up on the vault note as the link back to
 *             the recording. Never an internal handle; {@code PageReference.location}'s rule
 *             applies here for the same reason
 * @param mimeType what kind of file it is, or {@code null} if the folder does not say. Carried
 *             because reading one may depend on it — a Google Doc has no bytes to download and
 *             must be exported — and asking the folder again per read is a round trip for
 *             something the listing already knew
 */
public record FolderFile(String id, String name, String uri, String mimeType) {}
