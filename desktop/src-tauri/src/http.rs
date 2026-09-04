//! HTTP transport for manifests and content packages.
//!
//! This client never sends an `Authorization` header and never holds a token.
//! Manifest and content endpoints are anonymous, and that is not an oversight
//! on the server's part: refresh tokens are single-use and rotated, so if both
//! the UI and this engine held one, the second to refresh would present a
//! rotated token, trip reuse detection, and sign the user out because the
//! application raced itself. Keeping the session in exactly one place reduces
//! this module to HTTP plus verification.

use std::path::Path;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::Arc;
use std::time::Duration;

use tokio::io::AsyncWriteExt;

use crate::error::codes;
use crate::manifest::{ApiError, Catalog, TrackManifest};
use crate::model::EntityType;

/// Where the API lives. The desktop build talks to one deployment at a time and
/// has no UI for changing it, so it is read from the environment once at
/// startup and defaults to the local development server.
pub const API_BASE_URL_ENV: &str = "DEVATLAS_API_BASE_URL";
const DEFAULT_API_BASE_URL: &str = "http://localhost:8080/api/v1";

pub fn configured_base_url() -> String {
    std::env::var(API_BASE_URL_ENV)
        .ok()
        .filter(|value| !value.trim().is_empty())
        .unwrap_or_else(|| DEFAULT_API_BASE_URL.to_string())
}

/// Why a transfer did not produce bytes.
///
/// The variants exist to separate the three cases the queue treats
/// differently: the server was not reachable at all, the server answered
/// badly, and something local went wrong.
#[derive(Debug)]
pub enum TransferError {
    /// No response arrived: DNS failure, connection refused, connect timeout.
    /// This is the case that must not consume an attempt.
    Unreachable(String),
    /// A response arrived and then the connection dropped while its body was
    /// being read. Bytes may be on disk; the partial is a valid prefix.
    Interrupted(String),
    /// A response arrived with a status that is not success.
    Status {
        status: u16,
        code: String,
        retry_after: Option<Duration>,
    },
    /// The destination could not be written because there is no room.
    StorageFull(String),
    /// A local failure that is neither of the above.
    Local(String),
    /// A response the protocol does not describe.
    Unexpected(String),
}

impl std::fmt::Display for TransferError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            TransferError::Unreachable(m) => write!(f, "server unreachable: {m}"),
            TransferError::Interrupted(m) => write!(f, "transfer interrupted: {m}"),
            TransferError::Status { status, code, .. } => write!(f, "http {status} ({code})"),
            TransferError::StorageFull(m) => write!(f, "no space left: {m}"),
            TransferError::Local(m) => write!(f, "local failure: {m}"),
            TransferError::Unexpected(m) => write!(f, "unexpected response: {m}"),
        }
    }
}

/// The result of a conditional manifest request.
#[derive(Debug)]
pub enum ManifestFetch<T> {
    NotModified,
    Fetched { value: T, etag: Option<String> },
}

/// Everything needed to fetch one package.
pub struct PackageRequest<'a> {
    pub entity_type: EntityType,
    pub entity_id: &'a str,
    pub content_version: i64,
    /// Sent as `If-Match` when resuming, so content republished between
    /// attempts produces a `412` instead of a file spliced from two versions.
    pub expected_sha256: &'a str,
    pub destination: &'a Path,
    /// Bytes on disk so far, updated as they are written.
    ///
    /// Progress is published by a separate ticker rather than by a callback per
    /// chunk: a fast connection delivers thousands of chunks a second, and the
    /// UI is only allowed one event per quarter second per entity.
    pub progress: Option<Arc<AtomicU64>>,
}

#[derive(Clone)]
pub struct ContentClient {
    base_url: String,
    http: reqwest::Client,
}

impl ContentClient {
    pub fn new(base_url: impl Into<String>) -> Self {
        let http = reqwest::Client::builder()
            // A connect timeout is what makes "the network is not there"
            // observable in seconds rather than in whatever the operating
            // system decides. A whole-request timeout is deliberately not set:
            // it would abort a slow but healthy transfer.
            .connect_timeout(Duration::from_secs(10))
            .user_agent("devatlas-desktop")
            .build()
            .unwrap_or_default();
        Self {
            base_url: base_url.into().trim_end_matches('/').to_string(),
            http,
        }
    }

    pub fn base_url(&self) -> &str {
        &self.base_url
    }

    /// The catalog, one summary row per published track.
    pub async fn fetch_catalog(
        &self,
        etag: Option<&str>,
    ) -> Result<ManifestFetch<Catalog>, TransferError> {
        self.fetch_manifest(&format!("{}/manifest/catalog", self.base_url), etag)
            .await
    }

