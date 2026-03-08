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
        // 1. Use an absolute path to ensure the parser knows the base directory for relative $refs
        File apiFile = new File("F:\\Git\\OasParsingTest\\sampleOas\\openapi.yaml");
        String absolutePath = apiFile.getAbsolutePath();

        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setResolveFully(true); // Forces external $refs to be inlined into the object tree

        SwaggerParseResult result = new OpenAPIV3Parser().readLocation(absolutePath, null, options);

        // 2. Print errors if the parser couldn't find/read the external files
        if (result.getMessages() != null && !result.getMessages().isEmpty()) {
            System.out.println("Parser Messages (Check these for file-not-found errors):");
            result.getMessages().forEach(m -> System.err.println(" >> " + m));
        }

        OpenAPI openAPI = result.getOpenAPI();
        if (openAPI == null || openAPI.getPaths() == null) {
            System.err.println("Could not load OpenAPI. Stop.");
            return;
        }

        System.out.println("Scanning API paths for <ENCRYPT>...");
        openAPI.getPaths().forEach((pathName, pathItem) -> {
            scanOperationMap(pathName, pathItem.readOperationsMap());
        });
    }

    private static void scanOperationMap(String pathName, Map<PathItem.HttpMethod, Operation> operations) {
        if (operations == null) return;
        operations.forEach((method, op) -> {
            String basePath = "$.paths['" + pathName + "']." + method.toString().toLowerCase();

            // Check Request Body
            if (op.getRequestBody() != null && op.getRequestBody().getContent() != null) {
                op.getRequestBody().getContent().forEach((contentType, mediaType) -> {
                    scanSchema(basePath + ".requestBody.content['" + contentType + "'].schema",
                            mediaType.getSchema(), new HashSet<>());
                });
            }

            // Check Responses
            if (op.getResponses() != null) {
                op.getResponses().forEach((statusCode, response) -> {
                    if (response.getContent() != null) {
                        response.getContent().forEach((contentType, mediaType) -> {
                            scanSchema(basePath + ".responses['" + statusCode + "'].content['" + contentType + "'].schema",
                                    mediaType.getSchema(), new HashSet<>());
                        });
                    }
                });
            }
        });
    }

    private static void scanSchema(String currentPath, Schema<?> schema, Set<Schema<?>> visited) {
        if (schema == null || visited.contains(schema)) return;
        visited.add(schema);

        // Check for the tag in the current node
        if (schema.getDescription() != null && schema.getDescription().contains("<ENCRYPT>")) {
            System.out.println("[FOUND] " + currentPath);
        }

        // Handle Objects (properties)
        if (schema.getProperties() != null) {
            schema.getProperties().forEach((name, prop) ->
                    scanSchema(currentPath + ".properties['" + name + "']", prop, new HashSet<>(visited)));
        }

        // Handle Arrays (items)
        if (schema instanceof ArraySchema) {
            scanSchema(currentPath + ".items", ((ArraySchema) schema).getItems(), new HashSet<>(visited));
        } else if (schema.getItems() != null) {
            scanSchema(currentPath + ".items", schema.getItems(), new HashSet<>(visited));
        }

        // Handle Composition (allOf, anyOf, oneOf)
        if (schema instanceof ComposedSchema) {
            ComposedSchema cs = (ComposedSchema) schema;
            checkList(currentPath + ".allOf", cs.getAllOf(), visited);
            checkList(currentPath + ".anyOf", cs.getAnyOf(), visited);
            checkList(currentPath + ".oneOf", cs.getOneOf(), visited);
        }
    }

    private static void checkList(String path, java.util.List<Schema> schemas, Set<Schema<?>> visited) {
        if (schemas == null) return;
        for (int i = 0; i < schemas.size(); i++) {
            scanSchema(path + "[" + i + "]", schemas.get(i), new HashSet<>(visited));
        }
    }
}