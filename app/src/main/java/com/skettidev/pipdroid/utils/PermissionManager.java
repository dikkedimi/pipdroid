package com.skettidev.pipdroid.utils; // Change package name to match your app's structure

public class PermissionManager {
    private static PermissionManager instance;
    private boolean arePermissionsGranted;

    // Private constructor to prevent instantiation
    private PermissionManager() {}

    // Method to get the singleton instance of the class
    public static PermissionManager getInstance() {
        if (instance == null) {
            instance = new PermissionManager();
        }
        return instance;
    }

    // Method to set permissions granted state
    public void setPermissionsGranted(boolean granted) {
        this.arePermissionsGranted = granted;
    }

    // Method to get the permissions granted state
    public boolean arePermissionsGranted() {
        return arePermissionsGranted;
    }
}