    /// The full manifest for one track.
    pub async fn fetch_track_manifest(
        &self,
        track_id: &str,
        etag: Option<&str>,
    ) -> Result<ManifestFetch<TrackManifest>, TransferError> {
        self.fetch_manifest(
            &format!("{}/manifest/track/{}", self.base_url, track_id),
            etag,
        )
        .await
    }

    /// Asks whether the server is reachable at all.
    ///
    /// Any answer counts, including an error status: the question is whether
    /// there is a route to the server, not whether it is happy. This is what
    /// lifts the engine out of its offline state, so treating a `503` as
    /// "still offline" would leave a reachable server unreachable forever.
    pub async fn probe(&self) -> bool {
        let request = self
            .http
            .get(format!("{}/manifest/catalog", self.base_url))
            .header(reqwest::header::IF_NONE_MATCH, "\"probe\"");
        request.send().await.is_ok()
    }

    async fn fetch_manifest<T: serde::de::DeserializeOwned>(
        &self,
        url: &str,
        etag: Option<&str>,
    ) -> Result<ManifestFetch<T>, TransferError> {
        let mut request = self.http.get(url);
        if let Some(etag) = etag {
            request = request.header(reqwest::header::IF_NONE_MATCH, etag);
        }

        let response = request.send().await.map_err(classify_send_error)?;
        let status = response.status();

        if status == reqwest::StatusCode::NOT_MODIFIED {
            return Ok(ManifestFetch::NotModified);
        }
        if !status.is_success() {
            let retry_after = retry_after_of(response.headers());
            let body = response.bytes().await.ok();
            let code = body
                .as_deref()
                .and_then(|bytes| serde_json::from_slice::<ApiError>(bytes).ok())
                .map(|error| error.code)
                .unwrap_or_else(|| default_manifest_code_for(status).to_string());
            return Err(TransferError::Status {
                status: status.as_u16(),
                code,
                retry_after,
            });
        }

        let etag = header_string(response.headers(), reqwest::header::ETAG);
        let body = response
            .bytes()
            .await
            .map_err(|e| TransferError::Interrupted(e.to_string()))?;
        let value = serde_json::from_slice::<T>(&body)
            .map_err(|e| TransferError::Unexpected(format!("manifest did not parse: {e}")))?;
        Ok(ManifestFetch::Fetched { value, etag })
    }

    /// Downloads one package into `destination`, resuming if a partial is
    /// already there.
    ///
    /// The caller learns how many bytes arrived by looking at the file, which
    /// is also how it learns after a restart. Returning a count as well would
    /// give two answers to one question and invite them to disagree.
    pub async fn download_package(&self, request: PackageRequest<'_>) -> Result<(), TransferError> {
        let resume_from = match tokio::fs::metadata(request.destination).await {
            Ok(meta) => meta.len(),
            Err(_) => 0,
        };

        let url = format!(
            "{}/content/{}/{}?version={}",
            self.base_url,
            request.entity_type.path_segment(),
            request.entity_id,
            request.content_version
        );

        let mut builder = self.http.get(&url);
        if resume_from > 0 {
            builder = builder
                .header(reqwest::header::RANGE, format!("bytes={resume_from}-"))
                .header(
                    reqwest::header::IF_MATCH,
                    format!("\"{}\"", request.expected_sha256),
                );
        }

        let response = builder.send().await.map_err(classify_send_error)?;
        let status = response.status();

        if !status.is_success() {
            let retry_after = retry_after_of(response.headers());
            let body = response.bytes().await.ok();
            let code = body
                .as_deref()
                .and_then(|bytes| serde_json::from_slice::<ApiError>(bytes).ok())
                .map(|error| error.code)
                .unwrap_or_else(|| default_code_for(status, request.entity_type).to_string());
            return Err(TransferError::Status {
                status: status.as_u16(),
                code,
                retry_after,
            });
        }

        // A `200` to a range request means the server ignored the range and is
        // sending the whole representation. Appending would splice the start of
        // the document onto the middle of the previous attempt, so the partial
        // is discarded and the transfer starts over.
        let append = if resume_from > 0 {
            if status == reqwest::StatusCode::PARTIAL_CONTENT {
                verify_content_range(response.headers(), resume_from)?;
                true
            } else {
                false
            }
        } else {
            false
        };

        let mut file = if append {
            tokio::fs::OpenOptions::new()
                .append(true)
                .open(request.destination)
                .await
        } else {
            tokio::fs::File::create(request.destination).await
        }
        .map_err(classify_io_error)?;

        if let Some(counter) = &request.progress {
            counter.store(if append { resume_from } else { 0 }, Ordering::Relaxed);
        }

        let mut response = response;
        // An error while reading the body arrived after the response did: the
        // server answered and then the connection dropped mid-body. That is a
        // wrong answer, not an absent one, and it consumes an attempt.
        while let Some(chunk) = response
            .chunk()
            .await
            .map_err(|e| TransferError::Interrupted(e.to_string()))?
        {
            file.write_all(&chunk).await.map_err(classify_io_error)?;
            if let Some(counter) = &request.progress {
                counter.fetch_add(chunk.len() as u64, Ordering::Relaxed);
            }
        }
        file.flush().await.map_err(classify_io_error)?;
        file.sync_all().await.map_err(classify_io_error)?;

        Ok(())
    }
}

