/*
 * Google Drive Bookmark Backup Tool
 * ---------------------------------------------------------
 * This application performs the following steps:
 * 1. Authenticates the user via OAuth2.
 * 2. Simulates extracting bookmarks from a local browser.
 * 3. Serializes bookmarks into a JSON format.
 * 4. Checks Google Drive for an existing backup file.
 * 5. Either uploads a new backup or updates the existing one.
 */

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Main entry point for the Backup Application.
 */
public class DriveBookmarkBackup {

    // ==========================================
    // CONFIGURATION & CONSTANTS
    // ==========================================
    private static final String APPLICATION_NAME = "Java Bookmark Backup Tool/1.0";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    private static final String CREDENTIALS_FILE_PATH = "/credentials.json";
    private static final String BACKUP_FILE_NAME = "my_bookmarks_backup.json";
    private static final String BACKUP_MIME_TYPE = "application/json";
    
    // Scopes required for uploading and managing files
    private static final List<String> SCOPES = Collections.singletonList(DriveScopes.DRIVE_FILE);

    /**
     * Main execution method.
     */
    public static void main(String[] args) {
        System.out.println("==========================================");
        System.out.println("   Starting Google Drive Bookmark Backup  ");
        System.out.println("==========================================");

        try {
            // 1. Initialize Drive Service
            final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            Drive service = getDriveService(HTTP_TRANSPORT);

            // 2. Data Gathering Phase
            System.out.println("\n[INFO] Scanning local bookmarks...");
            BookmarkManager bookmarkManager = new BookmarkManager();
            List<Bookmark> localBookmarks = bookmarkManager.getLocalBookmarks();
            System.out.println("[INFO] Found " + localBookmarks.size() + " bookmarks to backup.");

            // 3. Serialization Phase
            System.out.println("[INFO] preparing backup file...");
            java.io.File uploadableFile = bookmarkManager.createBackupFile(localBookmarks);

            // 4. Cloud Upload Phase
            DriveUploader uploader = new DriveUploader(service);
            uploader.performBackup(uploadableFile);

            // 5. Cleanup
            System.out.println("\n[SUCCESS] Backup operation completed successfully.");

        } catch (FileNotFoundException e) {
            System.err.println("[ERROR] Could not find credentials.json. Please place it in the resources folder.");
            e.printStackTrace();
        } catch (IOException e) {
            System.err.println("[ERROR] An IO error occurred during the backup process.");
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("[ERROR] An unexpected error occurred.");
            e.printStackTrace();
        }
    }

