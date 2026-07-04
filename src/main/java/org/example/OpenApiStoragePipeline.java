package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

public class OpenApiStoragePipeline {

    public static void main(String[] args) {
        try {
            String rootFilePath = "src/main/resources/openapi/main.yaml";

            // 1. INGEST & BUNDLE (Process Multi-file layout)
            String bundledJson = bundleMultiFileSpec(rootFilePath);
            System.out.println("--- Bundled Flat JSON String for Database ---");
            System.out.println(bundledJson.substring(0, 200) + "... [truncated]");

            // [Simulation] Store it in database
            FakeDatabase db = new FakeDatabase();
            db.save(bundledJson);

            // 2. RETRIEVE & READ
            String retrievedJson = db.fetch();
            OpenAPI openApiObject = parseFromJsonString(retrievedJson);

            System.out.println("\n--- Successfully Reconstructed Object ---");
            System.out.println("API Title: " + openApiObject.getInfo().getTitle());
            System.out.println("Paths count: " + openApiObject.getPaths().size());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Reads a multi-file OpenAPI definition from a root path, follows all $refs,
     * and compiles it into a single flattened standard JSON String.
     */
    public static String bundleMultiFileSpec(String rootPath) throws Exception {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);       // Follow and resolve external files
        options.setResolveFully(true);  // Pull external components inline 

        SwaggerParseResult result = new OpenAPIV3Parser().readLocation(rootPath, null, options);
        
        if (result.getOpenAPI() == null) {
            throw new IllegalArgumentException("Failed to parse OpenAPI: " + result.getMessages());
        }

        // Use Swagger's configured Jackson Mapper to get a clean OpenAPI document string
        ObjectMapper cleanMapper = Json.mapper();
        return cleanMapper.writeValueAsString(result.getOpenAPI());
    }

    /**
     * Converts a database standard JSON string back into a functional Java OpenAPI object tree.
     */
    public static OpenAPI parseFromJsonString(String jsonContent) {
        SwaggerParseResult result = new OpenAPIV3Parser().readContents(jsonContent, null, null);
        
        if (result.getOpenAPI() == null) {
            throw new IllegalArgumentException("Invalid stored JSON schema: " + result.getMessages());
        }
        
        return result.getOpenAPI();
    }

    // Mock Database class for demonstration
    static class FakeDatabase {
        private String storageCell;
        public void save(String data) { this.storageCell = data; }
        public String fetch() { return this.storageCell; }
    }
}