/// A response the client could not even send. Only a connection-level failure
/// counts as being offline; a server that accepts the connection and then
/// answers slowly is answering badly, which is a different thing.
fn classify_send_error(error: reqwest::Error) -> TransferError {
    if error.is_connect() {
        TransferError::Unreachable(error.to_string())
    } else if error.is_timeout() {
        TransferError::Interrupted(error.to_string())
    } else {
        TransferError::Unexpected(error.to_string())
    }
}

fn classify_io_error(error: std::io::Error) -> TransferError {
    if is_storage_full(&error) {
        TransferError::StorageFull(error.to_string())
    } else {
        TransferError::Local(error.to_string())
    }
}

/// A full disk is the one local failure with its own protocol behaviour, so it
/// has to be recognised rather than lumped in with every other write error.
fn is_storage_full(error: &std::io::Error) -> bool {
    use std::io::ErrorKind;
    if matches!(
        error.kind(),
        ErrorKind::StorageFull | ErrorKind::QuotaExceeded
    ) {
        return true;
    }
    // ENOSPC on unix, ERROR_DISK_FULL on Windows, for the platforms and
    // toolchain versions that do not map them to a kind.
    matches!(error.raw_os_error(), Some(28) | Some(112))
}

fn verify_content_range(
    headers: &reqwest::header::HeaderMap,
    expected_start: u64,
) -> Result<(), TransferError> {
    let value = header_string(headers, reqwest::header::CONTENT_RANGE).ok_or_else(|| {
        TransferError::Unexpected("206 response carried no Content-Range".to_string())
    })?;
    // `bytes <start>-<end>/<total>`
    let start = value
        .trim()
        .strip_prefix("bytes ")
        .and_then(|rest| rest.split('-').next())
        .and_then(|start| start.trim().parse::<u64>().ok())
        .ok_or_else(|| TransferError::Unexpected(format!("unparsable Content-Range: {value}")))?;
    if start != expected_start {
        return Err(TransferError::Unexpected(format!(
            "206 resumed at {start}, expected {expected_start}"
        )));
    }
    Ok(())
}

fn header_string(
    headers: &reqwest::header::HeaderMap,
    name: reqwest::header::HeaderName,
) -> Option<String> {
    headers
        .get(name)
        .and_then(|value| value.to_str().ok())
        .map(|value| value.to_string())
}

fn retry_after_of(headers: &reqwest::header::HeaderMap) -> Option<Duration> {
    header_string(headers, reqwest::header::RETRY_AFTER)
        .and_then(|value| value.trim().parse::<u64>().ok())
        .map(Duration::from_secs)
}

/// The code to report when the body carried none.
fn default_code_for(status: reqwest::StatusCode, entity_type: EntityType) -> &'static str {
    match status.as_u16() {
        404 => entity_type.not_found_code(),
        409 => codes::CONTENT_VERSION_SUPERSEDED,
        412 => codes::CONTENT_CHANGED_DURING_RESUME,
        429 => codes::RATE_LIMITED,
        503 => codes::SERVICE_UNAVAILABLE,
        s if (500..600).contains(&s) => codes::INTERNAL_ERROR,
        _ => codes::UNEXPECTED_RESPONSE,
    }
}

