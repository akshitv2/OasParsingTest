package org.example;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.SimpleRequest;
import com.atlassian.oai.validator.report.ValidationReport;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class OasValidatorService {

    /**
     * Validates an HTTP request against an OpenAPI specification.
     *
     * @param oasPath    URL or file path to the OAS spec (e.g., "file:///path/to/spec.yaml")
     * @param method     HTTP Method (e.g., "POST", "GET") - required by the Atlassian builder
     * @param requestUrl The full request URL containing potential query strings
     * @param path       The exact resource path (e.g., "/api/v1/users")
     * @param headers    Map of header names to their list of values
     * @param payload    The raw request body string
     * @return ValidationReport containing potential errors or warnings
     */
    public static ValidationReport validate(
            String oasPath,
            String method,
            String requestUrl,
            Map<String, List<String>> headers,
            String payload) throws Exception {

        // 1. Initialize the validator instance using the spec path
        OpenApiInteractionValidator validator = OpenApiInteractionValidator
                .createForSpecificationUrl(oasPath)
                .build();

        // 2. Initialize the Atlassian request builder
        URI uri = new URI(requestUrl);
        SimpleRequest.Builder requestBuilder = new SimpleRequest.Builder(method, uri.getPath());

        // 3. Populate headers using library methods
        if (headers != null) {
            headers.forEach((key, values) ->
                    requestBuilder.withHeader(key, values.toArray(new String[0]))
            );
        }

        // 4. Populate request payload
        if (payload != null && !payload.isEmpty()) {
            requestBuilder.withBody(payload);
        }

        // 5. Parse and populate query parameters from the requestUrl using Java standard streams

        String query = uri.getRawQuery();
        if (query != null && !query.isEmpty()) {
            Map<String, List<String>> queryParams = Arrays.stream(query.split("&"))
                    .map(param -> param.split("=", 2))
                    .collect(Collectors.groupingBy(
                            pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                            Collectors.mapping(
                                    pair -> pair.length > 1 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "",
                                    Collectors.toList()
                            )
                    ));

            queryParams.forEach((key, values) ->
                    requestBuilder.withQueryParam(key, values.toArray(new String[0]))
            );
        }

        // 6. Execute and return validation findings
        ValidationReport report = validator.validateRequest(requestBuilder.build());

        if (report.hasErrors()) {
            report.getMessages().forEach(message -> {
                System.out.println("Validation Error: " + message.getMessage());
            });
        } else {
            System.out.println("Request is completely valid against the specification.");
        }

        return report;
    }

    static void main() throws Exception {
        OasValidatorService.validate("sampleOas/newOas.yaml", "GET", "/search?filter[status]=active&filter[role]=edt", null, null);


        List<TestCase> tList = new ArrayList<>();
        tList.add(new TestCase("sampleOas/newOas.yaml", "GET", "/users/;id=admin,user", null, null, false));
        // ADMIN is too long for 4 length
        tList.add(new TestCase("sampleOas/newOas.yaml", "GET", "/users/;id=admi,user", null, null, true));
        // THIS IS JUST RIGHT, CONFIRMED it's checking each element as total length > 4
        tList.add(new TestCase("sampleOas/newOas.yaml", "GET", "/usersA/;id=pow;id=user", null, null, true));
        // EXPLODING WORKS AS INTENDED
        tList.add(new TestCase("sampleOas/newOas.yaml", "GET", "/usersA/;id=admin;id=user", null, null, false));
        // EXPLODING DOESN"T allow > 4
//        OasValidatorService.validate("sampleOas/newOas.yaml", "GET",
//                "/items/.admin.user",
//                null, null);
//        OasValidatorService.validate("sampleOas/newOas.yaml", "GET",
//                "/itemsB/.admin.user",
//                null, null);
//        OasValidatorService.validate("sampleOas/newOas.yaml", "GET", "/users/pow,user", null, null);
//        OasValidatorService.validate("sampleOas/newOas.yaml", "GET", "/search?colors=admin%20user", null, null);
//        OasValidatorService.validate("sampleOas/newOas.yaml", "GET", "/search?colors=pow%20user", null, null);
//        OasValidatorService.validate("sampleOas/newOas.yaml", "GET", "/search?colors=admin+user", null, null);
//        OasValidatorService.validate("sampleOas/newOas.yaml", "GET", "/search?colors=pow+user", null, null);
//        OasValidatorService.validate("sampleOas/newOas.yaml", "GET", "/search?flags=adminx%7Cuser", null, null);
//        OasValidatorService.validate("sampleOas/newOas.yaml", "GET", "/search?filter[status]=active&filter[role]=editor", null, null);
//        OasValidatorService.validate("sampleOas/newOas.yaml", "GET", "/search?filter[status]=active&filter[role]=edt", null, null);
    }

    public static class TestCase {
        public String oasPath;
        public String method;
        public String requestUrl;
        public Map<String, List<String>> headers;
        public String payload;
        public Boolean expectedOutput;

        public TestCase(String oasPath, String method, String requestUrl, Map<String, List<String>> headers, String payload, Boolean expectedOutput) {
            this.oasPath = oasPath;
            this.method = method;
            this.requestUrl = requestUrl;
            this.headers = headers;
            this.payload = payload;
            this.expectedOutput = expectedOutput;
        }
    }
}