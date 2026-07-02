openapi: 3.1.0
info:
title: Parameter Serialization Showcase
version: 1.0.0
description: Demonstrates OAS parameter styles and explode properties.
paths:
/serialization/{matrixParam}/{labelParam}/{simpleParam}:
get:
summary: Get serialization examples
operationId: getSerialization
parameters:
# PATH PARAMETERS
- name: matrixParam
in: path
required: true
style: matrix
explode: false
schema:
type: array
items:
type: integer
description: "Path matrix style, explode false. Example: /serialization/;matrixParam=3,4,5/..."
- name: labelParam
in: path
required: true
style: label
explode: false
schema:
type: array
items:
type: integer
description: "Path label style, explode false. Example: /serialization/;/.../.3,4,5/..."
- name: simpleParam
in: path
required: true
style: simple
explode: false
schema:
type: array
items:
type: integer
description: "Path simple style, explode false. Example: /serialization/;/.../.../3,4,5"

        # QUERY PARAMETERS
        - name: formExplodeTrue
          in: query
          style: form
          explode: true
          schema:
            type: array
            items:
              type: integer
          description: "Query form style, explode true. Example: ?formExplodeTrue=3&formExplodeTrue=4"
        - name: formExplodeFalse
          in: query
          style: form
          explode: false
          schema:
            type: array
            items:
              type: integer
          description: "Query form style, explode false (comma delimited). Example: ?formExplodeFalse=3,4,5"
        - name: spaceDelimited
          in: query
          style: spaceDelimited
          explode: false
          schema:
            type: array
            items:
              type: integer
          description: "Query spaceDelimited style, explode false. Example: ?spaceDelimited=3%204%205"
        - name: pipeDelimited
          in: query
          style: pipeDelimited
          explode: false
          schema:
            type: array
            items:
              type: integer
          description: "Query pipeDelimited style, explode false. Example: ?pipeDelimited=3|4|5"
        - name: deepObject
          in: query
          style: deepObject
          explode: true
          schema:
            type: object
            properties:
              role:
                type: string
              firstName:
                type: string
          description: "Query deepObject style, explode true. Example: ?deepObject[role]=admin&deepObject[firstName]=Alex"

        # HEADER PARAMETERS
        - name: X-Simple-Header
          in: header
          style: simple
          explode: false
          schema:
            type: array
            items:
              type: integer
          description: "Header simple style, explode false. Example: X-Simple-Header: 3,4,5"

        # COOKIE PARAMETERS
        - name: formCookie
          in: cookie
          style: form
          explode: false
          schema:
            type: array
            items:
              type: integer
          description: "Cookie form style, explode false. Example: Cookie: formCookie=3,4,5"

      responses:
        '200':
          description: OK