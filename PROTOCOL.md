# modsync-server Protocol

This document describes the HTTP API exposed by `modsync-server` so that a client program can synchronise a local mod folder with the server.

---

## Endpoints

### `GET /manifest.json`

Returns the current pack manifest as JSON.

**Response headers**

| Header | Value |
|---|---|
| `Content-Type` | `application/json` |

**Response body** — a single JSON object:

```json
{
  "pack_name": "MyModpack",
  "pack_version": "28b971a69608cbd262a419a327acd3af1084d60e1253596a4af9ff8db794c0dc",
  "minecraft_version": "1.21.1",
  "loader": {
    "type": "fabric",
    "version": "0.16.0"
  },
  "mods": [
    {
      "filename": "lithium-0.11.jar",
      "sha256": "28b7856150f7eb569e9f495b6a836689ea1432ceee1e872826b46812d9ee8b5c",
      "url": "http://your-server:8080/jars/lithium-0.11.jar"
    },
    {
      "filename": "sodium-0.5.jar",
      "sha256": "4edf657b037458a5b2a87951de9bf5e9ee59506691c0a5f69f65b8c048b39aca",
      "url": "http://your-server:8080/jars/sodium-0.5.jar"
    }
  ]
}
```

**Fields**

| Field | Type | Description |
|---|---|---|
| `pack_name` | string | Human-readable pack name from server config |
| `pack_version` | string | SHA-256 fingerprint of the whole pack (see below) |
| `minecraft_version` | string | Minecraft version string, e.g. `"1.21.1"` |
| `loader.type` | string | Mod loader: `"fabric"` or `"neoforge"` |
| `loader.version` | string | Mod loader version string |
| `mods[].filename` | string | Bare filename, e.g. `"sodium-0.5.jar"` |
| `mods[].sha256` | string | Lowercase hex SHA-256 of the jar file |
| `mods[].url` | string | Absolute URL to download this jar |

The `mods` array is sorted alphabetically by `filename`. The order is stable across requests as long as the server has not been restarted with different jars.

---

### `pack_version` algorithm

`pack_version` is a content-addressed fingerprint of the entire mod set. It lets a client skip all per-file checks when nothing has changed.

```
pack_version = sha256( concat( mods[0].sha256, mods[1].sha256, … ) )
```

Steps:
1. Take each `sha256` string from `mods[]` in the order they appear in the manifest (alphabetical by `filename`).
2. Concatenate those lowercase hex strings directly, with no separator.
3. Compute SHA-256 of the resulting UTF-8 string and hex-encode it.

A client can recompute this locally from its own files to confirm it already has the correct pack without fetching the full manifest or comparing individual files.

---

### `GET /jars/<filename>`

Downloads a single mod jar.

`<filename>` must be a bare filename (no path separators, no `..`). Only filenames that appear in the current manifest are served; anything else returns 404.

**Response headers (200)**

| Header | Value |
|---|---|
| `Content-Type` | `application/java-archive` |
| `Content-Length` | Exact byte size of the jar |

**Response body** — raw jar bytes.

**Status codes**

| Code | Meaning |
|---|---|
| 200 | Jar found and returned |
| 404 | Filename not in manifest, or contains unsafe characters |
| 405 | Method other than GET |
| 500 | Server-side error |

---

### `GET /modsync.exe` (and other static assets)

Downloads a static file from the server's `static_directory`.

**Response headers (200)**

| Header | Value |
|---|---|
| `Content-Type` | `application/octet-stream` |
| `Content-Disposition` | `attachment; filename="<filename>"` (`.exe` files only) |
| `Content-Length` | Exact byte size |

**Status codes** — same as `/jars/`.

---

### `GET /`

HTML landing page for humans. Contains the manifest URL in a copyable block and a download link for `modsync.exe`. Not intended for programmatic use.

---

## Client sync algorithm

The recommended sync loop for a client:

```
1.  Fetch GET /manifest.json
2.  If local stored pack_version == manifest.pack_version → nothing to do, exit.
3.  Build a set of expected filenames from manifest.mods[].filename.
4.  Delete any local mod files NOT in the expected set.
5.  For each mod entry in manifest.mods[]:
      a. If the local file exists and sha256(local file) == entry.sha256 → skip.
      b. Otherwise: GET entry.url, write to disk as entry.filename.
      c. Verify sha256(downloaded file) == entry.sha256; abort if mismatch.
6.  Store manifest.pack_version locally for next run.
```

Steps 4 and 5 can be done in any order. Downloading jars in parallel (step 5) is safe — the server is stateless across requests.

---

## Error handling notes

- The server returns no response body on 4xx/5xx — only a status line.
- The manifest is built once at server startup. Restarting the server is required to pick up new or removed jars.
- The server does not support range requests. Re-download the full jar on any hash mismatch.
