package org.example;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class OASEncryptionScanner {

    public static void main(String[] args) {
        // Absolute path is necessary for the parser to find relative ./refs
        File apiFile = new File("F:\\Git\\OasParsingTest\\sampleOas\\openapi.yaml");
        String absolutePath = apiFile.getAbsolutePath();

        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setResolveFully(false); // Inlines external files

        SwaggerParseResult result = new OpenAPIV3Parser().readLocation(absolutePath, null, options);

        if (result.getOpenAPI() == null) {
            System.err.println("Failed to parse. Errors: " + result.getMessages());
            return;
        }

        OpenAPI openAPI = result.getOpenAPI();

        openAPI.getPaths().forEach((pathName, pathItem) -> {
            scanOperationMap(pathName, pathItem.readOperationsMap());
        });
    }

    private static void scanOperationMap(String pathName, Map<PathItem.HttpMethod, Operation> operations) {
        if (operations == null) return;
        operations.forEach((method, op) -> {
            String contextPrefix = "[" + method + " " + pathName + "]";

            // 1. Scan Request Body
            if (op.getRequestBody() != null && op.getRequestBody().getContent() != null) {
                op.getRequestBody().getContent().forEach((contentType, mediaType) -> {
                    System.out.println("\nChecking Request Body: " + contextPrefix);
                    scanSchema("$", mediaType.getSchema(), new HashSet<>());
                });
            }

            // 2. Scan Responses
            if (op.getResponses() != null) {
                op.getResponses().forEach((statusCode, response) -> {
                    if (response.getContent() != null) {
                        response.getContent().forEach((contentType, mediaType) -> {
                            System.out.println("\nChecking Response " + statusCode + ": " + contextPrefix);
                            scanSchema("$", mediaType.getSchema(), new HashSet<>());
                        });
                    }
                });
            }
        });
    }

    private static void scanSchema(String jsonPath, Schema<?> schema, Set<Schema<?>> visited) {
        if (schema == null || visited.contains(schema)) return;
        visited.add(schema);

        // Detect <ENCRYPT> in description
        if (schema.getDescription() != null && schema.getDescription().contains("<ENCRYPT>")) {
            System.out.println(jsonPath);
        }

        // Handle Objects
        if (schema.getProperties() != null) {
            schema.getProperties().forEach((propName, propSchema) -> {
                // Construct path: $.user -> $.user.id
                String nextPath = jsonPath + "." + propName;
                scanSchema(nextPath, propSchema, new HashSet<>(visited));
            });
        }

        // Handle Arrays
        if (schema instanceof ArraySchema || schema.getItems() != null) {
            Schema<?> itemSchema = (schema instanceof ArraySchema)
                    ? ((ArraySchema) schema).getItems()
                    : schema.getItems();

            // Construct path: $.userProjects -> $.userProjects.[*]
            scanSchema(jsonPath + ".[*]", itemSchema, new HashSet<>(visited));
        }

        // Handle Composition (allOf, anyOf, oneOf) - keep path the same as these are logical wraps
        if (schema instanceof ComposedSchema) {
            ComposedSchema cs = (ComposedSchema) schema;
            if (cs.getAllOf() != null) cs.getAllOf().forEach(s -> scanSchema(jsonPath, s, new HashSet<>(visited)));
            if (cs.getAnyOf() != null) cs.getAnyOf().forEach(s -> scanSchema(jsonPath, s, new HashSet<>(visited)));
            if (cs.getOneOf() != null) cs.getOneOf().forEach(s -> scanSchema(jsonPath, s, new HashSet<>(visited)));
        }
    }

    private static void stitchRefDescriptions(OpenAPI openAPI) {
        if (openAPI.getComponents() == null || openAPI.getComponents().getSchemas() == null) return;

        openAPI.getPaths().forEach((path, pathItem) -> {
            pathItem.readOperations().forEach(op -> {
                // Check Request Body
                if (op.getRequestBody() != null && op.getRequestBody().getContent() != null) {
                    op.getRequestBody().getContent().forEach((type, media) ->
                            preserveDescription(media.getSchema(), openAPI));
                }
                // Check Responses
                if (op.getResponses() != null) {
                    op.getResponses().forEach((code, resp) -> {
                        if (resp.getContent() != null) {
                            resp.getContent().forEach((type, media) ->
                                    preserveDescription(media.getSchema(), openAPI));
                        }
                    });
                }
            });
        });
    }

    private static void preserveDescription(Schema<?> schema, OpenAPI openAPI) {
        if (schema == null) return;

        // Check properties for the $ref + description combo
        if (schema.getProperties() != null) {
            schema.getProperties().forEach((name, prop) -> {
                if (prop.get$ref() != null && prop.getDescription() != null) {
                    // We found a sibling description!
                    // Let's find the target and append it.
                    Schema<?> target = getReferencedSchema(openAPI, prop.get$ref());
                    if (target != null) {
                        String newDesc = (target.getDescription() == null ? "" : target.getDescription() + " ")
                                + prop.getDescription();
                        target.setDescription(newDesc);
                    }
                }
                preserveDescription(prop, openAPI);
            });
        }
    }

    private static Schema<?> getReferencedSchema(OpenAPI openAPI, String ref) {
        if (ref == null || openAPI.getComponents() == null || openAPI.getComponents().getSchemas() == null) {
            return null;
        }

        // Usually refs look like: #/components/schemas/MyModel
        // or ./otherFile.yaml#/components/schemas/MyModel
        String schemaName;
        if (ref.contains("/")) {
            schemaName = ref.substring(ref.lastIndexOf("/") + 1);
        } else {
            schemaName = ref;
        }

        return openAPI.getComponents().getSchemas().get(schemaName);
    }
}