package com.cucumberforge.plugin.templates;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages predefined Cucumber scenario templates for common BDD patterns.
 */
public final class ScenarioTemplateManager {

    private static final List<ScenarioTemplate> TEMPLATES = new ArrayList<>();

    static {
        TEMPLATES.add(new ScenarioTemplate(
                "CRUD API",
                "Create, Read, Update, Delete operations for a REST resource",
                "  @api @crud\n" +
                "  Scenario: Create a new resource\n" +
                "    Given the API is available\n" +
                "    And I have a valid resource payload\n" +
                "    When I send a POST request to \"/api/resources\"\n" +
                "    Then the response status should be 201\n" +
                "    And the response should contain the created resource\n" +
                "    And the resource should have a generated ID\n\n" +
                "  Scenario: Get a resource by ID\n" +
                "    Given the API is available\n" +
                "    And a resource exists with ID \"1\"\n" +
                "    When I send a GET request to \"/api/resources/1\"\n" +
                "    Then the response status should be 200\n" +
                "    And the response should contain the resource details\n\n" +
                "  Scenario: Update an existing resource\n" +
                "    Given the API is available\n" +
                "    And a resource exists with ID \"1\"\n" +
                "    And I have an updated resource payload\n" +
                "    When I send a PUT request to \"/api/resources/1\"\n" +
                "    Then the response status should be 200\n" +
                "    And the resource should be updated\n\n" +
                "  Scenario: Delete a resource\n" +
                "    Given the API is available\n" +
                "    And a resource exists with ID \"1\"\n" +
                "    When I send a DELETE request to \"/api/resources/1\"\n" +
                "    Then the response status should be 204\n" +
                "    And the resource should no longer exist\n\n" +
                "  Scenario: Get a non-existent resource returns 404\n" +
                "    Given the API is available\n" +
                "    When I send a GET request to \"/api/resources/999\"\n" +
                "    Then the response status should be 404\n" +
                "    And the response should contain an error message"
        ));

        TEMPLATES.add(new ScenarioTemplate(
                "Authentication",
                "Login, logout, token handling, and authorization scenarios",
                "  @auth\n" +
                "  Scenario: Successful login with valid credentials\n" +
                "    Given a user exists with email \"user@example.com\" and password \"password123\"\n" +
                "    When I send a POST request to \"/api/auth/login\" with credentials\n" +
                "    Then the response status should be 200\n" +
                "    And the response should contain an access token\n" +
                "    And the response should contain a refresh token\n\n" +
                "  Scenario: Login with invalid credentials returns 401\n" +
                "    When I send a POST request to \"/api/auth/login\" with invalid credentials\n" +
                "    Then the response status should be 401\n" +
                "    And the response should contain \"Invalid credentials\"\n\n" +
                "  Scenario: Access protected endpoint with valid token\n" +
                "    Given I am authenticated as \"user@example.com\"\n" +
                "    When I send a GET request to \"/api/protected\" with the auth token\n" +
                "    Then the response status should be 200\n\n" +
                "  Scenario: Access protected endpoint without token returns 401\n" +
                "    When I send a GET request to \"/api/protected\" without a token\n" +
                "    Then the response status should be 401\n\n" +
                "  Scenario: Refresh an expired access token\n" +
                "    Given I have an expired access token\n" +
                "    And I have a valid refresh token\n" +
                "    When I send a POST request to \"/api/auth/refresh\"\n" +
                "    Then the response status should be 200\n" +
                "    And the response should contain a new access token"
        ));

        TEMPLATES.add(new ScenarioTemplate(
                "Error Handling",
                "Validation errors, 400, 404, 409, 500 scenarios",
                "  @errors\n" +
                "  Scenario: Request with missing required fields returns 400\n" +
                "    Given the API is available\n" +
                "    When I send a POST request to \"/api/resources\" with missing required fields\n" +
                "    Then the response status should be 400\n" +
                "    And the response should contain validation errors\n" +
                "    And the errors should indicate the missing fields\n\n" +
                "  Scenario: Request with invalid data format returns 400\n" +
                "    Given the API is available\n" +
                "    When I send a POST request to \"/api/resources\" with invalid email format\n" +
                "    Then the response status should be 400\n" +
                "    And the response should contain \"invalid email\"\n\n" +
                "  Scenario: Duplicate resource returns 409\n" +
                "    Given the API is available\n" +
                "    And a resource already exists with name \"duplicate\"\n" +
                "    When I send a POST request to \"/api/resources\" with name \"duplicate\"\n" +
                "    Then the response status should be 409\n" +
                "    And the response should contain \"already exists\"\n\n" +
                "  Scenario: Malformed JSON request returns 400\n" +
                "    Given the API is available\n" +
                "    When I send a POST request to \"/api/resources\" with malformed JSON\n" +
                "    Then the response status should be 400"
        ));

        TEMPLATES.add(new ScenarioTemplate(
                "Pagination & Sorting",
                "List resources with pagination, sorting, and filtering",
                "  @pagination\n" +
                "  Scenario: List resources with default pagination\n" +
                "    Given the API is available\n" +
                "    And 25 resources exist in the database\n" +
                "    When I send a GET request to \"/api/resources\"\n" +
                "    Then the response status should be 200\n" +
                "    And the response should contain 20 resources\n" +
                "    And the response should include pagination metadata\n\n" +
                "  Scenario: List resources with custom page size\n" +
                "    Given the API is available\n" +
                "    And 25 resources exist in the database\n" +
                "    When I send a GET request to \"/api/resources?page=0&size=5\"\n" +
                "    Then the response status should be 200\n" +
                "    And the response should contain 5 resources\n\n" +
                "  Scenario Outline: List resources sorted by field\n" +
                "    Given the API is available\n" +
                "    And multiple resources exist\n" +
                "    When I send a GET request to \"/api/resources?sort=<field>,<direction>\"\n" +
                "    Then the response status should be 200\n" +
                "    And the resources should be sorted by \"<field>\" in \"<direction>\" order\n\n" +
                "    Examples:\n" +
                "      | field     | direction |\n" +
                "      | name      | asc       |\n" +
                "      | name      | desc      |\n" +
                "      | createdAt | desc      |"
        ));

        TEMPLATES.add(new ScenarioTemplate(
                "File Upload",
                "File upload, download, and validation scenarios",
                "  @file\n" +
                "  Scenario: Upload a valid file\n" +
                "    Given the API is available\n" +
                "    And I am authenticated\n" +
                "    When I upload a file \"test.pdf\" to \"/api/files\"\n" +
                "    Then the response status should be 201\n" +
                "    And the response should contain the file URL\n\n" +
                "  Scenario: Upload a file exceeding size limit\n" +
                "    Given the API is available\n" +
                "    And I am authenticated\n" +
                "    When I upload a file exceeding 10MB to \"/api/files\"\n" +
                "    Then the response status should be 413\n" +
                "    And the response should contain \"File too large\"\n\n" +
                "  Scenario: Upload a file with unsupported format\n" +
                "    Given the API is available\n" +
                "    And I am authenticated\n" +
                "    When I upload a file \"malware.exe\" to \"/api/files\"\n" +
                "    Then the response status should be 415\n\n" +
                "  Scenario: Download an existing file\n" +
                "    Given the API is available\n" +
                "    And a file exists with ID \"file-123\"\n" +
                "    When I send a GET request to \"/api/files/file-123\"\n" +
                "    Then the response status should be 200\n" +
                "    And the response content type should be \"application/pdf\""
        ));

        TEMPLATES.add(new ScenarioTemplate(
                "Search & Filter",
                "Full-text search and multi-criteria filtering",
                "  @search\n" +
                "  Scenario: Search resources by keyword\n" +
                "    Given the API is available\n" +
                "    And resources exist with various names\n" +
                "    When I send a GET request to \"/api/resources?search=test\"\n" +
                "    Then the response status should be 200\n" +
                "    And all returned resources should contain \"test\" in their name\n\n" +
                "  Scenario: Filter resources by status\n" +
                "    Given the API is available\n" +
                "    And resources exist with status \"ACTIVE\" and \"INACTIVE\"\n" +
                "    When I send a GET request to \"/api/resources?status=ACTIVE\"\n" +
                "    Then the response status should be 200\n" +
                "    And all returned resources should have status \"ACTIVE\"\n\n" +
                "  Scenario: Filter resources by date range\n" +
                "    Given the API is available\n" +
                "    And resources exist with various creation dates\n" +
                "    When I send a GET request to \"/api/resources?from=2024-01-01&to=2024-12-31\"\n" +
                "    Then the response status should be 200\n" +
                "    And all returned resources should be within the date range\n\n" +
                "  Scenario: Search with no results\n" +
                "    Given the API is available\n" +
                "    When I send a GET request to \"/api/resources?search=nonexistent\"\n" +
                "    Then the response status should be 200\n" +
                "    And the response should contain 0 resources"
        ));

        TEMPLATES.add(new ScenarioTemplate(
                "WebSocket / Realtime",
                "WebSocket connection, messaging, and subscription scenarios",
                "  @websocket\n" +
                "  Scenario: Connect to WebSocket endpoint\n" +
                "    Given I am authenticated\n" +
                "    When I connect to the WebSocket at \"/ws/notifications\"\n" +
                "    Then the connection should be established\n\n" +
                "  Scenario: Receive real-time notification\n" +
                "    Given I am connected to the WebSocket at \"/ws/notifications\"\n" +
                "    When a new resource is created by another user\n" +
                "    Then I should receive a notification within 5 seconds\n" +
                "    And the notification should contain the resource details\n\n" +
                "  Scenario: Unauthorized WebSocket connection\n" +
                "    When I connect to the WebSocket at \"/ws/notifications\" without a token\n" +
                "    Then the connection should be rejected"
        ));

        TEMPLATES.add(new ScenarioTemplate(
                "Batch Operations",
                "Bulk create, update, delete operations",
                "  @batch\n" +
                "  Scenario: Bulk create multiple resources\n" +
                "    Given the API is available\n" +
                "    And I have a batch payload with 5 resources\n" +
                "    When I send a POST request to \"/api/resources/batch\"\n" +
                "    Then the response status should be 201\n" +
                "    And 5 resources should be created\n\n" +
                "  Scenario: Bulk delete resources\n" +
                "    Given the API is available\n" +
                "    And resources exist with IDs \"1\", \"2\", \"3\"\n" +
                "    When I send a DELETE request to \"/api/resources/batch\" with IDs\n" +
                "    Then the response status should be 204\n" +
                "    And none of the resources should exist\n\n" +
                "  Scenario: Partial batch failure\n" +
                "    Given the API is available\n" +
                "    And I have a batch payload with 3 valid and 2 invalid resources\n" +
                "    When I send a POST request to \"/api/resources/batch\"\n" +
                "    Then the response status should be 207\n" +
                "    And the response should indicate 3 successes and 2 failures"
        ));
    }

    private ScenarioTemplateManager() {}

    public static List<ScenarioTemplate> getTemplates() {
        return Collections.unmodifiableList(TEMPLATES);
    }

    public static ScenarioTemplate getTemplate(String name) {
        return TEMPLATES.stream()
                .filter(t -> t.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    // --- Template data class ---

    public static class ScenarioTemplate {
        private final String name;
        private final String description;
        private final String content;

        public ScenarioTemplate(String name, String description, String content) {
            this.name = name;
            this.description = description;
            this.content = content;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getContent() { return content; }

        @Override
        public String toString() { return name; }
    }
}
