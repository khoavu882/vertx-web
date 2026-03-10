package com.github.kaivu.vertxweb.web.rests;

import com.github.kaivu.vertxweb.constants.HttpStatusCodes;
import com.github.kaivu.vertxweb.constants.OpenApiConstants;
import com.github.kaivu.vertxweb.web.exceptions.ServiceException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class OpenApiRouter {
    private static final Logger log = LoggerFactory.getLogger(OpenApiRouter.class);

    private final String openApiYaml;
    private final String openApiJson;

    @Getter
    private final Router router;

    @Inject
    public OpenApiRouter(Vertx vertx) {
        this.openApiYaml = readResource(OpenApiConstants.RESOURCE_YAML, OpenApiConstants.RESOURCE_FALLBACK_YAML);
        this.openApiJson = readResource(OpenApiConstants.RESOURCE_JSON);
        this.router = Router.router(vertx);
        router.get(OpenApiConstants.ROUTE_YAML).handler(this::serveYaml);
        router.get(OpenApiConstants.ROUTE_JSON).handler(this::serveJson);
        router.get(OpenApiConstants.ROUTE_DOCS).handler(this::serveDocs);
        router.get(OpenApiConstants.ROUTE_DOCS_SLASH).handler(this::serveDocs);
    }

    private void serveYaml(RoutingContext ctx) {
        if (openApiYaml == null || openApiYaml.isBlank()) {
            throw new ServiceException("OpenAPI YAML document is unavailable", HttpStatusCodes.SERVICE_UNAVAILABLE);
        }
        ctx.response()
                .setStatusCode(HttpStatusCodes.OK)
                .putHeader("Content-Type", OpenApiConstants.CONTENT_TYPE_YAML)
                .end(openApiYaml);
    }

    private void serveJson(RoutingContext ctx) {
        if (openApiJson == null || openApiJson.isBlank()) {
            throw new ServiceException("OpenAPI JSON document is unavailable", HttpStatusCodes.SERVICE_UNAVAILABLE);
        }
        ctx.response()
                .setStatusCode(HttpStatusCodes.OK)
                .putHeader("Content-Type", OpenApiConstants.CONTENT_TYPE_JSON)
                .end(openApiJson);
    }

    private void serveDocs(RoutingContext ctx) {
        String html = String.format(
                """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8"/>
                  <meta name="viewport" content="width=device-width, initial-scale=1"/>
                  <title>Vert.x Web API Docs</title>
                  <link rel="stylesheet" href="https://unpkg.com/swagger-ui-dist@5/swagger-ui.css"/>
                  <style>body { margin: 0; } #swagger-ui { max-width: 1200px; margin: 0 auto; }</style>
                </head>
                <body>
                  <div id="swagger-ui"></div>
                  <script src="https://unpkg.com/swagger-ui-dist@5/swagger-ui-bundle.js"></script>
                  <script>
                    window.onload = function() {
                      SwaggerUIBundle({
                        url: '%s',
                        dom_id: '#swagger-ui',
                        deepLinking: true,
                        displayOperationId: true
                      });
                    };
                  </script>
                </body>
                </html>
                """,
                OpenApiConstants.ROUTE_YAML);
        ctx.response()
                .setStatusCode(HttpStatusCodes.OK)
                .putHeader("Content-Type", OpenApiConstants.CONTENT_TYPE_HTML)
                .end(html);
    }

    private String readResource(String... candidates) {
        for (String candidate : candidates) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(candidate)) {
                if (in == null) {
                    continue;
                }
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.error("Failed to read OpenAPI resource: {}", candidate, e);
            }
        }
        return null;
    }
}
