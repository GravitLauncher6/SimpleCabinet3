# Content-addressed storage (CAS) client guide

The CAS API is rooted at `/cas`. It stores immutable blobs, groups blob references into directories, and publishes directory manifests as versions.

## Authentication

Send the access token on every request:

```http
Authorization: Bearer <access-token>
```

Read operations require an authenticated user. Write operations require the `admin.cas.write` permission:

- `GET /cas/blobs/{hash}` — authenticated
- `GET /cas/directories`, `GET /cas/directories/list`, `GET /cas/branches`, and version reads — authenticated
- blob registration/upload, directory changes, publishing, and forking — `admin.cas.write`

All JSON requests use `Content-Type: application/json`.

## Blob workflow

### Upload a file

```http
POST /cas/upload
Content-Type: multipart/form-data
```

Use a multipart field named `file`.

```bash
curl -X POST "$BASE_URL/cas/upload" \
  -H "Authorization: Bearer $TOKEN" \
  -F 'file=@dist/app.zip'
```

Response (`201 Created`):

```json
{
  "id": 1,
  "hash": "sha256:...",
  "algorithm": "SHA-256",
  "sizeBytes": 12345,
  "storageKey": "blobs/sha256/ab/cd/...",
  "createdAt": "2026-08-23T12:00:00Z"
}
```

The upload is content-addressed using SHA-256. Uploading the same content again returns the existing blob record. Save `hash`, `storageKey`, and `sizeBytes`; all three are needed in a directory manifest.

### Upload multiple files

```http
POST /cas/upload/bulk
Content-Type: multipart/form-data
```

Send one or more multipart fields named `files`. An empty list is rejected.

### Register an object already in storage

Use this when the client has placed an object through another storage path:

```http
POST /cas/blobs
```

```json
{
  "hash": "sha256:...",
  "algorithm": "SHA-256",
  "sizeBytes": 12345,
  "storageKey": "external/path/app.zip"
}
```

The `storageKey` must already exist. A hash can only be registered once; re-registering it is idempotent only when storage key, size, and algorithm all match.

### Look up a blob

```http
GET /cas/blobs/{hash}
```

Use the complete hash, for example `sha256:abc...`. The response is the same `BlobResponse` shape shown above. A blob lookup does not download the object; the `storageKey` identifies it for manifest construction.

## Directory workflow

Directory keys are client-defined strings and are trimmed before storage. They must be unique and non-blank.

### Create a directory

```http
POST /cas/directories
```

```json
{
  "key": "com/example/app",
  "name": "Example App",
  "metadata": "{\"channel\":\"stable\"}"
}
```

`metadata` contains a JSON object encoded as a JSON string:

```json
{"key":"com/example/app","metadata":"{\"channel\":\"stable\"}"}
```

If omitted, directory metadata is `{}`. The response is `201 Created`:

```json
{
  "id": 10,
  "key": "com/example/app",
  "name": "Example App",
  "metadata": "{\"channel\":\"stable\"}",
  "createdAt": "2026-08-23T12:00:00Z"
}
```

Metadata is returned as a canonical JSON string, not as a nested JSON object.

### Read directories

```http
GET /cas/directories?directoryKey=com/example/app
GET /cas/directories/list
```

The first returns one `DirectoryResponse`; the second returns an array of them.

### Update directory metadata

```http
PATCH /cas/directories/metadata?directoryKey=com/example/app
```

```json
{"metadata":"{\"channel\":\"beta\",\"owner\":\"release\"}"}
```

The complete metadata object is replaced. Metadata must parse as a JSON object; arrays, scalar JSON values, and invalid JSON are rejected. The updated directory is returned with `200 OK`.

## Publishing versions

A manifest is a normalized list of files. Every listed blob must already be registered, and its storage key and size must match the blob record.

```http
POST /cas/versions
```

```json
{
  "directoryKey": "com/example/app",
  "branchName": "main",
  "manifest": "{\"files\":[{\"path\":\"app.zip\",\"hash\":\"sha256:...\",\"size\":12345,\"storageKey\":\"blobs/sha256/ab/cd/...\"}]}",
  "metadata": "{\"build\":42}",
  "expectedParentVersionId": null,
  "createdBy": 7,
  "message": "Release 1.0"
}
```

`manifest` and `metadata` are JSON strings in the request. A manifest may be either a JSON array or an object containing a `files` array; responses always return the canonical object form:

```json
{"files":[{"path":"app.zip","hash":"sha256:...","size":12345,"storageKey":"blobs/sha256/ab/cd/...","url":"http://localhost:8080/files/blobs/sha256/ab/cd/..."}]}
```

Manifest paths must be unique. Files are sorted by path before hashing, so `manifestHash` is stable regardless of input order. Version metadata must be a JSON object. `expectedParentVersionId` provides optimistic concurrency: publishing fails if the branch head differs from that ID.

Response (`201 Created`) fields:

```json
{
  "id": 20,
  "directoryId": 10,
  "directoryKey": "com/example/app",
  "branchName": "main",
  "parentVersionId": null,
  "manifestHash": "sha256:...",
  "manifest": "{\"files\":[]}",
  "metadata": "{\"build\":42}",
  "createdAt": "2026-08-23T12:00:00Z",
  "createdBy": 7,
  "message": "Release 1.0"
}
```

## Branches and versions

```http
GET /cas/branches?directoryKey=com/example/app
GET /cas/versions/latest?directoryKey=com/example/app&branchName=main
GET /cas/versions/{versionId}?directoryKey=com/example/app
```

Branch responses contain `headVersionId`. A branch without a published version cannot return a latest version.

## Forking

```http
POST /cas/fork
```

```json
{
  "sourceDirectoryKey": "com/example/app",
  "sourceBranchName": "main",
  "targetDirectoryKey": "com/example/app-preview",
  "targetName": "Preview",
  "targetBranchName": "main",
  "metadata": "{\"build\":42}",
  "createdBy": 7,
  "message": "Preview fork"
}
```

Forking creates a new directory and publishes a target version whose manifest points to the source version's manifest. The target version receives the supplied version metadata; the new directory itself starts with `{}` directory metadata.

## Client implementation notes

- Upload blobs first, then use the returned `hash`, `storageKey`, and `sizeBytes` in the manifest.
- Keep directory metadata separate from version metadata. Directory metadata describes the directory; version metadata describes one publication.
- Treat hashes and manifests as opaque response values. Do not reconstruct a manifest hash client-side; use the server's `manifestHash`.
- URL-encode `directoryKey`, `branchName`, and blob hashes when placing them in query parameters or path segments.
- Typical failures are `401` for missing authentication, `403` for a missing permission, `404` for an unknown directory/blob/version/branch, `409` for duplicate resources, and `400` for invalid JSON or manifest data.