/// The same mapping for manifest requests, where a `404` names a track rather
/// than an entity.
fn default_manifest_code_for(status: reqwest::StatusCode) -> &'static str {
    match status.as_u16() {
        404 => codes::TRACK_NOT_FOUND,
        429 => codes::RATE_LIMITED,
        503 => codes::SERVICE_UNAVAILABLE,
        s if (500..600).contains(&s) => codes::INTERNAL_ERROR,
        _ => codes::UNEXPECTED_RESPONSE,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use wiremock::matchers::{header, method, path, query_param};
    use wiremock::{Mock, MockServer, ResponseTemplate};

    const DIGEST: &str = "9f2c4e6a8b0d1f3579ace0246813579bdf02468ace13579bdf02468ace13579b";

    fn temp_file(dir: &tempfile::TempDir, name: &str) -> std::path::PathBuf {
        dir.path().join(name)
    }

    #[tokio::test]
    async fn a_fresh_download_writes_the_whole_body() {
        let server = MockServer::start().await;
        Mock::given(method("GET"))
            .and(path("/content/lesson/l1"))
            .and(query_param("version", "12"))
            .respond_with(ResponseTemplate::new(200).set_body_bytes(b"package-bytes".to_vec()))
            .mount(&server)
            .await;

        let dir = tempfile::tempdir().expect("temp dir");
        let destination = temp_file(&dir, "l1.part");
        let client = ContentClient::new(server.uri());
        client
            .download_package(PackageRequest {
                entity_type: EntityType::Lesson,
                entity_id: "l1",
                content_version: 12,
                expected_sha256: DIGEST,
                destination: &destination,
                progress: None,
            })
            .await
            .expect("download");

        assert_eq!(
            std::fs::read(&destination).expect("read"),
            b"package-bytes".to_vec()
        );
    }

    #[tokio::test]
    async fn a_resume_sends_range_and_if_match_and_appends() {
        let server = MockServer::start().await;
        Mock::given(method("GET"))
            .and(path("/content/lesson/l1"))
            .and(header("range", "bytes=8-"))
            .and(header("if-match", format!("\"{DIGEST}\"").as_str()))
            .respond_with(
                ResponseTemplate::new(206)
                    .insert_header("content-range", "bytes 8-12/13")
                    .set_body_bytes(b"bytes".to_vec()),
            )
            .mount(&server)
            .await;

        let dir = tempfile::tempdir().expect("temp dir");
        let destination = temp_file(&dir, "l1.part");
        std::fs::write(&destination, b"package-").expect("seed partial");

        ContentClient::new(server.uri())
            .download_package(PackageRequest {
                entity_type: EntityType::Lesson,
                entity_id: "l1",
                content_version: 12,
                expected_sha256: DIGEST,
                destination: &destination,
                progress: None,
            })
            .await
            .expect("resume");

        assert_eq!(
            std::fs::read(&destination).expect("read"),
            b"package-bytes".to_vec()
        );
    }

    #[tokio::test]
    async fn a_200_answer_to_a_range_request_restarts_from_zero() {
        let server = MockServer::start().await;
        Mock::given(method("GET"))
            .and(path("/content/lesson/l1"))
            .respond_with(ResponseTemplate::new(200).set_body_bytes(b"whole-document".to_vec()))
            .mount(&server)
            .await;

        let dir = tempfile::tempdir().expect("temp dir");
        let destination = temp_file(&dir, "l1.part");
        std::fs::write(&destination, b"stale-pre").expect("seed partial");

        ContentClient::new(server.uri())
            .download_package(PackageRequest {
                entity_type: EntityType::Lesson,
                entity_id: "l1",
                content_version: 12,
                expected_sha256: DIGEST,
                destination: &destination,
                progress: None,
            })
            .await
            .expect("restart");

        // Appending here would have produced a document made of two halves that
        // never belonged together, and its digest would have failed for a
        // reason no log would explain.
        assert_eq!(
            std::fs::read(&destination).expect("read"),
            b"whole-document".to_vec()
        );
    }

    #[tokio::test]
    async fn a_412_on_resume_is_reported_with_its_code() {
        let server = MockServer::start().await;
        Mock::given(method("GET"))
            .and(path("/content/lesson/l1"))
            .respond_with(ResponseTemplate::new(412).set_body_json(serde_json::json!({
                "code": "CONTENT_CHANGED_DURING_RESUME",
                "message": "the entity was republished"
            })))
            .mount(&server)
            .await;

        let dir = tempfile::tempdir().expect("temp dir");
        let destination = temp_file(&dir, "l1.part");
        std::fs::write(&destination, b"partial").expect("seed partial");

        let error = ContentClient::new(server.uri())
            .download_package(PackageRequest {
                entity_type: EntityType::Lesson,
                entity_id: "l1",
                content_version: 12,
                expected_sha256: DIGEST,
                destination: &destination,
                progress: None,
            })
            .await
            .expect_err("must fail");

        match error {
            TransferError::Status { status, code, .. } => {
                assert_eq!(status, 412);
                assert_eq!(code, codes::CONTENT_CHANGED_DURING_RESUME);
            }
            other => panic!("expected a status error, got {other:?}"),
        }
    }

    #[tokio::test]
    async fn a_404_reports_the_code_for_the_entity_type() {
        let server = MockServer::start().await;
        Mock::given(method("GET"))
            .and(path("/content/mind_map/m1"))
            .respond_with(ResponseTemplate::new(404).set_body_bytes(b"".to_vec()))
            .mount(&server)
            .await;

        let dir = tempfile::tempdir().expect("temp dir");
        let error = ContentClient::new(server.uri())
            .download_package(PackageRequest {
                entity_type: EntityType::MindMap,
                entity_id: "m1",
                content_version: 5,
                expected_sha256: DIGEST,
                destination: &temp_file(&dir, "m1.part"),
                progress: None,
            })
            .await
            .expect_err("must fail");

        match error {
            TransferError::Status { code, .. } => assert_eq!(code, codes::MIND_MAP_NOT_FOUND),
            other => panic!("expected a status error, got {other:?}"),
        }
    }

    #[tokio::test]
    async fn a_429_carries_its_retry_after() {
        let server = MockServer::start().await;
        Mock::given(method("GET"))
            .and(path("/content/lesson/l1"))
            .respond_with(ResponseTemplate::new(429).insert_header("retry-after", "7"))
            .mount(&server)
            .await;

        let dir = tempfile::tempdir().expect("temp dir");
        let error = ContentClient::new(server.uri())
            .download_package(PackageRequest {
                entity_type: EntityType::Lesson,
                entity_id: "l1",
                content_version: 12,
                expected_sha256: DIGEST,
                destination: &temp_file(&dir, "l1.part"),
                progress: None,
            })
            .await
            .expect_err("must fail");

        match error {
            TransferError::Status {
                code, retry_after, ..
            } => {
                assert_eq!(code, codes::RATE_LIMITED);
                assert_eq!(retry_after, Some(Duration::from_secs(7)));
            }
            other => panic!("expected a status error, got {other:?}"),
        }
    }

    #[tokio::test]
    async fn an_unreachable_server_is_distinguished_from_a_bad_answer() {
        // Bind a listener only to learn a port nothing is listening on, then
        // release it: connecting there is refused rather than answered.
        let listener = std::net::TcpListener::bind("127.0.0.1:0").expect("bind");
        let port = listener.local_addr().expect("addr").port();
        drop(listener);
        let uri = format!("http://127.0.0.1:{port}");

        let dir = tempfile::tempdir().expect("temp dir");
        let error = ContentClient::new(uri)
            .download_package(PackageRequest {
                entity_type: EntityType::Lesson,
                entity_id: "l1",
                content_version: 12,
                expected_sha256: DIGEST,
                destination: &temp_file(&dir, "l1.part"),
                progress: None,
            })
            .await
            .expect_err("must fail");

        assert!(
            matches!(error, TransferError::Unreachable(_)),
            "expected an unreachable error, got {error:?}"
        );
    }

    #[tokio::test]
    async fn a_304_manifest_is_reported_as_not_modified() {
        let server = MockServer::start().await;
        Mock::given(method("GET"))
            .and(path("/manifest/catalog"))
            .and(header("if-none-match", "\"abc\""))
            .respond_with(ResponseTemplate::new(304))
            .mount(&server)
            .await;

        let fetch = ContentClient::new(server.uri())
            .fetch_catalog(Some("\"abc\""))
            .await
            .expect("fetch");
        assert!(matches!(fetch, ManifestFetch::NotModified));
    }

    #[tokio::test]
    async fn a_track_manifest_is_returned_with_its_etag() {
        let server = MockServer::start().await;
        Mock::given(method("GET"))
            .and(path("/manifest/track/t1"))
            .respond_with(
                ResponseTemplate::new(200)
                    .insert_header("etag", "\"deadbeef\"")
                    .set_body_json(serde_json::json!({
                        "track_id": "t1",
                        "slug": "angular-path",
                        "content_version": 47,
                        "title": "The Angular Path",
                        "modules": [],
                        "entities": []
                    })),
            )
            .mount(&server)
            .await;

        match ContentClient::new(server.uri())
            .fetch_track_manifest("t1", None)
            .await
            .expect("fetch")
        {
            ManifestFetch::Fetched { value, etag } => {
                assert_eq!(value.content_version, 47);
                assert_eq!(etag.as_deref(), Some("\"deadbeef\""));
            }
            other => panic!("expected a fetched manifest, got {other:?}"),
        }
    }
}