    /**
     * Authenticates the user and builds the Drive service.
     */
    private static Drive getDriveService(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
        InputStream in = DriveBookmarkBackup.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        if (in == null) {
            throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
        }
        
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();
        
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        Credential credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");

        System.out.println("[AUTH] Credentials saved to " + TOKENS_DIRECTORY_PATH);
        
        return new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    // ==========================================
    // INTERNAL DATA MODELS
    // ==========================================

    /**
     * Represents a single URL bookmark.
     */
    public static class Bookmark {
        private String title;
        private String url;
        private String folder;
        private long createdAt;
        private List<String> tags;

        public Bookmark(String title, String url, String folder) {
            this.title = title;
            this.url = url;
            this.folder = folder;
            this.createdAt = System.currentTimeMillis();
            this.tags = new ArrayList<>();
        }

        public void addTag(String tag) {
            this.tags.add(tag);
        }

        // Getters and Setters for serialization
        public String getTitle() { return title; }
        public String getUrl() { return url; }
        public String getFolder() { return folder; }
        
        @Override
        public String toString() {
            return String.format("Bookmark{title='%s', url='%s'}", title, url);
        }
    }

    // ==========================================
    // BOOKMARK MANAGER LOGIC
    // ==========================================

    /**
     * Handles logic for retrieving and file-handling bookmarks.
     */
    public static class BookmarkManager {

        /**
         * Simulates fetching bookmarks. In a real app, this would parse
         * Chrome's "Bookmarks" JSON file or Firefox's SQLite DB.
         */
        public List<Bookmark> getLocalBookmarks() {
            List<Bookmark> bookmarks = new ArrayList<>();
            
            // Simulating complex data retrieval
            for (int i = 1; i <= 50; i++) {
                String folder = (i % 5 == 0) ? "Work" : "Personal";
                Bookmark b = new Bookmark(
                    "Useful Site #" + i,
                    "https://www.example.com/resource/" + i,
                    folder
                );
                
                if (i % 2 == 0) b.addTag("Technology");
                if (i % 3 == 0) b.addTag("News");
                
                bookmarks.add(b);
            }
            
            // Adding a specific critical bookmark
            bookmarks.add(new Bookmark("Google Drive API Docs", "https://developers.google.com/drive", "Dev"));
            
            return bookmarks;
        }

        /**
         * Serializes the bookmark list to a temporary JSON file.
         */
        public java.io.File createBackupFile(List<Bookmark> bookmarks) throws IOException {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String jsonContent = gson.toJson(bookmarks);

            // Create a temp file
            java.io.File tempFile = java.io.File.createTempFile("bookmark_backup_", ".json");
            tempFile.deleteOnExit();

            try (Writer writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(tempFile), StandardCharsets.UTF_8))) {
                writer.write(jsonContent);
            }

            System.out.println("[IO] Temporary backup file created at: " + tempFile.getAbsolutePath());
            System.out.println("[IO] File size: " + tempFile.length() + " bytes");
            return tempFile;
        }
    }

    // ==========================================
    // DRIVE UPLOADER LOGIC
    // ==========================================

    /**
     * Handles interaction with the Google Drive API.
     */
    public static class DriveUploader {
        private final Drive service;

        public DriveUploader(Drive service) {
            this.service = service;
        }

        /**
         * Main logic to decide whether to create new or update existing.
         */
        public void performBackup(java.io.File localFile) throws IOException {
            String existingFileId = searchForExistingBackup();

            if (existingFileId != null) {
                System.out.println("[DRIVE] Found existing backup (ID: " + existingFileId + "). Updating...");
                updateFile(existingFileId, localFile);
            } else {
                System.out.println("[DRIVE] No existing backup found. Creating new file...");
                createNewFile(localFile);
            }
        }

        /**
         * Searches for a file with the specific backup name and not trashed.
         */
        private String searchForExistingBackup() throws IOException {
            String query = "name = '" + BACKUP_FILE_NAME + "' and trashed = false and mimeType != 'application/vnd.google-apps.folder'";
            
            FileList result = service.files().list()
                    .setQ(query)
                    .setSpaces("drive")
                    .setFields("nextPageToken, files(id, name, createdTime)")
                    .execute();

            List<File> files = result.getFiles();
            
            if (files == null || files.isEmpty()) {
                return null;
            }

            // If multiple exist, return the first one (or handle duplicates logic here)
            File found = files.get(0);
            System.out.println("[DRIVE] Located file: " + found.getName() + " (Created: " + found.getCreatedTime() + ")");
            return found.getId();
        }

        /**
         * Uploads a new file to the root of the Drive.
         */
        private void createNewFile(java.io.File localFile) throws IOException {
            File fileMetadata = new File();
            fileMetadata.setName(BACKUP_FILE_NAME);
            fileMetadata.setMimeType(BACKUP_MIME_TYPE);
            fileMetadata.setDescription("Backup of user bookmarks generated by Java Tool");

            FileContent mediaContent = new FileContent(BACKUP_MIME_TYPE, localFile);

            File file = service.files().create(fileMetadata, mediaContent)
                    .setFields("id, name, webContentLink")
                    .execute();

            System.out.println("[DRIVE] New file created. ID: " + file.getId());
            System.out.println("[DRIVE] Download Link: " + file.getWebContentLink());
        }

        /**
         * Updates the content of an existing file.
         */
        private void updateFile(String fileId, java.io.File localFile) throws IOException {
            // Update metadata (optional, e.g., to update modified time explicitly or description)
            File fileMetadata = new File();
            fileMetadata.setDescription("Updated on " + new DateTime(System.currentTimeMillis()));

            FileContent mediaContent = new FileContent(BACKUP_MIME_TYPE, localFile);

            // We use the update method here
            File updatedFile = service.files().update(fileId, fileMetadata, mediaContent)
                    .setFields("id, name, modifiedTime")
                    .execute();

            System.out.println("[DRIVE] File updated successfully.");
            System.out.println("[DRIVE] New Modified Time: " + updatedFile.getModifiedTime());
        }
    }
    
    // ==========================================
    // UTILITIES & HELPERS (Simulated Complexity)
    // ==========================================
    
    /**
     * Helper to validate backup integrity before upload (Simulated).
     */
    public static class BackupValidator {
        
        public static boolean validate(java.io.File file) {
            // Logic to check if file is not empty and valid JSON
            if(file.length() == 0) return false;
            
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String firstLine = br.readLine();
                return firstLine != null && firstLine.trim().startsWith("[");
            } catch (IOException e) {
                return false;
            }
        }
        
        public static void logBackupStats(int count) {
            System.out.println("Stats: " + count + " items processed.");
            System.out.println("Memory Usage: " + (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) + " bytes");
        }
    }
}